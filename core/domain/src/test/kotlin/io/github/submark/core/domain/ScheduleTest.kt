package io.github.submark.core.domain

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.CycleUnit
import io.github.submark.core.model.MarkTiming
import io.github.submark.core.model.RenewalType
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import org.junit.Assert.assertThrows
import org.junit.Test

class ScheduleTest {

    private val today = d("2026-10-07")

    // ---- initialSchedule ----

    @Test
    fun initial_futureStart() {
        val s = BillingCalculator.initialSchedule(sub(d("2026-11-01")), today, generateHistory = true)
        assertThat(s).isEqualTo(BillingCalculator.InitialSchedule(d("2026-11-01"), d("2026-11-01"), null, emptyList()))
    }

    @Test
    fun initial_startToday() {
        for (history in listOf(true, false)) {
            val s = BillingCalculator.initialSchedule(sub(today), today, history)
            assertThat(s.nextPaymentDate).isEqualTo(today)
            assertThat(s.historicalDates).isEmpty()
            assertThat(s.lastPaymentDate).isNull()
        }
    }

    @Test
    fun initial_pastStartWithoutHistory_nextIsOnOrAfterToday() {
        val a = BillingCalculator.initialSchedule(sub(d("2026-07-15")), today, generateHistory = false)
        assertThat(a).isEqualTo(BillingCalculator.InitialSchedule(d("2026-07-15"), d("2026-10-15"), null, emptyList()))
        val b = BillingCalculator.initialSchedule(sub(d("2026-07-07")), today, generateHistory = false)
        assertThat(b.nextPaymentDate).isEqualTo(today)
        assertThat(b.anchor).isEqualTo(d("2026-07-07"))
    }

    @Test
    fun initial_pastStartWithHistory_includesTodayAndNextIsAfterToday() {
        val s = BillingCalculator.initialSchedule(sub(d("2026-07-07")), today, generateHistory = true)
        assertThat(s.historicalDates).containsExactly(d("2026-07-07"), d("2026-08-07"), d("2026-09-07"), d("2026-10-07")).inOrder()
        assertThat(s.lastPaymentDate).isEqualTo(d("2026-10-07"))
        assertThat(s.nextPaymentDate).isEqualTo(d("2026-11-07"))
        assertThat(s.anchor).isEqualTo(d("2026-07-07"))

        val t = BillingCalculator.initialSchedule(sub(d("2026-07-15")), today, generateHistory = true)
        assertThat(t.historicalDates).containsExactly(d("2026-07-15"), d("2026-08-15"), d("2026-09-15")).inOrder()
        assertThat(t.lastPaymentDate).isEqualTo(d("2026-09-15"))
        assertThat(t.nextPaymentDate).isEqualTo(d("2026-10-15"))
    }

    @Test
    fun initial_monthEndAnchorHistoryDoesNotDrift() {
        val s = BillingCalculator.initialSchedule(sub(d("2026-05-31")), today, generateHistory = true)
        assertThat(s.historicalDates)
            .containsExactly(d("2026-05-31"), d("2026-06-30"), d("2026-07-31"), d("2026-08-31"), d("2026-09-30")).inOrder()
        assertThat(s.nextPaymentDate).isEqualTo(d("2026-10-31"))
    }

    @Test
    fun initial_longHistoryCustomCycle() {
        val s = sub(d("2016-10-07")).copy(billingCycle = BillingCycle.CUSTOM, customCycleCount = 3, customCycleUnit = CycleUnit.DAY)
        assertThat(BillingCalculator.initialSchedule(s, today, false).nextPaymentDate).isEqualTo(d("2026-10-09"))
        val h = BillingCalculator.initialSchedule(s, today, true)
        assertThat(h.historicalDates).hasSize(1218) // k = 0..1217 (1217 -> 2026-10-06)
        assertThat(h.lastPaymentDate).isEqualTo(d("2026-10-06"))
        assertThat(h.nextPaymentDate).isEqualTo(d("2026-10-09"))
    }

