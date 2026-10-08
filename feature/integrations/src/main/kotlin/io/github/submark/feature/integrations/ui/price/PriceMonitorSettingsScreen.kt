package io.github.submark.feature.integrations.ui.price

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.core.ui.component.SettingsGroup
import io.github.submark.core.ui.component.SettingsSwitchRow
import io.github.submark.core.ui.component.SettingsValueRow
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.feature.integrations.R
import io.github.submark.feature.integrations.data.CountryCatalog
import io.github.submark.feature.integrations.ui.common.CountryPickerSheet

@Composable
fun PriceMonitorSettingsRoute(
    onBack: () -> Unit,
    viewModel: PriceMonitorSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    SnackbarEffect(viewModel.messages, snackbarHost)
    PriceMonitorSettingsScreen(
        state = state,
        snackbarHost = snackbarHost,
        onBack = onBack,
        onEnabled = viewModel::setEnabled,
        onInterval = viewModel::setInterval,
        onThreshold = viewModel::setThreshold,
        onStoreRegion = viewModel::setStoreRegion,
        onRunNow = viewModel::runNow,
        onTestNotification = viewModel::sendTestNotification,
        onAskClear = viewModel::askClear,
        onClearHistory = viewModel::clearHistory,
    )
}

@Composable
fun PriceMonitorSettingsScreen(
    state: PriceMonitorSettingsUiState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onInterval: (Int) -> Unit,
    onThreshold: (Int) -> Unit,
    onStoreRegion: (String) -> Unit,
    onRunNow: () -> Unit,
    onTestNotification: () -> Unit,
    onAskClear: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
) {
    val showRegionPicker = remember { mutableStateOf(false) }

    Scaffold(
        topBar = { SubMarkTopAppBar(title = stringResource(R.string.integrations_price_settings_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()),
        ) {
            SettingsGroup(title = stringResource(R.string.integrations_price_group_monitoring)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.integrations_price_master),
                    subtitle = stringResource(R.string.integrations_price_master_hint),
                    checked = state.enabled,
                    onCheckedChange = onEnabled,
                )
            }
            SettingsGroup(title = stringResource(R.string.integrations_price_group_schedule)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.integrations_price_interval),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row {
                        PriceMonitorSettingsUiState.INTERVAL_OPTIONS.forEach { hours ->
                            FilterChip(
                                selected = state.intervalHours == hours,
                                onClick = { onInterval(hours) },
                                label = { Text(stringResource(R.string.integrations_price_interval_hours, hours)) },
                                modifier = Modifier.padding(end = 8.dp, top = 4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.integrations_price_threshold, state.thresholdPercent),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Slider(
                        value = state.thresholdPercent.toFloat(),
                        onValueChange = { onThreshold(it.toInt()) },
                        valueRange = 1f..50f,
                    )
                    SettingsValueRow(
                        title = stringResource(R.string.integrations_price_store_region),
                        value = "${CountryCatalog.nameOf(state.storeRegion)} (${state.storeRegion})",
                        onClick = { showRegionPicker.value = true },
                    )
                }
            }
            SettingsGroup(title = stringResource(R.string.integrations_price_group_notifications)) {
                SettingsValueRow(
                    title = stringResource(R.string.integrations_price_notification_status),
                    value = stringResource(
                        if (state.notificationsAllowed) {
                            R.string.integrations_price_notifications_ready
                        } else {
                            R.string.integrations_price_notifications_denied
                        },
                    ),
                )
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Button(onClick = onTestNotification, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.integrations_price_test_notification))
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRunNow, enabled = state.enabled, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.integrations_price_run_now))
                    }
                }
            }
            SettingsGroup(title = stringResource(R.string.integrations_price_group_history)) {
                SettingsValueRow(
                    title = stringResource(R.string.integrations_price_history_count),
                    value = state.recordCount.toString(),
                )
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Button(onClick = { onAskClear(true) }, enabled = state.recordCount > 0, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.integrations_price_clear_history))
                    }
                }
            }
        }
    }

    if (showRegionPicker.value) {
        CountryPickerSheet(
            selectedCode = state.storeRegion,
            onSelect = {
                onStoreRegion(it)
                showRegionPicker.value = false
            },
            onDismiss = { showRegionPicker.value = false },
        )
    }
    if (state.confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.integrations_price_clear_history),
            message = stringResource(R.string.integrations_price_clear_history_message),
            onConfirm = onClearHistory,
            onDismiss = { onAskClear(false) },
            destructive = true,
        )
    }
}
