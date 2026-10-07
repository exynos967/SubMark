package io.github.submark.core.data.service

import io.github.submark.core.data.change.ChangeNotifier
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.result.InvalidReason
import io.github.submark.core.data.result.TransactionRunner
import io.github.submark.core.data.result.abort
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.database.dao.PaymentDao
import io.github.submark.core.database.dao.StoredValueDao
import io.github.submark.core.database.dao.SubscriptionDao
import io.github.submark.core.database.dao.WalletDao
import io.github.submark.core.domain.BillingCalculator
import io.github.submark.core.model.DateAdjustmentMode
import io.github.submark.core.model.PaymentKind
import io.github.submark.core.model.PaymentRecord
import io.github.submark.core.model.PaymentSource
import io.github.submark.core.model.PaymentStatus
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import io.github.submark.core.model.WalletTxnStatus
import io.github.submark.core.model.newId
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Input of the add-payment form. */
data class NewPayment(
    val subscriptionId: String,
    val amount: BigDecimal,
    val currencyCode: String,
    val paymentDate: LocalDate,
    val status: PaymentStatus = PaymentStatus.SUCCESS,
    val inAppPurchase: Boolean = false,
    val iapItemName: String? = null,
    val note: String? = null,
    /** END_DATE / NEXT_BILLING need [adjustmentTargetDate]; only applied to SUCCESS records. */
    val dateAdjustment: DateAdjustmentMode = DateAdjustmentMode.NONE,
    val adjustmentTargetDate: LocalDate? = null,
    val syncSubscriptionPrice: Boolean = false,
    /** Charge this wallet (SUCCESS and amount > 0 only). */
    val walletId: String? = null,
)

data class AddPaymentOutcome(
    val record: PaymentRecord,
    val endDate: LocalDate?,
    val nextPaymentDate: LocalDate?,
    val reactivated: Boolean,
    val priceUpdated: Boolean,
    val walletCharged: Boolean,
)

/** Editable fields of an existing record. Date adjustments are not re-applied on edit. */
data class PaymentEdit(
    val id: String,
    val amount: BigDecimal,
    val currencyCode: String,
    val paymentDate: LocalDate,
    val status: PaymentStatus,
    val inAppPurchase: Boolean,
    val iapItemName: String? = null,
    val note: String? = null,
    /** When set, the edit fails with [DataError.Stale] if the record changed since it was read. */
    val expectedUpdatedAt: Instant? = null,
)

