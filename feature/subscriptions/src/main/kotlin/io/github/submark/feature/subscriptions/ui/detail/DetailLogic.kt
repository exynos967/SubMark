package io.github.submark.feature.subscriptions.ui.detail

import io.github.submark.core.data.service.ExtendBy
import io.github.submark.core.domain.BillingCalculator
import io.github.submark.core.domain.CurrencyConverter
import io.github.submark.core.model.MarkTiming
import io.github.submark.core.model.PaymentKind
import io.github.submark.core.model.PaymentRecord
import io.github.submark.core.model.PaymentSource
import io.github.submark.core.model.PaymentStatus
import io.github.submark.core.model.RenewalType
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import java.math.BigDecimal
import java.time.LocalDate

/** Inclusive date range of the billing period currently running. */
data class BillingPeriod(val start: LocalDate, val end: LocalDate)

/** Sum of a set of amounts in one currency; [missingRates] = some amounts could not be converted and were skipped. */
data class MoneyTotal(val amount: BigDecimal, val currencyCode: String, val missingRates: Boolean)

/** What a payment row is called in the history preview. */
sealed interface PaymentRowLabel {
    data class Kind(val kind: PaymentKind) : PaymentRowLabel
    data class Extension(val from: LocalDate?, val to: LocalDate?) : PaymentRowLabel
    data class Source(val source: PaymentSource) : PaymentRowLabel
    data class Timing(val timing: MarkTiming) : PaymentRowLabel
}

enum class ExtendMode { DAYS, MONTHS, DATE }

enum class ExtendInputError { EMPTY, OUT_OF_RANGE, DATE_INVALID }

/** Result of validating the extend sheet input: either a request + resulting end date, or an error. */
data class ExtendPreview(val by: ExtendBy?, val newEnd: LocalDate?, val error: ExtendInputError?)

/** Pure rules behind the subscription detail screen. */
object DetailLogic {
    const val PREVIEW_PAYMENTS = 5
    const val MAX_EXTEND_DAYS = 1000
    const val MAX_EXTEND_MONTHS = 120

    fun isRecurring(sub: Subscription) = sub.kind == SubscriptionKind.REGULAR || sub.kind == SubscriptionKind.STORED_VALUE

    /** Same conditions as `SubscriptionService.markPaid` (plus not in trial). */
    fun isMarkable(sub: Subscription): Boolean =
        sub.status == SubscriptionStatus.ACTIVE && isRecurring(sub) && sub.nextPaymentDate != null &&
            sub.renewalType != RenewalType.TRIAL && BillingCalculator.cycleLength(sub) != null

    fun canExtend(sub: Subscription): Boolean = sub.isSingleCycle && sub.endDate != null && sub.kind != SubscriptionKind.LIFETIME

    fun isTrialEnded(sub: Subscription, today: LocalDate): Boolean {
        if (sub.renewalType != RenewalType.TRIAL || sub.kind == SubscriptionKind.WISHLIST) return false
        val end = BillingCalculator.trialEndDate(sub) ?: return false
        return end <= today
    }

    /** Trial days left (0 on the last day), null when not in a running trial. */
    fun trialDaysLeft(sub: Subscription, today: LocalDate): Long? {
        if (sub.renewalType != RenewalType.TRIAL) return null
        val end = BillingCalculator.trialEndDate(sub) ?: return null
        return BillingCalculator.daysUntil(end, today).takeIf { it >= 0 }
    }

    /**
     * Period currently running. Single-cycle: start..end. Recurring: previous occurrence .. day before the next date.
     * Null when there is no schedule (lifetime, wishlist, no next date).
     */
    fun currentPeriod(sub: Subscription): BillingPeriod? {
        if (!isRecurring(sub)) return null
        if (sub.isSingleCycle) {
            val end = sub.endDate ?: return null
            return BillingPeriod(sub.startDate, end)
        }
        val next = sub.nextPaymentDate ?: return null
        val cycle = BillingCalculator.cycleLength(sub) ?: return null
        val anchor = sub.cycleAnchorDate ?: BillingCalculator.firstBillingDate(sub)
        val k = BillingCalculator.firstOccurrenceIndex(anchor, cycle, next, inclusive = true, fixedDay = sub.fixedPaymentDay)
        val previous = if (k > 0 && BillingCalculator.occurrence(anchor, cycle, k, sub.fixedPaymentDay) == next) {
            BillingCalculator.occurrence(anchor, cycle, k - 1, sub.fixedPaymentDay)
        } else {
            BillingCalculator.add(next, cycle, -1)
        }
        return BillingPeriod(previous, next.minusDays(1).let { if (it < previous) previous else it })
    }

