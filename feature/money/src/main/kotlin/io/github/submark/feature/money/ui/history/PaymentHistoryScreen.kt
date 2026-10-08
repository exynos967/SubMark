package io.github.submark.feature.money.ui.history

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
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.model.PaymentRecord
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.MoneyText
import io.github.submark.core.ui.component.SearchField
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.StatusBadge
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.component.formatMoney
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.format.labelRes
import io.github.submark.core.ui.format.tone
import io.github.submark.feature.money.R
import io.github.submark.feature.money.data.MoneyEnv
import io.github.submark.feature.money.ui.common.BadgeRow
import io.github.submark.feature.money.ui.common.StatCell
import io.github.submark.feature.money.ui.common.formatDate
import io.github.submark.feature.money.ui.common.localizedNote
import io.github.submark.feature.money.ui.common.paymentBadges

@Composable
fun PaymentHistoryRoute(
    onBack: () -> Unit,
    onOpenRecord: (String) -> Unit,
    onAddPayment: (subscriptionId: String) -> Unit,
    viewModel: PaymentHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PaymentHistoryScreen(
        state = state,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onOpenRecord = onOpenRecord,
        onAdd = { onAddPayment(viewModel.subscriptionId) },
    )
}

@Composable
fun PaymentHistoryScreen(
    state: PaymentHistoryUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenRecord: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Scaffold(
        topBar = {
            SubMarkTopAppBar(
                title = if (state.subscriptionName.isEmpty()) stringResource(R.string.money_history_title_plain)
                else stringResource(R.string.money_history_title, state.subscriptionName),
                onBack = onBack,
            )
        },
        floatingActionButton = {
            if (state.canAdd) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.money_history_add)) },
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.missing -> EmptyState(
                title = stringResource(R.string.money_subscription_missing),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "summary") { HistorySummary(state) }
                if (state.totalRecordCount > 0) {
                    item(key = "search") {
                        SearchField(
                            query = state.query,
                            onQueryChange = onQueryChange,
                            placeholder = stringResource(R.string.money_history_search_hint),
                        )
                    }
                    item(key = "count") {
                        Text(
                            pluralStringResource(R.plurals.money_history_record_count, state.records.size, state.records.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                when {
                    state.totalRecordCount == 0 -> item(key = "empty") {
                        EmptyState(
                            title = stringResource(R.string.money_history_empty_title),
                            message = stringResource(if (state.canAdd) R.string.money_history_empty_message else R.string.money_history_wishlist),
                            icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                            actionLabel = if (state.canAdd) stringResource(R.string.money_history_add) else null,
                            onAction = if (state.canAdd) onAdd else null,
                        )
                    }
                    state.records.isEmpty() -> item(key = "no_match") {
                        EmptyState(title = stringResource(R.string.money_history_no_match), icon = Icons.Rounded.SearchOff)
                    }
                    else -> items(state.records, key = { it.id }) { record ->
                        SectionCard(contentPadding = PaddingValues(0.dp)) {
                            PaymentRow(record, state.env, onClick = { onOpenRecord(record.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySummary(state: PaymentHistoryUiState) {
    SectionCard {
        Row(Modifier.fillMaxWidth()) {
            StatCell(
                label = stringResource(R.string.money_history_total_paid),
                value = formatMoney(state.totalPaid, state.env.defaultCode, state.env.symbol(state.env.defaultCode)),
                modifier = Modifier.weight(1f),
            )
            StatCell(
                label = stringResource(R.string.money_history_successful),
                value = state.successCount.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        if (state.unconvertedCount > 0) {
            Text(
                pluralStringResource(R.plurals.money_unconverted_note, state.unconvertedCount, state.unconvertedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** One payment line: date, optional [title], badges, note and amount with its status. */
@Composable
internal fun PaymentRow(record: PaymentRecord, env: MoneyEnv, onClick: () -> Unit, title: String? = null, showDivider: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                formatDate(record.paymentDate),
                style = if (title == null) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall,
                fontWeight = if (title == null) FontWeight.Medium else null,
                color = if (title == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val note = localizedNote(record.iapItemName ?: record.note)?.asString()
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val badges = paymentBadges(record)
            if (badges.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                BadgeRow(badges)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            MoneyText(record.amount, record.currencyCode, symbol = env.symbol(record.currencyCode), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            StatusBadge(stringResource(record.status.labelRes), tone = record.status.tone)
        }
    }
    if (showDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}
