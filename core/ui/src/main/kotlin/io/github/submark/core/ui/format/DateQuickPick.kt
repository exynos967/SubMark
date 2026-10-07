package io.github.submark.core.ui.format

import androidx.annotation.StringRes
import io.github.submark.core.ui.R
import java.time.LocalDate

/** Shortcut dates offered by the date picker, all relative to today. */
enum class DateQuickPick(@StringRes val labelRes: Int) {
    TODAY(R.string.ui_date_today),
    YESTERDAY(R.string.ui_date_yesterday),
    TOMORROW(R.string.ui_date_tomorrow),
    WEEK_AGO(R.string.ui_quick_week_ago),
    IN_A_WEEK(R.string.ui_quick_in_week),
    MONTH_AGO(R.string.ui_quick_month_ago),
    IN_A_MONTH(R.string.ui_quick_in_month),
    YEAR_AGO(R.string.ui_quick_year_ago),
    IN_A_YEAR(R.string.ui_quick_in_year),
    ;

    fun apply(today: LocalDate): LocalDate = when (this) {
        TODAY -> today
        YESTERDAY -> today.minusDays(1)
        TOMORROW -> today.plusDays(1)
        WEEK_AGO -> today.minusWeeks(1)
        IN_A_WEEK -> today.plusWeeks(1)
        MONTH_AGO -> today.minusMonths(1)
        IN_A_MONTH -> today.plusMonths(1)
        YEAR_AGO -> today.minusYears(1)
        IN_A_YEAR -> today.plusYears(1)
    }
}
