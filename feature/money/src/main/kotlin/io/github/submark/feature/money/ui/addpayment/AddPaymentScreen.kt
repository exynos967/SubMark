package io.github.submark.feature.money.ui.addpayment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.data.service.AddPaymentOutcome
import io.github.submark.core.model.DateAdjustmentMode
import io.github.submark.core.model.PaymentKind
import io.github.submark.core.model.PaymentStatus
import io.github.submark.core.model.Subscription
import io.github.submark.core.ui.component.CurrencyPickerSheet
import io.github.submark.core.ui.component.DatePickerField
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.MoneyInputField
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.SegmentedTabs
import io.github.submark.core.ui.component.SettingsSwitchRow
import io.github.submark.core.ui.component.StatusBadge
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.icon.SubscriptionIcon
import io.github.submark.core.ui.component.formatMoney
import io.github.submark.core.ui.format.BadgeTone
import io.github.submark.core.ui.format.CycleLabels
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.format.labelRes
import io.github.submark.feature.money.R
import io.github.submark.feature.money.ui.common.InfoRow
import io.github.submark.feature.money.ui.common.PickerField
import io.github.submark.feature.money.ui.common.TipsCard
import io.github.submark.feature.money.ui.common.WalletSelector
import io.github.submark.feature.money.ui.common.formatDate
import io.github.submark.core.ui.R as CoreR

@Composable
fun AddPaymentRoute(onBack: () -> Unit, viewModel: AddPaymentViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.close.collect { onBack() } }
    AddPaymentScreen(
        state = state,
        onBack = onBack,
        onUpdate = viewModel::update,
        onAdjustmentMode = viewModel::setAdjustmentMode,
        onSave = viewModel::save,
        onDismissError = viewModel::dismissError,
        onAcknowledgeOutcome = viewModel::acknowledgeOutcome,
    )
}

