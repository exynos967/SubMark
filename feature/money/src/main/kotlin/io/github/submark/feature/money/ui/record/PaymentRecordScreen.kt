package io.github.submark.feature.money.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.model.DateAdjustmentMode
import io.github.submark.core.model.MarkTiming
import io.github.submark.core.model.PaymentKind
import io.github.submark.core.model.PaymentSource
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.StatusBadge
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.component.formatMoney
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.format.labelRes
import io.github.submark.core.ui.format.tone
import io.github.submark.feature.money.R
import io.github.submark.feature.money.ui.common.BadgeRow
import io.github.submark.feature.money.ui.common.InfoRow
import io.github.submark.feature.money.ui.common.formatDate
import io.github.submark.feature.money.ui.common.localizedNote
import io.github.submark.feature.money.ui.common.paymentBadges
import io.github.submark.core.ui.R as CoreR

@Composable
fun PaymentRecordRoute(
    onBack: () -> Unit,
    onEdit: (subscriptionId: String, paymentId: String) -> Unit,
    onOpenStoredValue: (subscriptionId: String) -> Unit,
    onOpenWalletActivity: (walletId: String) -> Unit,
    viewModel: PaymentRecordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.deleted.collect { onBack() } }
    PaymentRecordScreen(
        state = state,
        onBack = onBack,
        onToggleConverted = viewModel::toggleConverted,
        onEdit = { state.record?.let { onEdit(it.subscriptionId, it.id) } },
        onDelete = viewModel::delete,
        onDismissError = viewModel::dismissError,
        onOpenStoredValue = { state.record?.let { onOpenStoredValue(it.subscriptionId) } },
        onOpenWalletActivity = { state.wallet?.let { onOpenWalletActivity(it.id) } },
    )
}

