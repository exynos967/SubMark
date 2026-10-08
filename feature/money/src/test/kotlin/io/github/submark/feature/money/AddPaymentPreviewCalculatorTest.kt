package io.github.submark.feature.money

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.DateAdjustmentMode
import io.github.submark.core.model.PaymentStatus
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import io.github.submark.feature.money.ui.addpayment.AddPaymentPreviewCalculator
import io.github.submark.feature.money.ui.addpayment.DateEffect
import io.github.submark.feature.money.ui.addpayment.PreviewInput
import io.github.submark.feature.money.ui.addpayment.TargetProblem
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class AddPaymentPreviewCalculatorTest {
    private val today = LocalDate.of(2026, 10, 8)

    private fun sub(
        price: BigDecimal = BigDecimal("9.99"),
        endDate: LocalDate? = null,
        next: LocalDate? = LocalDate.of(2026, 12, 1),
        last: LocalDate = LocalDate.of(2026, 10, 1),
        mode: DateAdjustmentMode = DateAdjustmentMode.NONE,
        kind: SubscriptionKind = SubscriptionKind.REGULAR,
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        billing: BillingCycle = BillingCycle.MONTHLY,
    ) = Subscription(
        id = "s1", name = "Test", price = price, currencyCode = "USD", billingCycle = billing,
        cycleAnchorDate = last, nextPaymentDate = next, lastPaymentDate = last, endDate = endDate,
        kind = kind, status = status, categoryId = "c1", startDate = last,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    private fun input(
        date: LocalDate = today,
        status: PaymentStatus = PaymentStatus.SUCCESS,
        iap: Boolean = false,
        adjust: DateAdjustmentMode = DateAdjustmentMode.NONE,
        target: LocalDate? = null,
        syncPrice: Boolean = false,
        wallet: Boolean = false,
        amount: BigDecimal? = BigDecimal("9.99"),
        currencyCode: String = "USD",
    ) = PreviewInput(amount, currencyCode, date, status, iap, adjust, target, syncPrice, wallet)

    @Test
    fun `successful regular payment advances next payment by one cycle`() {
        val s = sub(next = LocalDate.of(2026, 12, 1))
        val p = AddPaymentPreviewCalculator.preview(s, input(date = LocalDate.of(2026, 10, 8)), today)
        assertThat(p.effect).isEqualTo(DateEffect.REGULAR)
        assertThat(p.nextPaymentDate).isEqualTo(LocalDate.of(2026, 11, 8))
        assertThat(p.nextDateChanged).isTrue()
    }

    @Test
    fun `IAP never changes dates`() {
        val s = sub(next = LocalDate.of(2026, 12, 1))
        val p = AddPaymentPreviewCalculator.preview(s, input(iap = true), today)
        assertThat(p.effect).isEqualTo(DateEffect.IN_APP_PURCHASE)
        assertThat(p.nextPaymentDate).isEqualTo(s.nextPaymentDate)
        assertThat(p.nextDateChanged).isFalse()
    }

    @Test
    fun `non-success status leaves dates untouched`() {
        val s = sub(next = LocalDate.of(2026, 12, 1))
        val p = AddPaymentPreviewCalculator.preview(s, input(status = PaymentStatus.PENDING), today)
        assertThat(p.effect).isEqualTo(DateEffect.NOT_SUCCESSFUL)
        assertThat(p.nextPaymentDate).isEqualTo(s.nextPaymentDate)
        assertThat(p.walletChargeApplies).isFalse()
    }

    @Test
    fun `end date adjustment sets end date without moving next payment`() {
        val s = sub(endDate = LocalDate.of(2027, 1, 1), next = LocalDate.of(2026, 12, 1))
        val p = AddPaymentPreviewCalculator.preview(s, input(adjust = DateAdjustmentMode.END_DATE, target = LocalDate.of(2027, 3, 1)), today)
        assertThat(p.effect).isEqualTo(DateEffect.END_DATE)
        assertThat(p.endDate).isEqualTo(LocalDate.of(2027, 3, 1))
        assertThat(p.endDateChanged).isTrue()
    }

    @Test
    fun `next billing adjustment moves next payment and clears end date`() {
        val s = sub(endDate = LocalDate.of(2027, 6, 1), next = LocalDate.of(2026, 12, 1))
        val p = AddPaymentPreviewCalculator.preview(s, input(adjust = DateAdjustmentMode.NEXT_BILLING, target = LocalDate.of(2026, 11, 15)), today)
        assertThat(p.effect).isEqualTo(DateEffect.NEXT_BILLING)
        assertThat(p.endDate).isNull()
        assertThat(p.nextPaymentDate).isEqualTo(LocalDate.of(2026, 11, 15))
    }

    @Test
    fun `target before payment date is rejected`() {
        val s = sub()
        val p = AddPaymentPreviewCalculator.preview(s, input(date = LocalDate.of(2026, 11, 1), adjust = DateAdjustmentMode.END_DATE, target = LocalDate.of(2026, 10, 15)), today)
        assertThat(p.targetProblem).isEqualTo(TargetProblem.BEFORE_PAYMENT_DATE)
    }

    @Test
    fun `target before current end date is rejected`() {
        val s = sub(endDate = LocalDate.of(2027, 6, 1))
        val p = AddPaymentPreviewCalculator.preview(s, input(adjust = DateAdjustmentMode.END_DATE, target = LocalDate.of(2027, 5, 1)), today)
        assertThat(p.targetProblem).isEqualTo(TargetProblem.BEFORE_CURRENT_END)
    }

    @Test
    fun `paused subscription reactivates when new coverage is after today`() {
        val s = sub(endDate = LocalDate.of(2027, 1, 1), status = SubscriptionStatus.PAUSED)
        val p = AddPaymentPreviewCalculator.preview(s, input(adjust = DateAdjustmentMode.END_DATE, target = LocalDate.of(2027, 2, 1)), today)
        assertThat(p.reactivates).isTrue()
    }

    @Test
    fun `price sync is reflected in preview`() {
        val s = sub(price = BigDecimal("9.99"))
        val p = AddPaymentPreviewCalculator.preview(s, input(amount = BigDecimal("12.99"), syncPrice = true), today)
        assertThat(p.priceUpdates).isTrue()
    }

    @Test
    fun `wallet charge applies only to successful payments above zero`() {
        val s = sub()
        assertThat(AddPaymentPreviewCalculator.preview(s, input(wallet = true), today).walletChargeApplies).isTrue()
        assertThat(AddPaymentPreviewCalculator.preview(s, input(wallet = true, amount = BigDecimal.ZERO), today).walletChargeApplies).isFalse()
        assertThat(AddPaymentPreviewCalculator.preview(s, input(wallet = true, status = PaymentStatus.PENDING), today).walletChargeApplies).isFalse()
    }

    @Test
    fun `min target for END_DATE is the later of payment date and current end`() {
        val s = sub(endDate = LocalDate.of(2027, 3, 1))
        assertThat(AddPaymentPreviewCalculator.minTarget(s, LocalDate.of(2026, 11, 1), DateAdjustmentMode.END_DATE)).isEqualTo(LocalDate.of(2027, 3, 1))
        assertThat(AddPaymentPreviewCalculator.minTarget(s, LocalDate.of(2027, 6, 1), DateAdjustmentMode.END_DATE)).isEqualTo(LocalDate.of(2027, 6, 1))
    }
}
