package dev.kiankonrad.currencycalculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** A partial keystroke is not a calculation error. At most one field is populated. */
data class Evaluation(
    val value: BigDecimal? = null,
    val error: String? = null,
    val incomplete: Boolean = false,
)

/** Decimal calculator grammar; input is canonical text with a period decimal separator. */
object MoneyEngine {
    private val context = MathContext.DECIMAL128
    private val hundred = BigDecimal("100")
    private const val maxInputLength = 512
    private const val maxNumberDigits = 128
    private const val maxDepth = 64

    fun evaluate(expression: String): Evaluation {
        if (expression.isBlank()) return Evaluation(incomplete = true)
        if (expression.length > maxInputLength) return Evaluation(error = "Calculation is too long")
        return try {
            Evaluation(value = Parser(expression).parse().value().let {
                if (it.signum() == 0) BigDecimal.ZERO else it
            })
        } catch (_: PartialInput) {
            Evaluation(incomplete = true)
        } catch (failure: InvalidInput) {
            Evaluation(error = failure.message ?: "Invalid calculation")
        } catch (_: ArithmeticException) {
            Evaluation(error = "Number is outside the supported range")
        } catch (_: NumberFormatException) {
            Evaluation(error = "Invalid number")
        }
    }

    /**
     * Convert the result of the whole source calculation, not every numeral. A numeral may
     * mean a price, a percentage, or a bill-split divisor, which cannot be inferred safely.
     */
    fun convertedExpression(expression: String, rate: BigDecimal): String {
        require(rate.signum() > 0) { "Exchange rate must be positive" }
        return if (rate.compareTo(BigDecimal.ONE) == 0) expression
        else "($expression) × ${rate.stripTrailingZeros().toPlainString()}"
    }

    /** Plain output can be reused as input without grouping or scientific-notation ambiguity. */
    fun format(value: BigDecimal, maxFractionDigits: Int = 8): String {
        require(maxFractionDigits in 0..34) { "Fraction digits must be between 0 and 34" }
        val rounded = value.setScale(maxFractionDigits, RoundingMode.HALF_UP)
        return if (rounded.signum() == 0) "0" else rounded.stripTrailingZeros().toPlainString()
    }

    private sealed interface Node {
        fun value(): BigDecimal
        // Percentage-only sums remain fractions; an amount plus a percentage is a markup.
        val percentage: Boolean get() = false
    }

    private data class NumberNode(val number: BigDecimal) : Node {
        override fun value() = number
    }

    private data class UnaryNode(val negative: Boolean, val child: Node) : Node {
        override fun value(): BigDecimal = child.value().let { if (negative) it.negate(context) else it }
        override val percentage get() = child.percentage
    }

    private data class GroupNode(val child: Node) : Node {
        override fun value() = child.value()
        override val percentage get() = child.percentage
    }

    private data class PercentNode(val child: Node) : Node {
        override fun value(): BigDecimal = child.value().divide(hundred, context)
        override val percentage = true
    }

    private data class BinaryNode(val operator: Char, val left: Node, val right: Node) : Node {
        override val percentage = (operator == '+' || operator == '-') && left.percentage && right.percentage
        override fun value(): BigDecimal {
            val a = left.value()
            val b = if ((operator == '+' || operator == '-') && !left.percentage && right.percentage) {
                a.multiply(right.value(), context)
            } else right.value()
            return when (operator) {
                '+' -> a.add(b, context)
                '-' -> a.subtract(b, context)
                '*' -> a.multiply(b, context)
                '/' -> {
                    if (b.signum() == 0) throw InvalidInput("Cannot divide by zero")
                    a.divide(b, context)
                }
                else -> throw InvalidInput("Unsupported operator")
            }
        }
    }

    private data class Token(val text: String, val number: Boolean = false)
    private class PartialInput : RuntimeException()
    private class InvalidInput(message: String) : RuntimeException(message)

    private class Parser(expression: String) {
        private val tokens = tokenize(expression)
        private var index = 0
        private fun current() = tokens.getOrNull(index)
        private fun matches(text: String) = current()?.text == text

        fun parse(): Node {
            val result = additive(0)
            if (current() != null) throw InvalidInput("Unexpected '${current()!!.text}'")
            return result
        }

        private fun additive(depth: Int): Node {
            var node = product(depth)
            while (matches("+") || matches("-")) {
                val operator = tokens[index++].text[0]
                node = BinaryNode(operator, node, product(depth))
            }
            return node
        }

        private fun product(depth: Int): Node {
            var node = unary(depth)
            while (matches("*") || matches("/")) {
                val operator = tokens[index++].text[0]
                node = BinaryNode(operator, node, unary(depth))
            }
            return node
        }

        private fun unary(depth: Int): Node {
            if (depth > maxDepth) throw InvalidInput("Too many nested operations")
            if (matches("+") || matches("-")) {
                val negative = tokens[index++].text == "-"
                return UnaryNode(negative, unary(depth + 1))
            }
            var node = primary(depth)
            while (matches("%")) {
                index++
                node = PercentNode(node)
            }
            return node
        }

        private fun primary(depth: Int): Node {
            val token = current() ?: throw PartialInput()
            if (token.number) {
                index++
                if (token.text == ".") {
                    if (current() == null) throw PartialInput()
                    throw InvalidInput("Enter a number before the operator")
                }
                return NumberNode(BigDecimal(token.text))
            }
            if (token.text == "(") {
                index++
                val child = additive(depth + 1)
                if (current() == null) throw PartialInput()
                if (!matches(")")) throw InvalidInput("Expected closing parenthesis")
                index++
                return GroupNode(child)
            }
            throw InvalidInput("Expected a number")
        }

        private fun tokenize(expression: String): List<Token> {
            val result = mutableListOf<Token>()
            var position = 0
            while (position < expression.length) {
                val char = expression[position]
                when {
                    char.isWhitespace() -> position++
                    char in '0'..'9' || char == '.' -> {
                        val start = position
                        var decimalPoints = 0
                        var digits = 0
                        while (position < expression.length &&
                            (expression[position] in '0'..'9' || expression[position] == '.')) {
                            if (expression[position] == '.') decimalPoints++ else digits++
                            position++
                        }
                        if (decimalPoints > 1) throw InvalidInput("A number can have only one decimal point")
                        if (digits > maxNumberDigits) throw InvalidInput("Number is too long")
                        result += Token(expression.substring(start, position), number = true)
                    }
                    char in "+-*/()%−×÷" -> {
                        result += Token(when (char) { '−' -> "-"; '×' -> "*"; '÷' -> "/"; else -> char.toString() })
                        position++
                    }
                    char == ',' -> throw InvalidInput("Use a decimal point without thousands separators")
                    else -> throw InvalidInput("Unsupported character '$char'")
                }
            }
            return result
        }
    }
}
