package io.github.submark.core.ui.format

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

/**
 * Pure money formatting. The symbol is always placed before the number (`$12.00`, `-¥3`, `CHF 9.50`);
 * grouping and decimal separators follow [Locale].
 */
object MoneyFormatter {

    /**
     * @param symbol display symbol (e.g. `Currency.symbol` from the database); defaults to the JDK symbol or the code.
     * @param hideDecimals truncates (never rounds up) to whole units; display-only.
     * @param compact abbreviates large values: K/M/B/T, or 万/亿 for Chinese locales.
     * @param showPlusSign prefixes positive values with "+" (wallet deltas, price changes).
     */
    fun format(
        amount: BigDecimal,
        currencyCode: String,
        symbol: String? = null,
        locale: Locale = Locale.getDefault(),
        hideDecimals: Boolean = false,
        compact: Boolean = false,
        showPlusSign: Boolean = false,
    ): String {
        val sign = when {
            amount.signum() < 0 -> "-"
            showPlusSign && amount.signum() > 0 -> "+"
            else -> ""
        }
        val number = formatNumber(amount.abs(), fractionDigits(currencyCode), locale, hideDecimals, compact)
        val sym = symbol?.takeIf { it.isNotBlank() } ?: defaultSymbol(currencyCode, locale)
        val separator = if (sym.length > 1 && sym.all { it.isLetter() }) " " else ""
        return "$sign$sym$separator$number"
    }

    /** Number only, no symbol. Negative values keep their sign. */
    fun formatNumber(
        amount: BigDecimal,
        fractionDigits: Int = 2,
        locale: Locale = Locale.getDefault(),
        hideDecimals: Boolean = false,
        compact: Boolean = false,
    ): String {
        val sign = if (amount.signum() < 0) "-" else ""
        val abs = amount.abs()
        if (compact) {
            compactParts(abs, locale)?.let { (value, suffix) ->
                return sign + decimalFormat(locale, 0, 1, RoundingMode.HALF_UP).format(value) + suffix
            }
        }
        val digits = if (hideDecimals) 0 else fractionDigits
        val rounding = if (hideDecimals) RoundingMode.DOWN else RoundingMode.HALF_UP
        return sign + decimalFormat(locale, digits, digits, rounding).format(abs)
    }

    /** ISO-4217 minor digits; 2 for unknown/custom codes. */
    fun fractionDigits(currencyCode: String): Int = runCatching {
        java.util.Currency.getInstance(currencyCode).defaultFractionDigits
    }.getOrNull()?.takeIf { it >= 0 } ?: 2

    fun defaultSymbol(currencyCode: String, locale: Locale = Locale.getDefault()): String = runCatching {
        java.util.Currency.getInstance(currencyCode).getSymbol(locale)
    }.getOrNull() ?: currencyCode

    private data class Unit(val divisor: BigDecimal, val suffix: String)

    private val westernUnits = listOf(
        Unit(BigDecimal("1E12"), "T"),
        Unit(BigDecimal("1E9"), "B"),
        Unit(BigDecimal("1E6"), "M"),
        Unit(BigDecimal("1E3"), "K"),
    )
    private val chineseUnits = listOf(
        Unit(BigDecimal("1E8"), "亿"),
        Unit(BigDecimal("1E4"), "万"),
    )

    /** Scaled value (1 decimal) + suffix, or null when the amount is below the smallest unit. */
    private fun compactParts(abs: BigDecimal, locale: Locale): Pair<BigDecimal, String>? {
        val units = if (locale.language == "zh") chineseUnits else westernUnits
        val index = units.indexOfFirst { abs >= it.divisor }
        if (index < 0) return null
        val unit = units[index]
        val scaled = abs.divide(unit.divisor).setScale(1, RoundingMode.HALF_UP)
        // 999_950 rounds to 1000.0K: promote to the next larger unit when there is one.
        if (index > 0) {
            val larger = units[index - 1]
            if (scaled.multiply(unit.divisor) >= larger.divisor) {
                return abs.divide(larger.divisor).setScale(1, RoundingMode.HALF_UP) to larger.suffix
            }
        }
        return scaled to unit.suffix
    }

    private fun decimalFormat(locale: Locale, minDigits: Int, maxDigits: Int, rounding: RoundingMode): NumberFormat =
        (NumberFormat.getNumberInstance(locale) as DecimalFormat).apply {
            isGroupingUsed = true
            minimumFractionDigits = minDigits
            maximumFractionDigits = maxDigits
            roundingMode = rounding
        }
}
