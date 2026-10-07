package io.github.submark.core.ui.format

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.CycleUnit
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.ui.R
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class CycleLabelsTest {
    @Test fun `built-in cycles`() {
        assertThat(CycleLabels.label(BillingCycle.MONTHLY, null, null)).isEqualTo(UiText.res(R.string.ui_cycle_monthly))
        assertThat(CycleLabels.label(null, null, null)).isEqualTo(UiText.res(R.string.ui_cycle_one_time))
        assertThat(CycleLabels.label(BillingCycle.CUSTOM, null, null)).isEqualTo(UiText.res(R.string.ui_cycle_custom))
    }

    @Test fun `custom cycles use plurals`() {
        assertThat(CycleLabels.label(BillingCycle.CUSTOM, 3, CycleUnit.DAY))
            .isEqualTo(UiText.Plural(R.plurals.ui_cycle_every_days, 3, listOf(3)))
        assertThat(CycleLabels.label(BillingCycle.CUSTOM, 2, CycleUnit.WEEK, showYmd = true))
            .isEqualTo(UiText.Plural(R.plurals.ui_cycle_every_weeks, 2, listOf(2)))
    }

    @Test fun `ymd folding`() {
        assertThat(CycleLabels.toYmd(425, CycleUnit.DAY)).isEqualTo(Triple(1, 2, 5))
        assertThat(CycleLabels.toYmd(14, CycleUnit.MONTH)).isEqualTo(Triple(1, 2, 0))
        assertThat(CycleLabels.toYmd(2, CycleUnit.WEEK)).isEqualTo(Triple(0, 0, 14))
        val label = CycleLabels.label(BillingCycle.CUSTOM, 425, CycleUnit.DAY, showYmd = true) as UiText.Res
        assertThat(label.id).isEqualTo(R.string.ui_cycle_every_ymd)
        assertThat((label.args.single() as UiText.Concat).parts).hasSize(3)
    }

    @Test fun `per suffix`() {
        assertThat(CycleLabels.perSuffix(BillingCycle.ANNUALLY)).isEqualTo(UiText.res(R.string.ui_cycle_per_year))
        assertThat(CycleLabels.perSuffix(BillingCycle.CUSTOM, 1, CycleUnit.DAY)).isEqualTo(UiText.res(R.string.ui_cycle_per_day))
        assertThat(CycleLabels.perSuffix(BillingCycle.CUSTOM, 3, CycleUnit.MONTH))
            .isEqualTo(UiText.res(R.string.ui_cycle_per_custom, UiText.Plural(R.plurals.ui_unit_months, 3, listOf(3))))
        assertThat(CycleLabels.perSuffix(null)).isEqualTo(UiText.raw(""))
    }

    @Test fun `subscription aware label`() {
        val now = Instant.EPOCH
        val base = Subscription(name = "x", price = BigDecimal.ONE, currencyCode = "USD", startDate = LocalDate.EPOCH, categoryId = "c", createdAt = now, updatedAt = now)
        assertThat(CycleLabels.label(base.copy(kind = SubscriptionKind.LIFETIME, billingCycle = null))).isEqualTo(UiText.res(R.string.ui_cycle_lifetime))
        assertThat(CycleLabels.label(base.copy(isSingleCycle = true))).isEqualTo(UiText.res(R.string.ui_cycle_single))
        assertThat(CycleLabels.label(base)).isEqualTo(UiText.res(R.string.ui_cycle_monthly))
    }
}
