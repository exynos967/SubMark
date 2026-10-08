package io.github.submark.feature.calendar.ui

import androidx.annotation.StringRes
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.InvalidReason
import io.github.submark.core.ui.format.UiText
import io.github.submark.feature.calendar.R

/** Localized, user-facing text for the [DataError] cases this feature can hit. */
fun DataError.toUiText(): UiText = when (this) {
    DataError.NotFound -> UiText.res(R.string.calendar_error_not_found)
    is DataError.Invalid -> UiText.res(reason.messageRes)
    is DataError.InUse -> UiText.res(R.string.calendar_error_in_use)
    is DataError.DuplicateAppStoreId -> UiText.res(R.string.calendar_error_duplicate_app)
    is DataError.InsufficientFunds -> UiText.res(R.string.calendar_error_insufficient_funds)
    is DataError.RateUnavailable -> UiText.res(R.string.calendar_error_rate_unavailable, currencyCode)
    is DataError.UnsupportedCurrency -> UiText.res(R.string.calendar_error_unsupported_currency, currencyCode)
    DataError.Stale -> UiText.res(R.string.calendar_error_stale)
    is DataError.Network -> UiText.res(R.string.calendar_error_network)
}

@get:StringRes
private val InvalidReason.messageRes: Int
    get() = when (this) {
        InvalidReason.NOT_MARKABLE -> R.string.calendar_invalid_not_markable
        InvalidReason.BLANK_NAME -> R.string.calendar_invalid_blank_name
        else -> R.string.calendar_invalid_generic
    }
