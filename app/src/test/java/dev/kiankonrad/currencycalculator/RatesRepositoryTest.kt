package dev.kiankonrad.currencycalculator

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class RatesRepositoryTest {
    private val now = Instant.parse("2026-10-19T01:50:00Z").toEpochMilli()
    private fun body(timestamp: String = "2026-10-19T01:45:00Z", rates: String = "\"EUR\":0.89,\"GBP\":0.75,\"JPY\":158.2,\"BTC\":0.00001") =
        """{"base":"USD","timestamp":"$timestamp","rates":{$rates}}"""

    @Test fun parsesCurrentSnapshotAndExcludesCrypto() {
        val snapshot = RatesCodec.parseProvider(body(), now)
        assertEquals(BigDecimal.ONE, snapshot.rates["USD"])
        assertFalse(snapshot.rates.containsKey("BTC"))
        assertTrue(snapshot.currencies.any { it.code == "EUR" && it.symbol == "€" && it.flag == "🇪🇺" })
        assertEquals(BigDecimal("0.89"), snapshot.crossRate("USD", "EUR"))
        assertEquals(BigDecimal.ONE, snapshot.crossRate("EUR", "EUR"))
        assertNull(snapshot.crossRate("USD", "XYZ"))
        assertFalse(snapshot.isStale(now))
    }

    @Test fun cacheRoundTripRetainsSourceTimeAndRetrievalTime() {
        val snapshot = RatesCodec.parseProvider(body(), now - 10_000)
        val restored = RatesCodec.decodeCache(RatesCodec.encodeCache(snapshot), now)
        assertEquals(snapshot, restored)
    }

    @Test fun downloadingOldDataDoesNotMakeItFresh() {
        val snapshot = RatesCodec.parseProvider(body("2026-10-18T01:45:00Z"), now)
        assertEquals(now, snapshot.fetchedAtMs)
        assertTrue(snapshot.isStale(now))
    }

    @Test fun futureSourceTimeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            RatesCodec.parseProvider(body("2026-10-19T03:45:00Z"), now)
        }
    }

    @Test fun invalidOrZeroRateRejectsWholeSnapshot() {
        for (rates in listOf("\"EUR\":0", "\"EUR\":-1", "\"EUR\":\"NaN\"", "\"USD\":2,\"EUR\":0.89")) {
            assertThrows(IllegalArgumentException::class.java) { RatesCodec.parseProvider(body(rates = rates), now) }
        }
    }

    @Test fun wrongBaseAndCorruptCacheAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { RatesCodec.parseProvider(body().replace("\"USD\"", "\"EUR\""), now) }
        assertThrows(Exception::class.java) { RatesCodec.decodeCache("{}", now) }
    }
}
