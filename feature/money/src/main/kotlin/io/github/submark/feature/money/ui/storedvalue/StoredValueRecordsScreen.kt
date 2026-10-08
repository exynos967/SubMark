package io.github.submark.feature.money.ui.storedvalue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.domain.StoredValueStatus
import io.github.submark.core.model.StoredValueRecord
import io.github.submark.core.model.StoredValueRecordType
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.core.ui.component.CurrencyPickerSheet
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.MoneyInputField
import io.github.submark.core.ui.component.MoneyText
import io.github.submark.core.ui.component.SearchField
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.SegmentedTabs
import io.github.submark.core.ui.component.SettingsSwitchRow
import io.github.submark.core.ui.component.StatusBadge
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.component.formatMoney
import io.github.submark.core.ui.format.BadgeTone
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.feature.money.R
import io.github.submark.feature.money.ui.common.InfoRow
import io.github.submark.feature.money.ui.common.PickerField
import io.github.submark.feature.money.ui.common.StatCell
import io.github.submark.feature.money.ui.common.WalletSelector
import io.github.submark.feature.money.ui.common.formatDate
import io.github.submark.feature.money.ui.common.localizedNote
import io.github.submark.core.ui.R as CoreR

@Composable
fun StoredValueRecordsRoute(onBack: () -> Unit, viewModel: StoredValueRecordsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    SnackbarEffect(viewModel.snackbar, snackbarHost)
    StoredValueRecordsScreen(
        state = state,
        snackbarHost = snackbarHost,
        onBack = onBack,
        onFilter = viewModel::setFilter,
        onQuery = viewModel::setQuery,
        onDelete = viewModel::delete,
        onOpenTopUp = viewModel::openTopUp,
        onCloseTopUp = viewModel::closeTopUp,
        onUpdateTopUp = viewModel::updateTopUp,
        onSubmitTopUp = viewModel::submitTopUp,
    )
}

