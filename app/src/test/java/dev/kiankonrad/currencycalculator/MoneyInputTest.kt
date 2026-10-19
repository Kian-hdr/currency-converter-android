package dev.kiankonrad.currencycalculator
import org.junit.Assert.*
import org.junit.Test
class MoneyInputTest {
 @Test fun digitsReplaceInitialZero() { assertEquals("12", MoneyInput.press(MoneyInput.press(Entry(), "1"),"2").expression) }
 @Test fun decimalStartsAtZero() { assertEquals("0.",MoneyInput.press(Entry(),".").expression) }
 @Test fun duplicateDecimalIgnored() { assertEquals("1.2",MoneyInput.press(Entry("1.2"),".").expression) }
 @Test fun newOperandDecimal() { assertEquals("1+0.",MoneyInput.press(Entry("1+"),".").expression) }
 @Test fun operatorReplacement() { assertEquals("12×",MoneyInput.press(Entry("12+"),"×").expression) }
 @Test fun unaryNegativeAfterMultiply() { assertEquals("12×−",MoneyInput.press(Entry("12×"),"−").expression) }
 @Test fun signTogglesLastOperand() { assertEquals("2+−3",MoneyInput.press(Entry("2+3"),"+/−").expression); assertEquals("2+3",MoneyInput.press(Entry("2+−3"),"+/−").expression) }
 @Test fun parenthesisKeyCloses() { assertEquals("(12)",MoneyInput.press(Entry("(12"),"( )").expression) }
 @Test fun adjacentParenthesisMultiplies() { assertEquals("12×(",MoneyInput.press(Entry("12"),"( )").expression) }
 @Test fun equalsAndNextDigit() { val result=MoneyInput.press(Entry("200−15%"),"=");assertEquals("170",result.expression);assertTrue(result.justEvaluated);assertEquals("5",MoneyInput.press(result,"5").expression) }
 @Test fun operatorContinuesResult() { assertEquals("170+",MoneyInput.press(Entry("170",true),"+").expression) }
 @Test fun deletionAndClear() { assertEquals("12",MoneyInput.press(Entry("123"),"⌫").expression);assertEquals(Entry(),MoneyInput.press(Entry("123"),"AC")) }
 @Test fun signTogglesPercentOperand() { assertEquals("200+−15%",MoneyInput.press(Entry("200+15%"),"+/−").expression);assertEquals("200+15%",MoneyInput.press(Entry("200+−15%"),"+/−").expression) }
 @Test fun signTogglesGroupedOperand() { assertEquals("200+−(10+5)",MoneyInput.press(Entry("200+(10+5)"),"+/−").expression) }
 @Test fun signStartsPendingOperand() { assertEquals("200+−",MoneyInput.press(Entry("200+"),"+/−").expression);assertEquals("200+",MoneyInput.press(Entry("200+−"),"+/−").expression) }
 @Test fun noRepeatedPercent() { assertEquals("15%",MoneyInput.press(Entry("15%"),"%").expression) }
}
