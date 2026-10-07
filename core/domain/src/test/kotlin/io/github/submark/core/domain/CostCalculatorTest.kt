package io.github.submark.core.domain

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.CycleUnit
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import org.junit.Test
import java.math.BigDecimal

class CostCalculatorTest {

    @Test
    fun cyclesPerYear() {
        assertThat(CostCalculator.cyclesPerYear(CycleLength(1, CycleUnit.MONTH))).isEqualToIgnoringScale(bd("12"))
        assertThat(CostCalculator.cyclesPerYear(CycleLength(3, CycleUnit.MONTH))).isEqualToIgnoringScale(bd("4"))
        assertThat(CostCalculator.cyclesPerYear(CycleLength(1, CycleUnit.YEAR))).isEqualToIgnoringScale(bd("1"))
        assertThat(CostCalculator.cyclesPerYear(CycleLength(2, CycleUnit.YEAR))).isEqualToIgnoringScale(bd("0.5"))
        assertThat(CostCalculator.cyclesPerYear(CycleLength(1, CycleUnit.WEEK))).isEqualToIgnoringScale(bd("52.1775"))
        assertThat(CostCalculator.cyclesPerYear(CycleLength(1, CycleUnit.DAY))).isEqualToIgnoringScale(bd("365.2425"))
    }

    @Test
    fun monthly_ofWeeklyAnnualCustom() {
        assertThat(CostCalculator.monthly(bd("10"), CycleLength(1, CycleUnit.WEEK))).isEqualToIgnoringScale(bd("43.48125"))
        assertThat(CostCalculator.monthly(bd("120"), CycleLength(1, CycleUnit.YEAR))).isEqualToIgnoringScale(bd("10"))
        assertThat(CostCalculator.monthly(bd("30"), CycleLength(3, CycleUnit.MONTH))).isEqualToIgnoringScale(bd("10"))
        assertThat(CostCalculator.monthly(bd("60"), CycleLength(6, CycleUnit.MONTH))).isEqualToIgnoringScale(bd("10"))
        assertThat(CostCalculator.monthly(bd("3"), CycleLength(3, CycleUnit.DAY))).isEqualToIgnoringScale(bd("30.436875"))
        assertThat(CostCalculator.monthly(bd("10"), CycleLength(1, CycleUnit.MONTH))).isEqualToIgnoringScale(bd("10"))
    }

    @Test
    fun annualDailyWeekly() {
        assertThat(CostCalculator.annualize(bd("10"), CycleLength(1, CycleUnit.MONTH))).isEqualToIgnoringScale(bd("120"))
        assertThat(CostCalculator.daily(bd("365.2425"), CycleLength(1, CycleUnit.YEAR))).isEqualToIgnoringScale(bd("1"))
        assertThat(CostCalculator.weekly(bd("52.1775"), CycleLength(1, CycleUnit.YEAR))).isEqualToIgnoringScale(bd("1"))
        assertThat(CostCalculator.weekly(bd("7"), CycleLength(1, CycleUnit.WEEK))).isEqualToIgnoringScale(bd("7"))
    }

    @Test
    fun monthlyOf_includesActiveRecurring() {
        val s = sub(price = "120").copy(billingCycle = BillingCycle.ANNUALLY)
        assertThat(CostCalculator.monthlyOf(s)).isEqualToIgnoringScale(bd("10"))
        assertThat(CostCalculator.annualOf(s)).isEqualToIgnoringScale(bd("120"))
        assertThat(CostCalculator.monthlyOf(s.copy(kind = SubscriptionKind.STORED_VALUE))).isEqualToIgnoringScale(bd("10"))
        val custom = sub(price = "3").copy(billingCycle = BillingCycle.CUSTOM, customCycleCount = 3, customCycleUnit = CycleUnit.DAY)
        assertThat(CostCalculator.monthlyOf(custom)).isEqualToIgnoringScale(bd("30.436875"))
    }

    @Test
    fun monthlyOf_singleCycleUsesItsSpan() {
        val s = sub(d("2026-01-01"), price = "30").copy(isSingleCycle = true, endDate = d("2026-01-31"))
        assertThat(CostCalculator.monthlyOf(s)).isEqualToIgnoringScale(bd("30.436875"))
    }

    @Test
    fun monthlyOf_excludesPausedWishlistLifetimeAndCycleless() {
        val s = sub(price = "10")
        assertThat(CostCalculator.monthlyOf(s.copy(status = SubscriptionStatus.PAUSED))).isNull()
        assertThat(CostCalculator.monthlyOf(s.copy(kind = SubscriptionKind.WISHLIST))).isNull()
        assertThat(CostCalculator.monthlyOf(s.copy(kind = SubscriptionKind.LIFETIME))).isNull()
        assertThat(CostCalculator.monthlyOf(s.copy(billingCycle = BillingCycle.CUSTOM))).isNull()
        assertThat(CostCalculator.annualOf(s.copy(status = SubscriptionStatus.PAUSED))).isNull()
        assertThat(CostCalculator.countsTowardRecurringCost(s)).isTrue()
        assertThat(CostCalculator.countsTowardRecurringCost(s.copy(kind = SubscriptionKind.LIFETIME))).isFalse()
    }

    @Test
    fun lifetimeDailyCostAndRound() {
        assertThat(CostCalculator.lifetimeDailyCost(bd("100"), 4)).isEqualToIgnoringScale(bd("25"))
        assertThat(CostCalculator.lifetimeDailyCost(bd("100"), 0)).isEqualToIgnoringScale(bd("100"))
        assertThat(CostCalculator.lifetimeDailyCost(bd("100"), -5)).isEqualToIgnoringScale(bd("100"))
        assertThat(CostCalculator.round(bd("43.48125"))).isEqualTo(bd("43.48"))
        assertThat(CostCalculator.round(bd("2.345"))).isEqualTo(bd("2.35"))
        assertThat(CostCalculator.round(BigDecimal.ONE, 0)).isEqualTo(bd("1"))
    }
}
