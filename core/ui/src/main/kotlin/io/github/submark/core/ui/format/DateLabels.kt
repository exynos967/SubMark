package io.github.submark.core.ui.format

import io.github.submark.core.ui.R
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** How urgent a due date is, relative to today. Drives [io.github.submark.core.ui.component.CountdownBadge] colors. */
enum class CountdownLevel { OVERDUE, TODAY, TOMORROW, SOON, LATER }

object DateLabels {

    fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

    /** Today / Tomorrow / Yesterday / In N days / N days ago. */
    fun relative(date: LocalDate, today: LocalDate): UiText {
        val days = daysBetween(today, date)
        return when {
            days == 0L -> UiText.res(R.string.ui_date_today)
            days == 1L -> UiText.res(R.string.ui_date_tomorrow)
            days == -1L -> UiText.res(R.string.ui_date_yesterday)
            days > 0 -> UiText.plural(R.plurals.ui_date_in_days, days.toInt())
            else -> UiText.plural(R.plurals.ui_date_days_ago, (-days).toInt())
        }
    }

    /** Like [relative] for future dates; past due dates read "Overdue N days". */
    fun due(dueDate: LocalDate, today: LocalDate): UiText {
        val days = daysBetween(today, dueDate)
        return if (days < 0) overdue((-days).toInt()) else relative(dueDate, today)
    }

    fun overdue(days: Int): UiText = UiText.plural(R.plurals.ui_date_overdue_days, days)

    fun countdownLevel(dueDate: LocalDate, today: LocalDate, soonDays: Int = 7): CountdownLevel {
        val days = daysBetween(today, dueDate)
        return when {
            days < 0 -> CountdownLevel.OVERDUE
            days == 0L -> CountdownLevel.TODAY
            days == 1L -> CountdownLevel.TOMORROW
            days <= soonDays -> CountdownLevel.SOON
            else -> CountdownLevel.LATER
        }
    }

    /** Elapsed time as "1Y 2M 5D" parts (zero parts omitted; "0D" when from == to). */
    fun duration(from: LocalDate, to: LocalDate): UiText {
        val p = Period.between(minOf(from, to), maxOf(from, to))
        return ymd(p.years, p.months, p.days)
    }

    internal fun ymd(years: Int, months: Int, days: Int): UiText {
        val parts = buildList {
            if (years > 0) add(UiText.res(R.string.ui_ymd_years, years))
            if (months > 0) add(UiText.res(R.string.ui_ymd_months, months))
            if (days > 0 || isEmpty()) add(UiText.res(R.string.ui_ymd_days, days))
        }
        return UiText.Concat(parts, " ")
    }

    fun formatDate(date: LocalDate, style: FormatStyle = FormatStyle.MEDIUM, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedDate(style).withLocale(locale).format(date)

    /** "Mar 3" / "3月3日". */
    fun formatMonthDay(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern(if (locale.language == "zh") "M月d日" else "MMM d", locale).format(date)

    /** "Mar" / "3月", for compact chart axes. */
    fun formatMonth(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern(if (locale.language == "zh") "M月" else "MMM", locale).format(date)

    /** "Mar 2026" / "2026年3月". */
    fun formatYearMonth(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern(if (locale.language == "zh") "yyyy年M月" else "MMM yyyy", locale).format(date)
}
