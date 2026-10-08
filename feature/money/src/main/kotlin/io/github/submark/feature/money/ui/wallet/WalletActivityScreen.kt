package io.github.submark.feature.money.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.model.WalletTransaction
import io.github.submark.core.model.WalletTxnStatus
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.MoneyText
import io.github.submark.core.ui.component.SearchField
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.StatusBadge
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.component.formatMoney
import io.github.submark.core.ui.format.BadgeTone
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.format.labelRes
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.feature.money.R
import io.github.submark.feature.money.ui.common.InfoRow
import io.github.submark.feature.money.ui.common.formatDate
import io.github.submark.feature.money.ui.common.localizedNote
import io.github.submark.core.ui.R as CoreR
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun WalletActivityRoute(onBack: () -> Unit, viewModel: WalletActivityViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    SnackbarEffect(viewModel.snackbar, host)
    WalletActivityScreen(
        state = state,
        snackbarHost = host,
        onBack = onBack,
        onQuery = viewModel::setQuery,
        onOpenDetail = viewModel::openDetail,
        onCloseDetail = viewModel::closeDetail,
        onReverse = viewModel::reverse,
    )
}

@Composable
fun WalletActivityScreen(
    state: WalletActivityUiState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onOpenDetail: (WalletTransaction) -> Unit,
    onCloseDetail: () -> Unit,
    onReverse: (WalletTransaction, String) -> Unit,
) {
    Scaffold(
        topBar = {
            SubMarkTopAppBar(
                title = stringResource(if (state.allWallets) R.string.money_wallet_all_activity else R.string.money_wallet_activity),
                subtitle = state.wallet?.let { w -> if (w.deletedAt != null) stringResource(R.string.money_wallet_deleted_name, w.name) else w.name },
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.totalCount > 0) {
                item(key = "search") { SearchField(state.query, onQuery, placeholder = stringResource(R.string.money_wallet_search_hint)) }
            }
            when {
                state.totalCount == 0 -> item(key = "empty") {
                    EmptyState(
                        title = stringResource(R.string.money_wallet_no_activity),
                        message = stringResource(R.string.money_wallet_no_activity_message),
                        icon = Icons.Rounded.ReceiptLong,
                    )
                }
                state.transactions.isEmpty() -> item(key = "nomatch") {
                    EmptyState(title = stringResource(R.string.money_history_no_match), icon = Icons.Rounded.SearchOff)
                }
                else -> items(state.transactions, key = { it.id }) { txn ->
                    TransactionRow(txn, state, onClick = { onOpenDetail(txn) })
                }
            }
        }
    }
    state.detail?.let { TransactionDetailSheet(it, state, onCloseDetail, onReverse) }
}

@Composable
private fun TransactionRow(txn: WalletTransaction, state: WalletActivityUiState, onClick: () -> Unit) {
    val env = state.env
    val code = state.walletCurrencies[txn.walletId] ?: return
    val reversed = txn.status == WalletTxnStatus.REVERSED
    val dt = txn.occurredAt.atZone(state.zone)
    val timeText = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(dt)
    SectionCard(onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(stringResource(txn.type.labelRes), tone = when (txn.signedDelta.signum()) { -1 -> BadgeTone.ERROR; 1 -> BadgeTone.SUCCESS; else -> BadgeTone.NEUTRAL })
                    if (reversed) StatusBadge(stringResource(R.string.money_wallet_txn_voided), tone = BadgeTone.WARNING)
                    if (txn.reversesTransactionId != null) StatusBadge(stringResource(R.string.money_wallet_reversal_badge), tone = BadgeTone.SECONDARY)
                }
                val walletName = state.walletNames[txn.walletId]
                Text(
                    buildString {
                        if (state.allWallets && walletName != null) {
                            if (state.deletedWalletIds.contains(txn.walletId)) append(stringResource(R.string.money_wallet_deleted_name, walletName)) else append(walletName)
                            append(" · ")
                        }
                        append(formatDate(dt.toLocalDate()))
                        append(" ")
                        append(timeText)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                localizedNote(txn.note)?.let { Text(it.asString(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text(
                    stringResource(R.string.money_wallet_balance_after_fmt, formatMoney(txn.balanceAfter, code, env.symbol(code))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MoneyText(txn.signedDelta, code, symbol = env.symbol(code), showPlusSign = true, colorBySign = true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDetailSheet(
    txn: WalletTransaction,
    state: WalletActivityUiState,
    onDismiss: () -> Unit,
    onReverse: (WalletTransaction, String) -> Unit,
) {
    val env = state.env
    val code = state.walletCurrencies[txn.walletId].orEmpty()
    var note by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).imePadding().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.money_wallet_txn_detail), style = MaterialTheme.typography.titleLarge)
            InfoRow(stringResource(R.string.money_record_kind), stringResource(txn.type.labelRes))
            InfoRow(stringResource(R.string.money_record_status), stringResource(if (txn.status == WalletTxnStatus.REVERSED) R.string.money_wallet_txn_voided else R.string.money_wallet_txn_committed))
            InfoRow(stringResource(R.string.money_wallet_txn_amount), formatMoney(txn.amount, code, env.symbol(code)))
            val walletName = state.walletNames[txn.walletId]
            if (walletName != null) {
                InfoRow(
                    stringResource(R.string.money_record_wallet),
                    if (state.deletedWalletIds.contains(txn.walletId)) stringResource(R.string.money_wallet_deleted_name, walletName) else walletName,
                )
            }
            InfoRow(stringResource(R.string.money_wallet_txn_effect), formatMoney(txn.signedDelta, code, env.symbol(code), showPlusSign = true))
            InfoRow(stringResource(R.string.money_wallet_balance_after), formatMoney(txn.balanceAfter, code, env.symbol(code)))
            val srcAmount = txn.sourceAmount
            val srcCode = txn.sourceCurrencyCode
            if (srcAmount != null && srcCode != null) {
                InfoRow(stringResource(R.string.money_wallet_txn_source_amount), formatMoney(srcAmount, srcCode, env.symbol(srcCode)))
            }
            InfoRow(
                stringResource(R.string.money_wallet_txn_date),
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(txn.occurredAt.atZone(state.zone)),
            )
            localizedNote(txn.note)?.asString()?.let { InfoRow(stringResource(R.string.money_record_note), it) }
            if (txn.status == WalletTxnStatus.COMMITTED) {
                val walletDeleted = state.deletedWalletIds.contains(txn.walletId)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.money_wallet_reverse_note)) },
                    singleLine = true,
                    enabled = !walletDeleted,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (walletDeleted) {
                    Text(stringResource(R.string.money_wallet_reverse_deleted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    OutlinedButton(
                        onClick = { onReverse(txn, note.trim()) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.money_wallet_reverse)) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
