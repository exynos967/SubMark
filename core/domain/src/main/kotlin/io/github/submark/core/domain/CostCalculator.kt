package io.github.submark.core.domain

import io.github.submark.core.model.CycleUnit
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Normalizes per-cycle prices to comparable periods. Constants: 52.1775 weeks / 365.2425 days per year. */
object CostCalculator {
    private val MC = MathContext.DECIMAL64
    private val DAYS_PER_YEAR = BigDecimal("365.2425")
    private val WEEKS_PER_YEAR = BigDecimal("52.1775")
    private val TWELVE = BigDecimal(12)

    /** How many cycles fit in one year. */
    fun cyclesPerYear(cycle: CycleLength): BigDecimal {
        val n = BigDecimal(cycle.count)
        return when (cycle.unit) {
            CycleUnit.DAY -> DAYS_PER_YEAR.divide(n, MC)
            CycleUnit.WEEK -> WEEKS_PER_YEAR.divide(n, MC)
            CycleUnit.MONTH -> TWELVE.divide(n, MC)
            CycleUnit.YEAR -> BigDecimal.ONE.divide(n, MC)
        }
    }

    fun annualize(price: BigDecimal, cycle: CycleLength): BigDecimal = price.multiply(cyclesPerYear(cycle), MC)

    fun monthly(price: BigDecimal, cycle: CycleLength): BigDecimal = annualize(price, cycle).divide(TWELVE, MC)

    fun daily(price: BigDecimal, cycle: CycleLength): BigDecimal = annualize(price, cycle).divide(DAYS_PER_YEAR, MC)

    fun weekly(price: BigDecimal, cycle: CycleLength): BigDecimal = annualize(price, cycle).divide(WEEKS_PER_YEAR, MC)

    /** Recurring subscriptions that count toward projected spending. */
    fun countsTowardRecurringCost(sub: Subscription): Boolean =
        sub.status == SubscriptionStatus.ACTIVE &&
            (sub.kind == SubscriptionKind.REGULAR || sub.kind == SubscriptionKind.STORED_VALUE)

    /** Monthly equivalent in the subscription's own currency, or null if it has no recurring cost. */
    fun monthlyOf(sub: Subscription): BigDecimal? {
        if (!countsTowardRecurringCost(sub)) return null
        val cycle = BillingCalculator.cycleLength(sub) ?: return null
        return monthly(sub.price, cycle)
    }

    fun annualOf(sub: Subscription): BigDecimal? = monthlyOf(sub)?.multiply(TWELVE, MC)

    /** Holding cost per day of a one-time purchase since [daysOwned]. */
    fun lifetimeDailyCost(price: BigDecimal, daysOwned: Long): BigDecimal =
        price.divide(BigDecimal(maxOf(1L, daysOwned)), MC)

    fun round(value: BigDecimal, scale: Int = 2): BigDecimal = value.setScale(scale, RoundingMode.HALF_UP)
}
