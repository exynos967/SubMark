package io.github.submark.feature.integrations.panel.ui.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.data.settings.ServicePanelTab
import io.github.submark.core.model.ApiServiceType
import io.github.submark.core.model.ServiceType
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.SegmentedTabs
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.theme.SubMarkTheme
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.core.ui.util.colorFromHex
import io.github.submark.feature.integrations.R
import io.github.submark.feature.integrations.panel.data.BudgetSnapshot
import io.github.submark.feature.integrations.panel.data.BudgetStatus
import io.github.submark.feature.integrations.panel.data.ClashSnapshot
import io.github.submark.feature.integrations.panel.data.EmbySnapshot
import io.github.submark.feature.integrations.panel.data.budget.QuotaKind
import io.github.submark.feature.integrations.panel.ui.displayName
import io.github.submark.feature.integrations.panel.ui.formatBudgetAmount
import io.github.submark.feature.integrations.panel.ui.formatBytes
import io.github.submark.feature.integrations.panel.ui.formatCount
import io.github.submark.feature.integrations.panel.ui.formatInstant
import io.github.submark.feature.integrations.panel.ui.labelRes
import io.github.submark.feature.integrations.panel.ui.messageRes
import io.github.submark.feature.integrations.panel.ui.relativeTimeRes
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.temporal.ChronoUnit

