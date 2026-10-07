package io.github.submark.core.data.service

import io.github.submark.core.data.currency.CurrencyRepository
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.InvalidReason
import io.github.submark.core.data.result.abort
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.database.dao.WalletDao
import io.github.submark.core.domain.CurrencyConverter
import io.github.submark.core.model.Wallet
import io.github.submark.core.model.WalletKind
import io.github.submark.core.model.WalletTransaction
import io.github.submark.core.model.WalletTxnStatus
import io.github.submark.core.model.WalletTxnType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wallet balance mutations. Every method must run inside a [io.github.submark.core.data.result.TransactionRunner]
 * block: failures [abort] the whole surrounding transaction so no payment is saved without its charge.
 */
@Singleton
internal class WalletLedger @Inject constructor(
    private val walletDao: WalletDao,
    private val currencies: CurrencyRepository,
    private val time: TimeProvider,
) {
    data class Link(val subscriptionId: String? = null, val paymentRecordId: String? = null, val storedValueRecordId: String? = null)

    /** Charges [amount] in [currencyCode] to the wallet (converted to the wallet currency) as an EXPENSE. */
    suspend fun charge(walletId: String, amount: BigDecimal, currencyCode: String, link: Link, note: String? = null, occurredAt: Instant = time.now()): WalletTransaction {
        val wallet = usableWallet(walletId)
        val converted = convert(amount, currencyCode, wallet.currencyCode, currencies.converter())
        checkFunds(wallet, converted)?.let { abort(it) }
        return post(
            wallet, WalletTxnType.EXPENSE, converted, converted.negate(), link, note, occurredAt,
            sourceAmount = amount.takeIf { currencyCode != wallet.currencyCode },
            sourceCurrencyCode = currencyCode.takeIf { it != wallet.currencyCode },
        )
    }

    /** Manual top-up (+) or expense (-) on the wallet screen. */
    suspend fun manual(walletId: String, type: WalletTxnType, amount: BigDecimal, note: String?): WalletTransaction {
        if (amount.signum() <= 0) abort(InvalidReason.NON_POSITIVE_AMOUNT)
        val wallet = usableWallet(walletId)
        val delta = if (type == WalletTxnType.EXPENSE) amount.negate() else amount
        if (delta.signum() < 0) checkFunds(wallet, amount)?.let { abort(it) }
        return post(wallet, type, amount, delta, Link(), note, time.now())
    }

    /** Opening balance of a new wallet, recorded as an ADJUSTMENT. */
    suspend fun openingBalance(wallet: Wallet, amount: BigDecimal): WalletTransaction =
        post(wallet, WalletTxnType.ADJUSTMENT, amount.abs(), amount, Link(), SystemNotes.INITIAL_BALANCE, time.now())

    /**
     * Compensates a committed transaction (REFUND for expenses, ADJUSTMENT otherwise) and marks it REVERSED.
     * Returns null when nothing is done: unknown or already reversed transaction, or a soft-deleted wallet.
     */
    suspend fun reverse(transactionId: String, note: String?): WalletTransaction? {
        val original = walletDao.getTransaction(transactionId) ?: return null
        if (original.status != WalletTxnStatus.COMMITTED) return null
        val wallet = walletDao.get(original.walletId) ?: return null
        if (wallet.deletedAt != null) return null
        walletDao.upsertTransaction(original.copy(status = WalletTxnStatus.REVERSED))
        val type = if (original.type == WalletTxnType.EXPENSE) WalletTxnType.REFUND else WalletTxnType.ADJUSTMENT
        val link = Link(original.subscriptionId, original.paymentRecordId, original.storedValueRecordId)
        return post(wallet, type, original.amount, original.signedDelta.negate(), link, note, time.now(), reverses = original.id)
    }

    /** Why [wallet] cannot pay [amount] in [currencyCode], or null if it can. */
    fun chargeProblem(wallet: Wallet, amount: BigDecimal, currencyCode: String, converter: CurrencyConverter): DataError? {
        if (wallet.deletedAt != null) return DataError.Invalid(InvalidReason.WALLET_DELETED)
        if (!wallet.isActive) return DataError.Invalid(InvalidReason.WALLET_INACTIVE)
        val converted = convertOrNull(amount, currencyCode, wallet.currencyCode, converter)
            ?: return DataError.RateUnavailable(missingRate(currencyCode, wallet.currencyCode, converter))
        return checkFunds(wallet, converted)
    }

    private suspend fun usableWallet(walletId: String): Wallet {
        val wallet = walletDao.get(walletId) ?: abort(DataError.NotFound)
        if (wallet.deletedAt != null) abort(InvalidReason.WALLET_DELETED)
        if (!wallet.isActive) abort(InvalidReason.WALLET_INACTIVE)
        return wallet
    }

    private fun checkFunds(wallet: Wallet, amount: BigDecimal): DataError? {
        val after = wallet.balance - amount
        return when (wallet.kind) {
            WalletKind.BALANCE_TRACKED ->
                if (after.signum() < 0) DataError.InsufficientFunds(wallet.id, wallet.balance.max(BigDecimal.ZERO), amount) else null
            WalletKind.CREDIT -> {
                val limit = wallet.creditLimit ?: return null
                if (after < limit.negate()) DataError.InsufficientFunds(wallet.id, (wallet.balance + limit).max(BigDecimal.ZERO), amount) else null
            }
            WalletKind.SETTLEMENT_ONLY -> null
        }
    }

    private fun convert(amount: BigDecimal, from: String, to: String, converter: CurrencyConverter): BigDecimal =
        convertOrNull(amount, from, to, converter) ?: abort(DataError.RateUnavailable(missingRate(from, to, converter)))

    private fun convertOrNull(amount: BigDecimal, from: String, to: String, converter: CurrencyConverter): BigDecimal? =
        if (from == to) amount else converter.convert(amount, from, to)?.setScale(2, RoundingMode.HALF_UP)

    private fun missingRate(from: String, to: String, converter: CurrencyConverter) = if (converter.rate(from) == null) from else to

    private suspend fun post(
        wallet: Wallet,
        type: WalletTxnType,
        amount: BigDecimal,
        delta: BigDecimal,
        link: Link,
        note: String?,
        occurredAt: Instant,
        sourceAmount: BigDecimal? = null,
        sourceCurrencyCode: String? = null,
        reverses: String? = null,
    ): WalletTransaction {
        val now = time.now()
        val balance = wallet.balance + delta
        val txn = WalletTransaction(
            walletId = wallet.id, type = type, amount = amount, signedDelta = delta, balanceAfter = balance,
            sourceAmount = sourceAmount, sourceCurrencyCode = sourceCurrencyCode,
            subscriptionId = link.subscriptionId, paymentRecordId = link.paymentRecordId, storedValueRecordId = link.storedValueRecordId,
            reversesTransactionId = reverses, note = note, occurredAt = occurredAt, createdAt = now,
        )
        walletDao.upsertTransaction(txn)
        walletDao.upsert(wallet.copy(balance = balance, updatedAt = now))
        return txn
    }
}
