package io.github.submark.feature.subscriptions.ui.common

import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.InvalidReason
import io.github.submark.core.ui.format.UiText
import io.github.submark.feature.subscriptions.R

/** Localized message for any [DataError] surfaced by this feature. */
fun DataError.toUiText(): UiText = when (this) {
    DataError.NotFound -> UiText.res(R.string.subscriptions_error_not_found)
    is DataError.Invalid -> reason.toUiText()
    is DataError.InUse -> UiText.plural(R.plurals.subscriptions_error_in_use, count, count)
    is DataError.DuplicateAppStoreId -> UiText.res(R.string.subscriptions_error_duplicate_app_id, existing.name)
    is DataError.InsufficientFunds -> UiText.res(R.string.subscriptions_error_insufficient_funds)
    is DataError.RateUnavailable -> UiText.res(R.string.subscriptions_error_rate_unavailable, currencyCode)
    is DataError.UnsupportedCurrency -> UiText.res(R.string.subscriptions_error_unsupported_currency, currencyCode)
    DataError.Stale -> UiText.res(R.string.subscriptions_error_stale)
    is DataError.Network -> UiText.res(R.string.subscriptions_error_network)
}

fun InvalidReason.toUiText(): UiText = UiText.res(
    when (this) {
        InvalidReason.BLANK_NAME -> R.string.subscriptions_error_blank_name
        InvalidReason.NAME_EXISTS -> R.string.subscriptions_error_name_exists
        InvalidReason.NEGATIVE_AMOUNT -> R.string.subscriptions_error_negative_amount
        InvalidReason.NON_POSITIVE_AMOUNT -> R.string.subscriptions_error_non_positive_amount
        InvalidReason.END_NOT_AFTER_START -> R.string.subscriptions_error_end_not_after_start
        InvalidReason.BILLING_CYCLE_REQUIRED -> R.string.subscriptions_error_cycle_required
        InvalidReason.CUSTOM_CYCLE_REQUIRED -> R.string.subscriptions_error_custom_cycle_required
        InvalidReason.SINGLE_CYCLE_REQUIRES_END -> R.string.subscriptions_error_single_cycle_requires_end
        InvalidReason.TRIAL_FIELDS_REQUIRED -> R.string.subscriptions_error_trial_fields_required
        InvalidReason.FIXED_DAY_UNSUPPORTED -> R.string.subscriptions_error_fixed_day_unsupported
        InvalidReason.BUNDLE_NEEDS_CHILD -> R.string.subscriptions_error_bundle_needs_child
        InvalidReason.START_DATE_LOCKED -> R.string.subscriptions_error_start_date_locked
        InvalidReason.NOT_MARKABLE -> R.string.subscriptions_error_not_markable
        InvalidReason.NOT_EXTENDABLE -> R.string.subscriptions_error_not_extendable
        InvalidReason.EXTEND_VALUE_OUT_OF_RANGE -> R.string.subscriptions_error_extend_value_out_of_range
        InvalidReason.EXTEND_DATE_INVALID -> R.string.subscriptions_error_extend_date_invalid
        InvalidReason.NOT_IN_TRIAL -> R.string.subscriptions_error_not_in_trial
        InvalidReason.INVALID_RENEWAL_TYPE -> R.string.subscriptions_error_invalid_renewal_type
        InvalidReason.NOT_WISHLIST -> R.string.subscriptions_error_not_wishlist
        InvalidReason.WISHLIST_NOT_ALLOWED -> R.string.subscriptions_error_wishlist_not_allowed
        InvalidReason.ADJUSTMENT_TARGET_INVALID -> R.string.subscriptions_error_generic_invalid
        InvalidReason.READ_ONLY_RECORD -> R.string.subscriptions_error_read_only
        InvalidReason.CURRENCY_LOCKED -> R.string.subscriptions_error_generic_invalid
        InvalidReason.WALLET_DELETED -> R.string.subscriptions_error_wallet_deleted
        InvalidReason.WALLET_INACTIVE -> R.string.subscriptions_error_wallet_inactive
        InvalidReason.WALLET_UNSUPPORTED_PAYMENT -> R.string.subscriptions_error_wallet_unsupported
        InvalidReason.CREDIT_LIMIT_INVALID -> R.string.subscriptions_error_generic_invalid
        InvalidReason.BALANCE_WOULD_BE_NEGATIVE -> R.string.subscriptions_error_balance_negative
        InvalidReason.CREATOR_REQUIRED,
        InvalidReason.CREATOR_CANNOT_BE_DELETED,
        InvalidReason.NOT_SHARED,
        -> R.string.subscriptions_error_generic_invalid
        InvalidReason.SYSTEM_ITEM -> R.string.subscriptions_error_system_item
        InvalidReason.OTHER_CATEGORY_PROTECTED -> R.string.subscriptions_error_other_protected
        InvalidReason.FOLDER_NEEDS_TAG -> R.string.subscriptions_error_folder_needs_tag
        InvalidReason.DROPDOWN_NEEDS_OPTION -> R.string.subscriptions_error_dropdown_needs_option
        InvalidReason.REQUIRED_FIELD_MISSING -> R.string.subscriptions_error_required_field_missing
        InvalidReason.INVALID_FIELD_VALUE -> R.string.subscriptions_error_invalid_field_value
        InvalidReason.CURRENCY_CODE_INVALID,
        InvalidReason.CURRENCY_EXISTS,
        InvalidReason.RATE_OUT_OF_RANGE,
        InvalidReason.DEFAULT_CURRENCY,
        InvalidReason.NOT_CUSTOM,
        InvalidReason.PERCENT_OUT_OF_RANGE,
        InvalidReason.UNSUPPORTED_EXPORT_VERSION,
        InvalidReason.MALFORMED_EXPORT,
        -> R.string.subscriptions_error_generic_invalid
        InvalidReason.NOT_SYSTEM -> R.string.subscriptions_error_not_system
    },
)