/** Payment records: add (with date effects and wallet charge), edit (wallet compensation), delete (revert). */
@Singleton
class PaymentService @Inject internal constructor(
    private val paymentDao: PaymentDao,
    private val subscriptionDao: SubscriptionDao,
    private val walletDao: WalletDao,
    private val storedValueDao: StoredValueDao,
    private val storedValue: StoredValueService,
    private val ledger: WalletLedger,
    private val tx: TransactionRunner,
    private val notifier: ChangeNotifier,
    private val time: TimeProvider,
) {
    fun observeAll(): Flow<List<PaymentRecord>> = paymentDao.observeAll()

    fun observeForSubscription(subscriptionId: String): Flow<List<PaymentRecord>> = paymentDao.observeForSubscription(subscriptionId)

    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<PaymentRecord>> = paymentDao.observeBetween(from, to)

    fun observe(id: String): Flow<PaymentRecord?> = paymentDao.observe(id)

    suspend fun get(id: String): PaymentRecord? = paymentDao.get(id)

    /** Deposits are managed through StoredValueService. */
    fun isReadOnly(record: PaymentRecord): Boolean = record.kind == PaymentKind.STORED_VALUE_DEPOSIT

    suspend fun add(input: NewPayment): DataResult<AddPaymentOutcome> {
        val result = tx.run { addInTx(input) }
        if (result.isSuccess) notifier.notifyChanged(input.subscriptionId)
        return result
    }

    suspend fun edit(edit: PaymentEdit): DataResult<PaymentRecord> {
        var subscriptionId: String? = null
        val result = tx.run {
            val record = paymentDao.get(edit.id) ?: abort(DataError.NotFound)
            subscriptionId = record.subscriptionId
            if (edit.expectedUpdatedAt != null && edit.expectedUpdatedAt != record.updatedAt) abort(DataError.Stale)
            if (isReadOnly(record)) abort(InvalidReason.READ_ONLY_RECORD)
            if (edit.amount.signum() < 0) abort(InvalidReason.NEGATIVE_AMOUNT)
            val kind = when {
                record.kind == PaymentKind.REGULAR && edit.inAppPurchase -> PaymentKind.IN_APP_PURCHASE
                record.kind == PaymentKind.IN_APP_PURCHASE && !edit.inAppPurchase -> PaymentKind.REGULAR
                else -> record.kind
            }
            var updated = record.copy(
                amount = edit.amount, currencyCode = edit.currencyCode, paymentDate = edit.paymentDate, status = edit.status,
                kind = kind, iapItemName = edit.iapItemName.takeIf { kind == PaymentKind.IN_APP_PURCHASE }, note = edit.note,
                updatedAt = time.now(),
            )
            updated = compensateWallet(record, updated)
            paymentDao.upsert(updated)
            updated
        }
        if (result.isSuccess) notifier.notifyChanged(subscriptionId!!)
        return result
    }

    /**
     * Deletes a record atomically with its wallet reversal. If it is the latest record that moved the
     * subscription's dates (and nothing changed them since), the dates captured in prev* are restored.
     */
    suspend fun delete(id: String): DataResult<Unit> {
        var subscriptionId: String? = null
        val result = tx.run {
            val record = paymentDao.get(id) ?: abort(DataError.NotFound)
            subscriptionId = record.subscriptionId
            deleteInTx(record)
        }
        if (result.isSuccess) notifier.notifyChanged(subscriptionId!!)
        return result
    }

    // ---- transaction-internal API for SubscriptionService ----

    /** Inserts [record], charging [walletId] first when given. Aborts (nothing saved) if the charge fails. */
    internal suspend fun insertWithWallet(record: PaymentRecord, walletId: String?): PaymentRecord {
        val saved = if (walletId != null && record.status == PaymentStatus.SUCCESS && record.amount.signum() > 0) {
            val txn = ledger.charge(walletId, record.amount, record.currencyCode, WalletLedger.Link(record.subscriptionId, record.id))
            record.copy(walletId = walletId, walletTransactionId = txn.id)
        } else {
            record
        }
        paymentDao.upsert(saved)
        return saved
    }

    internal suspend fun reverseWalletInTx(transactionId: String, note: String) {
        ledger.reverse(transactionId, note)
    }

    internal suspend fun deleteInTx(record: PaymentRecord) {
        if (record.kind == PaymentKind.STORED_VALUE_DEPOSIT) {
            storedValueDao.getByPaymentRecord(record.id)?.let { return storedValue.deleteRecordInTx(it) }
        }
        record.walletTransactionId?.let { ledger.reverse(it, SystemNotes.PAYMENT_DELETED) }
        paymentDao.deleteById(record.id)
        val sub = subscriptionDao.get(record.subscriptionId) ?: return
        val remaining = paymentDao.getForSubscription(sub.id)
        val latestAffecting = (remaining + record).filter(::affectsDates).maxByOrNull { it.createdAt }
        val reverted = if (latestAffecting?.id == record.id && sub.updatedAt <= record.createdAt) {
            sub.copy(
                endDate = record.prevEndDate,
                nextPaymentDate = record.prevNextPaymentDate,
                lastPaymentDate = record.prevLastPaymentDate,
                cycleAnchorDate = record.prevNextPaymentDate ?: sub.cycleAnchorDate,
            )
        } else if (record.status == PaymentStatus.SUCCESS && record.paymentDate == sub.lastPaymentDate) {
            sub.copy(lastPaymentDate = remaining.filter(::countsAsLastPayment).maxOfOrNull { it.paymentDate })
        } else {
            return
        }
        subscriptionDao.upsert(reverted.copy(updatedAt = time.now()))
    }

    private suspend fun addInTx(input: NewPayment): AddPaymentOutcome {
        val sub = subscriptionDao.get(input.subscriptionId) ?: abort(DataError.NotFound)
        if (sub.kind == SubscriptionKind.WISHLIST) abort(InvalidReason.WISHLIST_NOT_ALLOWED)
        if (input.amount.signum() < 0) abort(InvalidReason.NEGATIVE_AMOUNT)
        val success = input.status == PaymentStatus.SUCCESS
        val adjustment = if (success) input.dateAdjustment else DateAdjustmentMode.NONE
        val target = input.adjustmentTargetDate
        when (adjustment) {
            DateAdjustmentMode.NONE -> Unit
            DateAdjustmentMode.END_DATE -> {
                val currentEnd = sub.endDate
                if (target == null || target < input.paymentDate || (currentEnd != null && target < currentEnd)) {
                    abort(InvalidReason.ADJUSTMENT_TARGET_INVALID)
                }
            }
            DateAdjustmentMode.NEXT_BILLING -> if (target == null) abort(InvalidReason.ADJUSTMENT_TARGET_INVALID)
        }
        val kind = when {
            input.inAppPurchase -> PaymentKind.IN_APP_PURCHASE
            else -> PaymentKind.REGULAR
        }
        val now = time.now()
        val today = time.today()
        val isLatest = input.paymentDate >= (sub.lastPaymentDate ?: LocalDate.MIN)
        var updated = sub
        if (success) {
            when (adjustment) {
                DateAdjustmentMode.END_DATE -> updated = updated.copy(endDate = target)
                DateAdjustmentMode.NEXT_BILLING -> updated = updated.copy(nextPaymentDate = target, cycleAnchorDate = target, endDate = null)
                DateAdjustmentMode.NONE -> if (kind == PaymentKind.REGULAR && isLatest) updated = advanceFrom(updated, input.paymentDate)
            }
            if (kind == PaymentKind.REGULAR && isLatest) updated = updated.copy(lastPaymentDate = input.paymentDate)
        }
        val coverage = if (updated.endDate != sub.endDate) updated.endDate else updated.nextPaymentDate
        val reactivate = sub.status == SubscriptionStatus.PAUSED && updated !== sub && coverage != null && coverage > today
        if (reactivate) updated = updated.copy(status = SubscriptionStatus.ACTIVE, pausedAt = null)
        val priceUpdated = input.syncSubscriptionPrice && (sub.price != input.amount || sub.currencyCode != input.currencyCode)
        if (priceUpdated) updated = updated.copy(price = input.amount, currencyCode = input.currencyCode)

        val record = PaymentRecord(
            id = newId(), subscriptionId = sub.id, amount = input.amount, currencyCode = input.currencyCode,
            paymentDate = input.paymentDate, status = input.status, kind = kind, source = PaymentSource.USER_MANUAL,
            note = input.note?.takeIf { it.isNotBlank() }, iapItemName = input.iapItemName.takeIf { input.inAppPurchase },
            dateAdjustmentMode = adjustment, adjustmentTargetDate = target.takeIf { adjustment != DateAdjustmentMode.NONE },
            prevEndDate = sub.endDate, prevNextPaymentDate = sub.nextPaymentDate, prevLastPaymentDate = sub.lastPaymentDate,
            createdAt = now, updatedAt = now,
        )
        val saved = insertWithWallet(record, input.walletId)
        if (updated !== sub) subscriptionDao.upsert(updated.copy(updatedAt = now))
        return AddPaymentOutcome(saved, updated.endDate, updated.nextPaymentDate, reactivate, priceUpdated, saved.walletTransactionId != null)
    }

    /** A regular payment on [paidOn] starts the next cycle from that date. */
    private fun advanceFrom(sub: Subscription, paidOn: LocalDate): Subscription {
        if (sub.kind == SubscriptionKind.LIFETIME) return sub
        val cycle = BillingCalculator.cycleLength(sub) ?: return sub
        val next = BillingCalculator.occurrence(paidOn, cycle, 1, sub.fixedPaymentDay)
        return sub.copy(cycleAnchorDate = paidOn, nextPaymentDate = next.takeIf { sub.endDate == null || it <= sub.endDate })
    }

    /** Re-charges the wallet when amount, currency or status changed. Soft-deleted wallets are left alone. */
    private suspend fun compensateWallet(old: PaymentRecord, new: PaymentRecord): PaymentRecord {
        val txnId = old.walletTransactionId ?: return new
        val walletId = old.walletId ?: return new
        val changed = old.amount.compareTo(new.amount) != 0 || old.currencyCode != new.currencyCode || old.status != new.status
        if (!changed) return new
        val txn = walletDao.getTransaction(txnId)
        val wallet = walletDao.get(walletId)
        if (txn == null || txn.status != WalletTxnStatus.COMMITTED || wallet == null || wallet.deletedAt != null) return new
        ledger.reverse(txnId, SystemNotes.PAYMENT_EDITED)
        if (new.status != PaymentStatus.SUCCESS || new.amount.signum() <= 0) return new.copy(walletTransactionId = null)
        val charged = ledger.charge(walletId, new.amount, new.currencyCode, WalletLedger.Link(new.subscriptionId, new.id), SystemNotes.PAYMENT_EDITED)
        return new.copy(walletTransactionId = charged.id)
    }

    private fun affectsDates(record: PaymentRecord): Boolean =
        record.status == PaymentStatus.SUCCESS &&
            record.source != PaymentSource.HISTORY_GENERATED &&
            (record.kind == PaymentKind.REGULAR || record.kind == PaymentKind.EXTENSION || record.dateAdjustmentMode != DateAdjustmentMode.NONE)

    private fun countsAsLastPayment(record: PaymentRecord): Boolean =
        record.status == PaymentStatus.SUCCESS && (record.kind == PaymentKind.REGULAR || record.kind == PaymentKind.LIFETIME_PURCHASE)
}