@Composable
fun PaymentRecordScreen(
    state: PaymentRecordUiState,
    onBack: () -> Unit,
    onToggleConverted: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismissError: () -> Unit,
    onOpenStoredValue: () -> Unit,
    onOpenWalletActivity: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { SubMarkTopAppBar(title = stringResource(R.string.money_record_title), onBack = onBack) }) { padding ->
        val record = state.record
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            record == null -> EmptyState(title = stringResource(R.string.money_record_missing), modifier = Modifier.padding(padding))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val env = state.env
                // Amount header
                SectionCard {
                    Text(stringResource(R.string.money_record_original_amount), style = MaterialTheme.typography.labelMedium)
                    Text(
                        formatMoney(record.amount, record.currencyCode, env.symbol(record.currencyCode)),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(record.currencyCode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (record.currencyCode != env.defaultCode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.money_record_convert_toggle, env.defaultCode), modifier = Modifier.weight(1f))
                            Switch(checked = state.showConverted, onCheckedChange = { onToggleConverted() })
                        }
                        if (state.showConverted) {
                            Text(
                                state.convertedAmount?.let { formatMoney(it, env.defaultCode, env.symbol(env.defaultCode)) }
                                    ?: stringResource(R.string.money_rate_unavailable_short),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(stringResource(R.string.money_record_convert_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusBadge(stringResource(record.status.labelRes), tone = record.status.tone)
                    }
                    Spacer(Modifier.height(4.dp))
                    BadgeRow(paymentBadges(record))
                }

                SectionCard(title = stringResource(R.string.money_record_section_info)) {
                    InfoRow(stringResource(R.string.money_record_subscription), state.subscription?.name ?: "—")
                    InfoRow(stringResource(R.string.money_record_date), formatDate(record.paymentDate))
                    InfoRow(stringResource(R.string.money_record_status), stringResource(record.status.labelRes))
                    InfoRow(stringResource(R.string.money_record_kind), stringResource(record.kind.labelRes))
                    if (record.source != PaymentSource.USER_MANUAL) {
                        InfoRow(stringResource(R.string.money_record_source), stringResource(record.source.labelRes))
                    }
                    if (record.markTiming != MarkTiming.ON_TIME) {
                        InfoRow(stringResource(R.string.money_record_timing), stringResource(record.markTiming.labelRes))
                    }
                    record.originalDueDate?.let { InfoRow(stringResource(R.string.money_record_original_due), formatDate(it)) }
                    record.iapItemName?.let { InfoRow(stringResource(R.string.money_record_iap_item), it) }
                    InfoRow(stringResource(R.string.money_record_note), localizedNote(record.note)?.asString() ?: "—")
                }

                if (record.kind == PaymentKind.EXTENSION || record.dateAdjustmentMode != DateAdjustmentMode.NONE) {
                    SectionCard(title = stringResource(R.string.money_record_section_extension)) {
                        if (record.extensionFrom != null && record.extensionTo != null) {
                            InfoRow(stringResource(R.string.money_record_extension_from), formatDate(record.extensionFrom!!))
                            InfoRow(stringResource(R.string.money_record_extension_to), formatDate(record.extensionTo!!))
                        }
                        when (record.dateAdjustmentMode) {
                            DateAdjustmentMode.END_DATE -> record.adjustmentTargetDate?.let {
                                InfoRow(stringResource(R.string.money_record_adjust_end), formatDate(it))
                            }
                            DateAdjustmentMode.NEXT_BILLING -> record.adjustmentTargetDate?.let {
                                InfoRow(stringResource(R.string.money_record_adjust_next), formatDate(it))
                            }
                            DateAdjustmentMode.NONE -> Unit
                        }
                        record.prevEndDate?.let { InfoRow(stringResource(R.string.money_record_prev_end), formatDate(it)) }
                        record.prevNextPaymentDate?.let { InfoRow(stringResource(R.string.money_record_prev_next), formatDate(it)) }
                    }
                }

                if (record.walletId != null) {
                    SectionCard(
                        title = stringResource(R.string.money_record_section_wallet),
                        actionLabel = state.wallet?.takeIf { it.deletedAt == null }?.let { stringResource(R.string.money_record_wallet_activity) },
                        onAction = onOpenWalletActivity,
                    ) {
                        val wallet = state.wallet
                        val walletName = when {
                            wallet == null -> stringResource(R.string.money_wallet_unknown)
                            wallet.deletedAt != null -> stringResource(R.string.money_wallet_deleted_name, wallet.name)
                            else -> wallet.name
                        }
                        InfoRow(stringResource(R.string.money_record_wallet), walletName)
                        val txn = state.transaction
                        if (txn == null) {
                            Text(stringResource(R.string.money_record_wallet_txn_missing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            val walletCode = wallet?.currencyCode ?: record.currencyCode
                            InfoRow(stringResource(R.string.money_record_wallet_charged), formatMoney(txn.signedDelta, walletCode, env.symbol(walletCode), showPlusSign = true))
                            InfoRow(stringResource(R.string.money_wallet_balance_after), formatMoney(txn.balanceAfter, walletCode, env.symbol(walletCode)))
                            if (txn.status == io.github.submark.core.model.WalletTxnStatus.REVERSED) {
                                StatusBadge(stringResource(R.string.money_wallet_txn_voided), tone = io.github.submark.core.ui.format.BadgeTone.WARNING)
                            }
                        }
                    }
                }

                if (state.readOnly) {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.padding(4.dp))
                            Text(stringResource(R.string.money_record_read_only_deposit), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                        TextButton(onClick = onOpenStoredValue) { Text(stringResource(R.string.money_record_open_stored_value)) }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(onClick = onEdit, enabled = !state.readOnly, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Edit, contentDescription = null)
                        Spacer(Modifier.padding(4.dp))
                        Text(stringResource(R.string.money_record_edit))
                    }
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        enabled = !state.deleting,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                        Spacer(Modifier.padding(4.dp))
                        Text(stringResource(R.string.money_record_delete))
                    }
                }
            }
        }
    }

    val record = state.record
    if (confirmDelete && record != null) {
        val lines = buildList {
            add(stringResource(R.string.money_record_delete_message))
            if (record.walletTransactionId != null) add(stringResource(R.string.money_record_delete_wallet_note))
            if (record.kind == PaymentKind.EXTENSION || record.dateAdjustmentMode != DateAdjustmentMode.NONE) {
                add(stringResource(R.string.money_record_delete_extension_note))
            }
            if (record.kind == PaymentKind.STORED_VALUE_DEPOSIT) add(stringResource(R.string.money_record_delete_deposit_note))
        }
        ConfirmDialog(
            title = stringResource(R.string.money_record_delete_title),
            message = lines.joinToString("\n\n"),
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
            confirmLabel = stringResource(CoreR.string.ui_action_delete),
            destructive = true,
        )
    }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text(stringResource(R.string.money_record_delete_failed)) },
            text = { Text(error.asString()) },
            confirmButton = { TextButton(onClick = onDismissError) { Text(stringResource(CoreR.string.ui_action_ok)) } },
        )
    }
}
