package io.github.submark.feature.overview.ui.customize

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.submark.core.data.settings.ComponentSetting
import io.github.submark.core.data.settings.ModernOverviewComponent
import io.github.submark.core.ui.component.DragHandleIcon
import io.github.submark.core.ui.component.ReorderableItemsColumn
import io.github.submark.feature.overview.ui.customize.OverviewPreset
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.theme.SubMarkTheme
import io.github.submark.feature.overview.R

@Composable
fun OverviewCustomizationRoute(
    onBack: () -> Unit,
    viewModel: OverviewCustomizationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    OverviewCustomizationScreen(
        uiState = uiState,
        onBack = onBack,
        onToggleVisible = viewModel::toggleVisible,
        onMove = viewModel::move,
        onApplyPreset = viewModel::applyPreset,
        onResetOrder = viewModel::resetOrder,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewCustomizationScreen(
    uiState: OverviewCustomizationUiState,
    onBack: () -> Unit,
    onToggleVisible: (ComponentSetting<ModernOverviewComponent>, Boolean) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onApplyPreset: (OverviewPreset) -> Unit,
    onResetOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SubMarkTopAppBar(
                title = stringResource(R.string.overview_customize_title),
                subtitle = stringResource(R.string.overview_customize_subtitle),
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            // ── Presets ──────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PresetButton(
                        label = stringResource(R.string.overview_preset_complete),
                        selected = false,
                        onClick = { onApplyPreset(OverviewPreset.COMPLETE) },
                        modifier = Modifier.weight(1f),
                    )
                    PresetButton(
                        label = stringResource(R.string.overview_preset_essential),
                        selected = false,
                        onClick = { onApplyPreset(OverviewPreset.ESSENTIAL) },
                        modifier = Modifier.weight(1f),
                    )
                    PresetButton(
                        label = stringResource(R.string.overview_reset_order),
                        selected = false,
                        onClick = onResetOrder,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Empty warning ──────────────────────────────────────────────
            if (uiState.items.none { it.visible }) {
                item {
                    Text(
                        stringResource(R.string.overview_at_least_one),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            // ── Component list with drag & drop ────────────────────────────
            item {
                val items = uiState.items
                ReorderableItemsColumn(
                    items = items,
                    key = { it.id.toString() },
                    onMove = { f, t -> onMove(f, t) },
                    modifier = Modifier.fillMaxWidth(),
                ) { item, _isDragging, dragHandle ->
                    ComponentRow(
                        item = item,
                        onToggle = { visible -> onToggleVisible(item, visible) },
                        dragHandle = dragHandle,
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ComponentRow(
    item: ComponentSetting<ModernOverviewComponent>,
    onToggle: (Boolean) -> Unit,
    dragHandle: Modifier,
    modifier: Modifier = Modifier,
) {
    val componentName = componentName(item.id)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(36.dp)) {
                DragHandleIcon(dragHandle = dragHandle)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = componentName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = item.visible, onCheckedChange = { checked -> onToggle(checked) })
        }
    }
}

@Composable
private fun componentName(id: ModernOverviewComponent): String = when (id) {
    ModernOverviewComponent.SPENDING_HERO -> stringResource(R.string.overview_component_spendingHero)
    ModernOverviewComponent.COMING_UP -> stringResource(R.string.overview_component_comingUp)
    ModernOverviewComponent.PAYMENT_SCHEDULE -> stringResource(R.string.overview_component_paymentSchedule)
    ModernOverviewComponent.RECENT_PAYMENTS -> stringResource(R.string.overview_component_recentPayments)
    ModernOverviewComponent.WALLET_BALANCES -> stringResource(R.string.overview_component_walletBalances)
    ModernOverviewComponent.WISHLIST_PRICES -> stringResource(R.string.overview_component_wishlistPrices)
    ModernOverviewComponent.MY_SUBSCRIPTIONS -> stringResource(R.string.overview_component_mySubscriptions)
    ModernOverviewComponent.SPENDING_INSIGHTS -> stringResource(R.string.overview_component_spendingInsights)
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
