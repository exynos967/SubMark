package io.github.submark.core.domain

import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.CycleUnit
import io.github.submark.core.model.MarkTiming
import io.github.submark.core.model.RenewalType
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** A cycle length expressed in calendar units, so month arithmetic clamps instead of drifting. */
data class CycleLength(val count: Int, val unit: CycleUnit) {
    init {
        require(count > 0) { "cycle count must be positive" }
    }
}

/**
 * Pure date arithmetic for billing schedules.
 *
 * Occurrence k of a schedule is `anchor + k * cycle` computed from the anchor each time
 * (Jan 31 -> Feb 28 -> Mar 31), never by chaining from the previous occurrence.
 */
object BillingCalculator {

    fun cycleLength(sub: Subscription): CycleLength? = when {
        sub.kind == SubscriptionKind.LIFETIME -> null
        sub.isSingleCycle -> sub.endDate?.let { end ->
            val days = ChronoUnit.DAYS.between(sub.startDate, end).toInt()
            if (days > 0) CycleLength(days, CycleUnit.DAY) else null
        }
        else -> cycleLength(sub.billingCycle, sub.customCycleCount, sub.customCycleUnit)
    }

    fun cycleLength(cycle: BillingCycle?, customCount: Int?, customUnit: CycleUnit?): CycleLength? = when (cycle) {
        null -> null
        BillingCycle.WEEKLY -> CycleLength(1, CycleUnit.WEEK)
        BillingCycle.MONTHLY -> CycleLength(1, CycleUnit.MONTH)
        BillingCycle.QUARTERLY -> CycleLength(3, CycleUnit.MONTH)
        BillingCycle.SEMIANNUALLY -> CycleLength(6, CycleUnit.MONTH)
        BillingCycle.ANNUALLY -> CycleLength(1, CycleUnit.YEAR)
        BillingCycle.CUSTOM ->
            if (customCount != null && customCount > 0 && customUnit != null) CycleLength(customCount, customUnit) else null
    }

    /** [date] plus [times] cycles; month/year steps clamp to the last day of the target month. */
    fun add(date: LocalDate, cycle: CycleLength, times: Long = 1): LocalDate = when (cycle.unit) {
        CycleUnit.DAY -> date.plusDays(cycle.count * times)
        CycleUnit.WEEK -> date.plusWeeks(cycle.count * times)
        CycleUnit.MONTH -> date.plusMonths(cycle.count * times)
        CycleUnit.YEAR -> date.plusYears(cycle.count * times)
    }

    fun isMonthBased(cycle: CycleLength): Boolean = cycle.unit == CycleUnit.MONTH || cycle.unit == CycleUnit.YEAR

    /** Applies a fixed payment day (1..31) to a month-based occurrence, clamped to month end. */
    fun applyFixedDay(date: LocalDate, fixedDay: Int?): LocalDate {
        if (fixedDay == null) return date
        val ym = YearMonth.from(date)
        return ym.atDay(minOf(fixedDay, ym.lengthOfMonth()))
    }

    /** Occurrence k (k >= 0) of the schedule anchored at [anchor]. */
    fun occurrence(anchor: LocalDate, cycle: CycleLength, k: Long, fixedDay: Int? = null): LocalDate {
        val raw = add(anchor, cycle, k)
        return if (fixedDay != null && isMonthBased(cycle) && k > 0) applyFixedDay(raw, fixedDay) else raw
    }

    /** Index k of the first occurrence strictly after [after] (or on/after when [inclusive]). */
    fun firstOccurrenceIndex(
        anchor: LocalDate,
        cycle: CycleLength,
        after: LocalDate,
        inclusive: Boolean = false,
        fixedDay: Int? = null,
    ): Long {
        fun passes(d: LocalDate) = d > after || (inclusive && d == after)
        if (passes(anchor)) return 0
        // Estimate k from the day span, then step; avoids walking long histories one cycle at a time.
        val approxDays = when (cycle.unit) {
            CycleUnit.DAY -> cycle.count.toDouble()
            CycleUnit.WEEK -> cycle.count * 7.0
            CycleUnit.MONTH -> cycle.count * 30.436875
            CycleUnit.YEAR -> cycle.count * 365.2425
        }
        var k = maxOf(1L, (ChronoUnit.DAYS.between(anchor, after) / approxDays).toLong() - 1)
        while (k > 1 && passes(occurrence(anchor, cycle, k - 1, fixedDay))) k--
        while (!passes(occurrence(anchor, cycle, k, fixedDay))) k++
        return k
    }

    fun firstOccurrenceAfter(
        anchor: LocalDate,
        cycle: CycleLength,
        after: LocalDate,
        inclusive: Boolean = false,
        fixedDay: Int? = null,
    ): LocalDate = occurrence(anchor, cycle, firstOccurrenceIndex(anchor, cycle, after, inclusive, fixedDay), fixedDay)