    /** Sum of SUCCESS payments, converted to [currencyCode]. */
    fun historicalTotal(records: List<PaymentRecord>, currencyCode: String, converter: CurrencyConverter?): MoneyTotal {
        var total = BigDecimal.ZERO
        var missing = false
        records.filter { it.status == PaymentStatus.SUCCESS }.forEach { record ->
            val value = if (record.currencyCode == currencyCode) record.amount else converter?.convert(record.amount, record.currencyCode, currencyCode)
            if (value == null) missing = true else total += value
        }
        return MoneyTotal(total, currencyCode, missing)
    }

    /** Main price plus each child's price, converted to the main currency. */
    fun bundleTotal(main: Subscription, children: List<Subscription>, converter: CurrencyConverter?): MoneyTotal {
        var total = main.price
        var missing = false
        children.forEach { child ->
            val value = if (child.currencyCode == main.currencyCode) child.price else converter?.convert(child.price, child.currencyCode, main.currencyCode)
            if (value == null) missing = true else total += value
        }
        return MoneyTotal(total, main.currencyCode, missing)
    }

    /** Newest first; ties broken by creation time. */
    fun sortPayments(records: List<PaymentRecord>): List<PaymentRecord> =
        records.sortedWith(compareByDescending<PaymentRecord> { it.paymentDate }.thenByDescending { it.createdAt })

    fun paymentLabel(record: PaymentRecord): PaymentRowLabel = when {
        record.kind == PaymentKind.EXTENSION -> PaymentRowLabel.Extension(record.extensionFrom, record.extensionTo)
        record.kind != PaymentKind.REGULAR -> PaymentRowLabel.Kind(record.kind)
        record.source != PaymentSource.USER_MANUAL -> PaymentRowLabel.Source(record.source)
        record.markTiming != MarkTiming.ON_TIME -> PaymentRowLabel.Timing(record.markTiming)
        else -> PaymentRowLabel.Source(PaymentSource.USER_MANUAL)
    }

    /** Next date the service will set when a paused subscription is activated (no backfill of missed cycles). */
    fun nextDateAfterActivation(sub: Subscription, today: LocalDate): LocalDate? {
        val next = sub.nextPaymentDate ?: return null
        val cycle = BillingCalculator.cycleLength(sub) ?: return next
        val anchor = sub.cycleAnchorDate ?: return next
        if (next >= today || sub.isSingleCycle) return next
        val moved = BillingCalculator.firstOccurrenceAfter(anchor, cycle, today, inclusive = true, fixedDay = sub.fixedPaymentDay)
        return moved.takeIf { sub.endDate == null || it <= sub.endDate }
    }

    fun extendPreview(currentEnd: LocalDate, mode: ExtendMode, valueText: String, date: LocalDate?, today: LocalDate): ExtendPreview = when (mode) {
        ExtendMode.DAYS, ExtendMode.MONTHS -> {
            val text = valueText.trim()
            val value = text.toIntOrNull()
            val max = if (mode == ExtendMode.DAYS) MAX_EXTEND_DAYS else MAX_EXTEND_MONTHS
            when {
                text.isEmpty() -> ExtendPreview(null, null, ExtendInputError.EMPTY)
                value == null || value !in 1..max -> ExtendPreview(null, null, ExtendInputError.OUT_OF_RANGE)
                mode == ExtendMode.DAYS -> ExtendPreview(ExtendBy.Days(value), currentEnd.plusDays(value.toLong()), null)
                else -> ExtendPreview(ExtendBy.Months(value), currentEnd.plusMonths(value.toLong()), null)
            }
        }
        ExtendMode.DATE -> when {
            date == null -> ExtendPreview(null, null, ExtendInputError.EMPTY)
            date <= currentEnd || date <= today -> ExtendPreview(null, null, ExtendInputError.DATE_INVALID)
            else -> ExtendPreview(ExtendBy.Until(date), date, null)
        }
    }

    /** Opens in a browser even when the user typed a bare domain. */
    fun websiteUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) trimmed else "https://$trimmed"
    }

    fun appStoreUrl(appStoreId: String): String = "https://apps.apple.com/app/id${appStoreId.trim().removePrefix("id")}"
}