    @Test
    fun initial_fixedDayWithHistory() {
        val s = sub(d("2026-07-10")).copy(fixedPaymentDay = 1)
        val r = BillingCalculator.initialSchedule(s, today, generateHistory = true)
        assertThat(r.historicalDates).containsExactly(d("2026-07-10"), d("2026-08-01"), d("2026-09-01"), d("2026-10-01")).inOrder()
        assertThat(r.nextPaymentDate).isEqualTo(d("2026-11-01"))
        assertThat(BillingCalculator.initialSchedule(s, today, false).nextPaymentDate).isEqualTo(d("2026-11-01"))
    }

    @Test
    fun initial_trialInFuture() {
        val s = sub(d("2026-10-01")).copy(renewalType = RenewalType.TRIAL, trialStartDate = d("2026-10-01"), trialDays = 14)
        val r = BillingCalculator.initialSchedule(s, today, generateHistory = true)
        assertThat(r).isEqualTo(BillingCalculator.InitialSchedule(d("2026-10-15"), d("2026-10-15"), null, emptyList()))
        assertThat(BillingCalculator.trialEndDate(s)).isEqualTo(d("2026-10-15"))
    }

    @Test
    fun initial_trialEndedInPast() {
        val s = sub(d("2026-08-01")).copy(renewalType = RenewalType.TRIAL, trialStartDate = d("2026-08-01"), trialDays = 7)
        val r = BillingCalculator.initialSchedule(s, today, generateHistory = true)
        assertThat(r.anchor).isEqualTo(d("2026-08-08"))
        assertThat(r.historicalDates).containsExactly(d("2026-08-08"), d("2026-09-08")).inOrder()
        assertThat(r.nextPaymentDate).isEqualTo(d("2026-10-08"))
    }

    @Test
    fun firstBillingDate_trialFieldsIgnoredUnlessTrialRenewal() {
        val withTrialFields = sub(d("2026-10-01")).copy(trialStartDate = d("2026-10-01"), trialDays = 14)
        assertThat(BillingCalculator.firstBillingDate(withTrialFields)).isEqualTo(d("2026-10-01"))
        assertThat(BillingCalculator.firstBillingDate(withTrialFields.copy(renewalType = RenewalType.TRIAL))).isEqualTo(d("2026-10-15"))
        val incomplete = sub(d("2026-10-01")).copy(renewalType = RenewalType.TRIAL, trialStartDate = d("2026-10-01"))
        assertThat(BillingCalculator.firstBillingDate(incomplete)).isEqualTo(d("2026-10-01"))
        assertThat(BillingCalculator.trialEndDate(incomplete)).isNull()
    }

    @Test
    fun initial_singleCycleAutoVsManual() {
        val base = sub(d("2026-09-01")).copy(isSingleCycle = true, endDate = d("2026-12-01"))
        val auto = BillingCalculator.initialSchedule(base, today, generateHistory = true)
        assertThat(auto).isEqualTo(BillingCalculator.InitialSchedule(d("2026-09-01"), d("2026-12-01"), null, emptyList()))
        val manual = BillingCalculator.initialSchedule(base.copy(renewalType = RenewalType.MANUAL), today, generateHistory = true)
        assertThat(manual.nextPaymentDate).isNull()
        assertThat(manual.historicalDates).isEmpty()
    }

    @Test
    fun initial_endDateCapsNext() {
        val s = sub(d("2026-01-10"))
        assertThat(BillingCalculator.initialSchedule(s.copy(endDate = d("2026-10-08")), today, false).nextPaymentDate).isNull()
        assertThat(BillingCalculator.initialSchedule(s.copy(endDate = d("2026-10-10")), today, false).nextPaymentDate)
            .isEqualTo(d("2026-10-10"))
        assertThat(BillingCalculator.initialSchedule(s.copy(endDate = d("2026-10-09")), today, true).nextPaymentDate).isNull()
        // future start beyond end
        val f = sub(d("2026-11-01")).copy(endDate = d("2026-10-31"))
        assertThat(BillingCalculator.initialSchedule(f, today, true).nextPaymentDate).isNull()
    }

