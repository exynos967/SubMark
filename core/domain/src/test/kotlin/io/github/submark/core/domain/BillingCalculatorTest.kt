package io.github.submark.core.domain

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.CycleUnit
import io.github.submark.core.model.SubscriptionKind
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class BillingCalculatorTest {

    private val monthly = CycleLength(1, CycleUnit.MONTH)
    private val quarterly = CycleLength(3, CycleUnit.MONTH)
    private val annual = CycleLength(1, CycleUnit.YEAR)
    private val weekly = CycleLength(1, CycleUnit.WEEK)
    private val threeDays = CycleLength(3, CycleUnit.DAY)

    // ---- cycleLength ----

    @Test
    fun cycleLength_everyBillingCycle() {
        val expected = mapOf(
            BillingCycle.WEEKLY to CycleLength(1, CycleUnit.WEEK),
            BillingCycle.MONTHLY to CycleLength(1, CycleUnit.MONTH),
            BillingCycle.QUARTERLY to CycleLength(3, CycleUnit.MONTH),
            BillingCycle.SEMIANNUALLY to CycleLength(6, CycleUnit.MONTH),
            BillingCycle.ANNUALLY to CycleLength(1, CycleUnit.YEAR),
        )
        expected.forEach { (cycle, length) ->
            assertThat(BillingCalculator.cycleLength(cycle, null, null)).isEqualTo(length)
            // custom fields are ignored for non-custom cycles
            assertThat(BillingCalculator.cycleLength(cycle, 5, CycleUnit.DAY)).isEqualTo(length)
        }
        assertThat(BillingCalculator.cycleLength(null, 3, CycleUnit.DAY)).isNull()
    }

    @Test
    fun cycleLength_custom() {
        CycleUnit.entries.forEach { unit ->
            assertThat(BillingCalculator.cycleLength(BillingCycle.CUSTOM, 3, unit)).isEqualTo(CycleLength(3, unit))
        }
        assertThat(BillingCalculator.cycleLength(BillingCycle.CUSTOM, null, CycleUnit.DAY)).isNull()
        assertThat(BillingCalculator.cycleLength(BillingCycle.CUSTOM, 0, CycleUnit.DAY)).isNull()
        assertThat(BillingCalculator.cycleLength(BillingCycle.CUSTOM, -2, CycleUnit.DAY)).isNull()
        assertThat(BillingCalculator.cycleLength(BillingCycle.CUSTOM, 3, null)).isNull()
    }

    @Test
    fun cycleLength_subscription() {
        val base = sub(d("2026-01-01"))
        assertThat(BillingCalculator.cycleLength(base)).isEqualTo(monthly)
        assertThat(
            BillingCalculator.cycleLength(base.copy(billingCycle = BillingCycle.CUSTOM, customCycleCount = 13, customCycleUnit = CycleUnit.DAY)),
        ).isEqualTo(CycleLength(13, CycleUnit.DAY))
        // lifetime has no cycle even if a cycle is set
        assertThat(BillingCalculator.cycleLength(base.copy(kind = SubscriptionKind.LIFETIME))).isNull()
        assertThat(BillingCalculator.cycleLength(base.copy(kind = SubscriptionKind.WISHLIST, billingCycle = null))).isNull()
        // single-cycle: start..end in days, overriding billingCycle
        assertThat(BillingCalculator.cycleLength(base.copy(isSingleCycle = true, endDate = d("2026-01-31"))))
            .isEqualTo(CycleLength(30, CycleUnit.DAY))
        assertThat(BillingCalculator.cycleLength(base.copy(isSingleCycle = true, endDate = null))).isNull()
        assertThat(BillingCalculator.cycleLength(base.copy(isSingleCycle = true, endDate = d("2026-01-01")))).isNull()
        assertThat(BillingCalculator.cycleLength(base.copy(isSingleCycle = true, endDate = d("2025-12-01")))).isNull()
    }

    @Test
    fun cycleLength_rejectsNonPositiveCount() {
        assertThrows(IllegalArgumentException::class.java) { CycleLength(0, CycleUnit.DAY) }
        assertThrows(IllegalArgumentException::class.java) { CycleLength(-1, CycleUnit.MONTH) }
    }

    // ---- add ----

    @Test
    fun add_monthEndClamping() {
        assertThat(BillingCalculator.add(d("2023-01-31"), monthly)).isEqualTo(d("2023-02-28"))
        assertThat(BillingCalculator.add(d("2024-01-31"), monthly)).isEqualTo(d("2024-02-29"))
        assertThat(BillingCalculator.add(d("2024-03-31"), monthly)).isEqualTo(d("2024-04-30"))
        assertThat(BillingCalculator.add(d("2025-08-31"), quarterly)).isEqualTo(d("2025-11-30"))
        assertThat(BillingCalculator.add(d("2025-08-31"), CycleLength(6, CycleUnit.MONTH))).isEqualTo(d("2026-02-28"))
    }

    @Test
    fun add_leapYears() {
        assertThat(BillingCalculator.add(d("2024-02-29"), annual)).isEqualTo(d("2025-02-28"))
        assertThat(BillingCalculator.add(d("2024-02-29"), annual, 4)).isEqualTo(d("2028-02-29"))
        assertThat(BillingCalculator.add(d("2024-02-29"), CycleLength(12, CycleUnit.MONTH))).isEqualTo(d("2025-02-28"))
    }

    @Test
    fun add_timesAndUnits() {
        assertThat(BillingCalculator.add(d("2026-01-01"), weekly, 3)).isEqualTo(d("2026-01-22"))
        assertThat(BillingCalculator.add(d("2026-01-01"), threeDays, 5)).isEqualTo(d("2026-01-16"))
        assertThat(BillingCalculator.add(d("2026-01-01"), monthly, 0)).isEqualTo(d("2026-01-01"))
        assertThat(BillingCalculator.add(d("2026-12-31"), CycleLength(2, CycleUnit.WEEK))).isEqualTo(d("2027-01-14"))
    }

    // ---- occurrence: anchor based, no drift ----

    @Test
    fun occurrence_doesNotDrift() {
        val anchor = d("2024-01-31")
        assertThat((0L..5L).map { BillingCalculator.occurrence(anchor, monthly, it) }).containsExactly(
            d("2024-01-31"), d("2024-02-29"), d("2024-03-31"), d("2024-04-30"), d("2024-05-31"), d("2024-06-30"),
        ).inOrder()
        assertThat(BillingCalculator.occurrence(d("2023-01-31"), monthly, 1)).isEqualTo(d("2023-02-28"))
        assertThat(BillingCalculator.occurrence(d("2023-01-31"), monthly, 2)).isEqualTo(d("2023-03-31"))
    }

    @Test
    fun occurrence_leapAnchorAnnual() {
        val anchor = d("2024-02-29")
        assertThat((1L..4L).map { BillingCalculator.occurrence(anchor, annual, it) })
            .containsExactly(d("2025-02-28"), d("2026-02-28"), d("2027-02-28"), d("2028-02-29")).inOrder()
    }

    // ---- fixed payment day ----

    @Test
    fun fixedDay_31_inShortMonths() {
        val anchor = d("2024-01-15")
        assertThat((0L..4L).map { BillingCalculator.occurrence(anchor, monthly, it, fixedDay = 31) }).containsExactly(
            d("2024-01-15"), // the anchor itself is never moved
            d("2024-02-29"),
            d("2024-03-31"),
            d("2024-04-30"),
            d("2024-05-31"),
        ).inOrder()
        assertThat(BillingCalculator.occurrence(d("2023-01-15"), monthly, 1, fixedDay = 30)).isEqualTo(d("2023-02-28"))
    }

    @Test
    fun fixedDay_quarterly() {
        val anchor = d("2025-01-10")
        assertThat((1L..4L).map { BillingCalculator.occurrence(anchor, quarterly, it, fixedDay = 31) })
            .containsExactly(d("2025-04-30"), d("2025-07-31"), d("2025-10-31"), d("2026-01-31")).inOrder()
        // month-end anchor with a small fixed day stays in the anchor's month sequence
        assertThat(BillingCalculator.occurrence(d("2025-01-31"), quarterly, 1, fixedDay = 5)).isEqualTo(d("2025-04-05"))
    }

    @Test
    fun fixedDay_annualAndCustomMonths() {
        assertThat(BillingCalculator.occurrence(d("2023-02-10"), annual, 1, fixedDay = 29)).isEqualTo(d("2024-02-29"))
        assertThat(BillingCalculator.occurrence(d("2023-02-10"), annual, 2, fixedDay = 29)).isEqualTo(d("2025-02-28"))
        assertThat(BillingCalculator.occurrence(d("2026-01-20"), CycleLength(2, CycleUnit.MONTH), 1, fixedDay = 31))
            .isEqualTo(d("2026-03-31"))
    }

    @Test
    fun fixedDay_ignoredForDayAndWeekCycles() {
        assertThat(BillingCalculator.occurrence(d("2025-01-01"), weekly, 1, fixedDay = 31)).isEqualTo(d("2025-01-08"))
        assertThat(BillingCalculator.occurrence(d("2025-01-01"), threeDays, 2, fixedDay = 31)).isEqualTo(d("2025-01-07"))
    }

    @Test
    fun applyFixedDay() {
        assertThat(BillingCalculator.applyFixedDay(d("2026-02-10"), null)).isEqualTo(d("2026-02-10"))
        assertThat(BillingCalculator.applyFixedDay(d("2026-02-10"), 31)).isEqualTo(d("2026-02-28"))
        assertThat(BillingCalculator.applyFixedDay(d("2026-02-10"), 1)).isEqualTo(d("2026-02-01"))
        assertThat(BillingCalculator.isMonthBased(monthly)).isTrue()
        assertThat(BillingCalculator.isMonthBased(annual)).isTrue()
        assertThat(BillingCalculator.isMonthBased(weekly)).isFalse()
        assertThat(BillingCalculator.isMonthBased(threeDays)).isFalse()
    }

    // ---- firstOccurrenceIndex / After ----

    @Test
    fun firstOccurrence_inclusiveVsExclusive() {
        val anchor = d("2024-01-31")
        assertThat(BillingCalculator.firstOccurrenceIndex(anchor, monthly, d("2024-03-31"))).isEqualTo(3)
        assertThat(BillingCalculator.firstOccurrenceIndex(anchor, monthly, d("2024-03-31"), inclusive = true)).isEqualTo(2)
        assertThat(BillingCalculator.firstOccurrenceAfter(anchor, monthly, d("2024-03-31"))).isEqualTo(d("2024-04-30"))
        assertThat(BillingCalculator.firstOccurrenceAfter(anchor, monthly, d("2024-03-30"))).isEqualTo(d("2024-03-31"))
        // at the anchor itself
        assertThat(BillingCalculator.firstOccurrenceIndex(anchor, monthly, anchor)).isEqualTo(1)
        assertThat(BillingCalculator.firstOccurrenceIndex(anchor, monthly, anchor, inclusive = true)).isEqualTo(0)
    }

    @Test
    fun firstOccurrence_anchorAfterReference() {
        val anchor = d("2027-01-01")
        assertThat(BillingCalculator.firstOccurrenceIndex(anchor, monthly, d("2026-10-07"))).isEqualTo(0)
        assertThat(BillingCalculator.firstOccurrenceAfter(anchor, weekly, d("2026-10-07"), inclusive = true)).isEqualTo(anchor)
        assertThat(BillingCalculator.firstOccurrenceAfter(anchor, monthly, d("2016-10-07"), fixedDay = 15)).isEqualTo(anchor)
    }

    @Test
    fun firstOccurrence_longHistoryWeekly() {
        // 2016-10-06 and 2026-10-08 are Thursdays
        val anchor = d("2016-10-06")
        assertThat(BillingCalculator.firstOccurrenceAfter(anchor, weekly, d("2026-10-07"))).isEqualTo(d("2026-10-08"))
        assertThat(BillingCalculator.firstOccurrenceAfter(anchor, weekly, d("2026-10-08"), inclusive = true)).isEqualTo(d("2026-10-08"))
        assertThat(BillingCalculator.firstOccurrenceAfter(anchor, weekly, d("2026-10-08"))).isEqualTo(d("2026-10-15"))
        assertThat(BillingCalculator.firstOccurrenceIndex(anchor, weekly, d("2026-10-07"))).isEqualTo(522)
    }

    @Test
    fun firstOccurrence_longHistoryCustomThreeDays() {
        // 2016-10-07 -> 2026-10-07 = 3652 days; occurrences at day 3651 (Oct 6) and 3654 (Oct 9)
        val anchor = d("2016-10-07")
        assertThat(BillingCalculator.firstOccurrenceAfter(anchor, threeDays, d("2026-10-07"))).isEqualTo(d("2026-10-09"))
        assertThat(BillingCalculator.firstOccurrenceAfter(anchor, threeDays, d("2026-10-07"), inclusive = true)).isEqualTo(d("2026-10-09"))
        assertThat(BillingCalculator.firstOccurrenceAfter(anchor, threeDays, d("2026-10-06"), inclusive = true)).isEqualTo(d("2026-10-06"))
        assertThat(BillingCalculator.firstOccurrenceAfter(anchor, threeDays, d("2026-10-06"))).isEqualTo(d("2026-10-09"))
        assertThat(BillingCalculator.firstOccurrenceIndex(anchor, threeDays, d("2026-10-06"), inclusive = true)).isEqualTo(1217)
    }

    /** Compares the estimate-and-step search with a brute-force walk for many cycles and reference dates. */
    @Test
    fun firstOccurrence_matchesBruteForce() {
        val cases = listOf(
            Triple(d("2016-10-07"), weekly, null),
            Triple(d("2016-10-07"), threeDays, null),
            Triple(d("2016-01-31"), monthly, null),
            Triple(d("2016-01-31"), monthly, 31),
            Triple(d("2016-01-31"), monthly, 1),
            Triple(d("2016-02-29"), annual, null),
            Triple(d("2016-02-29"), annual, 29),
            Triple(d("2016-08-31"), quarterly, null),
            Triple(d("2016-03-10"), quarterly, 31),
            Triple(d("2016-05-31"), CycleLength(6, CycleUnit.MONTH), 30),
            Triple(d("2016-05-17"), CycleLength(13, CycleUnit.DAY), null),
            Triple(d("2016-12-31"), CycleLength(2, CycleUnit.WEEK), null),
            Triple(d("2016-12-31"), CycleLength(2, CycleUnit.YEAR), 31),
            Triple(d("2016-12-31"), CycleLength(1, CycleUnit.DAY), null),
        )
        for ((anchor, cycle, fixed) in cases) {
            var ref = anchor.minusDays(40)
            val end = anchor.plusYears(10).plusDays(400)
            while (ref <= end) {
                for (inclusive in listOf(false, true)) {
                    val expected = bruteIndex(anchor, cycle, ref, inclusive, fixed)
                    val actual = BillingCalculator.firstOccurrenceIndex(anchor, cycle, ref, inclusive, fixed)
                    assertThat(actual).isEqualTo(expected)
                }
                ref = ref.plusDays(if (ref < anchor.plusYears(9)) 11 else 1)
            }
        }
    }

    private fun bruteIndex(anchor: LocalDate, cycle: CycleLength, after: LocalDate, inclusive: Boolean, fixed: Int?): Long {
        var k = 0L
        while (true) {
            val occ = BillingCalculator.occurrence(anchor, cycle, k, fixed)
            if (occ > after || (inclusive && occ == after)) return k
            k++
        }
    }

    @Test
    fun occurrences_strictlyIncreasing() {
        val anchors = listOf(d("2024-01-31"), d("2024-02-29"), d("2023-12-31"), d("2024-01-01"))
        val cycles = listOf(monthly, quarterly, annual, CycleLength(2, CycleUnit.MONTH))
        for (a in anchors) for (c in cycles) for (fixed in listOf(null, 1, 15, 28, 29, 30, 31)) {
            val occ = (0L..60L).map { BillingCalculator.occurrence(a, c, it, fixed) }
            occ.zipWithNext().forEach { (x, y) -> assertThat(y).isGreaterThan(x) }
        }
    }

    // ---- occurrencesBetween ----

    @Test
    fun occurrencesBetween_inclusiveBounds() {
        val anchor = d("2024-01-31")
        assertThat(BillingCalculator.occurrencesBetween(anchor, monthly, d("2024-02-29"), d("2024-04-30")))
            .containsExactly(d("2024-02-29"), d("2024-03-31"), d("2024-04-30")).inOrder()
        assertThat(BillingCalculator.occurrencesBetween(anchor, monthly, d("2024-03-01"), d("2024-04-29")))
            .containsExactly(d("2024-03-31"))
        assertThat(BillingCalculator.occurrencesBetween(anchor, monthly, d("2024-03-31"), d("2024-03-31")))
            .containsExactly(d("2024-03-31"))
        assertThat(BillingCalculator.occurrencesBetween(anchor, monthly, d("2024-04-01"), d("2024-04-29"))).isEmpty()
        assertThat(BillingCalculator.occurrencesBetween(anchor, monthly, d("2024-04-30"), d("2024-03-01"))).isEmpty()
    }

    @Test
    fun occurrencesBetween_rangeStartingBeforeAnchor() {
        assertThat(BillingCalculator.occurrencesBetween(d("2024-01-31"), monthly, d("2023-12-01"), d("2024-02-01")))
            .containsExactly(d("2024-01-31"))
        assertThat(BillingCalculator.occurrencesBetween(d("2024-01-31"), monthly, d("2023-12-01"), d("2024-01-30"))).isEmpty()
    }

    @Test
    fun occurrencesBetween_withFixedDayAndLongHistory() {
        assertThat(BillingCalculator.occurrencesBetween(d("2016-01-15"), monthly, d("2026-01-01"), d("2026-04-30"), fixedDay = 31))
            .containsExactly(d("2026-01-31"), d("2026-02-28"), d("2026-03-31"), d("2026-04-30")).inOrder()
        val weeks = BillingCalculator.occurrencesBetween(d("2016-10-06"), weekly, d("2026-09-01"), d("2026-09-30"))
        assertThat(weeks).containsExactly(d("2026-09-03"), d("2026-09-10"), d("2026-09-17"), d("2026-09-24")).inOrder()
    }

    // ---- misc ----

    @Test
    fun daysUntil() {
        assertThat(BillingCalculator.daysUntil(d("2026-10-08"), d("2026-10-07"))).isEqualTo(1)
        assertThat(BillingCalculator.daysUntil(d("2026-10-07"), d("2026-10-07"))).isEqualTo(0)
        assertThat(BillingCalculator.daysUntil(d("2026-10-01"), d("2026-10-07"))).isEqualTo(-6)
    }
}
