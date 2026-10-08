package io.github.submark.feature.money.ui.paymentmethod

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.model.PaymentMethod
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.SectionHeader
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.icon.IconCatalog
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.core.ui.format.asString
import io.github.submark.feature.money.R
import io.github.submark.feature.money.ui.common.IconGridPickerDialog
import io.github.submark.feature.money.ui.common.PickerField
import io.github.submark.core.ui.R as CoreR

@Composable
fun PaymentMethodManagementRoute(onBack: () -> Unit, viewModel: PaymentMethodViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    SnackbarEffect(viewModel.snackbar, host)
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PaymentMethod?>(null) }

    PaymentMethodScreen(
        state = state,
        snackbarHost = host,
        onBack = onBack,
        onAdd = viewModel::openAdd,
        onEdit = viewModel::openEdit,
        onDelete = { pendingDelete = it },
        onReset = { confirmReset = true },
        onCloseForm = viewModel::closeForm,
        onUpdateForm = viewModel::updateForm,
        onSaveForm = viewModel::saveForm,
    )
    pendingDelete?.let { m ->
        ConfirmDialog(
            title = stringResource(R.string.money_pm_delete_title, m.name),
            message = stringResource(R.string.money_pm_delete_message),
            onConfirm = { pendingDelete = null; viewModel.delete(m) },
            onDismiss = { pendingDelete = null },
            confirmLabel = stringResource(CoreR.string.ui_action_delete),
            destructive = true,
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.money_pm_reset_title),
            message = stringResource(R.string.money_pm_reset_message),
            onConfirm = { confirmReset = false; viewModel.resetToDefault() },
            onDismiss = { confirmReset = false },
            confirmLabel = stringResource(R.string.money_pm_reset_button),
            destructive = true,
        )
    }
}

@Composable
fun PaymentMethodScreen(
    state: PaymentMethodUiState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (PaymentMethod) -> Unit,
    onDelete: (PaymentMethod) -> Unit,
    onReset: () -> Unit,
    onCloseForm: () -> Unit,
    onUpdateForm: ((PaymentMethodForm) -> PaymentMethodForm) -> Unit,
    onSaveForm: () -> Unit,
) {
    Scaffold(
        topBar = {
            SubMarkTopAppBar(
                title = stringResource(R.string.money_pm_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = onAdd, enabled = true) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.money_pm_add_cd))
                    }
                },
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.system.isNotEmpty()) {
                item(key = "system_header") { SectionHeader(stringResource(R.string.money_pm_system)) }
                items(state.system, key = { "s_${it.id}" }) { m ->
                    MethodRow(m, isCustom = false, onEdit = null, onDelete = null)
                }
            }
            item(key = "custom_header") {
                SectionHeader(stringResource(R.string.money_pm_custom))
                Text(
                    stringResource(R.string.money_pm_custom_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(4.dp))
            }
            if (state.custom.isEmpty()) {
                item(key = "custom_empty") {
                    EmptyState(
                        title = stringResource(R.string.money_pm_no_custom),
                        icon = Icons.Rounded.CreditCard,
                    )
                }
            }
            items(state.custom, key = { "c_${it.id}" }) { m ->
                MethodRow(m, isCustom = true, onEdit = { onEdit(m) }, onDelete = { onDelete(m) })
            }
            item(key = "danger") {
                Spacer(Modifier.height(8.dp))
                SectionCard {
                    Text(stringResource(R.string.money_pm_reset_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.money_pm_reset_button), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    state.form?.let { PaymentMethodFormSheet(it, onUpdateForm, onSaveForm, onCloseForm) }
}

@Composable
private fun MethodRow(
    method: PaymentMethod,
    isCustom: Boolean,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    SectionCard(contentPadding = PaddingValues(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    IconCatalog.vector(method.iconValue) ?: Icons.Rounded.CreditCard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(method.name, Modifier.weight(1f))
            if (isCustom) {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.money_more_actions_for, method.name))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        onEdit?.let { action ->
                            DropdownMenuItem(text = { Text(stringResource(CoreR.string.ui_action_edit)) }, onClick = { menu = false; action() })
                        }
                        onDelete?.let { action ->
                            DropdownMenuItem(
                                text = { Text(stringResource(CoreR.string.ui_action_delete), color = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; action() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentMethodFormSheet(
    form: PaymentMethodForm,
    onUpdate: ((PaymentMethodForm) -> PaymentMethodForm) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pickIcon by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).imePadding().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(if (form.id == null) R.string.money_pm_add else R.string.money_pm_edit), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> onUpdate { it.copy(name = v) } },
                label = { Text(stringResource(R.string.money_pm_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            PickerField(
                label = stringResource(R.string.money_pm_icon),
                text = form.iconValue ?: "credit_card",
                onClick = { pickIcon = true },
                modifier = Modifier.fillMaxWidth(),
            )
            form.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
            Button(onClick = onSave, enabled = !form.saving && form.name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(CoreR.string.ui_action_save))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (pickIcon) {
        IconGridPickerDialog(
            selected = form.iconValue,
            onSelect = { name -> onUpdate { it.copy(iconValue = name) }; pickIcon = false },
            onDismiss = { pickIcon = false },
        )
    }
}
