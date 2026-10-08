package io.github.submark.feature.backup.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.submark.core.model.BackupFrequency
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.core.ui.component.SettingsGroup
import io.github.submark.core.ui.component.SettingsSwitchRow
import io.github.submark.core.ui.component.SettingsValueRow
import io.github.submark.core.ui.format.asString
import io.github.submark.feature.backup.R
import io.github.submark.feature.backup.data.WebDavClient
import io.github.submark.feature.backup.data.WebDavError
import io.github.submark.feature.backup.ui.common.testStepLabel
import io.github.submark.feature.backup.ui.common.toUiText
import androidx.compose.ui.res.stringResource

/** Stateless editor body; wiring lives in the Route wrapper above. */
@Composable
internal fun ProfileEditorScreen(
    state: ProfileEditorUiState,
    callbacks: ProfileEditorCallbacks,
) {
    SettingsGroup(title = null) {
        EditorField(
            value = state.name,
            onValueChange = callbacks.onName,
            label = stringResource(R.string.backup_editor_name),
            error = state.nameError,
        )
    }

    SettingsGroup(title = stringResource(R.string.backup_editor_section_connection)) {
        EditorField(
            value = state.serverUrl,
            onValueChange = callbacks.onServerUrl,
            label = stringResource(R.string.backup_editor_server_url),
            placeholder = stringResource(R.string.backup_editor_server_url_hint),
            error = state.urlError,
            keyboardType = KeyboardType.Uri,
        )
        EditorField(
            value = state.remotePath,
            onValueChange = callbacks.onRemotePath,
            label = stringResource(R.string.backup_editor_remote_path),
            error = state.pathError,
        )
        EditorField(
            value = state.username,
            onValueChange = callbacks.onUsername,
            label = stringResource(R.string.backup_editor_username),
            error = state.usernameError,
            leading = { Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        )
        EditorField(
            value = state.password,
            onValueChange = callbacks.onPassword,
            label = stringResource(R.string.backup_editor_password),
            error = state.passwordError,
            password = true,
            supporting = if (state.hasStoredPassword && state.profileId != null) stringResource(R.string.backup_editor_password_keep) else null,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.backup_editor_allow_http_local),
            subtitle = stringResource(R.string.backup_editor_allow_http_local_subtitle),
            checked = state.allowHttpLocal,
            onCheckedChange = callbacks.onAllowHttpLocal,
        )
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Button(onClick = callbacks.onTest, enabled = !state.testRunning) {
                if (state.testRunning) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(" " + stringResource(R.string.backup_editor_test_running))
                } else {
                    Text(stringResource(R.string.backup_editor_test_connection))
                }
            }
        }
    }

    SettingsGroup(title = stringResource(R.string.backup_editor_section_security)) {
        SettingsSwitchRow(
            title = stringResource(R.string.backup_editor_encrypt),
            subtitle = stringResource(R.string.backup_editor_encrypt_subtitle),
            icon = Icons.Rounded.Lock,
            checked = state.encrypt,
            onCheckedChange = callbacks.onEncrypt,
        )
        if (state.encrypt) {
            EditorField(
                value = state.encPassword,
                onValueChange = callbacks.onEncPassword,
                label = stringResource(R.string.backup_editor_enc_password),
                error = state.encError,
                password = true,
                supporting = if (state.hasStoredEncPassword && state.profileId != null) stringResource(R.string.backup_editor_enc_password_keep) else null,
            )
            EditorField(
                value = state.encPasswordConfirm,
                onValueChange = callbacks.onEncPasswordConfirm,
                label = stringResource(R.string.backup_editor_enc_password_confirm),
                password = true,
            )
        }
    }

    SettingsGroup(title = stringResource(R.string.backup_editor_section_schedule)) {
        var showFrequency by remember { mutableStateOf(false) }
        SettingsValueRow(
            title = stringResource(R.string.backup_editor_frequency),
            value = frequencyLabel(state.frequency),
            icon = Icons.Rounded.Schedule,
            onClick = { showFrequency = true },
        )
        SettingsSwitchRow(
            title = stringResource(R.string.backup_editor_wifi_only),
            icon = Icons.Rounded.Wifi,
            checked = state.wifiOnly && state.frequency != BackupFrequency.MANUAL,
            onCheckedChange = callbacks.onWifiOnly,
            enabled = state.frequency != BackupFrequency.MANUAL,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.backup_editor_enabled),
            checked = state.enabled,
            onCheckedChange = callbacks.onEnabled,
        )
        if (showFrequency) {
            FrequencyDialog(
                selected = state.frequency,
                onSelect = { callbacks.onFrequency(it); showFrequency = false },
                onDismiss = { showFrequency = false },
            )
        }
    }

    SettingsGroup(
        title = stringResource(R.string.backup_editor_section_retention),
        footer = stringResource(R.string.backup_editor_keep_hint),
    ) {
        EditorField(
            value = state.keepCount,
            onValueChange = callbacks.onKeepCount,
            label = stringResource(R.string.backup_editor_keep_count),
            keyboardType = KeyboardType.Number,
        )
        EditorField(
            value = state.keepDays,
            onValueChange = callbacks.onKeepDays,
            label = stringResource(R.string.backup_editor_keep_days),
            keyboardType = KeyboardType.Number,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.backup_editor_verify_after_upload),
            subtitle = stringResource(R.string.backup_editor_verify_after_upload_subtitle),
            icon = Icons.Rounded.Storage,
            checked = state.verifyAfterUpload,
            onCheckedChange = callbacks.onVerifyAfterUpload,
        )
    }

    if (state.profileId != null) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = callbacks.onDelete) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text("  " + stringResource(R.string.backup_editor_delete_profile), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    state.testResults?.let { results ->
        TestResultDialog(results, onDismiss = callbacks.onDismissTest)
    }
}

