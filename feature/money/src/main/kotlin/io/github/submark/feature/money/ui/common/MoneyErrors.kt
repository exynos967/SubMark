package io.github.submark.feature.money.ui.common

import androidx.annotation.StringRes
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.InvalidReason
import io.github.submark.core.data.service.SystemNotes
import io.github.submark.core.ui.format.UiText
import io.github.submark.feature.money.R
import java.math.BigDecimal
import java.math.RoundingMode

/** Localized, user-facing text for a [DataError]. */
fun DataError.toUiText(): UiText = when (this) {
    DataError.NotFound -> UiText.res(R.string.money_error_not_found)
    is DataError.Invalid -> UiText.res(reason.messageRes)
    is DataError.InUse -> UiText.plural(R.plurals.money_error_in_use, count)
    is DataError.DuplicateAppStoreId -> UiText.res(R.string.money_error_duplicate_app, existing.name)
    is DataError.InsufficientFunds -> UiText.res(R.string.money_error_insufficient, plain(available), plain(required))
    is DataError.RateUnavailable -> UiText.res(R.string.money_error_rate_unavailable, currencyCode)
    is DataError.UnsupportedCurrency -> UiText.res(R.string.money_error_unsupported_currency, currencyCode)
    DataError.Stale -> UiText.res(R.string.money_error_stale)
    is DataError.Network -> UiText.res(R.string.money_error_network)
}

private fun plain(value: BigDecimal): String = value.setScale(2, RoundingMode.HALF_UP).toPlainString()

@get:StringRes
val InvalidReason.messageRes: Int
    get() = when (this) {
        InvalidReason.BLANK_NAME -> R.string.money_invalid_blank_name
        InvalidReason.NAME_EXISTS -> R.string.money_invalid_name_exists
        InvalidReason.NEGATIVE_AMOUNT -> R.string.money_invalid_negative_amount
        InvalidReason.NON_POSITIVE_AMOUNT -> R.string.money_invalid_non_positive_amount
        InvalidReason.END_NOT_AFTER_START -> R.string.money_invalid_end_not_after_start
        InvalidReason.BILLING_CYCLE_REQUIRED -> R.string.money_invalid_cycle_required
        InvalidReason.CUSTOM_CYCLE_REQUIRED -> R.string.money_invalid_custom_cycle_required
        InvalidReason.SINGLE_CYCLE_REQUIRES_END -> R.string.money_invalid_single_cycle_end
        InvalidReason.TRIAL_FIELDS_REQUIRED -> R.string.money_invalid_trial_fields
        InvalidReason.FIXED_DAY_UNSUPPORTED -> R.string.money_invalid_fixed_day
        InvalidReason.BUNDLE_NEEDS_CHILD -> R.string.money_invalid_bundle_child
        InvalidReason.START_DATE_LOCKED -> R.string.money_invalid_start_locked
        InvalidReason.NOT_MARKABLE -> R.string.money_invalid_not_markable
        InvalidReason.NOT_EXTENDABLE -> R.string.money_invalid_not_extendable
        InvalidReason.EXTEND_VALUE_OUT_OF_RANGE -> R.string.money_invalid_extend_range
        InvalidReason.EXTEND_DATE_INVALID -> R.string.money_invalid_extend_date
        InvalidReason.NOT_IN_TRIAL -> R.string.money_invalid_not_in_trial
        InvalidReason.INVALID_RENEWAL_TYPE -> R.string.money_invalid_renewal_type
        InvalidReason.NOT_WISHLIST -> R.string.money_invalid_not_wishlist
        InvalidReason.WISHLIST_NOT_ALLOWED -> R.string.money_invalid_wishlist
        InvalidReason.ADJUSTMENT_TARGET_INVALID -> R.string.money_invalid_adjustment_target
        InvalidReason.READ_ONLY_RECORD -> R.string.money_invalid_read_only
        InvalidReason.CURRENCY_LOCKED -> R.string.money_invalid_currency_locked
        InvalidReason.WALLET_DELETED -> R.string.money_invalid_wallet_deleted
        InvalidReason.WALLET_INACTIVE -> R.string.money_invalid_wallet_inactive
        InvalidReason.WALLET_UNSUPPORTED_PAYMENT -> R.string.money_invalid_wallet_unsupported
        InvalidReason.CREDIT_LIMIT_INVALID -> R.string.money_invalid_credit_limit
        InvalidReason.BALANCE_WOULD_BE_NEGATIVE -> R.string.money_invalid_balance_negative
        InvalidReason.CREATOR_REQUIRED -> R.string.money_invalid_creator_required
        InvalidReason.CREATOR_CANNOT_BE_DELETED -> R.string.money_invalid_creator_delete
        InvalidReason.NOT_SHARED -> R.string.money_invalid_not_shared
        InvalidReason.SYSTEM_ITEM -> R.string.money_invalid_system_item
        InvalidReason.OTHER_CATEGORY_PROTECTED -> R.string.money_invalid_other_category
        InvalidReason.FOLDER_NEEDS_TAG -> R.string.money_invalid_folder_tag
        InvalidReason.DROPDOWN_NEEDS_OPTION -> R.string.money_invalid_dropdown_option
        InvalidReason.REQUIRED_FIELD_MISSING -> R.string.money_invalid_required_field
        InvalidReason.INVALID_FIELD_VALUE -> R.string.money_invalid_field_value
        InvalidReason.CURRENCY_CODE_INVALID -> R.string.money_invalid_currency_code
        InvalidReason.CURRENCY_EXISTS -> R.string.money_invalid_currency_exists
        InvalidReason.RATE_OUT_OF_RANGE -> R.string.money_invalid_rate_range
        InvalidReason.DEFAULT_CURRENCY -> R.string.money_invalid_default_currency
        InvalidReason.NOT_CUSTOM -> R.string.money_invalid_not_custom
        InvalidReason.PERCENT_OUT_OF_RANGE -> R.string.money_invalid_percent_range
        InvalidReason.NOT_SYSTEM -> R.string.money_invalid_not_system
        InvalidReason.UNSUPPORTED_EXPORT_VERSION -> R.string.money_invalid_export_version
        InvalidReason.MALFORMED_EXPORT -> R.string.money_invalid_malformed_export
    }

/** Localizes data-layer system notes; user notes are returned verbatim. Null/blank -> null. */
fun localizedNote(note: String?): UiText? = when {
    note.isNullOrBlank() -> null
    !SystemNotes.isSystem(note) -> UiText.raw(note)
    note == SystemNotes.PAYMENT_DELETED -> UiText.res(R.string.money_note_payment_deleted)
    note == SystemNotes.PAYMENT_EDITED -> UiText.res(R.string.money_note_payment_edited)
    note == SystemNotes.SUBSCRIPTION_DELETED -> UiText.res(R.string.money_note_subscription_deleted)
    note == SystemNotes.STORED_VALUE_RECORD_DELETED -> UiText.res(R.string.money_note_stored_value_deleted)
    note == SystemNotes.INITIAL_BALANCE -> UiText.res(R.string.money_note_initial_balance)
    else -> UiText.res(R.string.money_note_system_unknown)
}
