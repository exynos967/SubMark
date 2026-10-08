package io.github.submark.feature.subscriptions.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.service.ExtendBy
import io.github.submark.core.model.SubscriptionPhoto
import io.github.submark.core.model.Wallet
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.core.ui.component.DatePickerField
import io.github.submark.core.ui.component.MoneyInputField
import io.github.submark.core.ui.component.SegmentedTabs
import io.github.submark.core.ui.format.MoneyInput
import io.github.submark.core.ui.format.asString
import io.github.submark.feature.subscriptions.R
import io.github.submark.feature.subscriptions.ui.common.toUiText
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExtendSheet(
    sheet: ExtendSheetState,
    currencySymbol: String?,
    today: LocalDate,
    busy: Boolean,
    onExtend: (ExtendBy, BigDecimal?) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(ExtendMode.MONTHS) }
    var valueText by rememberSaveable { mutableStateOf("1") }
    var date by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var feeText by rememberSaveable { mutableStateOf("") }
    val preview = DetailLogic.extendPreview(sheet.currentEnd, mode, valueText, date, today)
    val feeError = feeText.isNotBlank() && MoneyInput.validate(feeText, allowZero = true) != null
    val fee = MoneyInput.parse(feeText)?.takeIf { it.signum() > 0 }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.subscriptions_detail_extend_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.subscriptions_detail_extend_from, formatDay(sheet.currentEnd)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SegmentedTabs(items = ExtendMode.entries, selected = mode, onSelect = { mode = it }) {
                stringResource(
                    when (it) {
                        ExtendMode.DAYS -> R.string.subscriptions_detail_extend_mode_days
                        ExtendMode.MONTHS -> R.string.subscriptions_detail_extend_mode_months
                        ExtendMode.DATE -> R.string.subscriptions_detail_extend_mode_date
                    },
                )
            }
            if (mode == ExtendMode.DATE) {
                DatePickerField(
                    label = stringResource(R.string.subscriptions_detail_extend_new_end),
                    date = date,
                    onDateChange = { date = it },
                    today = today,
                    quickPicks = emptyList(),
                    minDate = maxOf(sheet.currentEnd, today).plusDays(1),
                    isError = preview.error == ExtendInputError.DATE_INVALID,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { text -> valueText = text.filter { it.isDigit() }.take(4) },
                    label = {
                        Text(
                            stringResource(
                                if (mode == ExtendMode.DAYS) R.string.subscriptions_detail_extend_days_label else R.string.subscriptions_detail_extend_months_label,
                            ),
                        )
                    },
                    isError = preview.error == ExtendInputError.OUT_OF_RANGE,
                    supportingText = {
                        Text(
                            stringResource(
                                if (mode == ExtendMode.DAYS) R.string.subscriptions_detail_extend_days_range else R.string.subscriptions_detail_extend_months_range,
                            ),
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                preview.newEnd?.let { stringResource(R.string.subscriptions_detail_extend_result, formatDay(it)) }
                    ?: stringResource(
                        when (preview.error) {
                            ExtendInputError.DATE_INVALID -> R.string.subscriptions_error_extend_date_invalid
                            ExtendInputError.OUT_OF_RANGE -> R.string.subscriptions_error_extend_value_out_of_range
                            else -> R.string.subscriptions_detail_extend_enter_value
                        },
                    ),
                style = MaterialTheme.typography.titleMedium,
                color = if (preview.error != null && preview.error != ExtendInputError.EMPTY) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            MoneyInputField(
                value = feeText,
                onValueChange = { feeText = it },
                label = stringResource(R.string.subscriptions_detail_extend_fee),
                currencySymbol = currencySymbol,
                allowZero = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(if (fee == null) R.string.subscriptions_detail_extend_free_notice else R.string.subscriptions_detail_extend_paid_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text(stringResource(io.github.submark.core.ui.R.string.ui_action_cancel)) }
                Button(
                    onClick = { preview.by?.let { onExtend(it, fee) } },
                    enabled = preview.by != null && !feeError && !busy,
                ) { Text(stringResource(R.string.subscriptions_detail_action_extend)) }
            }
        }
    }
}

@Composable
internal fun WishlistActivationDialog(
    name: String,
    wallets: List<Wallet>,
    walletProblem: DataError?,
    onCheckWallet: (String?) -> Unit,
    onConfirm: (toLifetime: Boolean, walletId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var toLifetime by rememberSaveable { mutableStateOf(false) }
    var walletId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(walletId, toLifetime) { onCheckWallet(walletId.takeIf { toLifetime }) }
    val usable = wallets.filter { it.isActive && it.deletedAt == null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subscriptions_detail_wishlist_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.subscriptions_detail_wishlist_message, name))
                Column(Modifier.selectableGroup()) {
                    ChoiceRow(stringResource(R.string.subscriptions_detail_wishlist_to_subscription), stringResource(R.string.subscriptions_detail_wishlist_to_subscription_desc), !toLifetime) { toLifetime = false }
                    ChoiceRow(stringResource(R.string.subscriptions_detail_wishlist_to_lifetime), stringResource(R.string.subscriptions_detail_wishlist_to_lifetime_desc), toLifetime) { toLifetime = true }
                }
                if (toLifetime && usable.isNotEmpty()) {
                    Text(stringResource(R.string.subscriptions_detail_wishlist_wallet), style = MaterialTheme.typography.labelLarge)
                    Column(Modifier.selectableGroup()) {
                        ChoiceRow(stringResource(R.string.subscriptions_detail_wishlist_no_wallet), null, walletId == null) { walletId = null }
                        usable.forEach { wallet -> ChoiceRow(wallet.name, null, walletId == wallet.id) { walletId = wallet.id } }
                    }
                    if (walletId != null && walletProblem != null) {
                        Text(walletProblem.toUiText().asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(toLifetime, walletId.takeIf { toLifetime }) },
                enabled = !(toLifetime && walletId != null && walletProblem != null),
            ) { Text(stringResource(R.string.subscriptions_detail_wishlist_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(io.github.submark.core.ui.R.string.ui_action_cancel)) } },
    )
}

@Composable
private fun ChoiceRow(title: String, description: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun TrialEndedDialog(
    name: String,
    trialEnd: LocalDate,
    onAuto: () -> Unit,
    onManual: () -> Unit,
    onDelete: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.subscriptions_detail_trial_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.subscriptions_detail_trial_message, name, formatDay(trialEnd)))
                Button(onClick = onAuto, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.subscriptions_detail_trial_auto)) }
                OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.subscriptions_detail_trial_manual)) }
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.subscriptions_detail_trial_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onLater) { Text(stringResource(R.string.subscriptions_detail_trial_later)) } },
    )
}

