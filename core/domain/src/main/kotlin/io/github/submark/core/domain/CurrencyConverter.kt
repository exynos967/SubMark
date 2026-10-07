package io.github.submark.core.domain

import java.math.BigDecimal
import java.math.MathContext

/**
 * Converts through USD: rates are units of each currency per 1 USD.
 * Returns null when a rate is missing so callers can show "rate unavailable" instead of wrong numbers.
 */
class CurrencyConverter(private val usdRates: Map<String, BigDecimal>) {

    fun rate(code: String): BigDecimal? = if (code == USD) BigDecimal.ONE else usdRates[code]?.takeIf { it.signum() > 0 }

    fun convert(amount: BigDecimal, from: String, to: String): BigDecimal? {
        if (from == to) return amount
        val rFrom = rate(from) ?: return null
        val rTo = rate(to) ?: return null
        return amount.multiply(rTo, MC).divide(rFrom, MC)
    }

    /** "1 [base] = x [quote]" for display. */
    fun crossRate(base: String, quote: String): BigDecimal? = convert(BigDecimal.ONE, base, quote)

    companion object {
        const val USD = "USD"
        private val MC = MathContext.DECIMAL64

        /** Turns a user-entered "1 [default] = [value] [code]" rate into the stored per-USD rate. */
        fun usdRateFromDefault(value: BigDecimal, defaultUsdRate: BigDecimal): BigDecimal = value.multiply(defaultUsdRate, MC)
    }
}
