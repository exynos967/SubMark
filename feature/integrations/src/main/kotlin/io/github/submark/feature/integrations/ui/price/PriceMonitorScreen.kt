package io.github.submark.feature.integrations.ui.price

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.model.PriceRecord
import io.github.submark.core.ui.chart.ChartEntry
import io.github.submark.core.ui.chart.LineBarChart
import io.github.submark.core.ui.chart.LineBarStyle
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.SettingsGroup
import io.github.submark.core.ui.component.SettingsSwitchRow
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.format.MoneyFormatter
import io.github.submark.feature.integrations.R
import io.github.submark.feature.integrations.data.CountryCatalog
import io.github.submark.feature.integrations.data.price.PriceCheck
import io.github.submark.feature.integrations.ui.common.MultiCountryPickerSheet
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PriceMonitorRoute(
    onBack: () -> Unit,
    viewModel: PriceMonitorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    io.github.submark.core.ui.util.SnackbarEffect(viewModel.messages, snackbarHost)
    PriceMonitorScreen(
        state = state,
        snackbarHost = snackbarHost,
        onBack = onBack,
        onToggle = viewModel::setEnabled,
        onCheckNow = viewModel::checkNow,
        onAddRegion = viewModel::addRegion,
        onRemoveRegion = viewModel::removeRegion,
        onRegionPicker = viewModel::setRegionPicker,
        onToggleRegion = viewModel::toggleRegionCollapsed,
    )
}

@Composable
fun PriceMonitorScreen(
    state: PriceMonitorUiState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onCheckNow: () -> Unit,
    onAddRegion: (String) -> Unit,
    onRemoveRegion: (String) -> Unit,
    onRegionPicker: (Boolean) -> Unit,
    onToggleRegion: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            SubMarkTopAppBar(
                title = stringResource(R.string.integrations_price_title),
                subtitle = state.subscription?.name,
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.subscription == null -> EmptyState(
                title = stringResource(R.string.integrations_error_not_found),
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
            !state.eligible -> EmptyState(
                title = stringResource(
                    if (state.needsAppStoreId) {
                        R.string.integrations_price_needs_app_store_id
                    } else {
                        R.string.integrations_price_wishlist_only
                    },
                ),
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
            else -> Content(state, padding, onToggle, onCheckNow, onAddRegion, onRemoveRegion, onRegionPicker, onToggleRegion)
        }
    }

    if (state.showRegionPicker) {
        MultiCountryPickerSheet(
            selectedCodes = state.monitor?.regions.orEmpty().map { it.uppercase() }.toSet(),
            onToggle = { code ->
                val has = state.monitor?.regions.orEmpty().any { it.equals(code, true) }
                if (has) onRemoveRegion(code) else onAddRegion(code)
            },
            onDismiss = { onRegionPicker(false) },
        )
    }
}

@Composable
private fun Content(
    state: PriceMonitorUiState,
    padding: PaddingValues,
    onToggle: (Boolean) -> Unit,
    onCheckNow: () -> Unit,
    onAddRegion: (String) -> Unit,
    onRemoveRegion: (String) -> Unit,
    onRegionPicker: (Boolean) -> Unit,
    onToggleRegion: (String) -> Unit,
) {
    val monitor = state.monitor
    LazyColumn(
        modifier = Modifier.padding(padding).fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            SettingsGroup {
                SettingsSwitchRow(
                    title = stringResource(R.string.integrations_price_enable),
                    subtitle = if (!state.masterEnabled) {
                        stringResource(R.string.integrations_price_master_off_hint)
                    } else if (monitor?.lastCheckAt != null) {
                        stringResource(R.string.integrations_price_last_check, monitor.lastCheckAt.toString().take(16).replace('T', ' '))
                    } else {
                        stringResource(R.string.integrations_price_never_checked)
                    },
                    checked = monitor?.enabled == true,
                    onCheckedChange = onToggle,
                    enabled = state.masterEnabled,
                )
            }
            if (!state.masterEnabled) {
                Text(
                    stringResource(R.string.integrations_price_master_off_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp),
                )
            }
        }
        if (monitor != null) {
            item {
                SectionCard(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    title = stringResource(R.string.integrations_price_regions),
                    actionLabel = stringResource(R.string.integrations_price_add_region),
                    onAction = { onRegionPicker(true) },
                ) {
                    monitor.regions.forEach { region ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "${CountryCatalog.nameOf(region)} (${region.uppercase()})",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            IconButton(onClick = { onRemoveRegion(region) }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.integrations_price_remove_region),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onCheckNow,
                        enabled = monitor.enabled && !state.checking,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(18.dp).height(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.integrations_price_check_now))
                    }
                }
            }
            val stats = state.regionStats
            if (stats.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.integrations_price_no_history),
                        message = stringResource(R.string.integrations_price_no_history_hint),
                    )
                }
            } else {
                items(stats, key = { it.region }) { regionStats ->
                    RegionSection(
                        stats = regionStats,
                        records = state.records.filter { it.region == regionStats.region },
                        collapsed = regionStats.region in state.collapsedRegions,
                        onToggle = { onToggleRegion(regionStats.region) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RegionSection(
    stats: PriceCheck.RegionStats,
    records: List<PriceRecord>,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    var selected by remember(stats.region) { mutableStateOf<Int?>(null) }
    SectionCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${CountryCatalog.nameOf(stats.region)} (${stats.region})",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(
                        R.string.integrations_price_stats,
                        MoneyFormatter.format(stats.current, stats.currencyCode),
                        MoneyFormatter.format(stats.lowest, stats.currencyCode),
                        MoneyFormatter.format(stats.highest, stats.currencyCode),
                        MoneyFormatter.format(stats.average, stats.currencyCode),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                if (collapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                contentDescription = stringResource(
                    if (collapsed) R.string.integrations_cd_expand else R.string.integrations_cd_collapse,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!collapsed) {
            Spacer(Modifier.height(12.dp))
            val sorted = records.sortedBy { it.checkedAt }
            LineBarChart(
                entries = sorted.map {
                    ChartEntry(
                        label = it.checkedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd")),
                        value = it.price.toDouble(),
                    )
                },
                style = LineBarStyle.LINE,
                selectedIndex = selected,
                onSelect = { selected = it },
                valueLabel = { MoneyFormatter.format(java.math.BigDecimal.valueOf(it), stats.currencyCode) },
            )
            selected?.let { idx ->
                sorted.getOrNull(idx)?.let { record ->
                    Text(
                        stringResource(
                            R.string.integrations_price_selected_point,
                            MoneyFormatter.format(record.price, record.currencyCode),
                            record.checkedAt.toString().take(16).replace('T', ' '),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            records.take(20).forEachIndexed { index, record ->
                if (index > 0) HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(
                        record.checkedAt.toString().take(16).replace('T', ' '),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        record.formattedPrice ?: MoneyFormatter.format(record.price, record.currencyCode),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