    @Test
    fun initial_endDateInPastCapsGeneratedHistory() {
        val s = sub(d("2026-06-10")).copy(endDate = d("2026-08-20"))
        val r = BillingCalculator.initialSchedule(s, today, generateHistory = true)
        assertThat(r.historicalDates).containsExactly(d("2026-06-10"), d("2026-07-10"), d("2026-08-10")).inOrder()
        assertThat(r.lastPaymentDate).isEqualTo(d("2026-08-10"))
        assertThat(r.nextPaymentDate).isNull()
        // an occurrence on the end date itself is still inside the validity
        val onEnd = BillingCalculator.initialSchedule(s.copy(endDate = d("2026-08-10")), today, true)
        assertThat(onEnd.historicalDates.last()).isEqualTo(d("2026-08-10"))
    }

    @Test
    fun initial_lifetimeAndWishlistHaveNoSchedule() {
        val lifetime = sub(d("2026-01-01")).copy(kind = SubscriptionKind.LIFETIME, billingCycle = null)
        assertThat(BillingCalculator.initialSchedule(lifetime, today, true))
            .isEqualTo(BillingCalculator.InitialSchedule(d("2026-01-01"), null, null, emptyList()))
        val lifetimeWithCycle = sub(d("2026-01-01")).copy(kind = SubscriptionKind.LIFETIME)
        assertThat(BillingCalculator.initialSchedule(lifetimeWithCycle, today, true).historicalDates).isEmpty()
        val wish = sub(d("2026-01-01")).copy(kind = SubscriptionKind.WISHLIST)
        val w = BillingCalculator.initialSchedule(wish, today, true)
        assertThat(w.nextPaymentDate).isNull()
        assertThat(w.historicalDates).isEmpty()
        val wishFuture = sub(d("2027-01-01")).copy(kind = SubscriptionKind.WISHLIST, billingCycle = null)
        assertThat(BillingCalculator.initialSchedule(wishFuture, today, true).nextPaymentDate).isNull()
    }

    @Test
    fun initial_invalidCustomCycleHasNoSchedule() {
        val s = sub(d("2026-01-01")).copy(billingCycle = BillingCycle.CUSTOM, customCycleCount = null)
        assertThat(BillingCalculator.initialSchedule(s, today, true).nextPaymentDate).isNull()
    }

    // ---- timingOf ----

    @Test
    fun timingOf() {
        val due = d("2026-10-07")
        assertThat(BillingCalculator.timingOf(due, due, true)).isEqualTo(MarkTiming.ON_TIME)
        assertThat(BillingCalculator.timingOf(due, due, false)).isEqualTo(MarkTiming.ON_TIME)
        assertThat(BillingCalculator.timingOf(due, d("2026-10-01"), true)).isEqualTo(MarkTiming.EARLY_NEW_CYCLE)
        assertThat(BillingCalculator.timingOf(due, d("2026-10-01"), false)).isEqualTo(MarkTiming.EARLY_ORIGINAL_CYCLE)
        assertThat(BillingCalculator.timingOf(due, d("2026-10-09"), true)).isEqualTo(MarkTiming.OVERDUE_NEW_CYCLE)
        assertThat(BillingCalculator.timingOf(due, d("2026-10-09"), false)).isEqualTo(MarkTiming.OVERDUE_ORIGINAL_CYCLE)
    }

    // ---- markPaid ----

    /** Monthly schedule anchored on Jan 31: Sep 30 is due, the following occurrence is Oct 31. */
    private val monthEnd = sub(d("2026-01-31")).copy(cycleAnchorDate = d("2026-01-31"), nextPaymentDate = d("2026-09-30"))
    private val due = d("2026-09-30")

    @Test
    fun markPaid_onTime_keepsAnchorAndDoesNotDrift() {
        val r = BillingCalculator.markPaid(monthEnd, due, due, MarkTiming.ON_TIME)
        assertThat(r).isEqualTo(BillingCalculator.MarkOutcome(d("2026-01-31"), d("2026-10-31"), due))
    }

    @Test
    fun markPaid_earlyOriginal() {
        val r = BillingCalculator.markPaid(monthEnd, due, d("2026-09-25"), MarkTiming.EARLY_ORIGINAL_CYCLE)
        assertThat(r).isEqualTo(BillingCalculator.MarkOutcome(d("2026-01-31"), d("2026-10-31"), d("2026-09-25")))
    }

    @Test
    fun markPaid_earlyNew_rebasesAnchor() {
        val r = BillingCalculator.markPaid(monthEnd, due, d("2026-09-25"), MarkTiming.EARLY_NEW_CYCLE)
        assertThat(r).isEqualTo(BillingCalculator.MarkOutcome(d("2026-09-25"), d("2026-10-25"), d("2026-09-25")))
    }

