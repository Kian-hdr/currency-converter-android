package dev.kiankonrad.currencycalculator

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.math.BigDecimal
import java.math.MathContext
import org.json.JSONArray
import org.json.JSONObject

data class CalculationRecord(val expression:String, val from:String, val to:String, val result:String)

class ConverterState(context:Context) {
 private val prefs = context.applicationContext.getSharedPreferences("converter_state",Context.MODE_PRIVATE)
 private val repository = RatesRepository(context.applicationContext)
 var entry by mutableStateOf(Entry(prefs.getString("expression","0") ?: "0",prefs.getBoolean("evaluated",false)))
  private set
 var completedExpression by mutableStateOf(prefs.getString("completed_expression", "") ?: "")
  private set
 val expressionForDisplay get() = if(entry.justEvaluated && completedExpression.isNotEmpty()) completedExpression else entry.expression
 var from by mutableStateOf(prefs.getString("from","EUR") ?: "EUR")
  private set
 var to by mutableStateOf(prefs.getString("to","USD") ?: "USD")
  private set
 var snapshot by mutableStateOf(repository.cached())
  private set
 var online by mutableStateOf(true)
  private set
 fun updateConnectivity(available:Boolean) { online=available }
 var loading by mutableStateOf(false)
  private set
 var networkError by mutableStateOf<String?>(null)
  private set
 var history by mutableStateOf(readHistory())
  private set
 val evaluation get() = MoneyEngine.evaluate(entry.expression)
 val rate get() = if(from==to) BigDecimal.ONE else snapshot?.crossRate(from,to)
 val converted get() = evaluation.value?.let { value -> rate?.let { value.multiply(it,MathContext.DECIMAL128) } }
 val currencies get() = snapshot?.currencies?.takeIf { it.isNotEmpty() } ?: FiatCatalog.all
 fun currency(code:String) = currencies.find { it.code==code } ?: initialCurrencies.find { it.code==code } ?: FiatCurrency(code,code,code,"🌐",2)
 fun press(key:String) {
  if(key!="=") completedExpression=""
  if(key=="=" && evaluation.value != null && !entry.justEvaluated && entry.expression!="0") {
   completedExpression=entry.expression
   val result = MoneyEngine.format(evaluation.value!!,16)
   history = (listOf(CalculationRecord(entry.expression,from,to,result))+history).take(30)
   saveHistory()
  }
  entry = MoneyInput.press(entry,key)
  persist()
 }
 fun chooseCurrency(target:Boolean,code:String) { if(target) to=code else from=code; persist() }
 fun swap() {
  val previous=from
  val amount=converted
  from=to;to=previous
  completedExpression=""
  if(amount!=null) entry=Entry(MoneyEngine.format(amount,24),true)
  persist()
 }
 fun restore(record:CalculationRecord) { from=record.from;to=record.to;entry=Entry(record.expression);completedExpression="";persist() }
 fun clearHistory() { history=emptyList();saveHistory() }
 suspend fun refresh(force:Boolean=false) {
  if(loading) return
  loading=true
  try {
   val result=repository.refresh(force)
   snapshot=result.snapshot ?: snapshot
   networkError=result.error
  } finally { loading=false }
 }
 private fun persist() { prefs.edit().putString("expression",entry.expression).putBoolean("evaluated",entry.justEvaluated).putString("completed_expression",completedExpression).putString("from",from).putString("to",to).apply() }
 private fun readHistory():List<CalculationRecord> = runCatching {
  val rows=JSONArray(prefs.getString("history","[]"))
  (0 until rows.length()).map { i-> rows.getJSONObject(i).let { CalculationRecord(it.getString("expression"),it.getString("from"),it.getString("to"),it.getString("result")) } }.take(30)
 }.getOrDefault(emptyList())
 private fun saveHistory() {
  val rows=JSONArray();history.forEach { rows.put(JSONObject().put("expression",it.expression).put("from",it.from).put("to",it.to).put("result",it.result)) }
  prefs.edit().putString("history",rows.toString()).apply()
 }
 companion object {
  val initialCurrencies=listOf(FiatCurrency("EUR","Euro","€","🇪🇺",2),FiatCurrency("USD","US dollar","$","🇺🇸",2),FiatCurrency("GBP","British pound","£","🇬🇧",2),FiatCurrency("AED","UAE dirham","\u20C3","🇦🇪",2),FiatCurrency("JPY","Japanese yen","¥","🇯🇵",0))
 }
}
