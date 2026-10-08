package io.github.submark.feature.money.ui.storedvalue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.component.formatMoney
import io.github.submark.core.ui.icon.SubscriptionIcon
import io.github.submark.core.ui.util.bleedHorizontally
import io.github.submark.feature.money.R
import io.github.submark.feature.money.data.MoneyEnv
import io.github.submark.feature.money.ui.common.StatCell

@Composable
fun StoredValueStatsRoute(
    onBack: () -> Unit,
    onOpenRecords: (subscriptionId: String) -> Unit,
    viewModel: StoredValueStatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StoredValueStatsScreen(state, onBack, viewModel::setPeriod, onOpenRecords)
}

@Composable
fun StoredValueStatsScreen(
    state: StoredValueStatsUiState,
    onBack: () -> Unit,
    onPeriod: (StoredValuePeriod) -> Unit,
    onOpenRecords: (String) -> Unit,
) {
    Scaffold(topBar = { SubMarkTopAppBar(title = stringResource(R.string.money_svs_title), onBack = onBack) }) { padding ->
        val stats = state.stats
        if (state.loading || stats == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        val env = state.env
        val def = env.defaultCode
        val sym = env.symbol(def)
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "overview") {
                SectionCard(title = stringResource(R.string.money_svs_overview)) {
                    Row {
                        StatCell(stringResource(R.string.money_svs_total_balance), formatMoney(stats.totalBalance, def, sym), Modifier.weight(1f))
                        StatCell(stringResource(R.string.money_svs_count), stats.subscriptionCount.toString(), Modifier.weight(1f))
                    }
                    Row {
                        StatCell(stringResource(R.string.money_svs_this_month), formatMoney(stats.thisMonthDeposits, def, sym), Modifier.weight(1f))
                        StatCell(stringResource(R.string.money_svs_this_year), formatMoney(stats.thisYearDeposits, def, sym), Modifier.weight(1f))
                    }
                }
            }
            item(key = "period") {
                Row(
                    Modifier.fillMaxWidth().bleedHorizontally(16.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StoredValuePeriod.entries.forEach { p ->
                        FilterChip(selected = p == state.period, onClick = { onPeriod(p) }, label = { Text(stringResource(p.labelRes())) })
                    }
                }
            }
            item(key = "period_stats") {
                SectionCard(title = stringResource(R.string.money_svs_top_ups)) {
                    Row {
                        StatCell(stringResource(R.string.money_svs_period_total), formatMoney(stats.periodDeposits, def, sym), Modifier.weight(1f))
                        StatCell(stringResource(R.string.money_svs_period_count), stats.periodDepositCount.toString(), Modifier.weight(1f))
                    }
                    Row {
                        StatCell(stringResource(R.string.money_svs_avg_daily), formatMoney(stats.averageDaily, def, sym), Modifier.weight(1f))
                        StatCell(stringResource(R.string.money_svs_avg_monthly), formatMoney(stats.averageMonthly, def, sym), Modifier.weight(1f))
                    }
                    if (stats.unconvertedCount > 0) {
                        Text(
                            pluralStringResource(R.plurals.money_unconverted_note, stats.unconvertedCount, stats.unconvertedCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(stringResource(R.string.money_svs_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (stats.subscriptions.isEmpty()) {
                item(key = "empty") {
                    EmptyState(title = stringResource(R.string.money_svs_empty), message = stringResource(R.string.money_svs_empty_message), icon = Icons.Rounded.Savings)
                }
            } else {
                item(key = "subs_header") { Text(stringResource(R.string.money_svs_subscriptions), style = MaterialTheme.typography.titleMedium) }
                items(stats.subscriptions, key = { it.subscription.id }) { s ->
                    val sub = s.subscription
                    SectionCard(onClick = { onOpenRecords(sub.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SubscriptionIcon(sub.iconType, sub.iconValue, fallbackName = sub.name, size = 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(sub.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    formatMoney(sub.storedValueBalance, sub.currencyCode, env.symbol(sub.currencyCode)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            StoredValueStatusBadge(s.status)
                        }
                    }
                }
            }
        }
    }
}

private fun StoredValuePeriod.labelRes(): Int = when (this) {
    StoredValuePeriod.ALL_TIME -> R.string.money_period_all_time
    StoredValuePeriod.THIS_MONTH -> R.string.money_period_this_month
    StoredValuePeriod.LAST_MONTH -> R.string.money_period_last_month
    StoredValuePeriod.THIS_YEAR -> R.string.money_period_this_year
    StoredValuePeriod.LAST_YEAR -> R.string.money_period_last_year
}
