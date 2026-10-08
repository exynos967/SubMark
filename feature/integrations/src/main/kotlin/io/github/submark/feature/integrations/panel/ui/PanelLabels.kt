package io.github.submark.feature.integrations.panel.ui

import io.github.submark.core.model.ApiServiceType
import io.github.submark.feature.integrations.R
import io.github.submark.feature.integrations.panel.data.BudgetStatus
import io.github.submark.feature.integrations.panel.data.PanelErrorReason
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Provider display names (brand names are not translated). */
fun ApiServiceType.displayName(): String = when (this) {
    ApiServiceType.DEEPSEEK -> "DeepSeek"
    ApiServiceType.NEWAPI -> "NewAPI"
    ApiServiceType.VAPI -> "V-API"
    ApiServiceType.PACKY -> "Packy"
    ApiServiceType.ZAI -> "GLM / Z.AI"
}

fun PanelErrorReason.messageRes(): Int = when (this) {
    PanelErrorReason.AUTH -> R.string.panel_error_auth
    PanelErrorReason.RATE_LIMITED -> R.string.panel_error_rate_limited
    PanelErrorReason.NOT_FOUND -> R.string.panel_error_not_found
    PanelErrorReason.SERVER_ERROR -> R.string.panel_error_server
    PanelErrorReason.TIMEOUT -> R.string.panel_error_timeout
    PanelErrorReason.NETWORK -> R.string.panel_error_network
    PanelErrorReason.PARSE -> R.string.panel_error_parse
    PanelErrorReason.MISSING_CONFIG -> R.string.panel_error_missing_config
    PanelErrorReason.UNKNOWN -> R.string.panel_error_unknown
}

fun BudgetStatus.labelRes(): Int = when (this) {
    BudgetStatus.NORMAL -> R.string.panel_budget_status_normal
    BudgetStatus.WARNING -> R.string.panel_budget_status_warning
    BudgetStatus.OVER -> R.string.panel_budget_status_over
}

/** Relative "x minutes ago" for card footers. */
fun relativeTimeRes(instant: Instant, nowEpochSeconds: Long): Pair<Int, Any?> {
    val delta = nowEpochSeconds - instant.epochSecond
    return when {
        delta < 60 -> R.string.panel_updated_just_now to null
        delta < 3600 -> R.string.panel_updated_minutes_ago to (delta / 60).toInt()
        delta < 86400 -> R.string.panel_updated_hours_ago to (delta / 3600).toInt()
        else -> R.string.panel_updated_days_ago to (delta / 86400).toInt()
    }
}

/** Medium date + short time for expiry/reset values, in the local zone. */
fun formatInstant(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(zone)
        .format(instant)

/** Byte counts formatted as KB/MB/GB/TB with one decimal for >= 10 units. */
fun formatBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index == 0) "${bytes} B" else "%.1f %s".format(value, units[index])
}

/** Token-ish big counts abbreviated: 1.2K, 3.4M, 5.6B. */
fun formatCount(value: Long): String = when {
    value >= 1_000_000_000 -> "%.1fB".format(value / 1_000_000_000.0)
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000 -> "%.1fK".format(value / 1_000.0)
    else -> value.toString()
}

/** Trims trailing zeros for compact money-ish display (12.50 -> 12.5). */
fun BigDecimal.trimmed(): String = stripTrailingZeros().toPlainString()

/** Currency-prefixed amount without locale lookups (budget USD/EUR/CNY codes). */
fun formatBudgetAmount(amount: BigDecimal?, currencyCode: String): String =
    if (amount == null) "—" else "${amount.setScale(2, RoundingMode.HALF_UP).trimmed()} $currencyCode"
