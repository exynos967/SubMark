package io.github.submark.core.ui.format

import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.CycleUnit
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.ui.R

object CycleLabels {

    /**
     * "Monthly", "Every 3 days", "Every 1Y 2M" ...; null cycle = "One-time".
     * @param showYmd custom cycles as years/months/days (days fold as 1Y = 360D, 1M = 30D, display only).
     */
    fun label(cycle: BillingCycle?, customCount: Int?, customUnit: CycleUnit?, showYmd: Boolean = false): UiText = when (cycle) {
        null -> UiText.res(R.string.ui_cycle_one_time)
        BillingCycle.WEEKLY -> UiText.res(R.string.ui_cycle_weekly)
        BillingCycle.MONTHLY -> UiText.res(R.string.ui_cycle_monthly)
        BillingCycle.QUARTERLY -> UiText.res(R.string.ui_cycle_quarterly)
        BillingCycle.SEMIANNUALLY -> UiText.res(R.string.ui_cycle_semiannually)
        BillingCycle.ANNUALLY -> UiText.res(R.string.ui_cycle_annually)
        BillingCycle.CUSTOM -> customLabel(customCount, customUnit, showYmd)
    }

    /** Subscription-aware label: lifetime purchases, single-cycle plans and wishlist items without a cycle. */
    fun label(sub: Subscription, showYmd: Boolean = false): UiText = when {
        sub.kind == SubscriptionKind.LIFETIME -> UiText.res(R.string.ui_cycle_lifetime)
        sub.isSingleCycle -> UiText.res(R.string.ui_cycle_single)
        else -> label(sub.billingCycle, sub.customCycleCount, sub.customCycleUnit, showYmd)
    }

    fun customLabel(count: Int?, unit: CycleUnit?, showYmd: Boolean = false): UiText {
        if (count == null || count <= 0 || unit == null) return UiText.res(R.string.ui_cycle_custom)
        if (showYmd && unit != CycleUnit.WEEK) {
            val (y, m, d) = toYmd(count, unit)
            return UiText.res(R.string.ui_cycle_every_ymd, DateLabels.ymd(y, m, d))
        }
        val res = when (unit) {
            CycleUnit.DAY -> R.plurals.ui_cycle_every_days
            CycleUnit.WEEK -> R.plurals.ui_cycle_every_weeks
            CycleUnit.MONTH -> R.plurals.ui_cycle_every_months
            CycleUnit.YEAR -> R.plurals.ui_cycle_every_years
        }
        return UiText.plural(res, count)
    }

    /** Short suffix shown after a price: "/mo", "/yr", "/3 days". Null cycle = no suffix (empty raw text). */
    fun perSuffix(cycle: BillingCycle?, customCount: Int? = null, customUnit: CycleUnit? = null): UiText = when (cycle) {
        null -> UiText.raw("")
        BillingCycle.WEEKLY -> UiText.res(R.string.ui_cycle_per_week)
        BillingCycle.MONTHLY -> UiText.res(R.string.ui_cycle_per_month)
        BillingCycle.QUARTERLY -> UiText.res(R.string.ui_cycle_per_quarter)
        BillingCycle.SEMIANNUALLY -> UiText.res(R.string.ui_cycle_per_half_year)
        BillingCycle.ANNUALLY -> UiText.res(R.string.ui_cycle_per_year)
        BillingCycle.CUSTOM -> when {
            customCount == null || customUnit == null || customCount <= 0 -> UiText.raw("")
            customCount == 1 -> when (customUnit) {
                CycleUnit.DAY -> UiText.res(R.string.ui_cycle_per_day)
                CycleUnit.WEEK -> UiText.res(R.string.ui_cycle_per_week)
                CycleUnit.MONTH -> UiText.res(R.string.ui_cycle_per_month)
                CycleUnit.YEAR -> UiText.res(R.string.ui_cycle_per_year)
            }
            else -> UiText.res(R.string.ui_cycle_per_custom, unitCount(customUnit, customCount))
        }
    }

    /** "3 days", "1 month" ... */
    fun unitCount(unit: CycleUnit, count: Int): UiText = UiText.plural(
        when (unit) {
            CycleUnit.DAY -> R.plurals.ui_unit_days
            CycleUnit.WEEK -> R.plurals.ui_unit_weeks
            CycleUnit.MONTH -> R.plurals.ui_unit_months
            CycleUnit.YEAR -> R.plurals.ui_unit_years
        },
        count,
    )

    /** Display-only folding: 1 year = 12 months = 360 days. Weeks are converted to days. */
    fun toYmd(count: Int, unit: CycleUnit): Triple<Int, Int, Int> = when (unit) {
        CycleUnit.DAY -> Triple(count / 360, (count % 360) / 30, count % 30)
        CycleUnit.WEEK -> (count * 7).let { Triple(it / 360, (it % 360) / 30, it % 30) }
        CycleUnit.MONTH -> Triple(count / 12, count % 12, 0)
        CycleUnit.YEAR -> Triple(count, 0, 0)
    }
}
