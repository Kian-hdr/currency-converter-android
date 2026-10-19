package dev.kiankonrad.currencycalculator

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import java.util.Currency
import java.util.Locale
import java.util.concurrent.TimeUnit

/** ISO fiat metadata from the platform; the flag identifies a representative territory. */
data class FiatCurrency(
    val code: String,
    val name: String,
    val symbol: String,
    val flag: String,
    val fractionDigits: Int
)

/** Rates are units per [base]. Provider time and device retrieval time are intentionally separate. */
data class RateSnapshot(
    val base: String,
    val rates: Map<String, BigDecimal>,
    val fetchedAtMs: Long,
    val providerAsOfMs: Long?,
    val source: String,
    val currencies: List<FiatCurrency>
) {
    fun crossRate(from: String, to: String): BigDecimal? {
        val fromRate = rates[from] ?: return null
        val toRate = rates[to] ?: return null
        if (fromRate.signum() <= 0 || toRate.signum() <= 0) return null
        return toRate.divide(fromRate, MathContext.DECIMAL128)
    }

    /** An old source timestamp is never made fresh merely by downloading it again. */
    fun isStale(nowMs: Long = System.currentTimeMillis()): Boolean {
        val asOf = providerAsOfMs ?: return true
        return asOf > nowMs + 300_000L || nowMs - asOf > STALE_AFTER_MS
    }

    companion object { const val STALE_AFTER_MS = 15 * 60_000L }
}

data class RatesResult(
    val snapshot: RateSnapshot?,
    val error: String? = null,
    val fromCache: Boolean = false
)

/**
 * Free keyless current snapshots from FXAPI, whose published refresh interval is five minutes.
 * One USD table serves every pair locally. No amounts, calculations, or user identifiers are sent.
 * Caller owns a foreground-only refresh loop; this repository never schedules background work.
 */
class RatesRepository(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build(),
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val prefs = context.applicationContext.getSharedPreferences("exchange_rates", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    @Volatile private var memory: RateSnapshot? = null
    private var lastAttemptMs: Long? = null
    private var lastError: String? = null

    fun cached(): RateSnapshot? {
        memory?.let { return it }
        val stored = prefs.getString("last_good", null) ?: return null
        return runCatching { RatesCodec.decodeCache(stored, clock()) }.getOrNull()?.also { memory = it }
    }

    suspend fun refresh(force: Boolean = false): RatesResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = clock()
            val previous = cached()
            val interval = if (force) 10_000L else 60_000L
            val elapsed = lastAttemptMs?.let { now - it }
            if (elapsed != null && elapsed >= 0 && elapsed < interval) {
                return@withLock RatesResult(previous, lastError, fromCache = true)
            }
            if (!force && lastAttemptMs == null && previous != null &&
                now >= previous.fetchedAtMs && now - previous.fetchedAtMs < 60_000L) {
                return@withLock RatesResult(previous, fromCache = true)
            }
            lastAttemptMs = now
            try {
                val request = Request.Builder().url(ENDPOINT)
                    .header("Accept", "application/json")
                    .header("User-Agent", "CurrencyConverter/1.0 Android")
                    .get().build()
                val snapshot = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException(if (response.code == 429) "Rate service is busy. Try again shortly." else "Rate service unavailable (${response.code}).")
                    }
                    val body = response.body ?: throw IOException("Rate service returned no data.")
                    if (body.contentLength() > 1_000_000L) throw IOException("Rate response was too large.")
                    RatesCodec.parseProvider(body.string(), clock())
                }
                // Commit only a completely validated snapshot; failures preserve last-good data.
                val persisted = prefs.edit().putString("last_good", RatesCodec.encodeCache(snapshot)).commit()
                memory = snapshot
                lastError = if (persisted) null else "Rates updated, but could not save offline data."
                RatesResult(snapshot, lastError)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                lastError = when (error) {
                    is java.net.UnknownHostException -> "No connection. Using saved rates if available."
                    is java.net.SocketTimeoutException -> "Rate request timed out. Using saved rates if available."
                    is IOException -> error.message ?: "Could not refresh rates."
                    else -> "Rate service returned invalid data. Using saved rates if available."
                }
                RatesResult(previous, lastError, fromCache = previous != null)
            }
        }
    }

    companion object {
        const val ENDPOINT = "https://fxapi.app/api/usd.json"
        const val SOURCE = "FXAPI"
        const val SOURCE_URL = "https://fxapi.app/"
    }
}

