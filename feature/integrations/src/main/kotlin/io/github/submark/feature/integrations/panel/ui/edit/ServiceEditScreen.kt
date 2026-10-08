package io.github.submark.feature.integrations.panel.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.model.ServiceType
import io.github.submark.core.ui.component.ColorPickerDialog
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.SettingsValueRow
import io.github.submark.core.ui.component.SettingsSwitchRow
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.util.colorFromHex
import io.github.submark.core.ui.util.toHex
import io.github.submark.feature.integrations.R
import io.github.submark.feature.integrations.panel.ui.formatInstant
import io.github.submark.feature.integrations.panel.ui.messageRes
import java.time.Instant

@Composable
fun ServiceEditScreenRoute(
    onBack: () -> Unit,
    viewModel: ServiceEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ServiceEditEvent.Saved -> onBack()
                ServiceEditEvent.TestSuccess -> snackbarHostState.showSnackbar(context.getString(R.string.panel_service_test_success))
                is ServiceEditEvent.Message -> snackbarHostState.showSnackbar(context.getString(event.reason.messageRes()))
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.panel_service_delete),
            message = stringResource(R.string.panel_service_delete_message, state.name),
            onConfirm = {
                showDeleteConfirm = false
                viewModel.delete()
            },
            onDismiss = { showDeleteConfirm = false },
            confirmLabel = stringResource(io.github.submark.core.ui.R.string.ui_action_delete),
            destructive = true,
        )
    }

    ServiceEditScreen(
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
fun ServiceEditScreen(
    state: ServiceEditUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onUpdate: ((ServiceEditUiState) -> ServiceEditUiState) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showColorPicker by remember { mutableStateOf(false) }

    if (showColorPicker) {
        ColorPickerDialog(
            initial = colorFromHex(state.colorHex),
            onConfirm = { color ->
                showColorPicker = false
                onUpdate { it.copy(colorHex = color?.toHex()) }
            },
            onDismiss = { showColorPicker = false },
        )
    }

    Scaffold(
        topBar = {
            SubMarkTopAppBar(
                title = stringResource(if (state.isEdit) R.string.panel_service_edit_title else R.string.panel_service_add_title),
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
            Text(stringResource(R.string.panel_service_type), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ServiceType.entries.forEach { type ->
                    FilterChip(
                        selected = state.type == type,
                        onClick = { onUpdate { it.copy(type = type) } },
                        label = {
                            Text(stringResource(if (type == ServiceType.CLASH) R.string.panel_service_type_clash else R.string.panel_service_type_emby))
                        },
                    )
                }
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = { v -> onUpdate { it.copy(name = v) } },
                label = { Text(stringResource(R.string.panel_service_name)) },
                isError = state.fieldError == ServiceFieldError.NAME_REQUIRED,
                supportingText = {
                    if (state.fieldError == ServiceFieldError.NAME_REQUIRED) {
                        Text(stringResource(R.string.panel_service_name_required))
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.url,
                onValueChange = { v -> onUpdate { it.copy(url = v) } },
                label = { Text(stringResource(R.string.panel_service_url)) },
                placeholder = {
                    Text(stringResource(
                        if (state.type == ServiceType.CLASH) R.string.panel_service_url_clash_hint else R.string.panel_service_url_emby_hint
                    ))
                },
                isError = state.fieldError == ServiceFieldError.URL_REQUIRED || state.fieldError == ServiceFieldError.URL_INVALID,
                supportingText = {
                    when (state.fieldError) {
                        ServiceFieldError.URL_REQUIRED -> Text(stringResource(R.string.panel_service_url_required))
                        ServiceFieldError.URL_INVALID -> Text(stringResource(R.string.panel_service_url_invalid))
                        else -> Unit
                    }
                },
                trailingIcon = {
                    TextButton(onClick = {
                        clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let { pasted ->
                            onUpdate { it.copy(url = pasted.trim()) }
                        }
                    }) {
                        Text(stringResource(R.string.panel_service_paste))
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.type == ServiceType.EMBY) {
                OutlinedTextField(
                    value = state.username,
                    onValueChange = { v -> onUpdate { it.copy(username = v) } },
                    label = { Text(stringResource(R.string.panel_service_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = { v -> onUpdate { it.copy(password = v) } },
                    label = { Text(stringResource(R.string.panel_service_password)) },
                    supportingText = {
                        if (state.isEdit && state.hasStoredPassword) {
                            Text(stringResource(R.string.panel_service_password_keep))
                        }
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = { v -> onUpdate { it.copy(apiKey = v) } },
                    label = { Text(stringResource(R.string.panel_service_api_key)) },
                    isError = state.fieldError == ServiceFieldError.AUTH_REQUIRED,
                    supportingText = {
                        when {
                            state.fieldError == ServiceFieldError.AUTH_REQUIRED ->
                                Text(stringResource(R.string.panel_service_auth_required))
                            state.isEdit && state.hasStoredApiKey ->
                                Text(stringResource(R.string.panel_service_api_key_keep))
                        }
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.panel_service_auto_refresh),
                    checked = state.autoRefresh,
                    onCheckedChange = { v -> onUpdate { it.copy(autoRefresh = v) } },
                )
            }
            if (state.autoRefresh) {
                OutlinedTextField(
                    value = state.intervalMinutes,
                    onValueChange = { v -> onUpdate { it.copy(intervalMinutes = v.filter(Char::isDigit).take(4)) } },
                    label = { Text(stringResource(R.string.panel_service_interval)) },
                    isError = state.fieldError == ServiceFieldError.INTERVAL_INVALID,
                    supportingText = {
                        Text(stringResource(if (state.fieldError == ServiceFieldError.INTERVAL_INVALID) R.string.panel_service_interval_invalid else R.string.panel_service_interval_min))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.panel_service_enabled),
                    checked = state.enabled,
                    onCheckedChange = { v -> onUpdate { it.copy(enabled = v) } },
                )
            }

            // Card color
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val color = colorFromHex(state.colorHex)
                Box(
                    Modifier
                        .size(28.dp)
                        .background(color ?: MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                )
                Text(
                    stringResource(R.string.panel_service_color),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showColorPicker = true }) {
                    Text(state.colorHex ?: stringResource(R.string.panel_service_color_default))
                }
            }

            if (state.isEdit) {
                SectionCard {
                    state.createdAtSeconds?.let {
                        SettingsValueRow(
                            title = stringResource(R.string.panel_service_created, formatInstant(Instant.ofEpochSecond(it))),
                            value = "",
                        )
                    }
                    SettingsValueRow(
                        title = stringResource(
                            R.string.panel_service_last_refresh,
                            state.lastRefreshSeconds?.let { formatInstant(Instant.ofEpochSecond(it)) }
                                ?: stringResource(R.string.panel_service_never),
                        ),
                        value = "",
                    )
                    state.lastError?.let { reason ->
                        val label = runCatching {
                            io.github.submark.feature.integrations.panel.data.PanelErrorReason.valueOf(reason)
                        }.getOrNull()?.let { r -> stringResource(r.messageRes()) } ?: reason
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            OutlinedButton(onClick = onTest, enabled = !state.testing && !state.saving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.testing) R.string.panel_service_testing else R.string.panel_service_test))
            }

            if (state.isEdit) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        stringResource(R.string.panel_service_delete),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
