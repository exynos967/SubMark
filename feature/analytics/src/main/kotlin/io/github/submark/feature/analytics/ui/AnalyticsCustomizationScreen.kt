package io.github.submark.feature.analytics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.submark.core.data.settings.AnalyticsComponent
import io.github.submark.core.data.settings.ComponentSetting
import io.github.submark.core.data.settings.SpendingMode
import io.github.submark.core.data.settings.SummaryPeriod
import io.github.submark.core.ui.component.DragHandleIcon
import io.github.submark.core.ui.component.ReorderableItemsColumn
import io.github.submark.core.ui.component.SegmentedTabs
import io.github.submark.core.ui.component.SettingsGroup
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.feature.analytics.R

@Composable
fun AnalyticsCustomizationRoute(
    onBack: () -> Unit,
    viewModel: AnalyticsCustomizationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    AnalyticsCustomizationScreen(
        uiState = uiState,
        onBack = onBack,
        onVisibleChange = viewModel::setVisible,
        onMove = viewModel::move,
        onPreset = viewModel::applyPreset,
        onReset = viewModel::reset,
        onDefaultPeriodChange = viewModel::setDefaultPeriod,
    )
}

@Composable
fun AnalyticsCustomizationScreen(
    uiState: AnalyticsCustomizationUiState,
    onBack: () -> Unit,
    onVisibleChange: (SpendingMode, AnalyticsComponent, Boolean) -> Unit,
    onMove: (SpendingMode, Int, Int) -> Unit,
    onPreset: (SpendingMode, AnalyticsPreset) -> Unit,
    onReset: (SpendingMode) -> Unit,
    onDefaultPeriodChange: (SummaryPeriod) -> Unit,
) {
    var mode by remember { mutableStateOf(SpendingMode.SUBSCRIPTIONS) }
    val components = if (mode == SpendingMode.SUBSCRIPTIONS) uiState.subscriptionComponents else uiState.lifetimeComponents

    Scaffold(
        topBar = { SubMarkTopAppBar(title = stringResource(R.string.analytics_custom_title), onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SegmentedTabs(
                    items = listOf(SpendingMode.SUBSCRIPTIONS, SpendingMode.LIFETIME),
                    selected = mode,
                    onSelect = { mode = it },
                ) { m ->
                    stringResource(if (m == SpendingMode.SUBSCRIPTIONS) R.string.analytics_custom_subscriptions_section else R.string.analytics_custom_lifetime_section)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.analytics_custom_presets),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { onPreset(mode, AnalyticsPreset.MINIMAL) }) {
                        Text(stringResource(R.string.analytics_custom_preset_minimal))
                    }
                    OutlinedButton(onClick = { onPreset(mode, AnalyticsPreset.DETAILED) }) {
                        Text(stringResource(R.string.analytics_custom_preset_detailed))
                    }
                    TextButton(onClick = { onReset(mode) }) {
                        Text(stringResource(R.string.analytics_custom_reset))
                    }
                }
            }

            item {
                ReorderableItemsColumn(
                    items = components,
                    key = { it.id },
                    onMove = { from, to -> onMove(mode, from, to) },
                ) { item, _, handle ->
                    ComponentRow(
                        item = item,
                        onVisibleChange = { visible -> onVisibleChange(mode, item.id, visible) },
                        dragHandle = handle,
                    )
                }
            }

            item {
                SettingsGroup(title = stringResource(R.string.analytics_custom_default_period)) {
                    SegmentedTabs(
                        items = listOf(SummaryPeriod.MONTH, SummaryPeriod.QUARTER, SummaryPeriod.YEAR),
                        selected = uiState.defaultPeriod,
                        onSelect = onDefaultPeriodChange,
                        modifier = Modifier.padding(12.dp),
                    ) { p ->
                        stringResource(
                            when (p) {
                                SummaryPeriod.MONTH -> R.string.analytics_period_month
                                SummaryPeriod.QUARTER -> R.string.analytics_period_quarter
                                SummaryPeriod.YEAR -> R.string.analytics_period_year
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComponentRow(
    item: ComponentSetting<AnalyticsComponent>,
    onVisibleChange: (Boolean) -> Unit,
    dragHandle: Modifier,
) {
    val forcedVisible = item.id == AnalyticsComponent.FINANCIAL_OVERVIEW
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DragHandleIcon(dragHandle)
        Text(
            componentLabel(item.id),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = forcedVisible || item.visible,
            onCheckedChange = { if (!forcedVisible) onVisibleChange(it) },
            enabled = !forcedVisible,
        )
    }
}

@Composable
private fun componentLabel(id: AnalyticsComponent): String = stringResource(
    when (id) {
        AnalyticsComponent.FINANCIAL_OVERVIEW -> R.string.analytics_component_financial_overview
        AnalyticsComponent.TREND -> R.string.analytics_component_trend
        AnalyticsComponent.HEATMAP -> R.string.analytics_component_heatmap
        AnalyticsComponent.CATEGORY -> R.string.analytics_component_category
        AnalyticsComponent.MULTI_DIMENSION -> R.string.analytics_component_multi_dimension
        AnalyticsComponent.STORED_VALUE -> R.string.analytics_component_stored_value
    },
)