    /** All occurrences within [from]..[to] inclusive. */
    fun occurrencesBetween(
        anchor: LocalDate,
        cycle: CycleLength,
        from: LocalDate,
        to: LocalDate,
        fixedDay: Int? = null,
    ): List<LocalDate> {
        if (to < from) return emptyList()
        var k = firstOccurrenceIndex(anchor, cycle, from, inclusive = true, fixedDay = fixedDay)
        return generateSequence { occurrence(anchor, cycle, k++, fixedDay) }.takeWhile { it <= to }.toList()
    }

    /** First billing date: start date, or the end of the trial for TRIAL subscriptions. */
    fun firstBillingDate(sub: Subscription): LocalDate =
        if (sub.renewalType == RenewalType.TRIAL && sub.trialStartDate != null && sub.trialDays != null) {
            sub.trialStartDate!!.plusDays(sub.trialDays!!.toLong())
        } else {
            sub.startDate
        }

    fun trialEndDate(sub: Subscription): LocalDate? =
        if (sub.trialStartDate != null && sub.trialDays != null) sub.trialStartDate!!.plusDays(sub.trialDays!!.toLong()) else null

    data class InitialSchedule(
        val anchor: LocalDate,
        val nextPaymentDate: LocalDate?,
        val lastPaymentDate: LocalDate?,
        /** Past occurrences to record as payments when history generation is on. */
        val historicalDates: List<LocalDate>,
    )

    /**
     * Schedule for a newly created subscription.
     * Past start without history generation -> next = first occurrence on/after today.
     */
    fun initialSchedule(sub: Subscription, today: LocalDate, generateHistory: Boolean): InitialSchedule {
        val anchor = firstBillingDate(sub)
        val cycle = cycleLength(sub)
        if (sub.kind == SubscriptionKind.LIFETIME || sub.kind == SubscriptionKind.WISHLIST || cycle == null) {
            return InitialSchedule(anchor, null, null, emptyList())
        }
        if (sub.isSingleCycle) {
            return InitialSchedule(anchor, if (sub.renewalType == RenewalType.AUTO) sub.endDate else null, null, emptyList())
        }
        if (anchor >= today) return InitialSchedule(anchor, capByEnd(anchor, sub.endDate), null, emptyList())
        return if (generateHistory) {
            val past = occurrencesBetween(anchor, cycle, anchor, today, sub.fixedPaymentDay)
            val next = firstOccurrenceAfter(anchor, cycle, today, inclusive = false, fixedDay = sub.fixedPaymentDay)
            InitialSchedule(anchor, capByEnd(next, sub.endDate), past.lastOrNull(), past)
        } else {
            val next = firstOccurrenceAfter(anchor, cycle, today, inclusive = true, fixedDay = sub.fixedPaymentDay)
            InitialSchedule(anchor, capByEnd(next, sub.endDate), null, emptyList())
        }
    }

    private fun capByEnd(next: LocalDate, end: LocalDate?): LocalDate? = if (end != null && next > end) null else next

    /** Timing of a manual "mark paid" relative to the due date. */
    fun timingOf(due: LocalDate, today: LocalDate, newCycle: Boolean): MarkTiming = when {
        today == due -> MarkTiming.ON_TIME
        today < due -> if (newCycle) MarkTiming.EARLY_NEW_CYCLE else MarkTiming.EARLY_ORIGINAL_CYCLE
        else -> if (newCycle) MarkTiming.OVERDUE_NEW_CYCLE else MarkTiming.OVERDUE_ORIGINAL_CYCLE
    }

    data class MarkOutcome(val anchor: LocalDate, val nextPaymentDate: LocalDate?, val lastPaymentDate: LocalDate)

    /**
     * New schedule after marking the occurrence due on [due] as paid on [paidOn].
     * ORIGINAL_CYCLE keeps the anchor; NEW_CYCLE re-bases the anchor to [paidOn].
     */
    fun markPaid(sub: Subscription, due: LocalDate, paidOn: LocalDate, timing: MarkTiming): MarkOutcome {
        val cycle = requireNotNull(cycleLength(sub)) { "subscription has no billing cycle" }
        val anchor = sub.cycleAnchorDate ?: firstBillingDate(sub)
        val rebase = timing == MarkTiming.EARLY_NEW_CYCLE || timing == MarkTiming.OVERDUE_NEW_CYCLE
        val newAnchor = if (rebase) paidOn else anchor
        val next = if (rebase) {
            add(paidOn, cycle)
        } else {
            firstOccurrenceAfter(anchor, cycle, due, inclusive = false, fixedDay = sub.fixedPaymentDay)
        }
        return MarkOutcome(newAnchor, capByEnd(next, sub.endDate), paidOn)
    }

    /** Overdue = active manual recurring subscription whose next date is in the past. */
    fun isOverdue(sub: Subscription, today: LocalDate): Boolean {
        val next = sub.nextPaymentDate ?: return false
        return sub.status == SubscriptionStatus.ACTIVE &&
            (sub.kind == SubscriptionKind.REGULAR || sub.kind == SubscriptionKind.STORED_VALUE) &&
            next < today
    }

    fun isExpired(sub: Subscription, today: LocalDate): Boolean = sub.endDate?.let { it < today } ?: false

    fun daysUntil(date: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(today, date)
}