@Composable
fun StoredValueRecordsScreen(
    state: StoredValueRecordsUiState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onFilter: (RecordFilter) -> Unit,
    onQuery: (String) -> Unit,
    onDelete: (StoredValueRecord) -> Unit,
    onOpenTopUp: () -> Unit,
    onCloseTopUp: () -> Unit,
    onUpdateTopUp: ((TopUpForm) -> TopUpForm) -> Unit,
    onSubmitTopUp: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<StoredValueRecord?>(null) }
    Scaffold(
        topBar = {
            SubMarkTopAppBar(
                title = stringResource(R.string.money_sv_title),
                subtitle = state.subscription?.name,
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            if (state.isStoredValue) {
                ExtendedFloatingActionButton(
                    onClick = onOpenTopUp,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.money_sv_top_up)) },
                )
            }
        },
    ) { padding ->
        val sub = state.subscription
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            sub == null -> EmptyState(title = stringResource(R.string.money_subscription_missing), modifier = Modifier.padding(padding))
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "header") {
                    SectionCard {
                        if (!state.isStoredValue) {
                            Text(stringResource(R.string.money_sv_not_stored_value), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.money_sv_balance), style = MaterialTheme.typography.labelMedium)
                                MoneyText(
                                    sub.storedValueBalance, sub.currencyCode, symbol = state.env.symbol(sub.currencyCode),
                                    style = MaterialTheme.typography.headlineMedium, colorBySign = sub.storedValueBalance.signum() < 0,
                                )
                            }
                            state.status?.let { StoredValueStatusBadge(it) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row {
                            StatCell(
                                stringResource(R.string.money_sv_fee),
                                formatMoney(sub.price, sub.currencyCode, state.env.symbol(sub.currencyCode)),
                                Modifier.weight(1f),
                            )
                            StatCell(
                                stringResource(if (state.debtCycles > 0) R.string.money_sv_debt_cycles else R.string.money_sv_payable_cycles),
                                if (state.debtCycles > 0) state.debtCycles.toString() else state.payableCycles?.toString() ?: "∞",
                                Modifier.weight(1f),
                            )
                        }
                    }
                }
                item(key = "filter") {
                    SegmentedTabs(RecordFilter.entries, state.filter, onFilter) {
                        stringResource(
                            when (it) {
                                RecordFilter.ALL -> R.string.money_sv_filter_all
                                RecordFilter.DEPOSIT -> R.string.money_sv_filter_deposit
                                RecordFilter.DEDUCTION -> R.string.money_sv_filter_deduction
                            },
                        )
                    }
                }
                if (state.totalCount > 0) {
                    item(key = "search") { SearchField(state.query, onQuery, placeholder = stringResource(R.string.money_sv_search_hint)) }
                }
                when {
                    state.totalCount == 0 -> item(key = "empty") {
                        EmptyState(
                            title = stringResource(R.string.money_sv_empty),
                            message = if (state.isStoredValue) stringResource(R.string.money_sv_empty_message) else null,
                            icon = Icons.Rounded.Savings,
                        )
                    }
                    state.records.isEmpty() -> item(key = "nomatch") {
                        EmptyState(title = stringResource(R.string.money_history_no_match), icon = Icons.Rounded.SearchOff)
                    }
                    else -> items(state.records, key = { it.id }) { record ->
                        RecordRow(record, state, onDelete = { pendingDelete = record })
                    }
                }
            }
        }
    }

    pendingDelete?.let { record ->
        ConfirmDialog(
            title = stringResource(R.string.money_sv_delete_title),
            message = stringResource(
                if (record.type == StoredValueRecordType.DEPOSIT) R.string.money_sv_delete_deposit_message else R.string.money_sv_delete_deduction_message,
            ),
            onConfirm = { pendingDelete = null; onDelete(record) },
            onDismiss = { pendingDelete = null },
            confirmLabel = stringResource(CoreR.string.ui_action_delete),
            destructive = true,
        )
    }
    if (state.topUp.open) TopUpSheet(state, onCloseTopUp, onUpdateTopUp, onSubmitTopUp)
}

