package io.github.submark.feature.money.ui.addpayment

import io.github.submark.core.domain.BillingCalculator
import io.github.submark.core.model.DateAdjustmentMode
import io.github.submark.core.model.PaymentStatus
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The form values that influence what saving will do to the subscription. */
data class PreviewInput(
    val amount: BigDecimal?,
    val currencyCode: String,
    val paymentDate: LocalDate,
    val status: PaymentStatus,
    val inAppPurchase: Boolean,
    val adjustment: DateAdjustmentMode,
    val target: LocalDate?,
    val syncPrice: Boolean,
    val payWithWallet: Boolean,
)

enum class TargetProblem { MISSING, BEFORE_PAYMENT_DATE, BEFORE_CURRENT_END }

/** Why the dates do (or do not) move; drives the explanation under the preview card. */
enum class DateEffect { NOT_SUCCESSFUL, IN_APP_PURCHASE, OLDER_THAN_LATEST, NO_CYCLE, REGULAR, END_DATE, NEXT_BILLING }

data class PaymentPreview(
    val effect: DateEffect,
    val endDate: LocalDate?,
    val nextPaymentDate: LocalDate?,
    val endDateChanged: Boolean,
    val nextDateChanged: Boolean,
    /** Days the end date is extended (END_DATE) or the next payment is deferred (NEXT_BILLING). */
    val adjustmentDays: Long?,
    val reactivates: Boolean,
    val priceUpdates: Boolean,
    /** A wallet charge only happens for successful payments above zero. */
    val walletChargeApplies: Boolean,
    val targetProblem: TargetProblem?,
)

/**
 * Live preview of the add-payment form. Mirrors the date effects documented on `PaymentService.add`
 * (the service stays the authority; this only explains the outcome before saving).
 */
object AddPaymentPreviewCalculator {

    fun preview(sub: Subscription, input: PreviewInput, today: LocalDate): PaymentPreview {
        val success = input.status == PaymentStatus.SUCCESS
        val adjustment = if (success) input.adjustment else DateAdjustmentMode.NONE
        val target = input.target
        val isLatest = input.paymentDate >= (sub.lastPaymentDate ?: LocalDate.MIN)
        val regular = !input.inAppPurchase

        var end = sub.endDate
        var next = sub.nextPaymentDate
        var changed = false
        var days: Long? = null
        var problem: TargetProblem? = null

        val effect = when {
            !success -> DateEffect.NOT_SUCCESSFUL
            adjustment == DateAdjustmentMode.END_DATE -> {
                problem = when {
                    target == null -> TargetProblem.MISSING
                    target < input.paymentDate -> TargetProblem.BEFORE_PAYMENT_DATE
                    sub.endDate != null && target < sub.endDate -> TargetProblem.BEFORE_CURRENT_END
                    else -> null
                }
                if (target != null) {
                    end = target
                    days = ChronoUnit.DAYS.between(sub.endDate ?: sub.nextPaymentDate ?: input.paymentDate, target)
                }
                changed = true
                DateEffect.END_DATE
            }
            adjustment == DateAdjustmentMode.NEXT_BILLING -> {
                if (target == null) problem = TargetProblem.MISSING
                else {
                    next = target
                    end = null
                    days = ChronoUnit.DAYS.between(sub.nextPaymentDate ?: input.paymentDate, target)
                }
                changed = true
                DateEffect.NEXT_BILLING
            }
            !regular -> DateEffect.IN_APP_PURCHASE
            !isLatest -> DateEffect.OLDER_THAN_LATEST
            else -> {
                // The latest regular payment always becomes the last payment date.
                changed = true
                val cycle = if (sub.kind == SubscriptionKind.LIFETIME) null else BillingCalculator.cycleLength(sub)
                if (cycle == null) {
                    DateEffect.NO_CYCLE
                } else {
                    val candidate = BillingCalculator.occurrence(input.paymentDate, cycle, 1, sub.fixedPaymentDay)
                    next = candidate.takeIf { sub.endDate == null || it <= sub.endDate }
                    DateEffect.REGULAR
                }
            }
        }

        val endChanged = end != sub.endDate
        val coverage = if (endChanged) end else next
        val reactivates = sub.status == SubscriptionStatus.PAUSED && changed && problem == null && coverage != null && coverage > today
        val amount = input.amount
        val priceUpdates = input.syncPrice && amount != null &&
            (sub.price.compareTo(amount) != 0 || sub.currencyCode != input.currencyCode)
        return PaymentPreview(
            effect = effect,
            endDate = end,
            nextPaymentDate = next,
            endDateChanged = endChanged,
            nextDateChanged = next != sub.nextPaymentDate,
            adjustmentDays = days,
            reactivates = reactivates,
            priceUpdates = priceUpdates,
            walletChargeApplies = input.payWithWallet && success && amount != null && amount.signum() > 0,
            targetProblem = problem,
        )
    }

    /** The adjustment target may not precede the payment date nor the current end date (END_DATE). */
    fun minTarget(sub: Subscription, paymentDate: LocalDate, mode: DateAdjustmentMode): LocalDate? = when (mode) {
        DateAdjustmentMode.END_DATE -> listOfNotNull(paymentDate, sub.endDate).max()
        else -> null
    }
}
