package io.github.submark.core.ui.format

import androidx.annotation.StringRes
import io.github.submark.core.ui.R
import java.math.BigDecimal

enum class MoneyInputError(@StringRes val messageRes: Int) {
    EMPTY(R.string.ui_money_error_empty),
    INVALID(R.string.ui_money_error_invalid),
    ZERO(R.string.ui_money_error_zero),
    NEGATIVE(R.string.ui_money_error_negative),
    TOO_LARGE(R.string.ui_money_error_too_large),
}

/** Pure helpers behind [io.github.submark.core.ui.component.MoneyInputField]. Accepts "." or "," as decimal separator. */
object MoneyInput {
    /** Hard ceiling so typos like 9999999999999 don't propagate into totals. */
    val MAX_AMOUNT = BigDecimal("999999999999")

    /** Keeps digits and a single decimal separator (normalised to "."), limits decimals, optional leading "-". */
    fun sanitize(raw: String, maxFractionDigits: Int = 2, allowNegative: Boolean = false): String {
        val out = StringBuilder()
        var seenSeparator = false
        var decimals = 0
        raw.trim().forEachIndexed { i, c ->
            when {
                c == '-' && allowNegative && i == 0 -> out.append(c)
                c.isDigit() -> {
                    if (seenSeparator) {
                        if (decimals < maxFractionDigits) { out.append(c); decimals++ }
                    } else {
                        out.append(c)
                    }
                }
                (c == '.' || c == ',') && !seenSeparator && maxFractionDigits > 0 -> {
                    seenSeparator = true
                    out.append('.')
                }
            }
        }
        return out.toString()
    }

    fun parse(text: String): BigDecimal? {
        val normalized = text.trim().replace(',', '.')
        if (normalized.isEmpty() || normalized == "-" || normalized == ".") return null
        return normalized.toBigDecimalOrNull()
    }

    fun validate(
        text: String,
        allowZero: Boolean = false,
        allowNegative: Boolean = false,
        max: BigDecimal = MAX_AMOUNT,
    ): MoneyInputError? {
        if (text.isBlank()) return MoneyInputError.EMPTY
        val value = parse(text) ?: return MoneyInputError.INVALID
        return when {
            value.signum() < 0 && !allowNegative -> MoneyInputError.NEGATIVE
            value.signum() == 0 && !allowZero -> MoneyInputError.ZERO
            value.abs() > max -> MoneyInputError.TOO_LARGE
            else -> null
        }
    }
}