/** Pure parsing/cache helpers are independently testable without Android or network access. */
internal object RatesCodec {
    fun parseProvider(body: String, nowMs: Long): RateSnapshot {
        require(body.length <= 1_000_000) { "Oversized response" }
        val json = JSONObject(body)
        require(json.getString("base").uppercase(Locale.ROOT) == "USD") { "Unexpected rate base" }
        val timestamp = Instant.parse(json.getString("timestamp")).toEpochMilli()
        require(timestamp > 0 && timestamp <= nowMs + 300_000L) { "Invalid provider time" }
        val rawRates = json.getJSONObject("rates")
        val supported = FiatCatalog.all.associateBy { it.code }
        val rates = linkedMapOf<String, BigDecimal>()
        rawRates.keys().forEach { rawCode ->
            val code = rawCode.uppercase(Locale.ROOT)
            if (code in supported) {
                val rate = rawRates.get(rawCode).toString().toBigDecimalOrNull()
                    ?: throw IllegalArgumentException("Invalid rate")
                require(rate.signum() > 0 && rate.precision() <= 40 && rate.scale() in -12..30) { "Invalid rate" }
                rates[code] = rate
            }
        }
        // FXAPI omits the identity rate in some base tables. It is exact by definition.
        rates["USD"]?.let { require(it.compareTo(BigDecimal.ONE) == 0) { "Invalid identity rate" } }
        rates["USD"] = BigDecimal.ONE
        require(rates.size > 1) { "No usable fiat rates" }
        return RateSnapshot("USD", rates.toMap(), nowMs, timestamp, RatesRepository.SOURCE,
            FiatCatalog.all.filter { it.code in rates })
    }

    fun encodeCache(snapshot: RateSnapshot): String {
        val rates = JSONObject()
        snapshot.rates.forEach { (code, rate) -> rates.put(code, rate.toPlainString()) }
        return JSONObject().put("version", 1).put("base", snapshot.base)
            .put("fetchedAtMs", snapshot.fetchedAtMs).put("providerAsOfMs", snapshot.providerAsOfMs)
            .put("source", snapshot.source).put("rates", rates).toString()
    }

    fun decodeCache(body: String, nowMs: Long): RateSnapshot {
        val cache = JSONObject(body)
        require(cache.getInt("version") == 1 && cache.getString("source") == RatesRepository.SOURCE)
        val fetched = cache.getLong("fetchedAtMs")
        require(fetched > 0 && fetched <= nowMs + 300_000L)
        val provider = cache.getLong("providerAsOfMs")
        val proxy = JSONObject().put("base", cache.getString("base"))
            .put("timestamp", Instant.ofEpochMilli(provider).toString()).put("rates", cache.getJSONObject("rates"))
        return parseProvider(proxy.toString(), nowMs).copy(fetchedAtMs = fetched)
    }
}

/** Current territorial fiat currencies, excluding crypto, commodities, and obsolete currency codes. */
object FiatCatalog {
    private val preferredTerritory = mapOf(
        "EUR" to "EU", "USD" to "US", "GBP" to "GB", "AED" to "AE", "AUD" to "AU",
        "CAD" to "CA", "CHF" to "CH", "NZD" to "NZ", "JPY" to "JP", "CNY" to "CN",
        "XAF" to "CM", "XOF" to "SN", "XCD" to "AG", "XPF" to "PF"
    )
    private val symbols = mapOf("EUR" to "€", "USD" to "$", "GBP" to "£", "JPY" to "¥",
        "CNY" to "¥", "AED" to "\u20C3", "INR" to "₹", "KRW" to "₩", "RUB" to "₽",
        "TRY" to "₺", "THB" to "฿", "VND" to "₫", "ILS" to "₪", "PHP" to "₱")
    val all: List<FiatCurrency> by lazy {
        val territories = linkedMapOf<String, String>()
        Locale.getISOCountries().sorted().forEach { territory ->
            runCatching { Currency.getInstance(Locale("", territory)) }.getOrNull()?.let { currency ->
                territories.putIfAbsent(currency.currencyCode, territory)
            }
        }
        // Explicit regional codes ensure platform territory mappings do not drop them.
        preferredTerritory.forEach { (code, territory) -> territories[code] = territory }
        territories.mapNotNull { (code, territory) ->
            runCatching { Currency.getInstance(code) }.getOrNull()?.let { currency ->
                FiatCurrency(code, currency.getDisplayName(Locale.ENGLISH), symbols[code] ?: currency.getSymbol(Locale.US),
                    flag(preferredTerritory[code] ?: territory), currency.defaultFractionDigits.coerceIn(0, 4))
            }
        }.sortedBy { it.code }
    }

    fun flag(territory: String): String = if (territory.length == 2 && territory.all { it in 'A'..'Z' }) {
        territory.map { String(Character.toChars(0x1F1E6 + it.code - 'A'.code)) }.joinToString("")
    } else "🌐"
}
