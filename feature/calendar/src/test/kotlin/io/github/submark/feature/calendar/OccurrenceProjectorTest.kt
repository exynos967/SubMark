package io.github.submark.feature.calendar

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.domain.CurrencyConverter
import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.PaymentRecord
import io.github.submark.core.model.PaymentStatus
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import io.github.submark.feature.calendar.data.CalendarPeriod
import io.github.submark.feature.calendar.data.OccurrenceProjector
import io.github.submark.feature.calendar.data.periodRange
import io.github.submark.feature.calendar.data.periodSplit
import io.github.submark.feature.calendar.data.timelineBuckets
import io.github.submark.core.data.settings.TimelinePeriod
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class OccurrenceProjectorTest {

    private val today = LocalDate.of(2026, 10, 8)
    private val converter = CurrencyConverter(mapOf("USD" to BigDecimal.ONE))

    private fun sub(
        id: String = "s1",
        name: String = "Test",
        price: BigDecimal = BigDecimal("9.99"),
        cycle: BillingCycle? = BillingCycle.MONTHLY,
        anchor: LocalDate = LocalDate.of(2026, 8, 1),
        next: LocalDate? = LocalDate.of(2026, 10, 1),
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        kind: SubscriptionKind = SubscriptionKind.REGULAR,
        parentId: String? = null,
        isSingleCycle: Boolean = false,
        endDate: LocalDate? = null,
        fixedDay: Int? = null,
    ) = Subscription(
        id = id, name = name, price = price, currencyCode = "USD",
        billingCycle = cycle, cycleAnchorDate = anchor, nextPaymentDate = next,
        lastPaymentDate = anchor, status = status, kind = kind, parentId = parentId,
        isSingleCycle = isSingleCycle, endDate = endDate, fixedPaymentDay = fixedDay,
        categoryId = "c1", startDate = anchor, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    private fun payment(subId: String, date: LocalDate, amount: BigDecimal = BigDecimal("9.99")) = PaymentRecord(
        subscriptionId = subId, amount = amount, currencyCode = "USD", paymentDate = date,
        status = PaymentStatus.SUCCESS, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    // ---- Projection across a range ----

    @Test
    fun `projected monthly occurrences match subscription cycle from the next date on`() {
        val s = sub(anchor = LocalDate.of(2026, 9, 1), next = LocalDate.of(2026, 10, 1), cycle = BillingCycle.MONTHLY)
        val byDate = OccurrenceProjector.project(
            subs = listOf(s), payments = emptyList(),
            from = LocalDate.of(2026, 9, 1), to = LocalDate.of(2026, 12, 31),
            defaultCode = "USD", converter = converter,
        )
        // Outstanding dues only: occurrences at or after nextPaymentDate; earlier ones were paid/auto-marked.
        assertThat(byDate.keys).containsExactly(
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 12, 1),
        )
    }

    @Test
    fun `paused and wishlist subscriptions are excluded`() {
        val paused = sub(status = SubscriptionStatus.PAUSED)
        val wishlist = sub(kind = SubscriptionKind.WISHLIST, cycle = BillingCycle.MONTHLY)
        val byDate = OccurrenceProjector.project(
            subs = listOf(paused, wishlist), payments = emptyList(),
            from = today, to = today.plusMonths(3),
            defaultCode = "USD", converter = converter,
        )
        assertThat(byDate).isEmpty()
    }

    @Test
    fun `end date caps the projection`() {
        val s = sub(anchor = LocalDate.of(2026, 9, 1), next = LocalDate.of(2026, 10, 1), endDate = LocalDate.of(2026, 10, 15))
        val byDate = OccurrenceProjector.project(
            subs = listOf(s), payments = emptyList(),
            from = LocalDate.of(2026, 9, 1), to = LocalDate.of(2026, 12, 31),
            defaultCode = "USD", converter = converter,
        )
        assertThat(byDate.keys).containsExactly(LocalDate.of(2026, 10, 1))
    }

    @Test
    fun `payment record on a projected date merges into the scheduled slot`() {
        val s = sub(anchor = LocalDate.of(2026, 9, 1), next = LocalDate.of(2026, 10, 1))
        val record = payment(s.id, LocalDate.of(2026, 10, 1))
        val byDate = OccurrenceProjector.project(
            subs = listOf(s), payments = listOf(record),
            from = LocalDate.of(2026, 9, 1), to = LocalDate.of(2026, 11, 30),
            defaultCode = "USD", converter = converter,
        )
        val onOct1 = byDate[LocalDate.of(2026, 10, 1)].orEmpty()
        assertThat(onOct1).hasSize(1)
        assertThat(onOct1.first().paid).isTrue()
    }

    @Test
    fun `bundle children are hidden unless includeChildren is set`() {
        val main = sub(id = "main")
        val child = sub(id = "child", parentId = "main", next = LocalDate.of(2026, 10, 2))
        val without = OccurrenceProjector.project(
            subs = listOf(main, child), payments = emptyList(),
            from = LocalDate.of(2026, 10, 1), to = LocalDate.of(2026, 10, 31),
            defaultCode = "USD", converter = converter, includeChildren = false,
        )
        assertThat(without[LocalDate.of(2026, 10, 2)].orEmpty()).isEmpty()
        val with = OccurrenceProjector.project(
            subs = listOf(main, child), payments = emptyList(),
            from = LocalDate.of(2026, 10, 1), to = LocalDate.of(2026, 10, 31),
            defaultCode = "USD", converter = converter, includeChildren = true,
        )
        assertThat(with[LocalDate.of(2026, 10, 2)].orEmpty()).hasSize(1)
    }

    // ---- Period paid/scheduled split ----

    @Test
    fun `period split counts paid records and unpaid occurrences separately`() {
        val s = sub(anchor = LocalDate.of(2026, 9, 1), next = LocalDate.of(2026, 12, 1), cycle = BillingCycle.MONTHLY)
        val paidRecord = payment(s.id, LocalDate.of(2026, 10, 1))
        val byDate = OccurrenceProjector.project(
            subs = listOf(s), payments = listOf(paidRecord),
            from = LocalDate.of(2026, 9, 1), to = LocalDate.of(2026, 12, 31),
            defaultCode = "USD", converter = converter,
        )
        val split = periodSplit(byDate, CalendarPeriod.MONTH, LocalDate.of(2026, 10, 15))
        assertThat(split.paid.compareTo(BigDecimal("9.99"))).isEqualTo(0)
        assertThat(split.scheduled.compareTo(BigDecimal.ZERO)).isEqualTo(0)
    }

    @Test
    fun `quarter period spans calendar quarter`() {
        val (start, end) = periodRange(CalendarPeriod.QUARTER, LocalDate.of(2026, 5, 20))
        assertThat(start).isEqualTo(LocalDate.of(2026, 4, 1))
        assertThat(end).isEqualTo(LocalDate.of(2026, 6, 30))
    }

    // ---- Coming up ----

    @Test
    fun `coming up includes overdue and next seven days, excluding paid`() {
        val overdue = sub(id = "a", anchor = LocalDate.of(2026, 9, 5), next = LocalDate.of(2026, 10, 5))
        val soon = sub(id = "b", anchor = LocalDate.of(2026, 9, 12), next = LocalDate.of(2026, 10, 12))
        val paid = sub(id = "c", anchor = LocalDate.of(2026, 9, 10), next = LocalDate.of(2026, 10, 10))
        val record = payment(paid.id, LocalDate.of(2026, 10, 10))
        val byDate = OccurrenceProjector.project(
            subs = listOf(overdue, soon, paid), payments = listOf(record),
            from = today.minusDays(30), to = today.plusDays(30),
            defaultCode = "USD", converter = converter,
        )
        val list = OccurrenceProjector.comingUp(byDate, today)
        assertThat(list.map { it.subscription.id }).containsExactly("a", "b")
    }

    @Test
    fun `coming up is sorted by date then name`() {
        val b = sub(id = "b", anchor = LocalDate.of(2026, 10, 9), next = LocalDate.of(2026, 10, 9))
        val a = sub(id = "a", anchor = LocalDate.of(2026, 10, 10), next = LocalDate.of(2026, 10, 10))
        val byDate = OccurrenceProjector.project(
            subs = listOf(b, a), payments = emptyList(),
            from = today, to = today.plusDays(7),
            defaultCode = "USD", converter = converter,
        )
        val list = OccurrenceProjector.comingUp(byDate, today)
        assertThat(list.map { it.subscription.id }).containsExactly("b", "a").inOrder()
    }

    // ---- Timeline buckets ----

    @Test
    fun `timeline buckets group occurrences by month within the window`() {
        val s = sub(anchor = LocalDate.of(2026, 10, 1), next = LocalDate.of(2026, 10, 1), cycle = BillingCycle.WEEKLY)
        val byDate = OccurrenceProjector.project(
            subs = listOf(s), payments = emptyList(),
            from = today, to = today.plusMonths(3),
            defaultCode = "USD", converter = converter,
        )
        val buckets = timelineBuckets(byDate, today, TimelinePeriod.THREE_MONTHS)
        assertThat(buckets).isNotEmpty()
        assertThat(buckets.first().month).isEqualTo(java.time.YearMonth.from(today))
    }
}