@Composable
fun PanelScreenRoute(
    onAddService: () -> Unit,
    onAddBudget: () -> Unit,
    onEditService: (String) -> Unit,
    onEditBudget: (String) -> Unit,
    viewModel: PanelViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val messages = remember(viewModel) { viewModel.messages.map { SnackbarMessage(io.github.submark.core.ui.format.UiText.res(it.messageRes())) } }
    SnackbarEffect(messages, snackbarHostState)

    PanelScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onTabSelect = viewModel::selectTab,
        onRefreshAll = viewModel::refreshAll,
        onAdd = { if (state.tab == ServicePanelTab.SERVICE) onAddService() else onAddBudget() },
        onRefreshBudget = viewModel::refreshBudget,
        onRefreshService = { id -> viewModel.refreshService(id, manual = true) },
        onToggleBudget = viewModel::setBudgetEnabled,
        onToggleService = viewModel::setServiceEnabled,
        onEditBudget = onEditBudget,
        onEditService = onEditService,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelScreen(
    state: PanelUiState,
    snackbarHostState: SnackbarHostState,
    onTabSelect: (ServicePanelTab) -> Unit,
    onRefreshAll: () -> Unit,
    onAdd: () -> Unit,
    onRefreshBudget: (String) -> Unit,
    onRefreshService: (String) -> Unit,
    onToggleBudget: (String, Boolean) -> Unit,
    onToggleService: (String, Boolean) -> Unit,
    onEditBudget: (String) -> Unit,
    onEditService: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            SubMarkTopAppBar(
                title = stringResource(R.string.panel_title),
                subtitle = stringResource(R.string.panel_subtitle),
                actions = {
                    IconButton(onClick = onAdd) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = stringResource(
                                if (state.tab == ServicePanelTab.SERVICE) R.string.panel_add_service else R.string.panel_add_budget
                            ),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SegmentedTabs(
                items = ServicePanelTab.entries,
                selected = state.tab,
                onSelect = onTabSelect,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                stringResource(if (it == ServicePanelTab.SERVICE) R.string.panel_tab_services else R.string.panel_tab_api_budget)
            }
            when {
                state.loading -> LoadingState()
                else -> PullToRefreshBox(
                    isRefreshing = state.refreshingAll,
                    onRefresh = onRefreshAll,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (state.tab) {
                        ServicePanelTab.SERVICE -> ServiceList(state, onRefreshService, onToggleService, onEditService, onAdd)
                        ServicePanelTab.API_BUDGET -> BudgetList(state, onRefreshBudget, onToggleBudget, onEditBudget, onAdd)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceList(
    state: PanelUiState,
    onRefresh: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onEdit: (String) -> Unit,
    onAdd: () -> Unit,
) {
    if (state.services.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            EmptyState(
                title = stringResource(R.string.panel_empty_services_title),
                message = stringResource(R.string.panel_empty_services_message),
                icon = Icons.Rounded.CloudQueue,
                actionLabel = stringResource(R.string.panel_add_service),
                onAction = onAdd,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        return
    }
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.services, key = { it.connection.id }) { item ->
            ServiceCard(item, state.nowEpochSeconds, onRefresh, onToggle, onEdit)
        }
    }
}

@Composable
private fun BudgetList(
    state: PanelUiState,
    onRefresh: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onEdit: (String) -> Unit,
    onAdd: () -> Unit,
) {
    if (state.budgets.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            EmptyState(
                title = stringResource(R.string.panel_empty_budget_title),
                message = stringResource(R.string.panel_empty_budget_message),
                icon = Icons.Rounded.Sync,
                actionLabel = stringResource(R.string.panel_add_budget),
                onAction = onAdd,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        return
    }
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.budgets, key = { it.config.id }) { item ->
            BudgetCard(item, state.nowEpochSeconds, onRefresh, onToggle, onEdit)
        }
    }
}

// ---------------------------------------------------------------------------
// Card chrome shared by both lists
// ---------------------------------------------------------------------------

@Composable
private fun CardHeader(
    title: String,
    typeLabel: String,
    enabled: Boolean,
    refreshing: Boolean,
    cooldownSeconds: Int,
    accentHex: String?,
    onRefresh: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val accent = colorFromHex(accentHex)
        if (accent != null) {
            Box(Modifier.size(12.dp).background(accent, CircleShape))
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                typeLabel + " · " + stringResource(if (enabled) R.string.panel_status_active else R.string.panel_status_paused),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (cooldownSeconds > 0) {
            Text(
                stringResource(R.string.panel_refresh_wait, cooldownSeconds),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        IconButton(onClick = onRefresh, enabled = !refreshing && cooldownSeconds == 0) {
            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.panel_refresh))
        }
        TextButton(onClick = onEdit) { Text(stringResource(R.string.panel_edit)) }
        androidx.compose.material3.Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun CardFooter(lastUpdated: Instant?, lastError: String?, nowEpochSeconds: Long) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column {
        lastUpdated?.let {
            val (res, arg) = relativeTimeRes(it, nowEpochSeconds)
            val rel = if (arg == null) stringResource(res) else stringResource(res, arg)
            Text(
                stringResource(R.string.panel_last_updated, rel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        lastError?.let { reason ->
            val label = runCatching {
                io.github.submark.feature.integrations.panel.data.PanelErrorReason.valueOf(reason)
            }.getOrNull()?.let { r -> stringResource(r.messageRes()) } ?: reason
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun StatusChip(status: BudgetStatus) {
    val (color, container) = when (status) {
        BudgetStatus.NORMAL -> SubMarkTheme.extendedColors.success to SubMarkTheme.extendedColors.successContainer
        BudgetStatus.WARNING -> SubMarkTheme.extendedColors.warning to SubMarkTheme.extendedColors.warningContainer
        BudgetStatus.OVER -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
    }
    Text(
        stringResource(status.labelRes()),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(container, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

// ---------------------------------------------------------------------------
// Service cards
// ---------------------------------------------------------------------------

@Composable
private fun ServiceCard(
    item: ServiceItem,
    nowEpochSeconds: Long,
    onRefresh: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onEdit: (String) -> Unit,
) {
    val connection = item.connection
    SectionCard {
        CardHeader(
            title = connection.name,
            typeLabel = stringResource(
                if (connection.type == ServiceType.CLASH) R.string.panel_service_type_clash else R.string.panel_service_type_emby
            ),
            enabled = connection.enabled,
            refreshing = item.refreshing,
            cooldownSeconds = item.refreshCooldownSeconds,
            accentHex = connection.colorHex,
            onRefresh = { onRefresh(connection.id) },
            onToggle = { onToggle(connection.id, it) },
            onEdit = { onEdit(connection.id) },
        )
        when (val snapshot = item.snapshot) {
            null -> {
                if (!connection.enabled) {
                    // paused: keep the last known state empty
                } else if (connection.lastError == null) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(
                        stringResource(
                            if (connection.type == ServiceType.CLASH) R.string.panel_clash_no_data_title
                            else R.string.panel_emby_no_data_title
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(
                            if (connection.type == ServiceType.CLASH) R.string.panel_clash_no_data_message
                            else R.string.panel_emby_no_data_message
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is ClashSnapshot -> {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                ClashBody(snapshot)
            }
            is EmbySnapshot -> {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                EmbyBody(snapshot)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        CardFooter(connection.lastRefreshAt, connection.lastError, nowEpochSeconds)
    }
}

@Composable
private fun ClashBody(snapshot: ClashSnapshot) {
    if (!snapshot.hasTrafficInfo) {
        Text(stringResource(R.string.panel_clash_no_traffic_title), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.panel_clash_no_traffic_message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val total = snapshot.totalBytes
    val percent = snapshot.usagePercent
    Text(stringResource(R.string.panel_clash_traffic_usage), style = MaterialTheme.typography.labelLarge)
    if (total != null && percent != null) {
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
        Text(
            stringResource(R.string.panel_clash_used_of_total, formatBytes(snapshot.usedBytes), formatBytes(total)),
            style = MaterialTheme.typography.bodyMedium,
        )
        snapshot.remainingBytes?.let {
            Text(
                stringResource(R.string.panel_clash_remaining) + ": " + formatBytes(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Text(formatBytes(snapshot.usedBytes), style = MaterialTheme.typography.bodyMedium)
    }
    snapshot.expireAt?.let { expiry ->
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.panel_clash_expiry, formatInstant(expiry)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (snapshot.expired) {
                Text(
                    stringResource(R.string.panel_clash_expired),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                val days = ChronoUnit.DAYS.between(snapshot.fetchedAt, expiry)
                Text(
                    stringResource(R.string.panel_clash_days_left, days.coerceAtLeast(0)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmbyBody(snapshot: EmbySnapshot) {
    InfoRow(stringResource(R.string.panel_emby_server), snapshot.serverName)
    InfoRow(stringResource(R.string.panel_emby_version), snapshot.version)
    InfoRow(stringResource(R.string.panel_emby_system), snapshot.operatingSystem)
    snapshot.userName?.let {
        InfoRow(
            stringResource(R.string.panel_emby_user),
            it + " · " + stringResource(if (snapshot.isAdmin) R.string.panel_emby_admin else R.string.panel_emby_regular_user),
        )
    }
    snapshot.lastActiveAt?.let {
        InfoRow(stringResource(R.string.panel_emby_last_active, formatInstant(it)), null)
    }
    snapshot.latencyMs?.let {
        Text(
            stringResource(R.string.panel_emby_latency, it),
            style = MaterialTheme.typography.bodySmall,
            color = SubMarkTheme.extendedColors.success,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    if (value == null) return
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------------------------------------------------------------------
// Budget cards
// ---------------------------------------------------------------------------

@Composable
private fun BudgetCard(
    item: BudgetItem,
    nowEpochSeconds: Long,
    onRefresh: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onEdit: (String) -> Unit,
) {
    val config = item.config
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(config.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        config.serviceType.displayName(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StatusChip(item.computation.status)
                }
            }
            IconButton(onClick = { onRefresh(config.id) }, enabled = !item.refreshing) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.panel_refresh))
            }
            TextButton(onClick = { onEdit(config.id) }) { Text(stringResource(R.string.panel_edit)) }
            androidx.compose.material3.Switch(checked = config.enabled, onCheckedChange = { onToggle(config.id, it) })
        }

        val snapshot = item.snapshot
        if (snapshot == null) {
            if (config.enabled && config.lastError == null) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(stringResource(R.string.panel_budget_no_data_title), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.panel_budget_no_data_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            when (config.serviceType) {
                ApiServiceType.DEEPSEEK -> DeepSeekBody(snapshot)
                ApiServiceType.PACKY -> PackyBody(snapshot)
                ApiServiceType.VAPI, ApiServiceType.NEWAPI -> QuotaBody(snapshot)
                ApiServiceType.ZAI -> ZaiBody(snapshot)
            }
            BudgetFooter(snapshot, item)
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        CardFooter(config.lastUpdatedAt, config.lastError, nowEpochSeconds)
    }
}

@Composable
private fun BudgetFooter(snapshot: BudgetSnapshot, item: BudgetItem) {
    val currency = item.config.currencyCode
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item.computation.usagePercent?.let {
            Text(
                stringResource(R.string.panel_budget_usage_percent, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item.computation.monthlyRemaining?.let {
            Text(
                stringResource(R.string.panel_budget_remaining_month) + ": " + formatBudgetAmount(it, currency),
                style = MaterialTheme.typography.bodySmall,
                color = if (it.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item.computation.dailyRemaining?.let {
            Text(
                stringResource(R.string.panel_budget_remaining_today) + ": " + formatBudgetAmount(it, currency),
                style = MaterialTheme.typography.bodySmall,
                color = if (it.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeepSeekBody(snapshot: BudgetSnapshot) {
    val allZero = snapshot.balances.all { it.totalDecimal.signum() == 0 }
    snapshot.balances.forEach { balance ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.panel_deepseek_total) + ": " + formatBudgetAmount(balance.totalDecimal, balance.currencyCode),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val parts = buildList {
                    balance.granted?.let { add(stringResource(R.string.panel_deepseek_granted, it)) }
                    balance.toppedUp?.let { add(stringResource(R.string.panel_deepseek_topped_up, it)) }
                }
                if (parts.isNotEmpty()) {
                    Text(
                        parts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(if (snapshot.isBalanceAvailable) R.string.panel_deepseek_sufficient else R.string.panel_deepseek_insufficient),
            style = MaterialTheme.typography.labelSmall,
            color = if (snapshot.isBalanceAvailable) SubMarkTheme.extendedColors.success else MaterialTheme.colorScheme.error,
        )
        if (snapshot.balances.size > 1) {
            Text(
                androidx.compose.ui.res.pluralStringResource(
                    io.github.submark.feature.integrations.R.plurals.panel_deepseek_currencies,
                    snapshot.balances.size, snapshot.balances.size,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (allZero) {
            Text(
                stringResource(R.string.panel_deepseek_all_zero),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PackyBody(snapshot: BudgetSnapshot) {
    snapshot.dailySpentAmount?.let { daily ->
        Text(
            stringResource(R.string.panel_packy_daily) + ": " + formatBudgetAmount(daily.toBigDecimalOrNull(), "USD"),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.panel_packy_monthly) + ": " +
                stringResource(
                    R.string.panel_packy_spent_of_budget,
                    formatBudgetAmount(snapshot.usedDecimal, "USD"),
                    formatBudgetAmount(snapshot.limitDecimal, "USD"),
                ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    snapshot.expiryAt?.let {
        Text(
            stringResource(R.string.panel_packy_expires, formatInstant(it)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuotaBody(snapshot: BudgetSnapshot) {
    val percent = snapshot.usedDecimal?.let { used ->
        snapshot.limitDecimal?.takeIf { it.signum() > 0 }?.let { limit ->
            used.multiply(java.math.BigDecimal(100)).divide(limit, 0, java.math.RoundingMode.HALF_UP).toInt()
        }
    }
    if (percent != null) {
        LinearProgressIndicator(
            progress = { (percent.coerceIn(0, 100)) / 100f },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
    }
    Text(
        stringResource(
            R.string.panel_budget_used_of_limit,
            formatBudgetAmount(snapshot.usedDecimal, snapshot.currencyCode),
            formatBudgetAmount(snapshot.limitDecimal, snapshot.currencyCode),
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        snapshot.totalRequests?.let {
            Text(
                stringResource(R.string.panel_budget_total_requests, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        snapshot.accountId?.let {
            Text(
                stringResource(R.string.panel_budget_account_id, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        snapshot.userRole?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ZaiBody(snapshot: BudgetSnapshot) {
    if (!snapshot.planName.isNullOrBlank()) {
        Text(snapshot.planName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else if (snapshot.quotas.isEmpty()) {
        Text(
            stringResource(R.string.panel_zai_no_plan),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    snapshot.quotas.forEach { quota ->
        val label = stringResource(
            when (quota.kind) {
                QuotaKind.SESSION_5H -> R.string.panel_zai_session_quota
                QuotaKind.WEEKLY -> R.string.panel_zai_weekly_quota
                else -> R.string.panel_zai_web_search_quota
            }
        )
        Column(Modifier.padding(top = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            val percent = quota.percentageUsed ?: if (quota.limit > 0) ((quota.used * 100) / quota.limit).toInt() else 0
            LinearProgressIndicator(
                progress = { (percent.coerceIn(0, 100)) / 100f },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            Text(
                stringResource(R.string.panel_zai_used, formatCount(quota.used)) + " · " +
                    stringResource(R.string.panel_zai_remaining, formatCount((quota.limit - quota.used).coerceAtLeast(0))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            quota.resetAt?.let {
                Text(
                    stringResource(R.string.panel_zai_resets, formatInstant(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
