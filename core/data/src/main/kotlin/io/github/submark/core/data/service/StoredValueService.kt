package io.github.submark.core.data.service

import io.github.submark.core.data.change.ChangeNotifier
import io.github.submark.core.data.currency.CurrencyRepository
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.result.InvalidReason
import io.github.submark.core.data.result.TransactionRunner
import io.github.submark.core.data.result.abort
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.database.dao.PaymentDao
import io.github.submark.core.database.dao.StoredValueDao
import io.github.submark.core.database.dao.SubscriptionDao
import io.github.submark.core.model.PaymentKind
import io.github.submark.core.model.PaymentRecord
import io.github.submark.core.model.StoredValueRecord
import io.github.submark.core.model.StoredValueRecordType
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.newId
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stored-value balances. Deposits are spending (a STORED_VALUE_DEPOSIT payment record);
 * deductions only lower the balance, so money is never counted twice.
 * The cached `Subscription.storedValueBalance` always equals deposits minus deductions.
 */
@Singleton
class StoredValueService @Inject internal constructor(
    private val storedValueDao: StoredValueDao,
    private val subscriptionDao: SubscriptionDao,
    private val paymentDao: PaymentDao,
    private val ledger: WalletLedger,
    private val currencies: CurrencyRepository,
    private val tx: TransactionRunner,
    private val notifier: ChangeNotifier,
    private val time: TimeProvider,
) {
    fun observeRecords(subscriptionId: String): Flow<List<StoredValueRecord>> = storedValueDao.observeForSubscription(subscriptionId)

    fun observeAllRecords(): Flow<List<StoredValueRecord>> = storedValueDao.observeAll()

    /**
     * Adds money: DEPOSIT record + STORED_VALUE_DEPOSIT payment, optionally charged to [walletId].
     * [currencyCode] may differ from the subscription currency (converted at the current rate).
     */
    suspend fun topUp(
        subscriptionId: String,
        amount: BigDecimal,
        currencyCode: String,
        description: String? = null,
        walletId: String? = null,
    ): DataResult<StoredValueRecord> {
        val result = tx.run {
            val sub = subscriptionDao.get(subscriptionId) ?: abort(DataError.NotFound)
            depositInTx(sub, amount, currencyCode, description, walletId, isInitial = false, date = time.today())
        }
        if (result.isSuccess) notifier.notifyChanged(subscriptionId)
        return result
    }

    /**
     * Deletes a record. A deposit also deletes its payment and reverses its wallet charge, and is refused
     * when the balance would become negative.
     */
    suspend fun deleteRecord(recordId: String): DataResult<Unit> {
        var subscriptionId: String? = null
        val result = tx.run {
            val record = storedValueDao.get(recordId) ?: abort(DataError.NotFound)
            subscriptionId = record.subscriptionId
            deleteRecordInTx(record)
        }
        if (result.isSuccess) notifier.notifyChanged(subscriptionId!!)
        return result
    }

    /** Recomputes the cached balance from the records (repair tool). */
    suspend fun recomputeBalance(subscriptionId: String): DataResult<BigDecimal> = tx.run {
        val sub = subscriptionDao.get(subscriptionId) ?: abort(DataError.NotFound)
        val balance = balanceOf(storedValueDao.getForSubscription(sub.id))
        subscriptionDao.upsert(sub.copy(storedValueBalance = balance, updatedAt = time.now()))
        balance
    }

    // ---- transaction-internal operations used by other services ----

    internal suspend fun depositInTx(
        sub: Subscription,
        amount: BigDecimal,
        currencyCode: String,
        description: String?,
        walletId: String?,
        isInitial: Boolean,
        date: LocalDate,
    ): StoredValueRecord {
        if (sub.kind != SubscriptionKind.STORED_VALUE) abort(InvalidReason.NOT_MARKABLE)
        if (amount.signum() <= 0) abort(InvalidReason.NON_POSITIVE_AMOUNT)
        val converted = if (currencyCode == sub.currencyCode) {
            amount
        } else {
            currencies.converter().convert(amount, currencyCode, sub.currencyCode)?.setScale(2, RoundingMode.HALF_UP)
                ?: abort(DataError.RateUnavailable(currencyCode))
        }
        val now = time.now()
        val recordId = newId()
        val paymentId = newId()
        val walletTxn = walletId?.let {
            ledger.charge(it, amount, currencyCode, WalletLedger.Link(sub.id, paymentId, recordId), occurredAt = now)
        }
        paymentDao.upsert(
            PaymentRecord(
                id = paymentId, subscriptionId = sub.id, amount = amount, currencyCode = currencyCode, paymentDate = date,
                kind = PaymentKind.STORED_VALUE_DEPOSIT, note = description, walletId = walletTxn?.walletId,
                walletTransactionId = walletTxn?.id, createdAt = now, updatedAt = now,
            ),
        )
        val balance = sub.storedValueBalance + converted
        val record = StoredValueRecord(
            id = recordId, subscriptionId = sub.id, type = StoredValueRecordType.DEPOSIT, amount = amount, currencyCode = currencyCode,
            amountInSubscriptionCurrency = converted, balanceAfter = balance, description = description, isInitial = isInitial,
            paymentRecordId = paymentId, walletTransactionId = walletTxn?.id, occurredAt = now, createdAt = now,
        )
        storedValueDao.upsert(record)
        subscriptionDao.upsert(sub.copy(storedValueBalance = balance, updatedAt = now))
        return record
    }

    /** Deducts one cycle fee (the price) for the occurrence on [dueDate]. The balance may go negative (debt). */
    internal suspend fun deductInTx(sub: Subscription, dueDate: LocalDate): Pair<StoredValueRecord, Subscription> {
        val now = time.now()
        val balance = sub.storedValueBalance - sub.price
        val record = StoredValueRecord(
            subscriptionId = sub.id, type = StoredValueRecordType.DEDUCTION, amount = sub.price, currencyCode = sub.currencyCode,
            amountInSubscriptionCurrency = sub.price, balanceAfter = balance,
            occurredAt = dueDate.atStartOfDay(time.zone()).toInstant(), createdAt = now,
        )
        storedValueDao.upsert(record)
        val updated = sub.copy(storedValueBalance = balance, updatedAt = now)
        subscriptionDao.upsert(updated)
        return record to updated
    }

    internal suspend fun deleteRecordInTx(record: StoredValueRecord) {
        val sub = subscriptionDao.get(record.subscriptionId) ?: abort(DataError.NotFound)
        val remaining = storedValueDao.getForSubscription(sub.id).filter { it.id != record.id }
        val balance = balanceOf(remaining)
        if (record.type == StoredValueRecordType.DEPOSIT && balance.signum() < 0) abort(InvalidReason.BALANCE_WOULD_BE_NEGATIVE)
        record.walletTransactionId?.let { ledger.reverse(it, SystemNotes.STORED_VALUE_RECORD_DELETED) }
        storedValueDao.deleteById(record.id)
        record.paymentRecordId?.let { paymentDao.deleteById(it) }
        subscriptionDao.upsert(sub.copy(storedValueBalance = balance, updatedAt = time.now()))
    }

    private fun balanceOf(records: List<StoredValueRecord>): BigDecimal = records.fold(BigDecimal.ZERO) { acc, r ->
        if (r.type == StoredValueRecordType.DEPOSIT) acc + r.amountInSubscriptionCurrency else acc - r.amountInSubscriptionCurrency
    }
}
