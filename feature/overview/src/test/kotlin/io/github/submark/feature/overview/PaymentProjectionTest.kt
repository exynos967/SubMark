package io.github.submark.feature.overview

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.domain.CurrencyConverter
import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.PaymentRecord
import io.github.submark.core.model.PaymentStatus
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import io.github.submark.feature.overview.data.PaymentProjection
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class PaymentProjectionTest {

    private val today = LocalDate.of(2026, 10, 8)
    private val identity = CurrencyConverter(mapOf("USD" to BigDecimal.ONE))

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
        currencyCode: String = "USD",
    ) = Subscription(
        id = id, name = name, price = price, currencyCode = currencyCode,
        billingCycle = cycle, cycleAnchorDate = anchor, nextPaymentDate = next,
        lastPaymentDate = anchor, status = status, kind = kind, parentId = parentId,
        isSingleCycle = isSingleCycle, endDate = endDate,
        categoryId = "c1", startDate = anchor, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    private fun payment(subId: String, date: LocalDate, amount: BigDecimal = BigDecimal("9.99")) = PaymentRecord(
        subscriptionId = subId, amount = amount, currencyCode = "USD", paymentDate = date,
        status = PaymentStatus.SUCCESS, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    // ---- Occurrence projection ----

    @Test
    fun `active recurring subscription projects from its next date forward`() {
        val s = sub(anchor = LocalDate.of(2026, 9, 1), next = LocalDate.of(2026, 10, 1))
        val report = PaymentProjection.project(
            subs = listOf(s), payments = emptyList(),
            from = LocalDate.of(2026, 9, 1), to = LocalDate.of(2026, 12, 31), today = today,
            defaultCode = "USD", converter = identity,
        )
        assertThat(report.byDate.keys).containsExactly(
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1),
        )
    }

    @Test
    fun `paused and wishlist subscriptions produce no occurrences`() {
        val paused = sub(status = SubscriptionStatus.PAUSED)
        val wishlist = sub(kind = SubscriptionKind.WISHLIST, cycle = BillingCycle.MONTHLY)
        val report = PaymentProjection.project(
            subs = listOf(paused, wishlist), payments = emptyList(),
            from = LocalDate.of(2026, 10, 1), to = LocalDate.of(2026, 12, 31), today = today,
            defaultCode = "USD", converter = identity,
        )
        assertThat(report.byDate).isEmpty()
        assertThat(report.scheduled.compareTo(BigDecimal.ZERO)).isEqualTo(0)
    }

    @Test
    fun `end date stops the projection`() {
        val s = sub(anchor = LocalDate.of(2026, 9, 1), next = LocalDate.of(2026, 10, 1), endDate = LocalDate.of(2026, 10, 15))
        val report = PaymentProjection.project(
            subs = listOf(s), payments = emptyList(),
            from = LocalDate.of(2026, 9, 1), to = LocalDate.of(2026, 12, 31), today = today,
            defaultCode = "USD", converter = identity,
        )
        assertThat(report.byDate.keys).containsExactly(LocalDate.of(2026, 10, 1))
    }

    @Test
    fun `single cycle subscription contributes its sole due date`() {
        val s = sub(
            anchor = LocalDate.of(2026, 10, 1), next = LocalDate.of(2026, 11, 1),
            isSingleCycle = true, endDate = LocalDate.of(2026, 11, 1),
        )
        val report = PaymentProjection.project(
            subs = listOf(s), payments = emptyList(),
            from = LocalDate.of(2026, 10, 1), to = LocalDate.of(2026, 12, 31), today = today,
            defaultCode = "USD", converter = identity,
        )
        assertThat(report.byDate.keys).containsExactly(LocalDate.of(2026, 11, 1))
    }

    // ---- Period paid/scheduled split ----

    @Test
    fun `between limits paid and scheduled totals to the period`() {
        // The overview loads three months of history, but this month's totals must ignore September's payment.
        val s = sub(anchor = LocalDate.of(2026, 9, 15), next = LocalDate.of(2026, 10, 15))
        val report = PaymentProjection.project(
            subs = listOf(s), payments = listOf(payment(s.id, LocalDate.of(2026, 9, 15))),
            from = LocalDate.of(2026, 7, 1), to = LocalDate.of(2026, 10, 31), today = today,
            defaultCode = "USD", converter = identity,
        ).between(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        assertThat(report.paid.compareTo(BigDecimal.ZERO)).isEqualTo(0)
        assertThat(report.scheduled.compareTo(BigDecimal("9.99"))).isEqualTo(0)
    }

    @Test
    fun `paid and scheduled totals separate actual records from projected dues`() {
        val s = sub(anchor = LocalDate.of(2026, 9, 1), next = LocalDate.of(2026, 11, 1))
        val record = payment(s.id, LocalDate.of(2026, 10, 1))
        val report = PaymentProjection.project(
            subs = listOf(s), payments = listOf(record),
            from = LocalDate.of(2026, 10, 1), to = LocalDate.of(2026, 12, 31), today = today,
            defaultCode = "USD", converter = identity,
        )
        assertThat(report.paid.compareTo(BigDecimal("9.99"))).isEqualTo(0)
        // Nov 1 + Dec 1 = 19.98
        assertThat(report.scheduled.compareTo(BigDecimal("19.98"))).isEqualTo(0)
        assertThat(report.projectedTotal.compareTo(BigDecimal("29.97"))).isEqualTo(0)
    }

    @Test
    fun `failed and pending payment records are excluded from paid totals`() {
        val s = sub()
        val failed = PaymentRecord(
            subscriptionId = s.id, amount = BigDecimal("9.99"), currencyCode = "USD",
            paymentDate = LocalDate.of(2026, 10, 1), status = PaymentStatus.FAILED,
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        )
        val report = PaymentProjection.project(
            subs = listOf(s), payments = listOf(failed),
            from = LocalDate.of(2026, 10, 1), to = LocalDate.of(2026, 10, 31), today = today,
            defaultCode = "USD", converter = identity,
        )
        assertThat(report.paid.compareTo(BigDecimal.ZERO)).isEqualTo(0)
    }

    @Test
    fun `currency conversion normalizes amounts to the default currency`() {
        val converter = CurrencyConverter(mapOf("USD" to BigDecimal.ONE, "EUR" to BigDecimal("0.90")))
        val s = sub(next = LocalDate.of(2026, 10, 10), currencyCode = "EUR", price = BigDecimal("10.00"))
        val report = PaymentProjection.project(
            subs = listOf(s), payments = emptyList(),
            from = LocalDate.of(2026, 10, 1), to = LocalDate.of(2026, 10, 31), today = today,
            defaultCode = "USD", converter = converter,
        )
        val usd = report.scheduled
        // 10 EUR * (rate(USD)/rate(EUR)) = 10 / 0.9 = 11.1111...
        assertThat(usd.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()).isEqualTo("11.11")
    }

    // ---- Coming up ----

    @Test
    fun `coming up is limited to overdue plus the next seven days window`() {
        val overdue = sub(id = "a", anchor = LocalDate.of(2026, 9, 8), next = LocalDate.of(2026, 10, 8))
        val inWindow = sub(id = "b", anchor = LocalDate.of(2026, 9, 12), next = LocalDate.of(2026, 10, 12))
        val beyond = sub(id = "c", anchor = LocalDate.of(2026, 9, 20), next = LocalDate.of(2026, 10, 20))
        val report = PaymentProjection.project(
            subs = listOf(overdue, inWindow, beyond), payments = emptyList(),
            from = LocalDate.of(2026, 10, 1), to = LocalDate.of(2026, 11, 30), today = today,
            defaultCode = "USD", converter = identity,
        )
        val list = PaymentProjection.comingUp(report, today)
        assertThat(list.map { it.subscription.id }).containsExactly("a", "b")
    }

    @Test
    fun `coming up excludes occurrences already paid`() {
        val s = sub(id = "a", anchor = LocalDate.of(2026, 9, 10), next = LocalDate.of(2026, 10, 10))
        val record = payment(s.id, LocalDate.of(2026, 10, 10))
        val report = PaymentProjection.project(
            subs = listOf(s), payments = listOf(record),
            from = LocalDate.of(2026, 10, 1), to = LocalDate.of(2026, 11, 30), today = today,
            defaultCode = "USD", converter = identity,
        )
        assertThat(PaymentProjection.comingUp(report, today)).isEmpty()
    }

    @Test
    fun `recent paid returns only payments within the past seven days`() {
        val s = sub(next = LocalDate.of(2026, 12, 1))
        val recent = PaymentProjection.project(
            subs = listOf(s),
            payments = listOf(
                payment(s.id, LocalDate.of(2026, 10, 5)),
                payment(s.id, LocalDate.of(2026, 9, 25)),
            ),
            from = LocalDate.of(2026, 9, 1), to = LocalDate.of(2026, 12, 31), today = today,
            defaultCode = "USD", converter = identity,
        )
        val list = PaymentProjection.recentPaid(recent, today)
        assertThat(list).hasSize(1)
        assertThat(list.first().date).isEqualTo(LocalDate.of(2026, 10, 5))
    }

    // ---- Monthly points ----

    @Test
    fun `monthly points total paid amounts per calendar month`() {
        val s = sub(next = LocalDate.of(2026, 12, 1))
        val report = PaymentProjection.project(
            subs = listOf(s),
            payments = listOf(
                payment(s.id, LocalDate.of(2026, 10, 1), BigDecimal("9.99")),
                payment(s.id, LocalDate.of(2026, 10, 15), BigDecimal("9.99")),
                payment(s.id, LocalDate.of(2026, 11, 1), BigDecimal("9.99")),
            ),
            from = LocalDate.of(2026, 10, 1), to = LocalDate.of(2026, 11, 30), today = today,
            defaultCode = "USD", converter = identity,
        )
        val points = PaymentProjection.monthlyPoints(report)
        assertThat(points).hasSize(2)
        assertThat(points[0].total.compareTo(BigDecimal("19.98"))).isEqualTo(0)
        assertThat(points[1].total.compareTo(BigDecimal("9.99"))).isEqualTo(0)
    }
}
