package io.github.submark.core.data.result

import io.github.submark.core.model.Subscription
import java.math.BigDecimal

/** Outcome of an operation that can fail for an expected, user-facing reason. Bugs still throw. */
sealed interface DataResult<out T> {
    data class Success<T>(val value: T) : DataResult<T>
    data class Failure(val error: DataError) : DataResult<Nothing>

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.value
    fun errorOrNull(): DataError? = (this as? Failure)?.error
}

inline fun <T, R> DataResult<T>.map(transform: (T) -> R): DataResult<R> = when (this) {
    is DataResult.Success -> DataResult.Success(transform(value))
    is DataResult.Failure -> this
}

inline fun <T> DataResult<T>.onSuccess(action: (T) -> Unit): DataResult<T> = also { if (it is DataResult.Success) action(it.value) }

inline fun <T> DataResult<T>.onFailure(action: (DataError) -> Unit): DataResult<T> = also { if (it is DataResult.Failure) action(it.error) }

/** Expected failures. UI maps each case (and each [InvalidReason]) to a localized message. */
sealed interface DataError {
    data object NotFound : DataError
    data class Invalid(val reason: InvalidReason, val detail: String? = null) : DataError
    /** Entity is still referenced (e.g. currency used by [count] rows). */
    data class InUse(val count: Int) : DataError
    data class DuplicateAppStoreId(val existing: Subscription) : DataError
    /** Wallet cannot cover [required] (wallet currency); [available] = balance or remaining credit. */
    data class InsufficientFunds(val walletId: String, val available: BigDecimal, val required: BigDecimal) : DataError
    data class RateUnavailable(val currencyCode: String) : DataError
    /** No rate provider knows this currency code. */
    data class UnsupportedCurrency(val currencyCode: String) : DataError
    /** The entity changed since the caller read it. */
    data object Stale : DataError
    data class Network(val message: String?) : DataError
}

enum class InvalidReason {
    BLANK_NAME,
    NAME_EXISTS,
    NEGATIVE_AMOUNT,
    NON_POSITIVE_AMOUNT,
    END_NOT_AFTER_START,
    BILLING_CYCLE_REQUIRED,
    CUSTOM_CYCLE_REQUIRED,
    SINGLE_CYCLE_REQUIRES_END,
    TRIAL_FIELDS_REQUIRED,
    FIXED_DAY_UNSUPPORTED,
    BUNDLE_NEEDS_CHILD,
    START_DATE_LOCKED,
    NOT_MARKABLE,
    NOT_EXTENDABLE,
    EXTEND_VALUE_OUT_OF_RANGE,
    EXTEND_DATE_INVALID,
    NOT_IN_TRIAL,
    INVALID_RENEWAL_TYPE,
    NOT_WISHLIST,
    WISHLIST_NOT_ALLOWED,
    ADJUSTMENT_TARGET_INVALID,
    READ_ONLY_RECORD,
    CURRENCY_LOCKED,
    WALLET_DELETED,
    WALLET_INACTIVE,
    WALLET_UNSUPPORTED_PAYMENT,
    CREDIT_LIMIT_INVALID,
    BALANCE_WOULD_BE_NEGATIVE,
    CREATOR_REQUIRED,
    CREATOR_CANNOT_BE_DELETED,
    NOT_SHARED,
    SYSTEM_ITEM,
    OTHER_CATEGORY_PROTECTED,
    FOLDER_NEEDS_TAG,
    DROPDOWN_NEEDS_OPTION,
    REQUIRED_FIELD_MISSING,
    INVALID_FIELD_VALUE,
    CURRENCY_CODE_INVALID,
    CURRENCY_EXISTS,
    RATE_OUT_OF_RANGE,
    DEFAULT_CURRENCY,
    NOT_CUSTOM,
    PERCENT_OUT_OF_RANGE,
    NOT_SYSTEM,
    UNSUPPORTED_EXPORT_VERSION,
    MALFORMED_EXPORT,
}