/** Aggregated event surface for the editor screen. */
internal data class ProfileEditorCallbacks(
    val onName: (String) -> Unit,
    val onServerUrl: (String) -> Unit,
    val onRemotePath: (String) -> Unit,
    val onUsername: (String) -> Unit,
    val onPassword: (String) -> Unit,
    val onAllowHttpLocal: (Boolean) -> Unit,
    val onEncrypt: (Boolean) -> Unit,
    val onEncPassword: (String) -> Unit,
    val onEncPasswordConfirm: (String) -> Unit,
    val onFrequency: (BackupFrequency) -> Unit,
    val onKeepCount: (String) -> Unit,
    val onKeepDays: (String) -> Unit,
    val onWifiOnly: (Boolean) -> Unit,
    val onVerifyAfterUpload: (Boolean) -> Unit,
    val onEnabled: (Boolean) -> Unit,
    val onTest: () -> Unit,
    val onDismissTest: () -> Unit,
    val onDelete: () -> Unit,
    val onSave: () -> Unit,
)

@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: Int? = null,
    supporting: String? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    leading: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, maxLines = 1) } },
        isError = error != null,
        supportingText = {
            when {
                error != null -> Text(stringResource(error), color = MaterialTheme.colorScheme.error)
                supporting != null -> Text(supporting)
            }
        },
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        leadingIcon = leading,
        singleLine = true,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun frequencyLabel(f: BackupFrequency): String = stringResource(
    when (f) {
        BackupFrequency.MANUAL -> R.string.backup_editor_frequency_manual
        BackupFrequency.DAILY -> R.string.backup_editor_frequency_daily
        BackupFrequency.WEEKLY -> R.string.backup_editor_frequency_weekly
        BackupFrequency.EVERY_30_DAYS -> R.string.backup_editor_frequency_every30
    }
)

@Composable
private fun FrequencyDialog(selected: BackupFrequency, onSelect: (BackupFrequency) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_editor_frequency)) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                BackupFrequency.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                            selected = option == selected,
                            role = Role.RadioButton,
                            onClick = { onSelect(option) },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(frequencyLabel(option), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(io.github.submark.core.ui.R.string.ui_action_cancel)) }
        },
    )
}

/** Per-step results of the connection test. */
@Composable
private fun TestResultDialog(results: List<WebDavClient.TestStepResult>, onDismiss: () -> Unit) {
    val allOk = results.all { it.ok } && results.size == WebDavClient.TestStep.entries.size
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (allOk) R.string.backup_editor_test_ok else R.string.backup_editor_test_failed)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                results.forEach { step ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (step.ok) Icons.Rounded.Check else Icons.Rounded.Close,
                            contentDescription = null,
                            tint = if (step.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(stringResource(testStepLabel(step.step)), style = MaterialTheme.typography.bodyMedium)
                            step.error?.let { err ->
                                Text(err.toUiText().asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(io.github.submark.core.ui.R.string.ui_action_ok)) } },
    )
}
