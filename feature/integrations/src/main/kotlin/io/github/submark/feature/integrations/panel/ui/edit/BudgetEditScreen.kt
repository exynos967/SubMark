package io.github.submark.feature.integrations.panel.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.model.ApiServiceType
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.MoneyInputField
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.SettingsSwitchRow
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.feature.integrations.R
import io.github.submark.feature.integrations.panel.ui.displayName
import io.github.submark.feature.integrations.panel.ui.messageRes

@Composable
fun BudgetEditScreenRoute(
    onBack: () -> Unit,
    viewModel: BudgetEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDuplicateInfo by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                BudgetEditEvent.Saved -> onBack()
                BudgetEditEvent.TestSuccess -> snackbarHostState.showSnackbar(context.getString(R.string.panel_budget_test_success))
                BudgetEditEvent.DuplicateType -> showDuplicateInfo = true
                is BudgetEditEvent.Message -> snackbarHostState.showSnackbar(context.getString(event.reason.messageRes()))
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.panel_budget_delete),
            message = stringResource(R.string.panel_budget_delete_message, state.name),
            onConfirm = {
                showDeleteConfirm = false
                viewModel.delete()
            },
            onDismiss = { showDeleteConfirm = false },
            confirmLabel = stringResource(io.github.submark.core.ui.R.string.ui_action_delete),
            destructive = true,
        )
    }
    if (showDuplicateInfo) {
        AlertDialog(
            onDismissRequest = { showDuplicateInfo = false },
            title = { Text(stringResource(R.string.panel_budget_duplicate_title)) },
            text = { Text(stringResource(R.string.panel_budget_duplicate_message)) },
            confirmButton = {
                TextButton(onClick = { showDuplicateInfo = false }) {
                    Text(stringResource(io.github.submark.core.ui.R.string.ui_action_ok))
                }
            },
        )
    }

    BudgetEditScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onUpdate = viewModel::update,
        onSave = viewModel::save,
        onTest = viewModel::testConnection,
        onDelete = { showDeleteConfirm = true },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetEditScreen(
    state: BudgetEditUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onUpdate: ((BudgetEditUiState) -> BudgetEditUiState) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
) {
    Scaffold(
        topBar = {
            SubMarkTopAppBar(
                title = stringResource(if (state.isEdit) R.string.panel_budget_edit_title else R.string.panel_budget_add_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = onSave, enabled = !state.saving && !state.testing) {
                        if (state.saving) {
                            CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Check, contentDescription = stringResource(io.github.submark.core.ui.R.string.ui_action_save))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Service type (locked on edit)
            Text(stringResource(R.string.panel_budget_type), style = MaterialTheme.typography.titleSmall)
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ApiServiceType.entries.forEach { type ->
                    FilterChip(
                        selected = state.serviceType == type,
                        onClick = { if (!state.isEdit) onUpdate { it.copy(serviceType = type) } },
                        label = { Text(type.displayName()) },
                        enabled = !state.isEdit,
                    )
                }
            }
            if (state.isEdit) {
                Text(
                    stringResource(R.string.panel_budget_type_locked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(
                    when (state.serviceType) {
                        ApiServiceType.DEEPSEEK -> R.string.panel_guide_deepseek
                        ApiServiceType.NEWAPI -> R.string.panel_guide_newapi
                        ApiServiceType.VAPI -> R.string.panel_guide_vapi
                        ApiServiceType.PACKY -> R.string.panel_guide_packy
                        ApiServiceType.ZAI -> R.string.panel_guide_zai
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.name,
                onValueChange = { v -> onUpdate { it.copy(name = v) } },
                label = { Text(stringResource(R.string.panel_budget_name)) },
                isError = state.fieldError == BudgetFieldError.NAME_REQUIRED,
                supportingText = {
                    if (state.fieldError == BudgetFieldError.NAME_REQUIRED) {
                        Text(stringResource(R.string.panel_budget_name_required))
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = { v -> onUpdate { it.copy(apiKey = v) } },
                label = { Text(stringResource(R.string.panel_budget_api_key)) },
                isError = state.fieldError == BudgetFieldError.KEY_REQUIRED,
                supportingText = {
                    when {
                        state.fieldError == BudgetFieldError.KEY_REQUIRED ->
                            Text(stringResource(R.string.panel_budget_key_required))
                        state.isEdit && state.hasStoredKey ->
                            Text(stringResource(R.string.panel_budget_api_key_keep))
                    }
                },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            val needsBaseUrl = state.serviceType == ApiServiceType.NEWAPI
            val optionalBaseUrl = state.serviceType == ApiServiceType.PACKY || state.serviceType == ApiServiceType.VAPI
            if (needsBaseUrl || optionalBaseUrl) {
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = { v -> onUpdate { it.copy(baseUrl = v) } },
                    label = {
                        Text(stringResource(if (needsBaseUrl) R.string.panel_budget_base_url else R.string.panel_budget_base_url_optional))
                    },
                    isError = state.fieldError == BudgetFieldError.BASE_URL_REQUIRED,
                    supportingText = {
                        if (state.fieldError == BudgetFieldError.BASE_URL_REQUIRED) {
                            Text(stringResource(R.string.panel_budget_base_url_required))
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.serviceType == ApiServiceType.NEWAPI || state.serviceType == ApiServiceType.VAPI) {
                OutlinedTextField(
                    value = state.userId,
                    onValueChange = { v -> onUpdate { it.copy(userId = v) } },
                    label = { Text(stringResource(R.string.panel_budget_user_id_optional)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = state.currencyCode,
                onValueChange = { v -> onUpdate { it.copy(currencyCode = v.uppercase().take(10)) } },
                label = { Text(stringResource(R.string.panel_budget_currency)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            MoneyInputField(
                value = state.monthlyBudget,
                onValueChange = { v -> onUpdate { it.copy(monthlyBudget = v) } },
                label = stringResource(R.string.panel_budget_monthly),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                SettingsSwitchRow(
                    horizontalPadding = 0.dp,
                    title = stringResource(R.string.panel_budget_daily_enabled),
                    checked = state.dailyEnabled,
                    onCheckedChange = { v -> onUpdate { it.copy(dailyEnabled = v) } },
                )
            }
            if (state.dailyEnabled) {
                MoneyInputField(
                    value = state.dailyBudget,
                    onValueChange = { v -> onUpdate { it.copy(dailyBudget = v) } },
                    label = stringResource(R.string.panel_budget_daily),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(stringResource(R.string.panel_budget_threshold), style = MaterialTheme.typography.titleSmall)
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BudgetEditViewModel.THRESHOLDS.forEach { threshold ->
                    FilterChip(
                        selected = state.alertThreshold == threshold,
                        onClick = { onUpdate { it.copy(alertThreshold = threshold) } },
                        label = { Text(stringResource(R.string.panel_budget_threshold_value, threshold)) },
                    )
                }
            }

            SectionCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                SettingsSwitchRow(
                    horizontalPadding = 0.dp,
                    title = stringResource(R.string.panel_budget_enabled),
                    checked = state.enabled,
                    onCheckedChange = { v -> onUpdate { it.copy(enabled = v) } },
                )
            }

            OutlinedButton(onClick = onTest, enabled = !state.testing && !state.saving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.testing) R.string.panel_budget_testing else R.string.panel_budget_test))
            }

            if (state.isEdit) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        stringResource(R.string.panel_budget_delete),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
