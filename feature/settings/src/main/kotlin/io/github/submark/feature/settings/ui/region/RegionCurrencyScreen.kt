package io.github.submark.feature.settings.ui.region

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.data.settings.AppSettings
import io.github.submark.core.data.settings.PopularRegionScope
import io.github.submark.core.ui.component.CurrencyPickerSheet
import io.github.submark.core.ui.component.SettingsGroup
import io.github.submark.core.ui.component.SettingsNavRow
import io.github.submark.core.ui.component.SettingsSwitchRow
import io.github.submark.core.ui.component.SettingsValueRow
import io.github.submark.core.ui.navigation.BudgetSettingsRoute
import io.github.submark.core.ui.navigation.CurrencyManagementRoute
import io.github.submark.core.ui.navigation.HistoricalRatesRoute
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.feature.settings.R
import io.github.submark.feature.settings.ui.common.ChoiceDialog
import io.github.submark.feature.settings.ui.common.SettingsPage
import io.github.submark.feature.settings.ui.common.labelRes
import java.util.Locale

@Composable
internal fun RegionCurrencyScreenRoute(
    onBack: () -> Unit,
    onNavigate: (Any) -> Unit,
    viewModel: RegionCurrencyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    SnackbarEffect(viewModel.messages, snackbarHostState)
    RegionCurrencyScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onNavigate = onNavigate,
        onDefaultCurrency = viewModel::setDefaultCurrency,
        onUpdate = viewModel::update,
    )
}

private enum class RegionDialog { CURRENCY, STORE, SCOPE }

@Composable
internal fun RegionCurrencyScreen(
    state: RegionCurrencyUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onNavigate: (Any) -> Unit,
    onDefaultCurrency: (String) -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<RegionDialog?>(null) }
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    SettingsPage(
        title = stringResource(R.string.settings_region_title),
        onBack = onBack,
        loading = state.loading,
        snackbarHostState = snackbarHostState,
    ) {
        val money = state.money
        SettingsGroup(title = stringResource(R.string.settings_region_currency_group)) {
            SettingsValueRow(
                title = stringResource(R.string.settings_region_default_currency),
                subtitle = stringResource(R.string.settings_region_default_currency_desc),
                value = state.defaultCurrency?.let { "${it.code} · ${it.symbol}" } ?: money.defaultCurrencyCode,
                icon = Icons.Rounded.Payments,
                onClick = { dialog = RegionDialog.CURRENCY },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_region_show_conversion),
                subtitle = stringResource(R.string.settings_region_show_conversion_desc),
                icon = Icons.Rounded.SwapHoriz,
                checked = money.showCurrencyConversion,
                onCheckedChange = { on -> onUpdate { it.copy(money = it.money.copy(showCurrencyConversion = on)) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_region_hide_decimals),
                subtitle = stringResource(R.string.settings_region_hide_decimals_desc),
                icon = Icons.Rounded.Savings,
                checked = money.hideDecimalPlaces,
                onCheckedChange = { on -> onUpdate { it.copy(money = it.money.copy(hideDecimalPlaces = on)) } },
            )
            SettingsNavRow(stringResource(R.string.settings_region_manage_currencies), { onNavigate(CurrencyManagementRoute) }, icon = Icons.Rounded.CurrencyExchange)
            SettingsNavRow(stringResource(R.string.settings_region_historical_rates), { onNavigate(HistoricalRatesRoute) }, icon = Icons.Rounded.History)
            SettingsNavRow(stringResource(R.string.settings_region_budget), { onNavigate(BudgetSettingsRoute) }, icon = Icons.Rounded.AccountBalanceWallet)
        }
        SettingsGroup(
            title = stringResource(R.string.settings_region_store_group),
            footer = stringResource(R.string.settings_region_store_footer),
        ) {
            SettingsValueRow(
                title = stringResource(R.string.settings_region_store),
                value = regionLabel(state.storeRegion, locale),
                icon = Icons.Rounded.Storefront,
                onClick = { dialog = RegionDialog.STORE },
            )
            SettingsValueRow(
                title = stringResource(R.string.settings_region_scope),
                value = stringResource(state.regionScope.labelRes),
                icon = Icons.Rounded.TravelExplore,
                onClick = { dialog = RegionDialog.SCOPE },
            )
        }

        when (dialog) {
            RegionDialog.CURRENCY -> CurrencyPickerSheet(
                currencies = state.currencies,
                selectedCode = money.defaultCurrencyCode,
                defaultCode = money.defaultCurrencyCode,
                title = stringResource(R.string.settings_region_default_currency),
                onSelect = { onDefaultCurrency(it.code); dialog = null },
                onDismiss = { dialog = null },
            )
            RegionDialog.STORE -> ChoiceDialog(
                title = stringResource(R.string.settings_region_store),
                options = RegionCurrencyViewModel.STORE_REGIONS,
                selected = state.storeRegion,
                label = { regionLabel(it, locale) },
                onSelect = { code -> onUpdate { it.copy(integrations = it.integrations.copy(storeRegion = code)) } },
                onDismiss = { dialog = null },
            )
            RegionDialog.SCOPE -> ChoiceDialog(
                title = stringResource(R.string.settings_region_scope),
                options = PopularRegionScope.entries,
                selected = state.regionScope,
                label = { stringResource(it.labelRes) },
                onSelect = { scope -> onUpdate { it.copy(integrations = it.integrations.copy(popularRegionScope = scope)) } },
                onDismiss = { dialog = null },
            )
            null -> Unit
        }
    }
}

@Composable
private fun regionLabel(code: String?, locale: Locale): String =
    if (code == null) stringResource(R.string.settings_region_store_device)
    else "${Locale("", code.uppercase(Locale.ROOT)).getDisplayCountry(locale)} (${code.uppercase(Locale.ROOT)})"

