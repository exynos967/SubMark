package io.github.submark.core.data.service

import io.github.submark.core.data.change.ChangeNotifier
import io.github.submark.core.data.currency.CurrencyRepository
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.result.InvalidReason
import io.github.submark.core.data.result.TransactionRunner
import io.github.submark.core.data.result.abort
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.database.dao.SubscriptionDao
import io.github.submark.core.database.dao.WalletDao
import io.github.submark.core.domain.BillingCalculator
import io.github.submark.core.domain.CurrencyConverter
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.Wallet
import io.github.submark.core.model.WalletKind
import io.github.submark.core.model.WalletTransaction
import io.github.submark.core.model.WalletTxnType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Sum of active BALANCE_TRACKED wallets in [currencyCode]; wallets without a usable rate are listed. */
data class TrackedAssets(val total: BigDecimal, val currencyCode: String, val walletCount: Int, val missingRateCodes: Set<String>)

/** How long a wallet can pay a subscription. [payableCycles] null = unlimited (overdraft allowed). */
data class WalletCoverage(
    val perCycleAmount: BigDecimal?,
    val payableCycles: Long?,
    val coversUntil: LocalDate?,
    val linkedSubscriptionCount: Int,
)

/** Global wallets: lifecycle, manual top-ups/deductions and derived figures. Charges happen in payment flows. */
@Singleton
class WalletService @Inject internal constructor(
    private val walletDao: WalletDao,
    private val subscriptionDao: SubscriptionDao,
    private val ledger: WalletLedger,
    private val currencies: CurrencyRepository,
    private val tx: TransactionRunner,
    private val notifier: ChangeNotifier,
    private val time: TimeProvider,
) {
    /** Non-deleted wallets, active first. */
    fun observeWallets(): Flow<List<Wallet>> = walletDao.observeVisible()

    /** Including soft-deleted ones, for history labels ("x (deleted)"). */
    fun observeAllWallets(): Flow<List<Wallet>> = walletDao.observeAllIncludingDeleted()

    fun observe(id: String): Flow<Wallet?> = walletDao.observe(id)

    fun observeTransactions(walletId: String): Flow<List<WalletTransaction>> = walletDao.observeTransactions(walletId)

    fun observeAllTransactions(): Flow<List<WalletTransaction>> = walletDao.observeAllTransactions()

    suspend fun get(id: String): Wallet? = walletDao.get(id)

    suspend fun getTransaction(id: String): WalletTransaction? = walletDao.getTransaction(id)

    suspend fun create(
        name: String,
        kind: WalletKind,
        currencyCode: String,
        initialBalance: BigDecimal = BigDecimal.ZERO,
        creditLimit: BigDecimal? = null,
        colorHex: String? = null,
        iconValue: String? = null,
    ): DataResult<Wallet> = tx.run {
        if (name.isBlank()) abort(InvalidReason.BLANK_NAME)
        validateCreditLimit(kind, creditLimit)
        if (kind == WalletKind.BALANCE_TRACKED && initialBalance.signum() < 0) abort(InvalidReason.NEGATIVE_AMOUNT)
        val now = time.now()
        val order = (walletDao.getAll().maxOfOrNull { it.sortOrder } ?: -1) + 1
        val wallet = Wallet(
            name = name.trim(), kind = kind, currencyCode = currencyCode, creditLimit = creditLimit.takeIf { kind == WalletKind.CREDIT },
            colorHex = colorHex, iconValue = iconValue, sortOrder = order, createdAt = now, updatedAt = now,
        )
        walletDao.upsert(wallet)
        if (initialBalance.signum() != 0) ledger.openingBalance(wallet, initialBalance)
        walletDao.get(wallet.id)!!
    }

    /** Edits name, kind, credit limit, style and currency (currency only while no transaction exists). */
    suspend fun update(wallet: Wallet): DataResult<Wallet> = tx.run {
        val current = walletDao.get(wallet.id) ?: abort(DataError.NotFound)
        if (current.deletedAt != null) abort(InvalidReason.WALLET_DELETED)
        if (wallet.name.isBlank()) abort(InvalidReason.BLANK_NAME)
        if (wallet.currencyCode != current.currencyCode && walletDao.countTransactions(wallet.id) > 0) abort(InvalidReason.CURRENCY_LOCKED)
        validateCreditLimit(wallet.kind, wallet.creditLimit)
        current.copy(
            name = wallet.name.trim(), kind = wallet.kind, currencyCode = wallet.currencyCode,
            creditLimit = wallet.creditLimit.takeIf { wallet.kind == WalletKind.CREDIT },
            colorHex = wallet.colorHex, iconValue = wallet.iconValue, sortOrder = wallet.sortOrder, updatedAt = time.now(),
        ).also { walletDao.upsert(it) }
    }

    suspend fun isCurrencyLocked(walletId: String): Boolean = walletDao.countTransactions(walletId) > 0

    /** Excludes the wallet from payments and assets and unlinks its subscriptions (not restored on reactivate). */
    suspend fun deactivate(id: String): DataResult<Unit> = retire(id) { it.copy(isActive = false) }

    suspend fun reactivate(id: String): DataResult<Unit> = tx.run {
        val current = walletDao.get(id) ?: abort(DataError.NotFound)
        if (current.deletedAt != null) abort(InvalidReason.WALLET_DELETED)
        walletDao.upsert(current.copy(isActive = true, updatedAt = time.now()))
    }

    /** Soft delete: history stays, no balance-clearing transaction, irreversible. */
    suspend fun delete(id: String): DataResult<Unit> = retire(id) { it.copy(isActive = false, deletedAt = time.now()) }

    suspend fun topUp(walletId: String, amount: BigDecimal, note: String? = null): DataResult<WalletTransaction> =
        tx.run { ledger.manual(walletId, WalletTxnType.TOP_UP, amount, note) }

    /** Manual expense; obeys the wallet kind rules (no negative balance, credit limit). */
    suspend fun deduct(walletId: String, amount: BigDecimal, note: String? = null): DataResult<WalletTransaction> =
        tx.run { ledger.manual(walletId, WalletTxnType.EXPENSE, amount, note) }

    /** Voids a committed transaction with a compensating one. No-op for soft-deleted wallets. */
    suspend fun reverseTransaction(transactionId: String, note: String? = null): DataResult<Unit> = tx.run {
        walletDao.getTransaction(transactionId) ?: abort(DataError.NotFound)
        ledger.reverse(transactionId, note)
        Unit
    }

    /** Why the wallet cannot pay [amount] in [currencyCode] right now (for wallet pickers), or null if it can. */
    suspend fun chargeProblem(walletId: String, amount: BigDecimal, currencyCode: String): DataError? {
        val wallet = walletDao.get(walletId) ?: return DataError.NotFound
        return ledger.chargeProblem(wallet, amount, currencyCode, currencies.converter())
    }

    fun observeTrackedAssets(currencyCode: String): Flow<TrackedAssets> =
        combine(walletDao.observeVisible(), currencies.observeConverter()) { wallets, converter -> trackedAssets(wallets, currencyCode, converter) }

    companion object {
        /** Active, non-deleted BALANCE_TRACKED wallets converted to [currencyCode]. CREDIT and SETTLEMENT_ONLY are excluded. */
        fun trackedAssets(wallets: List<Wallet>, currencyCode: String, converter: CurrencyConverter): TrackedAssets {
            val tracked = wallets.filter { it.kind == WalletKind.BALANCE_TRACKED && it.isActive && it.deletedAt == null }
            var total = BigDecimal.ZERO
            val missing = mutableSetOf<String>()
            tracked.forEach { w ->
                val value = converter.convert(w.balance, w.currencyCode, currencyCode)
                if (value == null) missing += w.currencyCode else total += value
            }
            return TrackedAssets(total.setScale(2, RoundingMode.HALF_UP), currencyCode, tracked.size, missing)
        }

        /**
         * Coverage of [subscription] by [wallet]: whole cycles payable from the balance (plus credit limit),
         * and the due date of the last covered cycle. Unlimited for SETTLEMENT_ONLY and CREDIT without a limit.
         */
        fun coverage(subscription: Subscription, wallet: Wallet, converter: CurrencyConverter, linkedSubscriptionCount: Int): WalletCoverage {
            val perCycle = converter.convert(subscription.price, subscription.currencyCode, wallet.currencyCode)
                ?.setScale(2, RoundingMode.HALF_UP)
            val available = when (wallet.kind) {
                WalletKind.BALANCE_TRACKED -> wallet.balance
                WalletKind.CREDIT -> wallet.creditLimit?.let { wallet.balance + it }
                WalletKind.SETTLEMENT_ONLY -> null
            }
            if (available == null || perCycle == null || perCycle.signum() <= 0) {
                return WalletCoverage(perCycle, null, null, linkedSubscriptionCount)
            }
            val cycles = maxOf(0L, available.divide(perCycle, MathContext.DECIMAL64).setScale(0, RoundingMode.DOWN).toLong())
            val cycle = BillingCalculator.cycleLength(subscription)
            val next = subscription.nextPaymentDate
            val until = if (cycles > 0 && cycle != null && next != null) BillingCalculator.add(next, cycle, cycles - 1) else null
            return WalletCoverage(perCycle, cycles, until, linkedSubscriptionCount)
        }
    }

    private fun validateCreditLimit(kind: WalletKind, limit: BigDecimal?) {
        if (kind == WalletKind.CREDIT && limit != null && limit.signum() < 0) abort(InvalidReason.CREDIT_LIMIT_INVALID)
    }

    private suspend fun retire(id: String, change: (Wallet) -> Wallet): DataResult<Unit> {
        var linked: Set<String> = emptySet()
        val result = tx.run {
            val current = walletDao.get(id) ?: abort(DataError.NotFound)
            if (current.deletedAt != null) abort(InvalidReason.WALLET_DELETED)
            linked = subscriptionDao.getAll().filter { it.walletId == id }.map { it.id }.toSet()
            walletDao.upsert(change(current).copy(updatedAt = time.now()))
            walletDao.unlinkSubscriptions(id)
        }
        if (result.isSuccess) notifier.notifyChanged(linked)
        return result
    }
}
