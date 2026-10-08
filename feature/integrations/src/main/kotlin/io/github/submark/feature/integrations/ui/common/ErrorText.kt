package io.github.submark.feature.integrations.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.InvalidReason
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.format.asString
import io.github.submark.feature.integrations.R
import io.github.submark.feature.integrations.data.ai.AiError
import io.github.submark.feature.integrations.data.icons.IconPackError
import io.github.submark.feature.integrations.data.itunes.ItunesError
import io.github.submark.feature.integrations.data.popular.CatalogError
import io.github.submark.feature.integrations.data.rawg.RawgError

/** Localized message for a core [DataError]; falls back to [DataError.message]/generic. */
fun DataError.toUiText(): UiText = when (this) {
    is DataError.NotFound -> UiText.res(R.string.integrations_error_not_found)
    is DataError.Invalid -> when (reason) {
        InvalidReason.BLANK_NAME -> UiText.res(R.string.integrations_error_blank_name)
        InvalidReason.NEGATIVE_AMOUNT, InvalidReason.NON_POSITIVE_AMOUNT -> UiText.res(R.string.integrations_error_bad_amount)
        else -> UiText.res(R.string.integrations_error_invalid)
    }
    is DataError.Network -> UiText.res(R.string.integrations_error_network)
    is DataError.InUse -> UiText.res(R.string.integrations_error_in_use)
    else -> UiText.res(R.string.integrations_error_generic)
}

fun CatalogError.toUiText(): UiText = when (this) {
    CatalogError.InvalidUrl -> UiText.res(R.string.integrations_error_invalid_url)
    is CatalogError.Http -> UiText.res(R.string.integrations_error_http, code)
    CatalogError.Network -> UiText.res(R.string.integrations_error_network)
    CatalogError.Parse -> UiText.res(R.string.integrations_error_parse)
}

fun ItunesError.toUiText(): UiText = when (this) {
    ItunesError.Network -> UiText.res(R.string.integrations_error_network)
    ItunesError.Parse -> UiText.res(R.string.integrations_error_parse)
    ItunesError.NoItems -> UiText.res(R.string.integrations_error_itunes_no_items)
    ItunesError.IncompletePrice -> UiText.res(R.string.integrations_error_itunes_incomplete)
}

fun RawgError.toUiText(): UiText = when (this) {
    RawgError.NoApiKey -> UiText.res(R.string.integrations_error_rawg_no_key)
    RawgError.Network -> UiText.res(R.string.integrations_error_network)
    is RawgError.Http -> UiText.res(R.string.integrations_error_http, code)
    RawgError.Parse -> UiText.res(R.string.integrations_error_parse)
    RawgError.NoResults -> UiText.res(R.string.integrations_error_rawg_no_results)
}

fun IconPackError.toUiText(): UiText = when (this) {
    IconPackError.InvalidUrl -> UiText.res(R.string.integrations_error_invalid_url)
    is IconPackError.Http -> UiText.res(R.string.integrations_error_http, code)
    IconPackError.Network -> UiText.res(R.string.integrations_error_network)
    IconPackError.Parse -> UiText.res(R.string.integrations_error_parse)
}

fun AiError.toUiText(): UiText = when (this) {
    AiError.InvalidConfig -> UiText.res(R.string.integrations_error_ai_config)
    AiError.Disabled -> UiText.res(R.string.integrations_error_ai_disabled)
    AiError.ImageProcessing -> UiText.res(R.string.integrations_error_ai_image)
    is AiError.Http -> UiText.res(R.string.integrations_error_ai_http, code)
    AiError.Network -> UiText.res(R.string.integrations_error_network)
    AiError.EmptyResponse -> UiText.res(R.string.integrations_error_ai_empty)
    AiError.InvalidJson -> UiText.res(R.string.integrations_error_ai_json)
}

/** Maps any of this feature's failure throwables to a localized message. */
fun Throwable.uiMessage(fallback: UiText = UiText.res(R.string.integrations_error_generic)): UiText = when (this) {
    is io.github.submark.feature.integrations.data.popular.CatalogFetchException -> error.toUiText()
    is io.github.submark.feature.integrations.data.itunes.ItunesException -> error.toUiText()
    is io.github.submark.feature.integrations.data.rawg.RawgException -> error.toUiText()
    is io.github.submark.feature.integrations.data.icons.IconPackException -> error.toUiText()
    is io.github.submark.feature.integrations.data.ai.AiException -> error.toUiText()
    else -> fallback
}

@Composable
fun Throwable.errorText(): String = uiMessage().asString()