@Composable
internal fun DeleteDialog(state: DeleteDialogState, onDelete: (reverse: Boolean) -> Unit, onDismiss: () -> Unit) {
    val message = buildList {
        add(stringResource(R.string.subscriptions_detail_delete_message))
        if (state.childCount > 0) add(pluralStringResource(R.plurals.subscriptions_detail_delete_children, state.childCount, state.childCount))
        if (state.hasWalletCharges) add(stringResource(R.string.subscriptions_detail_delete_wallet_message))
    }.joinToString("\n\n")
    val title = stringResource(R.string.subscriptions_detail_delete_title, state.name)
    if (!state.hasWalletCharges) {
        ConfirmDialog(
            title = title,
            message = message,
            onConfirm = { onDelete(false) },
            onDismiss = onDismiss,
            confirmLabel = stringResource(io.github.submark.core.ui.R.string.ui_action_delete),
            destructive = true,
            icon = Icons.Rounded.Delete,
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message)
                OutlinedButton(onClick = { onDelete(false) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.subscriptions_detail_delete_keep_wallet))
                }
                Button(onClick = { onDelete(true) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.subscriptions_detail_delete_reverse_wallet))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(io.github.submark.core.ui.R.string.ui_action_cancel)) } },
    )
}

/** Full-screen photo viewer with a delete action. */
@Composable
internal fun PhotoViewer(photo: SubscriptionPhoto, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var confirm by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = photoFile(context, photo.fileName),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.subscriptions_detail_photo_close), tint = Color.White)
                }
                IconButton(onClick = { confirm = true }) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.subscriptions_detail_photo_delete), tint = Color.White)
                }
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = stringResource(R.string.subscriptions_detail_photo_delete_title),
            message = null,
            onConfirm = {
                confirm = false
                onDelete()
            },
            onDismiss = { confirm = false },
            confirmLabel = stringResource(io.github.submark.core.ui.R.string.ui_action_delete),
            destructive = true,
        )
    }
}
