package dev.kiankonrad.currencycalculator
import java.math.BigDecimal
import java.math.MathContext
object DisplayFormat { fun rate(value:BigDecimal)=value.round(MathContext(7)) }
