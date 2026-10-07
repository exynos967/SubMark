package io.github.submark.core.ui.format

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.ui.R
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class DateLabelsTest {
    private val today = LocalDate.of(2026, 3, 10)

    @Test fun `relative labels`() {
        assertThat(DateLabels.relative(today, today)).isEqualTo(UiText.res(R.string.ui_date_today))
        assertThat(DateLabels.relative(today.plusDays(1), today)).isEqualTo(UiText.res(R.string.ui_date_tomorrow))
        assertThat(DateLabels.relative(today.minusDays(1), today)).isEqualTo(UiText.res(R.string.ui_date_yesterday))
        assertThat(DateLabels.relative(today.plusDays(5), today)).isEqualTo(UiText.Plural(R.plurals.ui_date_in_days, 5, listOf(5)))
        assertThat(DateLabels.relative(today.minusDays(3), today)).isEqualTo(UiText.Plural(R.plurals.ui_date_days_ago, 3, listOf(3)))
    }

    @Test fun `due labels say overdue for past dates`() {
        assertThat(DateLabels.due(today.minusDays(2), today)).isEqualTo(UiText.Plural(R.plurals.ui_date_overdue_days, 2, listOf(2)))
        assertThat(DateLabels.due(today, today)).isEqualTo(UiText.res(R.string.ui_date_today))
    }

    @Test fun `countdown levels`() {
        assertThat(DateLabels.countdownLevel(today.minusDays(1), today)).isEqualTo(CountdownLevel.OVERDUE)
        assertThat(DateLabels.countdownLevel(today, today)).isEqualTo(CountdownLevel.TODAY)
        assertThat(DateLabels.countdownLevel(today.plusDays(1), today)).isEqualTo(CountdownLevel.TOMORROW)
        assertThat(DateLabels.countdownLevel(today.plusDays(7), today)).isEqualTo(CountdownLevel.SOON)
        assertThat(DateLabels.countdownLevel(today.plusDays(8), today)).isEqualTo(CountdownLevel.LATER)
        assertThat(CountdownLevel.OVERDUE.tone).isEqualTo(BadgeTone.ERROR)
    }

    @Test fun `duration in ymd parts`() {
        val d = DateLabels.duration(LocalDate.of(2025, 1, 5), LocalDate.of(2026, 3, 10)) as UiText.Concat
        assertThat(d.parts).containsExactly(
            UiText.res(R.string.ui_ymd_years, 1),
            UiText.res(R.string.ui_ymd_months, 2),
            UiText.res(R.string.ui_ymd_days, 5),
        ).inOrder()
        val zero = DateLabels.duration(today, today) as UiText.Concat
        assertThat(zero.parts).containsExactly(UiText.res(R.string.ui_ymd_days, 0))
    }

    @Test fun `month day patterns per locale`() {
        assertThat(DateLabels.formatMonthDay(today, Locale.US)).isEqualTo("Mar 10")
        assertThat(DateLabels.formatMonthDay(today, Locale.SIMPLIFIED_CHINESE)).isEqualTo("3月10日")
        assertThat(DateLabels.formatYearMonth(today, Locale.SIMPLIFIED_CHINESE)).isEqualTo("2026年3月")
    }

    @Test fun `quick picks are relative to today`() {
        assertThat(DateQuickPick.YESTERDAY.apply(today)).isEqualTo(LocalDate.of(2026, 3, 9))
        assertThat(DateQuickPick.IN_A_MONTH.apply(LocalDate.of(2026, 1, 31))).isEqualTo(LocalDate.of(2026, 2, 28))
        assertThat(DateQuickPick.YEAR_AGO.apply(today)).isEqualTo(LocalDate.of(2025, 3, 10))
    }
}
