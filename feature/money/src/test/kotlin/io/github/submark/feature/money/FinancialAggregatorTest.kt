package io.github.submark.feature.money

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.data.settings.FinancialDetailFilter
import io.github.submark.core.domain.CurrencyConverter
import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.PaymentKind
import io.github.submark.core.model.PaymentRecord
import io.github.submark.core.model.PaymentSource
import io.github.submark.core.model.PaymentStatus
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.feature.money.ui.financial.FinancialAggregator
import io.github.submark.feature.money.ui.financial.SpendingType
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class FinancialAggregatorTest {
    private val converter = CurrencyConverter(mapOf("USD" to BigDecimal.ONE))
    private val converters = mapOf(
        LocalDate.of(2026, 9, 1) to converter,
        LocalDate.of(2026, 10, 1) to converter,
    )

    private fun sub(
        id: String, kind: SubscriptionKind = SubscriptionKind.REGULAR, billing: BillingCycle = BillingCycle.MONTHLY,
        shared: Boolean = false, parentId: String? = null,
    ) = Subscription(
        id = id, name = "Sub $id", price = BigDecimal.TEN, currencyCode = "USD", billingCycle = billing,
        kind = kind, isShared = shared, parentId = parentId, cycleAnchorDate = LocalDate.of(2026, 9, 1),
        nextPaymentDate = LocalDate.of(2026, 12, 1), lastPaymentDate = LocalDate.of(2026, 10, 1),
        categoryId = "c1", startDate = LocalDate.of(2026, 9, 1),
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    private fun record(
        id: String, date: LocalDate, kind: PaymentKind = PaymentKind.REGULAR, status: PaymentStatus = PaymentStatus.SUCCESS,
        amount: BigDecimal = BigDecimal("10.00"), currency: String = "USD", source: PaymentSource = PaymentSource.USER_MANUAL,
    ) = PaymentRecord(
        id = id, subscriptionId = id, amount = amount, currencyCode = currency, paymentDate = date,
        status = status, kind = kind, source = source, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    @Test
    fun `successful payments are summed and grouped by month`() {
        val subs = mapOf(
            "r1" to sub("r1", billing = BillingCycle.MONTHLY),
            "r2" to sub("r2", billing = BillingCycle.MONTHLY),
        )
        val records = listOf(
            record("r1", LocalDate.of(2026, 9, 5)),
            record("r2", LocalDate.of(2026, 9, 10 )),
            record("r1", LocalDate.of(2026, 10, 5)),
        )
        val result = FinancialAggregator.aggregate(records, subs, FinancialDetailFilter.INCLUDE_LIFETIME, "USD", converters)
        assertThat(result.paymentCount).isEqualTo(3)
        assertThat(result.total.compareTo(BigDecimal("30.00"))).isEqualTo(0)
        assertThat(result.months.map { it.month }).containsExactly(YearMonth.of(2026, 9), YearMonth.of(2026, 10))
    }

    @Test
    fun `pending and failed records are excluded`() {
        val subs = mapOf("r1" to sub("r1"), "r2" to sub("r2"))
        val records = listOf(
            record("r1", LocalDate.of(2026, 9, 1), status = PaymentStatus.PENDING),
            record("r2", LocalDate.of(2026, 9, 2), status = PaymentStatus.FAILED),
        )
        val result = FinancialAggregator.aggregate(records, subs, FinancialDetailFilter.INCLUDE_LIFETIME, "USD", converters)
        assertThat(result.paymentCount).isEqualTo(0)
        assertThat(result.total.signum()).isEqualTo(0)
    }

    @Test
    fun `missing rates count against unconverted`() {
        val subs = mapOf("r1" to sub("r1"))
        val records = listOf(record("r1", LocalDate.of(2026, 9, 1), currency = "THB"))
        val result = FinancialAggregator.aggregate(records, subs, FinancialDetailFilter.INCLUDE_LIFETIME, "USD", converters)
        assertThat(result.unconvertedCount).isEqualTo(1)
        assertThat(result.paymentCount).isEqualTo(0)
    }

    @Test
    fun `lifetime filter excludes non-lifetime`() {
        val lifetime = PaymentRecord(
            id = "l1", subscriptionId = "l1", amount = BigDecimal("99.00"), currencyCode = "USD",
            paymentDate = LocalDate.of(2026, 9, 1), status = PaymentStatus.SUCCESS, kind = PaymentKind.LIFETIME_PURCHASE,
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        )
        val subs = mapOf("l1" to sub("l1", kind = SubscriptionKind.LIFETIME), "r1" to sub("r1"))
        val records = listOf(lifetime, record("r1", LocalDate.of(2026, 9, 1)))
        val result = FinancialAggregator.aggregate(records, subs, FinancialDetailFilter.LIFETIME_ONLY, "USD", converters)
        assertThat(result.paymentCount).isEqualTo(1)
        assertThat(result.byType.map { it.first }).containsExactly(SpendingType.LIFETIME)
    }

    @Test
    fun `type classification tags IAP, shared and stored value`() {
        val subs = mapOf(
            "iap1" to sub("iap1"),
            "sh1" to sub("sh1", shared = true),
            "sv1" to sub("sv1", kind = SubscriptionKind.STORED_VALUE, billing = BillingCycle.MONTHLY),
        )
        val records = listOf(
            record("iap1", LocalDate.of(2026, 9, 1), kind = PaymentKind.IN_APP_PURCHASE),
            record("sh1", LocalDate.of(2026, 9, 2), amount = BigDecimal("5.00")),
            record("sv1", LocalDate.of(2026, 9, 3), kind = PaymentKind.STORED_VALUE_DEPOSIT, amount = BigDecimal("100.00")),
        )
        val result = FinancialAggregator.aggregate(records, subs, FinancialDetailFilter.INCLUDE_LIFETIME, "USD", converters)
        assertThat(result.byType.map { it.first }).containsExactly(SpendingType.IAP, SpendingType.SHARED, SpendingType.STORED_VALUE_DEPOSIT)
    }

    @Test
    fun `bundle child is tagged when parent is set`() {
        val subs = mapOf("b1" to sub("b1", parentId = "p"))
        val records = listOf(record("b1", LocalDate.of(2026, 9, 1)))
        val result = FinancialAggregator.aggregate(records, subs, FinancialDetailFilter.INCLUDE_LIFETIME, "USD", converters)
        assertThat(result.byType.map { it.first }).containsExactly(SpendingType.BUNDLE_CHILD)
    }

    @Test
    fun `expense months only counts months with payments`() {
        val subs = mapOf("r1" to sub("r1"))
        val records = listOf(
            record("r1", LocalDate.of(2026, 1, 15)),
            record("r1", LocalDate.of(2026, 3, 15)),
            record("r1", LocalDate.of(2026, 3, 20)),
        )
        val converters = mapOf(LocalDate.of(2026, 3, 1) to converter) + mapOf(LocalDate.of(2026, 1, 1) to converter)
        val result = FinancialAggregator.aggregate(records, subs, FinancialDetailFilter.INCLUDE_LIFETIME, "USD", converters)
        assertThat(result.expenseMonths).isEqualTo(2)
    }
}