    @Test
    fun markPaid_overdueOriginal() {
        val r = BillingCalculator.markPaid(monthEnd, due, d("2026-10-05"), MarkTiming.OVERDUE_ORIGINAL_CYCLE)
        assertThat(r).isEqualTo(BillingCalculator.MarkOutcome(d("2026-01-31"), d("2026-10-31"), d("2026-10-05")))
    }

    @Test
    fun markPaid_overdueOriginal_farBehindStaysOverdue() {
        val r = BillingCalculator.markPaid(monthEnd, d("2026-07-31"), today, MarkTiming.OVERDUE_ORIGINAL_CYCLE)
        assertThat(r.nextPaymentDate).isEqualTo(d("2026-08-31"))
        assertThat(r.anchor).isEqualTo(d("2026-01-31"))
    }

    @Test
    fun markPaid_overdueNew_rebasesAnchor() {
        val r = BillingCalculator.markPaid(monthEnd, due, d("2026-10-05"), MarkTiming.OVERDUE_NEW_CYCLE)
        assertThat(r).isEqualTo(BillingCalculator.MarkOutcome(d("2026-10-05"), d("2026-11-05"), d("2026-10-05")))
    }

    @Test
    fun markPaid_newCycle_monthEndPaymentClampsFromNewAnchor() {
        val r = BillingCalculator.markPaid(monthEnd, d("2026-12-31"), d("2027-01-31"), MarkTiming.OVERDUE_NEW_CYCLE)
        assertThat(r.nextPaymentDate).isEqualTo(d("2027-02-28"))
        // the occurrence after that comes from the re-based anchor, not chained from Feb 28
        val after = BillingCalculator.firstOccurrenceAfter(r.anchor, CycleLength(1, CycleUnit.MONTH), r.nextPaymentDate!!)
        assertThat(after).isEqualTo(d("2027-03-31"))
    }

    @Test
    fun markPaid_missingAnchorFallsBackToFirstBillingDate() {
        val s = sub(d("2026-01-31")).copy(nextPaymentDate = due)
        assertThat(BillingCalculator.markPaid(s, due, due, MarkTiming.ON_TIME).nextPaymentDate).isEqualTo(d("2026-10-31"))
        assertThat(BillingCalculator.markPaid(s, due, due, MarkTiming.ON_TIME).anchor).isEqualTo(d("2026-01-31"))
        val trial = sub(d("2026-08-01")).copy(renewalType = RenewalType.TRIAL, trialStartDate = d("2026-08-01"), trialDays = 7)
        assertThat(BillingCalculator.markPaid(trial, d("2026-09-08"), d("2026-09-08"), MarkTiming.ON_TIME).nextPaymentDate)
            .isEqualTo(d("2026-10-08"))
    }

    @Test
    fun markPaid_endDateCaps() {
        val s = monthEnd.copy(endDate = d("2026-10-15"))
        assertThat(BillingCalculator.markPaid(s, due, due, MarkTiming.ON_TIME).nextPaymentDate).isNull()
        assertThat(BillingCalculator.markPaid(s, due, d("2026-10-05"), MarkTiming.OVERDUE_NEW_CYCLE).nextPaymentDate).isNull()
        assertThat(BillingCalculator.markPaid(s, due, d("2026-09-14"), MarkTiming.EARLY_NEW_CYCLE).nextPaymentDate)
            .isEqualTo(d("2026-10-14"))
        assertThat(BillingCalculator.markPaid(s.copy(endDate = d("2026-10-31")), due, due, MarkTiming.ON_TIME).nextPaymentDate)
            .isEqualTo(d("2026-10-31"))
    }

