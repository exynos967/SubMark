package io.github.submark.feature.subscriptions.detail

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.data.service.ExtendBy
import io.github.submark.core.domain.CurrencyConverter
import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.MarkTiming
import io.github.submark.core.model.PaymentKind
import io.github.submark.core.model.PaymentRecord
import io.github.submark.core.model.PaymentSource
import io.github.submark.core.model.PaymentStatus
import io.github.submark.core.model.RenewalType
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import io.github.submark.feature.subscriptions.ui.detail.BillingPeriod
import io.github.submark.feature.subscriptions.ui.detail.DetailLogic
import io.github.submark.feature.subscriptions.ui.detail.ExtendInputError
import io.github.submark.feature.subscriptions.ui.detail.ExtendMode
import io.github.submark.feature.subscriptions.ui.detail.PaymentRowLabel
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class DetailLogicTest {
    private val today = LocalDate.of(2026, 3, 10)
    private val now = Instant.parse("2026-03-10T10:00:00Z")

    private fun sub(
        kind: SubscriptionKind = SubscriptionKind.REGULAR,
        cycle: BillingCycle? = BillingCycle.MONTHLY,
        start: LocalDate = LocalDate.of(2026, 1, 31),
        anchor: LocalDate? = start,
        next: LocalDate? = LocalDate.of(2026, 3, 31),
        end: LocalDate? = null,
        single: Boolean = false,
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        renewal: RenewalType = RenewalType.AUTO,
        price: String = "10",
    ) = Subscription(
        name = "Test", kind = kind, price = BigDecimal(price), currencyCode = "USD", billingCycle = cycle,
        startDate = start, cycleAnchorDate = anchor, nextPaymentDate = next, endDate = end, isSingleCycle = single,
        status = status, renewalType = renewal, categoryId = "cat_other", createdAt = now, updatedAt = now,
    )

    private fun payment(
        amount: String,
        currency: String = "USD",
        date: LocalDate = today,
        status: PaymentStatus = PaymentStatus.SUCCESS,
        kind: PaymentKind = PaymentKind.REGULAR,
        source: PaymentSource = PaymentSource.USER_MANUAL,
        timing: MarkTiming = MarkTiming.ON_TIME,
        created: Instant = now,
    ) = PaymentRecord(
        subscriptionId = "s", amount = BigDecimal(amount), currencyCode = currency, paymentDate = date, status = status,
        kind = kind, source = source, markTiming = timing, createdAt = created, updatedAt = created,
    )

    @Test
    fun currentPeriod_monthEndAnchor_usesPreviousClampedOccurrence() {
        // Anchor Jan 31: occurrences Jan 31, Feb 28, Mar 31.
        val period = DetailLogic.currentPeriod(sub())
        assertThat(period).isEqualTo(BillingPeriod(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 30)))
    }

    @Test
    fun currentPeriod_nextIsAnchor_goesBackOneCycle() {
        val s = sub(start = LocalDate.of(2026, 4, 1), anchor = LocalDate.of(2026, 4, 1), next = LocalDate.of(2026, 4, 1))
        assertThat(DetailLogic.currentPeriod(s)).isEqualTo(BillingPeriod(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
    }

    @Test
    fun currentPeriod_singleCycle_isStartToEnd() {
        val s = sub(single = true, cycle = null, start = LocalDate.of(2026, 1, 1), end = LocalDate.of(2026, 6, 30), next = null)
        assertThat(DetailLogic.currentPeriod(s)).isEqualTo(BillingPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)))
    }

    @Test
    fun currentPeriod_lifetimeOrNoNext_isNull() {
        assertThat(DetailLogic.currentPeriod(sub(kind = SubscriptionKind.LIFETIME, cycle = null, next = null))).isNull()
        assertThat(DetailLogic.currentPeriod(sub(next = null))).isNull()
    }

    @Test
    fun historicalTotal_sumsSuccessOnly_andConverts() {
        val converter = CurrencyConverter(mapOf("EUR" to BigDecimal("0.5")))
        val records = listOf(
            payment("10"),
            payment("5", currency = "EUR"),
            payment("99", status = PaymentStatus.FAILED),
            payment("7", status = PaymentStatus.PENDING),
        )
        val total = DetailLogic.historicalTotal(records, "USD", converter)
        assertThat(total.amount.compareTo(BigDecimal("20"))).isEqualTo(0)
        assertThat(total.missingRates).isFalse()
    }

    @Test
    fun historicalTotal_missingRate_isFlaggedAndSkipped() {
        val total = DetailLogic.historicalTotal(listOf(payment("10"), payment("3", currency = "XYZ")), "USD", CurrencyConverter(emptyMap()))
        assertThat(total.amount.compareTo(BigDecimal("10"))).isEqualTo(0)
        assertThat(total.missingRates).isTrue()
    }

    @Test
    fun bundleTotal_addsChildrenInMainCurrency() {
        val main = sub(price = "10")
        val child = sub(price = "4").copy(currencyCode = "EUR")
        val total = DetailLogic.bundleTotal(main, listOf(child), CurrencyConverter(mapOf("EUR" to BigDecimal("2"))))
        assertThat(total.amount.compareTo(BigDecimal("12"))).isEqualTo(0)
    }

    @Test
    fun paymentLabel_priorities() {
        assertThat(DetailLogic.paymentLabel(payment("1", kind = PaymentKind.EXTENSION).copy(extensionFrom = today, extensionTo = today.plusDays(5))))
            .isEqualTo(PaymentRowLabel.Extension(today, today.plusDays(5)))
        assertThat(DetailLogic.paymentLabel(payment("1", kind = PaymentKind.LIFETIME_PURCHASE))).isEqualTo(PaymentRowLabel.Kind(PaymentKind.LIFETIME_PURCHASE))
        assertThat(DetailLogic.paymentLabel(payment("1", source = PaymentSource.SYSTEM_AUTO))).isEqualTo(PaymentRowLabel.Source(PaymentSource.SYSTEM_AUTO))
        assertThat(DetailLogic.paymentLabel(payment("1", timing = MarkTiming.EARLY_NEW_CYCLE))).isEqualTo(PaymentRowLabel.Timing(MarkTiming.EARLY_NEW_CYCLE))
        assertThat(DetailLogic.paymentLabel(payment("1"))).isEqualTo(PaymentRowLabel.Source(PaymentSource.USER_MANUAL))
    }

    @Test
    fun sortPayments_newestFirst_thenCreated() {
        val a = payment("1", date = today.minusDays(3))
        val b = payment("2", date = today, created = now)
        val c = payment("3", date = today, created = now.plusSeconds(5))
        assertThat(DetailLogic.sortPayments(listOf(a, b, c))).containsExactly(c, b, a).inOrder()
    }

    @Test
    fun extendPreview_daysAndMonths() {
        val end = LocalDate.of(2026, 1, 31)
        val days = DetailLogic.extendPreview(end, ExtendMode.DAYS, "10", null, today)
        assertThat(days.by).isEqualTo(ExtendBy.Days(10))
        assertThat(days.newEnd).isEqualTo(LocalDate.of(2026, 2, 10))
        val months = DetailLogic.extendPreview(end, ExtendMode.MONTHS, "1", null, today)
        assertThat(months.newEnd).isEqualTo(LocalDate.of(2026, 2, 28))
    }

    @Test
    fun extendPreview_validation() {
        val end = LocalDate.of(2026, 4, 1)
        assertThat(DetailLogic.extendPreview(end, ExtendMode.DAYS, "", null, today).error).isEqualTo(ExtendInputError.EMPTY)
        assertThat(DetailLogic.extendPreview(end, ExtendMode.DAYS, "1001", null, today).error).isEqualTo(ExtendInputError.OUT_OF_RANGE)
        assertThat(DetailLogic.extendPreview(end, ExtendMode.MONTHS, "121", null, today).error).isEqualTo(ExtendInputError.OUT_OF_RANGE)
        assertThat(DetailLogic.extendPreview(end, ExtendMode.MONTHS, "0", null, today).error).isEqualTo(ExtendInputError.OUT_OF_RANGE)
        assertThat(DetailLogic.extendPreview(end, ExtendMode.DATE, "", end, today).error).isEqualTo(ExtendInputError.DATE_INVALID)
        val ok = DetailLogic.extendPreview(end, ExtendMode.DATE, "", end.plusDays(1), today)
        assertThat(ok.by).isEqualTo(ExtendBy.Until(end.plusDays(1)))
    }

    @Test
    fun extendPreview_dateMustBeAfterToday() {
        val end = today.minusDays(10)
        assertThat(DetailLogic.extendPreview(end, ExtendMode.DATE, "", today, today).error).isEqualTo(ExtendInputError.DATE_INVALID)
    }

    @Test
    fun markableAndExtendable() {
        assertThat(DetailLogic.isMarkable(sub())).isTrue()
        assertThat(DetailLogic.isMarkable(sub(status = SubscriptionStatus.PAUSED))).isFalse()
        assertThat(DetailLogic.isMarkable(sub(renewal = RenewalType.TRIAL))).isFalse()
        assertThat(DetailLogic.isMarkable(sub(kind = SubscriptionKind.LIFETIME, cycle = null))).isFalse()
        assertThat(DetailLogic.canExtend(sub(single = true, end = today.plusDays(3)))).isTrue()
        assertThat(DetailLogic.canExtend(sub(end = today.plusDays(3)))).isFalse()
    }

    @Test
    fun trialEnded_whenTrialEndReached() {
        val s = sub(renewal = RenewalType.TRIAL).copy(trialStartDate = today.minusDays(7), trialDays = 7)
        assertThat(DetailLogic.isTrialEnded(s, today)).isTrue()
        assertThat(DetailLogic.isTrialEnded(s, today.minusDays(1))).isFalse()
        assertThat(DetailLogic.trialDaysLeft(s, today.minusDays(3))).isEqualTo(3L)
    }

    @Test
    fun nextDateAfterActivation_skipsMissedCycles() {
        val s = sub(start = LocalDate.of(2026, 1, 5), anchor = LocalDate.of(2026, 1, 5), next = LocalDate.of(2026, 2, 5), status = SubscriptionStatus.PAUSED)
        assertThat(DetailLogic.nextDateAfterActivation(s, today)).isEqualTo(LocalDate.of(2026, 4, 5))
        val future = s.copy(nextPaymentDate = LocalDate.of(2026, 3, 20))
        assertThat(DetailLogic.nextDateAfterActivation(future, today)).isEqualTo(LocalDate.of(2026, 3, 20))
    }

    @Test
    fun urls() {
        assertThat(DetailLogic.websiteUrl("example.com")).isEqualTo("https://example.com")
        assertThat(DetailLogic.websiteUrl("http://a.b")).isEqualTo("http://a.b")
        assertThat(DetailLogic.appStoreUrl("id123")).isEqualTo("https://apps.apple.com/app/id123")
        assertThat(DetailLogic.appStoreUrl("123")).isEqualTo("https://apps.apple.com/app/id123")
    }
}