@Composable
private fun RecordRow(record: StoredValueRecord, state: StoredValueRecordsUiState, onDelete: () -> Unit) {
    val sub = state.subscription ?: return
    val deposit = record.type == StoredValueRecordType.DEPOSIT
    SectionCard(contentPadding = PaddingValues(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val title = localizedNote(record.description)?.asString() ?: stringResource(
                    when {
                        deposit && record.isInitial -> R.string.money_sv_default_initial
                        deposit -> R.string.money_sv_default_top_up
                        else -> R.string.money_sv_default_deduction
                    },
                )
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    formatDate(record.occurredAt.atZone(state.zone).toLocalDate()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.money_sv_balance_after, formatMoney(record.balanceAfter, sub.currencyCode, state.env.symbol(sub.currencyCode))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                val signed = if (deposit) record.amount else record.amount.negate()
                MoneyText(signed, record.currencyCode, symbol = state.env.symbol(record.currencyCode), showPlusSign = true, colorBySign = true, style = MaterialTheme.typography.titleMedium)
                if (record.currencyCode != sub.currencyCode) {
                    Text(
                        "≈ " + formatMoney(record.amountInSubscriptionCurrency, sub.currencyCode, state.env.symbol(sub.currencyCode)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge(
                    stringResource(if (deposit) R.string.money_sv_filter_deposit else R.string.money_sv_filter_deduction),
                    tone = if (deposit) BadgeTone.SUCCESS else BadgeTone.NEUTRAL,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.money_sv_delete_cd))
            }
        }
    }
}

@Composable
fun StoredValueStatusBadge(status: StoredValueStatus) {
    val (label, tone) = when (status) {
        StoredValueStatus.SUFFICIENT -> R.string.money_sv_status_sufficient to BadgeTone.SUCCESS
        StoredValueStatus.LOW -> R.string.money_sv_status_low to BadgeTone.WARNING
        StoredValueStatus.EMPTY -> R.string.money_sv_status_empty to BadgeTone.WARNING
        StoredValueStatus.ZERO -> R.string.money_sv_status_zero to BadgeTone.NEUTRAL
        StoredValueStatus.DEBT -> R.string.money_sv_status_debt to BadgeTone.ERROR
    }
    StatusBadge(stringResource(label), tone = tone)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TopUpSheet(
    state: StoredValueRecordsUiState,
    onDismiss: () -> Unit,
    onUpdate: ((TopUpForm) -> TopUpForm) -> Unit,
    onSubmit: () -> Unit,
) {
    val sub = state.subscription ?: return
    val form = state.topUp
    val env = state.env
    var pickCurrency by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).imePadding().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.money_sv_top_up_title), style = MaterialTheme.typography.titleLarge)
            InfoRow(stringResource(R.string.money_sv_current_balance), formatMoney(sub.storedValueBalance, sub.currencyCode, env.symbol(sub.currencyCode)))
            MoneyInputField(
                value = form.amountText,
                onValueChange = { v -> onUpdate { it.copy(amountText = v, error = null) } },
                label = stringResource(R.string.money_add_amount),
                currencySymbol = env.symbol(form.currencyCode),
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.quickAmounts.forEach { q ->
                    AssistChip(
                        onClick = { onUpdate { it.copy(amountText = q.toPlainString(), currencyCode = sub.currencyCode, error = null) } },
                        label = { Text(formatMoney(q, sub.currencyCode, env.symbol(sub.currencyCode))) },
                    )
                }
            }
            PickerField(
                label = stringResource(R.string.money_add_currency),
                text = form.currencyCode,
                onClick = { pickCurrency = true },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.note,
                onValueChange = { v -> onUpdate { it.copy(note = v) } },
                label = { Text(stringResource(R.string.money_add_note)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SettingsSwitchRow(
                title = stringResource(R.string.money_sv_fund_wallet),
                subtitle = stringResource(R.string.money_sv_fund_wallet_desc),
                checked = form.useWallet,
                onCheckedChange = { c -> onUpdate { it.copy(useWallet = c) } },
            )
            if (form.useWallet) {
                WalletSelector(state.walletChoices, form.walletId, { id -> onUpdate { it.copy(walletId = id) } }, env.symbols)
            }
            val preview = state.topUpPreview
            SectionCard(title = stringResource(R.string.money_add_preview_title)) {
                if (preview?.balanceAfter == null) {
                    Text(
                        stringResource(
                            if (form.amount != null && form.amount!!.signum() > 0) R.string.money_rate_unavailable_short else R.string.money_sv_preview_enter_amount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    if (form.currencyCode != sub.currencyCode) {
                        InfoRow(stringResource(R.string.money_sv_converted), formatMoney(preview.converted!!, sub.currencyCode, env.symbol(sub.currencyCode)))
                    }
                    InfoRow(stringResource(R.string.money_sv_balance_after_label), formatMoney(preview.balanceAfter, sub.currencyCode, env.symbol(sub.currencyCode)))
                    InfoRow(
                        stringResource(R.string.money_sv_payable_cycles),
                        preview.payableCycles?.let { pluralStringResource(R.plurals.money_sv_cycles, it.toInt(), it.toInt()) } ?: "∞",
                    )
                    preview.status?.let { StoredValueStatusBadge(it) }
                }
            }
            form.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Button(onClick = onSubmit, enabled = !form.saving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.money_sv_top_up))
            }
            Spacer(Modifier.height(16.dp).width(1.dp))
        }
    }
    if (pickCurrency) {
        CurrencyPickerSheet(
            currencies = state.currencies,
            selectedCode = form.currencyCode,
            defaultCode = env.defaultCode,
            onSelect = { c -> onUpdate { it.copy(currencyCode = c.code) }; pickCurrency = false },
            onDismiss = { pickCurrency = false },
        )
    }
}