    @Test
    fun markPaid_weeklyAndCustom() {
        val weekly = sub(d("2026-01-01")).copy(billingCycle = BillingCycle.WEEKLY, cycleAnchorDate = d("2026-01-01"))
        assertThat(BillingCalculator.markPaid(weekly, d("2026-10-08"), d("2026-10-08"), MarkTiming.ON_TIME).nextPaymentDate)
            .isEqualTo(d("2026-10-15"))
        assertThat(BillingCalculator.markPaid(weekly, d("2026-10-08"), today, MarkTiming.EARLY_NEW_CYCLE).nextPaymentDate)
            .isEqualTo(d("2026-10-14"))
        val custom = sub(d("2016-10-07")).copy(
            billingCycle = BillingCycle.CUSTOM, customCycleCount = 3, customCycleUnit = CycleUnit.DAY, cycleAnchorDate = d("2016-10-07"),
        )
        assertThat(BillingCalculator.markPaid(custom, d("2026-10-06"), today, MarkTiming.OVERDUE_ORIGINAL_CYCLE).nextPaymentDate)
            .isEqualTo(d("2026-10-09"))
    }

    @Test
    fun markPaid_fixedDayOriginalCycle() {
        val s = sub(d("2026-01-15")).copy(cycleAnchorDate = d("2026-01-15"), fixedPaymentDay = 31)
        assertThat(BillingCalculator.markPaid(s, d("2026-01-15"), d("2026-01-15"), MarkTiming.ON_TIME).nextPaymentDate)
            .isEqualTo(d("2026-02-28"))
        assertThat(BillingCalculator.markPaid(s, d("2026-02-28"), d("2026-02-28"), MarkTiming.ON_TIME).nextPaymentDate)
            .isEqualTo(d("2026-03-31"))
    }

    /** After a re-base the returned next date must be occurrence 1 of the new schedule (fixed day included). */
    @Test
    fun markPaid_newCycleNextIsConsistentWithRebasedSchedule() {
        val s = sub(d("2026-01-01")).copy(cycleAnchorDate = d("2026-01-01"), fixedPaymentDay = 1)
        val r = BillingCalculator.markPaid(s, d("2026-03-01"), d("2026-03-20"), MarkTiming.OVERDUE_NEW_CYCLE)
        assertThat(r.anchor).isEqualTo(d("2026-03-20"))
        val cycle = BillingCalculator.cycleLength(s)!!
        assertThat(r.nextPaymentDate)
            .isEqualTo(BillingCalculator.firstOccurrenceAfter(r.anchor, cycle, r.anchor, fixedDay = s.fixedPaymentDay))
        assertThat(r.nextPaymentDate).isEqualTo(d("2026-04-01"))
    }

    @Test
    fun markPaid_withoutCycleThrows() {
        val lifetime = sub(d("2026-01-01")).copy(kind = SubscriptionKind.LIFETIME)
        assertThrows(IllegalArgumentException::class.java) {
            BillingCalculator.markPaid(lifetime, due, due, MarkTiming.ON_TIME)
        }
    }

    // ---- overdue / expired ----

    @Test
    fun isOverdue() {
        val s = sub(d("2026-01-01")).copy(nextPaymentDate = d("2026-10-06"))
        assertThat(BillingCalculator.isOverdue(s, today)).isTrue()
        assertThat(BillingCalculator.isOverdue(s.copy(kind = SubscriptionKind.STORED_VALUE), today)).isTrue()
        assertThat(BillingCalculator.isOverdue(s.copy(nextPaymentDate = today), today)).isFalse()
        assertThat(BillingCalculator.isOverdue(s.copy(nextPaymentDate = d("2026-10-08")), today)).isFalse()
        assertThat(BillingCalculator.isOverdue(s.copy(nextPaymentDate = null), today)).isFalse()
        assertThat(BillingCalculator.isOverdue(s.copy(status = SubscriptionStatus.PAUSED), today)).isFalse()
        assertThat(BillingCalculator.isOverdue(s.copy(kind = SubscriptionKind.LIFETIME), today)).isFalse()
        assertThat(BillingCalculator.isOverdue(s.copy(kind = SubscriptionKind.WISHLIST), today)).isFalse()
    }

    @Test
    fun isExpired() {
        val s = sub(d("2026-01-01"))
        assertThat(BillingCalculator.isExpired(s, today)).isFalse()
        assertThat(BillingCalculator.isExpired(s.copy(endDate = d("2026-10-06")), today)).isTrue()
        assertThat(BillingCalculator.isExpired(s.copy(endDate = today), today)).isFalse()
        assertThat(BillingCalculator.isExpired(s.copy(endDate = d("2026-10-08")), today)).isFalse()
    }
}
