package dev.kiankonrad.currencycalculator
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
class DisplayFormatTest {
 @Test fun verySmallCurrencyRateDoesNotRoundToZeroAndCrashConversionDisplay() { val rate=DisplayFormat.rate(BigDecimal("0.000000000123456789")); assertTrue(rate.signum()>0); assertTrue(MoneyEngine.convertedExpression("1000",rate).contains("0.0000000001234568")) }
 @Test fun rateUsesSignificantDigitsAtBothEndsOfScale() {assertEquals("1.123457",DisplayFormat.rate(BigDecimal("1.123456789")).toPlainString());assertEquals("1234568",DisplayFormat.rate(BigDecimal("1234567.89")).toPlainString())}
}