@Composable
fun AddPaymentScreen(
    state: AddPaymentUiState,
    onBack: () -> Unit,
    onUpdate: ((PaymentForm) -> PaymentForm) -> Unit,
    onAdjustmentMode: (DateAdjustmentMode) -> Unit,
    onSave: () -> Unit,
    onDismissError: () -> Unit,
    onAcknowledgeOutcome: () -> Unit,
) {
    Scaffold(
        topBar = {
            SubMarkTopAppBar(
                title = stringResource(if (state.isEdit) R.string.money_add_title_edit else R.string.money_add_title),
                onBack = onBack,
                actions = {
                    if (state.saving) {
                        CircularProgressIndicator(Modifier.padding(12.dp).width(24.dp).height(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onSave, enabled = state.canSave) {
                            Icon(Icons.Rounded.Check, contentDescription = stringResource(CoreR.string.ui_action_save))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val sub = state.subscription
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.missing || sub == null -> EmptyState(title = stringResource(R.string.money_record_missing), modifier = Modifier.padding(padding))
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LinkedSubscriptionCard(state, sub)
                if (state.readOnly) {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Lock, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.money_add_read_only), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                PaymentInfoSection(state, onUpdate)
                if (!state.isEdit) {
                    DateAdjustmentSection(state, sub, onUpdate, onAdjustmentMode)
                    PreviewCard(state, sub)
                    OptionsSection(state, onUpdate)
                } else if (state.editingRecord?.walletTransactionId != null) {
                    Text(
                        stringResource(R.string.money_add_edit_wallet_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TipsCard(
                    title = stringResource(R.string.money_tips_title),
                    lines = buildList {
                        add(stringResource(R.string.money_add_tip_currency))
                        if (sub.isShared) add(stringResource(R.string.money_add_tip_shared))
                        add(stringResource(R.string.money_add_tip_dates))
                        if (!state.isEdit) add(stringResource(R.string.money_add_tip_wallet))
                    },
                )
                Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(CoreR.string.ui_action_save))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text(stringResource(R.string.money_add_save_failed)) },
            text = { Text(error.asString()) },
            confirmButton = { TextButton(onClick = onDismissError) { Text(stringResource(CoreR.string.ui_action_ok)) } },
        )
    }
    state.outcome?.let { SaveResultDialog(it, state, onAcknowledgeOutcome) }
}

@Composable
private fun LinkedSubscriptionCard(state: AddPaymentUiState, sub: Subscription) {
    val env = state.env
    SectionCard(title = stringResource(R.string.money_add_linked_subscription)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SubscriptionIcon(sub.iconType, sub.iconValue, fallbackName = sub.name, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(sub.name, style = MaterialTheme.typography.titleMedium)
                val price = formatMoney(sub.price, sub.currencyCode, env.symbol(sub.currencyCode))
                Text(
                    "$price · ${CycleLabels.label(sub).asString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusBadge(stringResource(sub.kind.labelRes), tone = BadgeTone.NEUTRAL)
                if (sub.isShared) StatusBadge(stringResource(R.string.money_add_shared_badge), tone = BadgeTone.TERTIARY)
            }
        }
        state.userShare?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.money_add_user_share, formatMoney(it, sub.currencyCode, env.symbol(sub.currencyCode))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PaymentInfoSection(state: AddPaymentUiState, onUpdate: ((PaymentForm) -> PaymentForm) -> Unit) {
    val f = state.form
    val enabled = !state.readOnly
    var pickCurrency by rememberSaveable { mutableStateOf(false) }
    SectionCard(title = stringResource(R.string.money_add_section_payment)) {
        MoneyInputField(
            value = f.amountText,
            onValueChange = { v -> onUpdate { it.copy(amountText = v) } },
            label = stringResource(R.string.money_add_amount),
            currencySymbol = state.env.symbol(f.currencyCode),
            allowZero = true,
            showErrorWhenEmpty = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        PickerField(
            label = stringResource(R.string.money_add_currency),
            text = state.env.currencies[f.currencyCode]?.let { "${it.code} · ${it.name}" } ?: f.currencyCode,
            onClick = { pickCurrency = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        DatePickerField(
            label = stringResource(R.string.money_add_date),
            date = f.paymentDate,
            onDateChange = { d -> onUpdate { it.copy(paymentDate = d) } },
            today = state.today,
            showRelative = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.money_add_status), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        SegmentedTabs(
            items = PaymentStatus.entries,
            selected = f.status,
            onSelect = { s -> if (enabled) onUpdate { it.copy(status = s) } },
        ) { stringResource(it.labelRes) }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = f.note,
            onValueChange = { v -> onUpdate { it.copy(note = v) } },
            label = { Text(stringResource(R.string.money_add_note)) },
            enabled = enabled && !state.noteLocked,
            supportingText = if (state.noteLocked) {
                { Text(stringResource(R.string.money_add_note_locked)) }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        val kind = state.editingRecord?.kind
        if (kind == null || kind == PaymentKind.REGULAR || kind == PaymentKind.IN_APP_PURCHASE) {
            SettingsSwitchRow(
                title = stringResource(R.string.money_add_iap),
                subtitle = stringResource(R.string.money_add_iap_desc),
                checked = f.inAppPurchase,
                onCheckedChange = { c -> onUpdate { it.copy(inAppPurchase = c) } },
                enabled = enabled,
            )
            if (f.inAppPurchase) {
                OutlinedTextField(
                    value = f.iapItemName,
                    onValueChange = { v -> onUpdate { it.copy(iapItemName = v) } },
                    label = { Text(stringResource(R.string.money_add_iap_item)) },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (pickCurrency) {
        CurrencyPickerSheet(
            currencies = state.currencies,
            selectedCode = f.currencyCode,
            defaultCode = state.env.defaultCode,
            onSelect = { c -> onUpdate { it.copy(currencyCode = c.code) }; pickCurrency = false },
            onDismiss = { pickCurrency = false },
        )
    }
}

@Composable
private fun DateAdjustmentSection(
    state: AddPaymentUiState,
    sub: Subscription,
    onUpdate: ((PaymentForm) -> PaymentForm) -> Unit,
    onAdjustmentMode: (DateAdjustmentMode) -> Unit,
) {
    val f = state.form
    val success = f.status == PaymentStatus.SUCCESS
    SectionCard(title = stringResource(R.string.money_add_section_dates)) {
        SettingsSwitchRow(
            title = stringResource(R.string.money_add_adjust_dates),
            subtitle = stringResource(if (success) R.string.money_add_adjust_dates_desc else R.string.money_add_adjust_success_only),
            checked = f.adjustDates && success,
            onCheckedChange = { c -> onUpdate { it.copy(adjustDates = c) } },
            enabled = success,
        )
        if (f.adjustDates && success) {
            SegmentedTabs(
                items = listOf(DateAdjustmentMode.END_DATE, DateAdjustmentMode.NEXT_BILLING),
                selected = f.adjustmentMode,
                onSelect = onAdjustmentMode,
            ) {
                stringResource(if (it == DateAdjustmentMode.END_DATE) R.string.money_add_mode_end_date else R.string.money_add_mode_next_billing)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(
                    if (f.adjustmentMode == DateAdjustmentMode.END_DATE) R.string.money_add_mode_end_date_desc else R.string.money_add_mode_next_billing_desc,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val preview = state.preview
            val days = preview?.adjustmentDays
            val problemText = when (preview?.targetProblem) {
                TargetProblem.BEFORE_PAYMENT_DATE -> stringResource(R.string.money_add_target_before_payment)
                TargetProblem.BEFORE_CURRENT_END -> stringResource(R.string.money_add_target_before_end)
                else -> null
            }
            DatePickerField(
                label = stringResource(
                    if (f.adjustmentMode == DateAdjustmentMode.END_DATE) R.string.money_add_target_end else R.string.money_add_target_next,
                ),
                date = f.targetDate,
                onDateChange = { d -> onUpdate { it.copy(targetDate = d) } },
                today = state.today,
                minDate = AddPaymentPreviewCalculator.minTarget(sub, f.paymentDate, f.adjustmentMode),
                isError = problemText != null,
                supportingText = problemText ?: days?.let {
                    if (f.adjustmentMode == DateAdjustmentMode.END_DATE) {
                        pluralStringResource(R.plurals.money_add_extend_days, it.toInt(), it.toInt())
                    } else {
                        pluralStringResource(R.plurals.money_add_defer_days, it.toInt(), it.toInt())
                    }
                } ?: stringResource(R.string.money_add_target_required),
                modifier = Modifier.fillMaxWidth(),
            )
            if (f.adjustmentMode == DateAdjustmentMode.NEXT_BILLING && sub.endDate != null) {
                Text(
                    stringResource(R.string.money_add_next_clears_end),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun PreviewCard(state: AddPaymentUiState, sub: Subscription) {
    val preview = state.preview ?: return
    SectionCard(title = stringResource(R.string.money_add_preview_title)) {
        val none = stringResource(R.string.money_add_preview_none)
        InfoRow(
            stringResource(R.string.money_add_preview_end),
            preview.endDate?.let { formatDate(it) } ?: none,
            valueColor = if (preview.endDateChanged) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified,
        )
        InfoRow(
            stringResource(R.string.money_add_preview_next),
            preview.nextPaymentDate?.let { formatDate(it) } ?: none,
            valueColor = if (preview.nextDateChanged) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified,
        )
        if (preview.reactivates) {
            StatusBadge(stringResource(R.string.money_add_preview_reactivate), tone = BadgeTone.SUCCESS)
            Spacer(Modifier.height(4.dp))
        }
        if (preview.priceUpdates) {
            Text(
                stringResource(
                    R.string.money_add_preview_price,
                    state.form.amount?.let { formatMoney(it, state.form.currencyCode, state.env.symbol(state.form.currencyCode)) } ?: "",
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        val explanation = when (preview.effect) {
            DateEffect.NOT_SUCCESSFUL -> R.string.money_add_effect_unsuccessful
            DateEffect.IN_APP_PURCHASE -> R.string.money_add_effect_iap
            DateEffect.OLDER_THAN_LATEST -> R.string.money_add_effect_older
            DateEffect.NO_CYCLE -> R.string.money_add_effect_no_cycle
            DateEffect.REGULAR -> R.string.money_add_effect_regular
            DateEffect.END_DATE -> R.string.money_add_effect_end_date
            DateEffect.NEXT_BILLING -> R.string.money_add_effect_next_billing
        }
        Text(stringResource(explanation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (sub.status == io.github.submark.core.model.SubscriptionStatus.PAUSED && !preview.reactivates) {
            Text(stringResource(R.string.money_add_preview_stays_paused), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OptionsSection(state: AddPaymentUiState, onUpdate: ((PaymentForm) -> PaymentForm) -> Unit) {
    val f = state.form
    val sub = state.subscription ?: return
    SectionCard(title = stringResource(R.string.money_add_section_options), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
        SettingsSwitchRow(
            title = stringResource(R.string.money_add_sync_price),
            subtitle = stringResource(R.string.money_add_sync_price_desc, formatMoney(sub.price, sub.currencyCode, state.env.symbol(sub.currencyCode))),
            checked = f.syncPrice,
            onCheckedChange = { c -> onUpdate { it.copy(syncPrice = c) } },
        )
        val success = f.status == PaymentStatus.SUCCESS
        val positive = (f.amount?.signum() ?: 0) > 0
        SettingsSwitchRow(
            title = stringResource(R.string.money_add_pay_wallet),
            subtitle = stringResource(
                when {
                    !success -> R.string.money_add_wallet_success_only
                    !positive -> R.string.money_add_wallet_positive_only
                    else -> R.string.money_add_pay_wallet_desc
                },
            ),
            checked = f.payWithWallet,
            onCheckedChange = { c -> onUpdate { it.copy(payWithWallet = c) } },
        )
        if (f.payWithWallet) {
            WalletSelector(
                choices = state.walletChoices,
                selectedId = f.walletId,
                onSelect = { id -> onUpdate { it.copy(walletId = id) } },
                symbols = state.env.symbols,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun SaveResultDialog(outcome: AddPaymentOutcome, state: AddPaymentUiState, onDismiss: () -> Unit) {
    val record = outcome.record
    val lines = buildList {
        add(
            stringResource(
                R.string.money_add_result_saved,
                formatMoney(record.amount, record.currencyCode, state.env.symbol(record.currencyCode)),
                formatDate(record.paymentDate),
            ),
        )
        val sub = state.subscription
        if (record.dateAdjustmentMode == DateAdjustmentMode.END_DATE) {
            outcome.endDate?.let { add(stringResource(R.string.money_add_result_end_date, formatDate(it))) }
        } else if (outcome.nextPaymentDate != null && outcome.nextPaymentDate != sub?.nextPaymentDate) {
            add(stringResource(R.string.money_add_result_next_date, formatDate(outcome.nextPaymentDate!!)))
        }
        if (outcome.priceUpdated) add(stringResource(R.string.money_add_result_price))
        if (outcome.reactivated) add(stringResource(R.string.money_add_result_reactivated))
        if (outcome.walletCharged) add(stringResource(R.string.money_add_result_wallet))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.money_add_result_title), fontWeight = FontWeight.SemiBold) },
        text = { Text(lines.joinToString("\n")) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.ui_action_done)) } },
    )
}
