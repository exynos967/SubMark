package io.github.submark.core.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * [usdRate] = units of this currency per 1 USD. Conversion goes through USD as a fixed pivot,
 * so changing the default currency never requires rebasing stored rates.
 */
@Serializable
@Entity(tableName = "currencies")
data class Currency(
    @PrimaryKey val code: String,
    val name: String,
    val symbol: String,
    val isCustom: Boolean = false,
    val isEnabled: Boolean = false,
    val usdRate: Money? = null,
    val rateMode: RateMode = RateMode.AUTO,
    val rateProvider: RateProvider? = null,
    val rateUpdatedAt: Timestamp? = null,
    val sortOrder: Int = 0,
)

/** Cached daily rate, quote units per 1 USD. */
@Serializable
@Entity(tableName = "historical_rates", primaryKeys = ["date", "code"])
data class HistoricalRate(
    val date: Day,
    val code: String,
    val usdRate: Money,
    val provider: RateProvider,
    val fetchedAt: Timestamp,
)

@Serializable
@Entity(
    tableName = "payment_records",
    indices = [Index("subscriptionId"), Index("paymentDate"), Index("walletTransactionId")],
    foreignKeys = [ForeignKey(entity = Subscription::class, parentColumns = ["id"], childColumns = ["subscriptionId"], onDelete = ForeignKey.CASCADE)],
)
data class PaymentRecord(
    @PrimaryKey val id: String = newId(),
    val subscriptionId: String,
    /** For shared subscriptions this is the user's own share. */
    val amount: Money,
    val currencyCode: String,
    val paymentDate: Day,
    val status: PaymentStatus = PaymentStatus.SUCCESS,
    val kind: PaymentKind = PaymentKind.REGULAR,
    val source: PaymentSource = PaymentSource.USER_MANUAL,
    val markTiming: MarkTiming = MarkTiming.ON_TIME,
    /** Scheduled due date when marked early/overdue. */
    val originalDueDate: Day? = null,
    val note: String? = null,
    val iapItemName: String? = null,
    val dateAdjustmentMode: DateAdjustmentMode = DateAdjustmentMode.NONE,
    val adjustmentTargetDate: Day? = null,
    /** Subscription dates before this record applied; used to revert on delete. */
    val prevEndDate: Day? = null,
    val prevNextPaymentDate: Day? = null,
    val prevLastPaymentDate: Day? = null,
    val extensionFrom: Day? = null,
    val extensionTo: Day? = null,
    val walletId: String? = null,
    val walletTransactionId: String? = null,
    /** Main record that produced this one through bundle payment sync. */
    val bundleParentPaymentId: String? = null,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
)

@Serializable
@Entity(tableName = "payment_methods")
data class PaymentMethod(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val iconValue: String,
    val isSystem: Boolean = false,
    val sortOrder: Int = 0,
)

@Serializable
@Entity(tableName = "wallets")
data class Wallet(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val kind: WalletKind = WalletKind.BALANCE_TRACKED,
    /** Locked once any transaction exists. */
    val currencyCode: String,
    /** Cached running balance = sum of committed signed deltas. */
    val balance: Money = BigDecimal.ZERO,
    /** CREDIT only; null = unlimited. */
    val creditLimit: Money? = null,
    val isActive: Boolean = true,
    /** Soft delete; history stays readable. */
    val deletedAt: Timestamp? = null,
    val colorHex: String? = null,
    val iconValue: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
)

@Serializable
@Entity(tableName = "wallet_transactions", indices = [Index("walletId"), Index("subscriptionId"), Index("paymentRecordId")])
data class WalletTransaction(
    @PrimaryKey val id: String = newId(),
    val walletId: String,
    val type: WalletTxnType,
    val status: WalletTxnStatus = WalletTxnStatus.COMMITTED,
    /** Positive magnitude in wallet currency. */
    val amount: Money,
    /** Effect on balance: + top-up/refund, - expense, +/- adjustment. */
    val signedDelta: Money,
    val balanceAfter: Money,
    val sourceAmount: Money? = null,
    val sourceCurrencyCode: String? = null,
    val subscriptionId: String? = null,
    val paymentRecordId: String? = null,
    val storedValueRecordId: String? = null,
    /** Set on the compensating transaction. */
    val reversesTransactionId: String? = null,
    val note: String? = null,
    val occurredAt: Timestamp,
    val createdAt: Timestamp,
)

@Serializable
@Entity(
    tableName = "stored_value_records",
    indices = [Index("subscriptionId")],
    foreignKeys = [ForeignKey(entity = Subscription::class, parentColumns = ["id"], childColumns = ["subscriptionId"], onDelete = ForeignKey.CASCADE)],
)
data class StoredValueRecord(
    @PrimaryKey val id: String = newId(),
    val subscriptionId: String,
    val type: StoredValueRecordType,
    /** > 0, in [currencyCode]. */
    val amount: Money,
    val currencyCode: String,
    val amountInSubscriptionCurrency: Money,
    val balanceAfter: Money,
    val description: String? = null,
    val isInitial: Boolean = false,
    /** DEPOSIT -> its STORED_VALUE_DEPOSIT payment record. */
    val paymentRecordId: String? = null,
    val walletTransactionId: String? = null,
    val occurredAt: Timestamp,
    val createdAt: Timestamp,
)

@Serializable
@Entity(
    tableName = "shared_configs",
    foreignKeys = [ForeignKey(entity = Subscription::class, parentColumns = ["id"], childColumns = ["subscriptionId"], onDelete = ForeignKey.CASCADE)],
)
data class SharedConfig(
    @PrimaryKey val subscriptionId: String,
    val splitMode: SplitMode = SplitMode.EQUAL,
    val description: String? = null,
)

@Serializable
@Entity(
    tableName = "shared_members",
    indices = [Index("subscriptionId")],
    foreignKeys = [ForeignKey(entity = Subscription::class, parentColumns = ["id"], childColumns = ["subscriptionId"], onDelete = ForeignKey.CASCADE)],
)
data class SharedMember(
    @PrimaryKey val id: String = newId(),
    val subscriptionId: String,
    val name: String,
    val email: String? = null,
    val note: String? = null,
    val status: MemberStatus = MemberStatus.ACTIVE,
    val isCreator: Boolean = false,
    /** RATIO mode, 0..100. */
    val ratioPercent: Money? = null,
    /** FIXED_AMOUNT mode, in [paymentCurrencyCode] or the subscription currency. */
    val fixedAmount: Money? = null,
    val paymentCurrencyCode: String? = null,
    val joinedAt: Day,
    val sortOrder: Int = 0,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
)
