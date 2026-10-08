package io.github.submark.feature.calendar

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.data.settings.CalendarMode
import io.github.submark.core.data.settings.TimelinePeriod
import io.github.submark.feature.calendar.data.CalendarScenario
import org.junit.Test
import java.time.LocalDate

class CalendarScenarioTest {

    private val today = LocalDate.of(2026, 10, 8)

    private fun scenario(
        mode: CalendarMode = CalendarMode.MONTH,
        anchor: LocalDate = LocalDate.of(2026, 10, 1),
        selected: LocalDate = today,
    ) = CalendarScenario(
        mode = mode,
        timelinePeriod = TimelinePeriod.ONE_MONTH,
        anchorDate = anchor,
        selectedDate = selected,
        today = today,
    )

    @Test
    fun `month range covers the whole calendar month`() {
        val (from, to) = scenario().range()
        assertThat(from).isEqualTo(LocalDate.of(2026, 10, 1))
        assertThat(to).isEqualTo(LocalDate.of(2026, 10, 31))
    }

    @Test
    fun `week range covers Monday to Sunday of the anchor`() {
        // Anchor on a Wednesday.
        val (from, to) = scenario(mode = CalendarMode.WEEK, anchor = LocalDate.of(2026, 10, 7)).range()
        assertThat(from).isEqualTo(LocalDate.of(2026, 10, 7))
        assertThat(to).isEqualTo(LocalDate.of(2026, 10, 13))
    }

    @Test
    fun `timeline range starts today and spans the period`() {
        val (from, to) = scenario(mode = CalendarMode.TIMELINE).range()
        assertThat(from).isEqualTo(today)
        assertThat(to).isEqualTo(today.plusMonths(1))
    }

    @Test
    fun `previous and next move the month anchor by one month`() {
        val prev = scenario().previous()
        val next = scenario().next()
        assertThat(prev.anchorDate).isEqualTo(LocalDate.of(2026, 9, 1))
        assertThat(next.anchorDate).isEqualTo(LocalDate.of(2026, 11, 1))
    }

    @Test
    fun `previous and next move the week anchor by one week`() {
        val sc = scenario(mode = CalendarMode.WEEK, anchor = LocalDate.of(2026, 10, 5))
        assertThat(sc.previous().anchorDate).isEqualTo(LocalDate.of(2026, 9, 28))
        assertThat(sc.next().anchorDate).isEqualTo(LocalDate.of(2026, 10, 12))
    }

    @Test
    fun `select changes the selected date and recenters the month`() {
        val sc = scenario().select(LocalDate.of(2026, 11, 20))
        assertThat(sc.selectedDate).isEqualTo(LocalDate.of(2026, 11, 20))
        assertThat(sc.anchorDate).isEqualTo(LocalDate.of(2026, 11, 1))
    }

    @Test
    fun `goToday recenters on today`() {
        val sc = scenario(anchor = LocalDate.of(2026, 3, 1)).goToday()
        assertThat(sc.anchorDate).isEqualTo(today.withDayOfMonth(1))
        assertThat(sc.selectedDate).isEqualTo(today)
    }
}
