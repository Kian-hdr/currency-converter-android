package dev.kiankonrad.currencycalculator

data class Entry(val expression: String = "0", val justEvaluated: Boolean = false)

object MoneyInput {
 private val operators = setOf('+','−','-','×','*','÷','/')
 fun press(entry: Entry, key: String): Entry {
  var text = entry.expression.ifEmpty { "0" }
  if(key == "AC") return Entry()
  if(key == "⌫") return Entry(text.dropLast(1).ifEmpty { "0" })
  if(key == "=") {
   val result = MoneyEngine.evaluate(text)
   return if(result.value != null) Entry(MoneyEngine.format(result.value, 24), true) else entry
  }
  if(key.length == 1 && key[0].isDigit()) {
   if(entry.justEvaluated || text == "0") text = ""
   if(text.lastOrNull() in listOf(')', '%')) text += "×"
   return Entry((text + key).take(256))
  }
  if(key == ".") {
   if(entry.justEvaluated) text = "0"
   val tail = text.takeLastWhile { it.isDigit() || it == '.' }
   if('.' in tail) return entry
   if(tail.isEmpty()) text += if(text.lastOrNull() in listOf(')', '%')) "×0" else "0"
   return Entry((text + ".").take(256))
  }
  if(key == "+/−") {
   if(text.last() in operators || text.last() == '(') {
    val lastIsUnaryMinus = text.last() in listOf('−','-') && (text.length == 1 || text[text.length-2] in operators || text[text.length-2]=='(')
    return Entry(if(lastIsUnaryMinus) text.dropLast(1).ifEmpty {"0"} else text+"−")
   }
   var end = text.length
   while(end>0 && text[end-1]=='%') end--
   var start = end
   if(end>0 && text[end-1]==')') {
    var depth=0
    for(i in end-1 downTo 0) {
     if(text[i]==')') depth++
     if(text[i]=='(') depth--
     if(depth==0) {start=i;break}
    }
   } else {
    while(start>0 && (text[start-1].isDigit() || text[start-1]=='.')) start--
   }
   if(start==end) return entry
   val unary = text.getOrNull(start-1) in listOf('−','-') && (start==1 || text.getOrNull(start-2) in operators || text.getOrNull(start-2)=='(')
   return Entry(if(unary) text.removeRange(start-1,start) else text.substring(0,start)+"−"+text.substring(start))
  }
  if(key == "( )") {
   val balance = text.count { it == '(' } - text.count { it == ')' }
   val operand = text.lastOrNull()?.let { it.isDigit() || it in ")%" } == true
   return Entry(when { balance > 0 && operand -> text+")"; text == "0" || entry.justEvaluated -> "("; operand -> text+"×("; else -> text+"(" }.take(256))
  }
  if(key == "%") return if(text.lastOrNull()?.let { it.isDigit() || it == ')' } == true) Entry((text+"%").take(256)) else entry
  if(key.length == 1 && key[0] in operators) {
   if(text.last() == '(') return if(key in listOf("−","-")) Entry(text+"−") else entry
   if(text.last() in operators) {
    if(key in listOf("−","-") && text.last() !in listOf('−','-')) return Entry(text+"−")
    text = text.dropLastWhile { it in operators }.ifEmpty { "0" }
   }
   return Entry((text+key).take(256))
  }
  return entry
 }
}
