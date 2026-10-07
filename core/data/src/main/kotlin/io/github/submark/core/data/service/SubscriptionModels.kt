package io.github.submark.core.data.service

import io.github.submark.core.data.result.DataError
import io.github.submark.core.model.MarkTiming
import io.github.submark.core.model.Subscription
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Add/edit form payload. [subscription] carries the form fields; the service assigns timestamps and
 * computes schedule fields (anchor, next/last payment date), so callers may leave those at any value.
 */
data class SubscriptionDraft(
    val subscription: Subscription,
    val tagIds: List<String> = emptyList(),
    /** fieldId -> encoded value; null/blank clears. */
    val customFieldValues: Map<String, String?> = emptyMap(),
    /** Record one payment per past occurrence when the start date is in the past (REGULAR only). */
    val generateHistory: Boolean = false,
    /** LIFETIME: charge the purchase to this wallet. */
    val purchaseWalletId: String? = null,
    /** STORED_VALUE: initial deposit in the subscription currency, optionally funded by a wallet. */
    val initialDeposit: BigDecimal? = null,
    val initialDepositWalletId: String? = null,
    /** Non-empty = create a bundle: this draft becomes MAIN, these become CHILD subscriptions (create only). */
    val children: List<SubscriptionDraft> = emptyList(),
    /** Skip the duplicate App Store id check ("Add anyway"). */
    val allowDuplicateAppStoreId: Boolean = false,
)

data class MarkPaidOutcome(
    val timing: MarkTiming,
    /** Null for stored-value subscriptions (a deduction record is written instead). */
    val paymentRecordId: String?,
    val nextPaymentDate: LocalDate?,
    val syncedChildIds: List<String>,
)

sealed interface ExtendBy {
    /** 1..1000 days. */
    data class Days(val days: Int) : ExtendBy
    /** 1..120 calendar months, clamped to month end. */
    data class Months(val months: Int) : ExtendBy
    /** Must be after the current end date and after today. */
    data class Until(val date: LocalDate) : ExtendBy
}

data class DeleteOutcome(
    val deletedIds: Set<String>,
    /** Photo files of the deleted subscriptions; the caller removes them from storage. */
    val photoFileNames: List<String>,
)

data class WalletFailure(val subscriptionId: String, val subscriptionName: String, val error: DataError)

/** What [SubscriptionService.processDue] did; the UI shows alerts from it. */
data class ProcessDueSummary(
    val autoMarkedCount: Int,
    val expiredIds: List<String>,
    val expiredNames: List<String>,
    /** TRIAL subscriptions whose trial has ended and await `resolveTrial`. */
    val trialEndedIds: List<String>,
    /** AUTO renewals blocked by their wallet; they stay due. */
    val walletFailures: List<WalletFailure>,
)
