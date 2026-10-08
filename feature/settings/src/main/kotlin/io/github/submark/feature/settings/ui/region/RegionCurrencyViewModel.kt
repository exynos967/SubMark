package io.github.submark.feature.settings.ui.region

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.currency.CurrencyRepository
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.onFailure
import io.github.submark.core.data.settings.AppSettings
import io.github.submark.core.data.settings.MoneySettings
import io.github.submark.core.data.settings.PopularRegionScope
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.model.Currency
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.settings.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegionCurrencyUiState(
    val loading: Boolean = true,
    val currencies: List<Currency> = emptyList(),
    val money: MoneySettings = MoneySettings(),
    val storeRegion: String? = null,
    val regionScope: PopularRegionScope = PopularRegionScope.LOCAL,
) {
    val defaultCurrency: Currency? get() = currencies.firstOrNull { it.code == money.defaultCurrencyCode }
}

@HiltViewModel
class RegionCurrencyViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val currencies: CurrencyRepository,
) : ViewModel() {

    private val snackbar = Channel<SnackbarMessage>(Channel.BUFFERED)
    val messages: Flow<SnackbarMessage> = snackbar.receiveAsFlow()

    val state: StateFlow<RegionCurrencyUiState> =
        combine(settings.settings, currencies.observeCurrencies()) { s, list ->
            RegionCurrencyUiState(
                loading = false,
                currencies = list,
                money = s.money,
                storeRegion = s.integrations.storeRegion,
                regionScope = s.integrations.popularRegionScope,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RegionCurrencyUiState())

    fun setDefaultCurrency(code: String) {
        viewModelScope.launch {
            currencies.setDefault(code).onFailure { error ->
                val msg = if (error is DataError.NotFound) R.string.settings_region_currency_missing else R.string.settings_error_generic
                snackbar.send(SnackbarMessage(UiText.res(msg)))
            }
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settings.update(transform) }
    }

    companion object {
        /** App Store storefronts offered for price lookups (ISO 3166 alpha-2, lowercase). null = device country. */
        val STORE_REGIONS: List<String?> = listOf(null, "us", "cn", "hk", "tw", "jp", "kr", "gb", "de", "fr", "ca", "au", "in", "tr", "ng", "br")
    }
}
