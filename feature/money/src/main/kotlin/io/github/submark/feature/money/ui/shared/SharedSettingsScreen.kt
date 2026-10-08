package io.github.submark.feature.money.ui.shared

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.model.MemberStatus
import io.github.submark.core.model.SharedMember
import io.github.submark.core.model.SplitMode
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.core.ui.component.CurrencyPickerSheet
import io.github.submark.core.ui.component.DatePickerField
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.MoneyInputField
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.SegmentedTabs
import io.github.submark.core.ui.component.StatusBadge
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.component.formatMoney
import io.github.submark.core.ui.format.BadgeTone
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.format.descriptionRes
import io.github.submark.core.ui.format.labelRes
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.feature.money.R
import io.github.submark.feature.money.ui.common.InfoRow
import io.github.submark.feature.money.ui.common.PickerField
import io.github.submark.feature.money.ui.common.StatCell
import io.github.submark.feature.money.ui.common.rememberMoneyFormatter
import io.github.submark.core.ui.R as CoreR

@Composable
fun SharedSettingsRoute(onBack: () -> Unit, viewModel: SharedSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    SnackbarEffect(viewModel.snackbar, host)
    SharedSettingsScreen(
        state = state,
        snackbarHost = host,
        onBack = onBack,
        onEnable = viewModel::enable,
        onDisable = viewModel::disable,
        onSplitMode = viewModel::setSplitMode,
        onSaveDescription = viewModel::saveDescription,
        onAddMember = viewModel::newMember,
        onEditMember = viewModel::editMember,
        onDeleteMember = viewModel::deleteMember,
        onUpdateDraft = viewModel::updateDraft,
        onSaveDraft = viewModel::saveDraft,
        onCloseDraft = viewModel::closeDraft,
    )
}

