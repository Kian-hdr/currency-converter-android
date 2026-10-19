package dev.kiankonrad.currencycalculator

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class MoneyEngineTest {
    private fun value(expression: String): BigDecimal {
        val result = MoneyEngine.evaluate(expression)
        assertNull("$expression: ${result.error}", result.error)
        assertFalse("$expression unexpectedly incomplete", result.incomplete)
        return requireNotNull(result.value)
    }

    private fun expect(expression: String, expected: String) {
        assertEquals(expression, 0, value(expression).compareTo(BigDecimal(expected)))
    }

    @Test fun exactDecimalAdditionDoesNotUseBinaryFloatingPoint() {
        expect("0.1 + 0.2", "0.3")
        expect("123456789.99 + 0.01", "123456790")
    }

    @Test fun respectsOperatorPrecedenceAndParentheses() {
        expect("2 + 3 × 4", "14")
        expect("(2 + 3) × 4", "20")
        expect("120 ÷ 3", "40")
        expect("24/3*2", "16")
        expect("(12.5 - 2.5) / (3 + 2)", "2")
    }

    @Test fun supportsSignedAmountsAndUnaryOperators() {
        expect("−15 + 5", "-10")
        expect("2 × -3", "-6")
        expect("-(2 + 3)", "-5")
        expect("--5", "5")
        expect("1 - -2", "3")
        expect(".5 + 1.", "1.5")
    }

    @Test fun percentagesFollowMoneyCalculatorSemantics() {
        expect("200 − 15%", "170")
        expect("1000 + 20%", "1200")
        expect("200 × 15%", "30")
        expect("15%", "0.15")
        expect("200 / 25%", "800")
        expect("100 + 10% + 10%", "121")
        expect("200 − (10 + 5)%", "170")
        expect("200 + -15%", "170")
        expect("200 + (15%)", "230")
        expect("(200 − 15%) / 2", "85")
    }

    @Test fun percentInsideAnOrdinaryProductDoesNotBecomeAnAdditiveMarkup() {
        expect("200 + 10% × 2", "200.2")
        expect("200 × (10% + 5%)", "30")
        expect("10% + 5%", "0.15")
        expect("200 + (10% + 5%)", "230")
    }

    @Test fun divideByZeroIsAnErrorIncludingCalculatedZero() {
        for (expression in listOf("2/0", "2/(1-1)", "0/0", "1/0%")) {
            val result = MoneyEngine.evaluate(expression)
            assertNotNull(expression, result.error)
            assertNull(expression, result.value)
            assertFalse(expression, result.incomplete)
        }
    }

    @Test fun incompleteEntryIsDistinctFromMalformedInput() {
        for (expression in listOf("", "  ", "-", ".", "1+", "1/", "(", "(1+2", "1+-", "1+.", "1+(2*")) {
            val result = MoneyEngine.evaluate(expression)
            assertTrue("Expected incomplete: $expression, got $result", result.incomplete)
            assertNull(expression, result.error)
            assertNull(expression, result.value)
        }
        for (expression in listOf("1..2", "1*/2", "1 2", "2(3)", "()", "1)", "%", "1,000", "2^3", "NaN", "1+*")) {
            val result = MoneyEngine.evaluate(expression)
            assertNotNull("Expected error: $expression, got $result", result.error)
            assertFalse(expression, result.incomplete)
            assertNull(expression, result.value)
        }
    }

    @Test fun recurringDivisionIsFiniteAndUsesDecimalPrecision() {
        val third = value("1/3")
        assertEquals(34, third.precision())
        assertEquals("0.3333333333333333333333333333333333", third.toPlainString())
    }

    @Test fun limitsOverlongInputAndExcessiveNestingWithoutCrashing() {
        assertNotNull(MoneyEngine.evaluate("9".repeat(600)).error)
        assertNotNull(MoneyEngine.evaluate("(".repeat(80) + "1" + ")".repeat(80)).error)
    }

    @Test fun convertedFormulaPreservesPercentAndAllArithmetic() {
        val expression = "(200 - 15%) / 2"
        val rate = BigDecimal("1.14")
        val converted = MoneyEngine.convertedExpression(expression, rate)
        assertEquals(0, value(converted).compareTo(value(expression).multiply(rate)))
        assertEquals(expression, MoneyEngine.convertedExpression(expression, BigDecimal.ONE))
    }

    @Test fun formattingIsPlainStableAndRoundsHalfUp() {
        assertEquals("123.45", MoneyEngine.format(BigDecimal("123.45000")))
        assertEquals("0", MoneyEngine.format(BigDecimal("-0.0000000001")))
        assertEquals("1.01", MoneyEngine.format(BigDecimal("1.005"), 2))
        assertEquals("100000000000000000000", MoneyEngine.format(BigDecimal("1E+20")))
        assertEquals("0.33333333", MoneyEngine.format(value("1/3")))
    }
}
