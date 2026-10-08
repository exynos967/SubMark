package io.github.submark.feature.calendar.data

import io.github.submark.core.data.settings.CalendarMode
import io.github.submark.core.data.settings.TimelinePeriod
import io.github.submark.core.model.Subscription
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** What the calendar screen is currently looking at; pure reducer over user actions. */
data class CalendarScenario(
    val mode: CalendarMode,
    val timelinePeriod: TimelinePeriod,
    /** First day of the visible month (MONTH) or week (WEEK). TIMELINE always starts today. */
    val anchorDate: LocalDate,
    val selectedDate: LocalDate,
    val today: LocalDate,
) {
    /** Date range whose occurrences should be loaded. */
    fun range(): Pair<LocalDate, LocalDate> = when (mode) {
        CalendarMode.MONTH -> YearMonth.from(anchorDate).let { it.atDay(1) to it.atEndOfMonth() }
        CalendarMode.WEEK -> anchorDate to anchorDate.plusDays(6)
        CalendarMode.TIMELINE -> today to today.plusMonths(timelinePeriod.months().toLong())
    }

    fun withMode(mode: CalendarMode): CalendarScenario = copy(
        mode = mode,
        anchorDate = if (mode == CalendarMode.WEEK) selectedDate.startOfWeek() else selectedDate.withDayOfMonth(1),
    )

    fun withTimelinePeriod(period: TimelinePeriod): CalendarScenario = copy(timelinePeriod = period)

    fun select(date: LocalDate): CalendarScenario =
        copy(selectedDate = date, anchorDate = if (mode == CalendarMode.MONTH) date.withDayOfMonth(1) else date.startOfWeek())

    fun previous(): CalendarScenario = when (mode) {
        CalendarMode.MONTH -> copy(anchorDate = anchorDate.minusMonths(1))
        CalendarMode.WEEK -> copy(anchorDate = anchorDate.minusWeeks(1))
        CalendarMode.TIMELINE -> this
    }

    fun next(): CalendarScenario = when (mode) {
        CalendarMode.MONTH -> copy(anchorDate = anchorDate.plusMonths(1))
        CalendarMode.WEEK -> copy(anchorDate = anchorDate.plusWeeks(1))
        CalendarMode.TIMELINE -> this
    }

    fun goToday(): CalendarScenario =
        copy(anchorDate = today.withDayOfMonth(1), selectedDate = today)

    fun jumpTo(date: LocalDate): CalendarScenario =
        copy(anchorDate = date.withDayOfMonth(1), selectedDate = date)
}

fun TimelinePeriod.months(): Int = when (this) {
    TimelinePeriod.ONE_MONTH -> 1
    TimelinePeriod.THREE_MONTHS -> 3
    TimelinePeriod.SIX_MONTHS -> 6
    TimelinePeriod.ONE_YEAR -> 12
}

fun LocalDate.startOfWeek(): LocalDate = minusDays(((dayOfWeek.value - java.time.DayOfWeek.MONDAY.value + 7) % 7).toLong())

/** Buckets of upcoming occurrences over the timeline window, grouped by calendar month. */
data class TimelineBucket(
    val month: YearMonth,
    val occurrences: List<Occurrence>,
)

fun timelineBuckets(
    byDate: Map<LocalDate, List<Occurrence>>,
    today: LocalDate,
    period: TimelinePeriod,
): List<TimelineBucket> {
    val end = today.plusMonths(period.months().toLong())
    val grouped = byDate.toSortedMap()
        .filterKeys { it >= today && it <= end }
        .flatMap { (_, list) -> list }
        .groupBy { YearMonth.from(it.date) }
    return grouped.toSortedMap().map { (month, list) -> TimelineBucket(month, list) }
}

/** Days until [date] for countdown labels; negative = overdue. */
fun daysUntil(date: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(today, date)

/** Whether an agenda row belongs to a bundle child shown with the badge setting on. */
fun isChild(sub: Subscription): Boolean = sub.parentId != null