@Composable
fun SharedSettingsScreen(
    state: SharedSettingsUiState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onEnable: (String) -> Unit,
    onDisable: () -> Unit,
    onSplitMode: (SplitMode) -> Unit,
    onSaveDescription: (String) -> Unit,
    onAddMember: () -> Unit,
    onEditMember: (SharedMember) -> Unit,
    onDeleteMember: (SharedMember) -> Unit,
    onUpdateDraft: ((MemberDraft) -> MemberDraft) -> Unit,
    onSaveDraft: () -> Unit,
    onCloseDraft: () -> Unit,
) {
    var confirmDisable by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SharedMember?>(null) }
    Scaffold(
        topBar = { SubMarkTopAppBar(title = stringResource(R.string.money_shared_title), subtitle = state.subscription?.name, onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        val sub = state.subscription
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            sub == null -> EmptyState(stringResource(R.string.money_subscription_missing), Modifier.padding(padding))
            !state.enabled -> EnableSharing(state, onEnable, Modifier.padding(padding))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val env = state.env
                val preview = state.preview
                val money = rememberMoneyFormatter(sub.currencyCode, env.symbol(sub.currencyCode))
                SectionCard(title = stringResource(R.string.money_shared_status)) {
                    Row {
                        StatCell(stringResource(R.string.money_shared_total_cost), money(sub.price), Modifier.weight(1f))
                        StatCell(
                            stringResource(R.string.money_shared_members),
                            stringResource(R.string.money_shared_member_count, preview?.activeCount ?: 0, state.members.size),
                            Modifier.weight(1f),
                        )
                    }
                }
                SectionCard(title = stringResource(R.string.money_shared_split_mode)) {
                    SplitMode.entries.forEach { mode ->
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !state.busy, role = Role.RadioButton) { onSplitMode(mode) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = state.config?.splitMode == mode, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(stringResource(mode.labelRes), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(mode.descriptionRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                SectionCard(
                    title = stringResource(R.string.money_shared_member_list),
                    actionLabel = stringResource(R.string.money_shared_add_member),
                    onAction = onAddMember,
                ) {
                    state.members.forEachIndexed { index, member ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        MemberRow(
                            member = member,
                            share = preview?.shares?.firstOrNull { it.member.id == member.id }?.share,
                            mode = state.config?.splitMode ?: SplitMode.EQUAL,
                            money = money,
                            fixedText = member.fixedAmount?.let { formatMoney(it, member.paymentCurrencyCode ?: sub.currencyCode, env.symbol(member.paymentCurrencyCode ?: sub.currencyCode)) },
                            onClick = { onEditMember(member) },
                            onDelete = if (member.isCreator) null else ({ pendingDelete = member }),
                        )
                    }
                }
                if (preview != null) {
                    SectionCard(title = stringResource(R.string.money_shared_preview)) {
                        preview.shares.forEach { s ->
                            InfoRow(s.member.name, money(s.share))
                        }
                        if (preview.mode == SplitMode.RATIO) {
                            InfoRow(stringResource(R.string.money_shared_ratio_total), "${preview.ratioTotal.stripTrailingZeros().toPlainString()}%")
                        }
                        preview.warnings.forEach { w ->
                            val text = when (w) {
                                SplitWarning.NO_ACTIVE_MEMBERS -> stringResource(R.string.money_shared_warn_no_active)
                                SplitWarning.NO_CREATOR -> stringResource(R.string.money_invalid_creator_required)
                                SplitWarning.RATIO_MISMATCH -> stringResource(R.string.money_shared_warn_ratio, preview.ratioTotal.stripTrailingZeros().toPlainString())
                                SplitWarning.FIXED_EXCESS -> stringResource(R.string.money_shared_warn_excess, money(preview.fixedDifference))
                                SplitWarning.FIXED_REMAINING -> stringResource(R.string.money_shared_warn_remaining, money(preview.fixedDifference.negate()))
                                SplitWarning.FIXED_RATE_MISSING -> stringResource(R.string.money_shared_warn_rate)
                            }
                            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(end = 6.dp))
                                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                DescriptionCard(state.config?.description.orEmpty(), onSaveDescription)
                OutlinedButton(
                    onClick = { confirmDisable = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.money_shared_disable)) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmDisable) {
        ConfirmDialog(
            title = stringResource(R.string.money_shared_disable_title),
            message = stringResource(R.string.money_shared_disable_message),
            onConfirm = { confirmDisable = false; onDisable() },
            onDismiss = { confirmDisable = false },
            confirmLabel = stringResource(R.string.money_shared_disable),
            destructive = true,
        )
    }
    pendingDelete?.let { m ->
        ConfirmDialog(
            title = stringResource(R.string.money_shared_delete_member_title),
            message = stringResource(R.string.money_shared_delete_member_message, m.name),
            onConfirm = { pendingDelete = null; onDeleteMember(m) },
            onDismiss = { pendingDelete = null },
            confirmLabel = stringResource(CoreR.string.ui_action_delete),
            destructive = true,
        )
    }
    state.draft?.let { MemberEditorSheet(it, state, onUpdateDraft, onSaveDraft, onCloseDraft) }
}

@Composable
private fun EnableSharing(state: SharedSettingsUiState, onEnable: (String) -> Unit, modifier: Modifier) {
    val defaultName = stringResource(R.string.money_shared_creator_default)
    var name by rememberSaveable { mutableStateOf(defaultName) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EmptyState(
            title = stringResource(R.string.money_shared_off_title),
            message = stringResource(R.string.money_shared_off_message),
            icon = Icons.Rounded.Group,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.money_shared_creator_name)) },
            singleLine = true,
            isError = name.isBlank(),
            supportingText = { Text(stringResource(R.string.money_shared_creator_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onEnable(name.trim()) }, enabled = name.isNotBlank() && !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.money_shared_enable))
        }
    }
}

@Composable
private fun MemberRow(
    member: SharedMember,
    share: java.math.BigDecimal?,
    mode: SplitMode,
    money: (java.math.BigDecimal) -> String,
    fixedText: String?,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(member.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (member.isCreator) StatusBadge(stringResource(R.string.money_shared_creator_badge), tone = BadgeTone.PRIMARY)
                if (member.status != MemberStatus.ACTIVE) {
                    StatusBadge(stringResource(member.status.labelRes), tone = if (member.status == MemberStatus.PENDING) BadgeTone.WARNING else BadgeTone.NEUTRAL)
                }
            }
            val detail = when (mode) {
                SplitMode.RATIO, SplitMode.EQUAL -> member.ratioPercent?.let { "${it.stripTrailingZeros().toPlainString()}%" }
                SplitMode.FIXED_AMOUNT -> fixedText
                SplitMode.CREATOR_PAYS -> null
            }
            val sub = listOfNotNull(detail, member.email).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (mode == SplitMode.CREATOR_PAYS && !member.isCreator) {
                Text(stringResource(R.string.money_shared_no_payment), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        share?.let { Text(money(it), style = MaterialTheme.typography.titleSmall) }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.money_shared_delete_member_cd, member.name))
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

@Composable
private fun DescriptionCard(current: String, onSave: (String) -> Unit) {
    var text by rememberSaveable(current) { mutableStateOf(current) }
    SectionCard(title = stringResource(R.string.money_shared_description)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.money_shared_description_hint)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        if (text != current) {
            TextButton(onClick = { onSave(text) }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(CoreR.string.ui_action_save)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberEditorSheet(
    draft: MemberDraft,
    state: SharedSettingsUiState,
    onUpdate: ((MemberDraft) -> MemberDraft) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mode = state.config?.splitMode ?: SplitMode.EQUAL
    var pickCurrency by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).imePadding().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(if (draft.id == null) R.string.money_shared_add_member else R.string.money_shared_edit_member),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = draft.name,
                onValueChange = { v -> onUpdate { it.copy(name = v, error = null) } },
                label = { Text(stringResource(R.string.money_shared_member_name)) },
                singleLine = true,
                isError = draft.name.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.email,
                onValueChange = { v -> onUpdate { it.copy(email = v, error = null) } },
                label = { Text(stringResource(R.string.money_shared_member_email)) },
                singleLine = true,
                isError = !draft.emailValid,
                supportingText = if (!draft.emailValid) ({ Text(stringResource(R.string.money_shared_email_invalid)) }) else null,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.note,
                onValueChange = { v -> onUpdate { it.copy(note = v) } },
                label = { Text(stringResource(R.string.money_shared_member_note)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.money_shared_member_status), style = MaterialTheme.typography.labelLarge)
            SegmentedTabs(MemberStatus.entries, draft.status, { s -> onUpdate { it.copy(status = s) } }) { stringResource(it.labelRes) }
            Text(
                stringResource(
                    when (draft.status) {
                        MemberStatus.ACTIVE -> R.string.money_shared_status_active_desc
                        MemberStatus.INACTIVE -> R.string.money_shared_status_inactive_desc
                        MemberStatus.PENDING -> R.string.money_shared_status_pending_desc
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (mode) {
                SplitMode.RATIO -> {
                    Text(stringResource(R.string.money_shared_ratio_label, draft.ratio.toInt()), style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = draft.ratio,
                        onValueChange = { v -> onUpdate { it.copy(ratio = kotlin.math.round(v)) } },
                        valueRange = 0f..100f,
                        steps = 99,
                    )
                }
                SplitMode.FIXED_AMOUNT -> {
                    MoneyInputField(
                        value = draft.fixedText,
                        onValueChange = { v -> onUpdate { it.copy(fixedText = v, error = null) } },
                        label = stringResource(R.string.money_shared_fixed_amount),
                        currencySymbol = state.env.symbol(draft.currencyCode),
                        allowZero = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SplitMode.EQUAL -> Text(stringResource(R.string.money_shared_equal_auto), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SplitMode.CREATOR_PAYS -> Unit
            }
            PickerField(
                label = stringResource(R.string.money_shared_payment_currency),
                text = draft.currencyCode,
                onClick = { pickCurrency = true },
                modifier = Modifier.fillMaxWidth(),
            )
            DatePickerField(
                label = stringResource(R.string.money_shared_joined),
                date = draft.joinedAt,
                onDateChange = { d -> onUpdate { it.copy(joinedAt = d) } },
                today = state.today,
                modifier = Modifier.fillMaxWidth(),
            )
            draft.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
            Button(onClick = onSave, enabled = !draft.saving && draft.name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.PersonAdd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(CoreR.string.ui_action_save))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (pickCurrency) {
        CurrencyPickerSheet(
            currencies = state.currencies,
            selectedCode = draft.currencyCode,
            defaultCode = state.env.defaultCode,
            onSelect = { c -> onUpdate { it.copy(currencyCode = c.code) }; pickCurrency = false },
            onDismiss = { pickCurrency = false },
        )
    }
}
