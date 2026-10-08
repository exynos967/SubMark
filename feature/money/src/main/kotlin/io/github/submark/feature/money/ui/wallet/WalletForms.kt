package io.github.submark.feature.money.ui.wallet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.submark.core.model.WalletKind
import io.github.submark.core.model.WalletTxnType
import io.github.submark.core.ui.component.ColorPickerDialog
import io.github.submark.core.ui.component.CurrencyPickerSheet
import io.github.submark.core.ui.component.MoneyInputField
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.SettingsSwitchRow
import io.github.submark.core.ui.component.formatMoney
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.format.descriptionRes
import io.github.submark.core.ui.format.labelRes
import io.github.submark.core.ui.util.colorFromHex
import io.github.submark.core.ui.util.toHex
import io.github.submark.feature.money.R
import io.github.submark.feature.money.data.MoneyEnv
import io.github.submark.feature.money.ui.common.IconGridPickerDialog
import io.github.submark.feature.money.ui.common.InfoRow
import io.github.submark.feature.money.ui.common.PickerField
import io.github.submark.feature.money.ui.common.WalletIconBadge
import io.github.submark.feature.money.ui.common.toUiText
import io.github.submark.feature.money.ui.common.availableCredit
import io.github.submark.core.ui.R as CoreR

/** Top-up / deduct sheet with an after-balance preview and kind-rule errors. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletAmountSheet(
    sheet: WalletAmountState,
    env: MoneyEnv,
    onUpdate: ((WalletAmountState) -> WalletAmountState) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val wallet = sheet.wallet
    val isTopUp = sheet.type == WalletTxnType.TOP_UP
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).imePadding().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(if (isTopUp) R.string.money_wallet_top_up else R.string.money_wallet_deduct), style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                WalletIconBadge(wallet, size = 36)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(wallet.name, style = MaterialTheme.typography.titleMedium)
                    Text(formatMoney(wallet.balance, wallet.currencyCode, env.symbol(wallet.currencyCode)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            MoneyInputField(
                value = sheet.amountText,
                onValueChange = { v -> onUpdate { it.copy(amountText = v) } },
                label = stringResource(R.string.money_add_amount),
                currencySymbol = env.symbol(wallet.currencyCode),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = sheet.note,
                onValueChange = { v -> onUpdate { it.copy(note = v) } },
                label = { Text(stringResource(R.string.money_add_note)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val after = WalletManagementViewModel.afterBalance(wallet, sheet.type, sheet.amount)
            val problem = if (!isTopUp) WalletManagementViewModel.amountProblem(wallet, sheet.amount) else null
            SectionCard(title = stringResource(R.string.money_add_preview_title)) {
                InfoRow(stringResource(R.string.money_wallet_balance_label), formatMoney(wallet.balance, wallet.currencyCode, env.symbol(wallet.currencyCode)))
                if (after == null) {
                    Text(stringResource(R.string.money_wallet_enter_amount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    InfoRow(
                        stringResource(if (isTopUp) R.string.money_wallet_after_top_up else R.string.money_wallet_after_deduct),
                        formatMoney(after, wallet.currencyCode, env.symbol(wallet.currencyCode)),
                        valueColor = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
                problem?.let { Text(it.toUiText().asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (!isTopUp && wallet.kind == WalletKind.CREDIT) {
                    wallet.availableCredit()?.let {
                        InfoRow(stringResource(R.string.money_wallet_available_credit), formatMoney(it, wallet.currencyCode, env.symbol(wallet.currencyCode)))
                    }
                }
            }
            sheet.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
            Button(onClick = onSubmit, enabled = !sheet.saving && sheet.amount != null && problem == null, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(CoreR.string.ui_action_save))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Create / edit wallet form: name, kind, currency, initial balance / credit limit, style, active. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletFormSheet(
    form: WalletFormState,
    env: MoneyEnv,
    onUpdate: ((WalletFormState) -> WalletFormState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pickCurrency by rememberSaveable { mutableStateOf(false) }
    var pickIcon by rememberSaveable { mutableStateOf(false) }
    var pickColor by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).imePadding().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(if (form.id == null) R.string.money_wallet_create else R.string.money_wallet_edit), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.money_wallet_basic_section), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> onUpdate { it.copy(name = v) } },
                label = { Text(stringResource(R.string.money_wallet_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.money_wallet_kind), style = MaterialTheme.typography.labelLarge)
            WalletKind.entries.forEach { kind ->
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { onUpdate { it.copy(kind = kind) } }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = form.kind == kind, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(stringResource(kind.labelRes), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(kind.descriptionRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            PickerField(
                label = stringResource(R.string.money_wallet_currency),
                text = form.currencyCode.ifEmpty { stringResource(R.string.money_wallet_choose_currency) },
                onClick = { if (!form.currencyLocked) pickCurrency = true },
                enabled = !form.currencyLocked,
                supportingText = if (form.currencyLocked) stringResource(R.string.money_wallet_currency_locked) else null,
                modifier = Modifier.fillMaxWidth(),
            )
            MoneyInputField(
                value = form.initialText,
                onValueChange = { v -> onUpdate { it.copy(initialText = v) } },
                label = stringResource(R.string.money_wallet_initial_balance),
                currencySymbol = env.symbol(form.currencyCode),
                allowZero = true,
                enabled = form.id == null,
                modifier = Modifier.fillMaxWidth(),
            )
            if (form.id != null) {
                Text(stringResource(R.string.money_wallet_initial_locked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (form.kind == WalletKind.CREDIT) {
                MoneyInputField(
                    value = form.creditText,
                    onValueChange = { v -> onUpdate { it.copy(creditText = v) } },
                    label = stringResource(R.string.money_wallet_credit_limit),
                    currencySymbol = env.symbol(form.currencyCode),
                    allowZero = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.money_wallet_credit_limit_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.money_wallet_style_section), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    PickerField(
                        label = stringResource(R.string.money_wallet_color),
                        text = colorFromHex(form.colorHex)?.let { stringResource(R.string.money_wallet_custom_color) } ?: stringResource(R.string.money_wallet_default_color),
                        onClick = { pickColor = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(Modifier.weight(1f)) {
                    PickerField(
                        label = stringResource(R.string.money_wallet_icon),
                        text = form.iconValue ?: stringResource(R.string.money_wallet_default_icon),
                        onClick = { pickIcon = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            // Preview
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WalletIconBadge(
                        io.github.submark.core.model.Wallet(
                            name = form.name.ifBlank { "?" }, kind = form.kind, currencyCode = form.currencyCode.ifBlank { env.defaultCode },
                            colorHex = form.colorHex, iconValue = form.iconValue, createdAt = java.time.Instant.EPOCH, updatedAt = java.time.Instant.EPOCH,
                        ),
                        size = 40,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(form.name.ifBlank { stringResource(R.string.money_wallet_preview_name) }, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(form.kind.labelRes) + " · " + form.currencyCode.ifBlank { env.defaultCode }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (form.id != null && form.active) {
                SettingsSwitchRow(
                    title = stringResource(R.string.money_wallet_active),
                    subtitle = stringResource(if (form.linkedCount > 0) R.string.money_wallet_active_linked else R.string.money_wallet_active_none),
                    checked = form.active,
                    onCheckedChange = { c -> onUpdate { it.copy(active = c) } },
                )
            }
            form.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
            Button(onClick = onSave, enabled = !form.saving && form.name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(CoreR.string.ui_action_save))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (pickCurrency) {
        CurrencyPickerSheet(
            currencies = env.currencies.values.filter { it.isEnabled },
            selectedCode = form.currencyCode,
            defaultCode = env.defaultCode,
            onSelect = { c -> onUpdate { it.copy(currencyCode = c.code) }; pickCurrency = false },
            onDismiss = { pickCurrency = false },
        )
    }
    if (pickIcon) {
        IconGridPickerDialog(
            selected = form.iconValue,
            onSelect = { name -> onUpdate { it.copy(iconValue = name) }; pickIcon = false },
            onDismiss = { pickIcon = false },
        )
    }
    if (pickColor) {
        ColorPickerDialog(
            initial = colorFromHex(form.colorHex),
            onConfirm = { color -> onUpdate { it.copy(colorHex = color?.toHex()) }; pickColor = false },
            onDismiss = { pickColor = false },
        )
    }
}
