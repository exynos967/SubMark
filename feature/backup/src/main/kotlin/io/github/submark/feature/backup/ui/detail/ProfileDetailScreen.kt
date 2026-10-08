package io.github.submark.feature.backup.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.submark.core.model.BackupJob
import io.github.submark.core.model.RestoreMode
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.SettingsGroup
import io.github.submark.core.ui.component.SettingsNavRow
import io.github.submark.feature.backup.R
import io.github.submark.feature.backup.data.BackupManifest
import io.github.submark.feature.backup.ui.common.kindLabel
import io.github.submark.feature.backup.ui.common.phaseLabel
import java.text.DateFormat
import java.time.Instant
import java.util.Date

@Composable
internal fun ProfileDetailScreen(
    state: ProfileDetailUiState,
    onBackUpNow: () -> Unit,
    onRefreshRemote: () -> Unit,
    onVerifyQuick: (BackupManifest) -> Unit,
    onVerifyFull: (BackupManifest) -> Unit,
    onDeleteRemote: (BackupManifest) -> Unit,
    onRestore: (BackupManifest) -> Unit,
    onModeChosen: (RestoreMode) -> Unit,
    onExactConfirmed: () -> Unit,
    onCancelDialogs: () -> Unit,
    onPasswordEntered: (String) -> Unit,
) {
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

    state.profile?.let { profile ->
        SettingsGroup(title = profile.name) {
            SettingsNavRow(
                title = stringResource(R.string.backup_back_up_now),
                subtitle = profile.serverUrl,
                icon = Icons.Rounded.PlayArrow,
                onClick = onBackUpNow,
                enabled = !state.busy,
            )
        }
    }

    SettingsGroup(
        title = stringResource(R.string.backup_detail_remote),
        footer = null,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onRefreshRemote, enabled = !state.remoteLoading) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Text(" " + stringResource(R.string.backup_detail_refresh))
            }
        }
        when {
            state.remoteLoading -> LoadingState()
            state.remote == null -> TextButton(onClick = onRefreshRemote, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.backup_detail_remote_load))
            }
            state.remote!!.isEmpty() -> EmptyState(title = stringResource(R.string.backup_detail_remote_empty), icon = Icons.Rounded.CloudDownload)
            else -> Column {
                state.remote!!.forEach { manifest ->
                    RemoteBackupRow(
                        manifest = manifest,
                        dateLabel = dateFormat.format(Date.from(runCatching { Instant.parse(manifest.createdAt) }.getOrDefault(Instant.EPOCH))),
                        onVerifyQuick = { onVerifyQuick(manifest) },
                        onVerifyFull = { onVerifyFull(manifest) },
                        onDelete = { onDeleteRemote(manifest) },
                        onRestore = { onRestore(manifest) },
                        busy = state.busy,
                    )
                }
            }
        }
    }

    SettingsGroup(title = stringResource(R.string.backup_detail_jobs)) {
        if (state.jobs.isEmpty()) {
            EmptyState(title = stringResource(R.string.backup_detail_jobs_empty))
        } else {
            Column {
                state.jobs.take(10).forEach { job -> JobRow(job, dateFormat) }
            }
        }
    }

    // ---------------------------------------------------------------- restore dialogs

    state.pendingModePick?.let {
        RestoreModePicker(
            onSelect = onModeChosen,
            onDismiss = onCancelDialogs,
        )
    }
    state.pendingExactConfirm?.let {
        ConfirmDialog(
            title = stringResource(R.string.backup_mode_exact_confirm_title),
            message = stringResource(R.string.backup_mode_exact_confirm_body),
            confirmLabel = stringResource(R.string.backup_mode_exact_confirm_action),
            destructive = true,
            onConfirm = onExactConfirmed,
            onDismiss = onCancelDialogs,
        )
    }
    state.pendingPasswordPrompt?.let {
        PasswordPromptDialog(
            onConfirm = onPasswordEntered,
            onDismiss = onCancelDialogs,
        )
    }
}

@Composable
private fun RemoteBackupRow(
    manifest: BackupManifest,
    dateLabel: String,
    busy: Boolean,
    onVerifyQuick: () -> Unit,
    onVerifyFull: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(manifest.payloadFileName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (manifest.encrypted) {
                    Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.backup_detail_encrypted), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(dateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.backup_detail_device, manifest.device), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.backup_detail_app_version, manifest.appVersion), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.backup_detail_size, formatBytes(manifest.sizeBytes)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = onRestore, enabled = !busy) { Text(stringResource(R.string.backup_detail_restore)) }
                TextButton(onClick = onVerifyQuick, enabled = !busy) { Text(stringResource(R.string.backup_detail_verify_quick)) }
                TextButton(onClick = onVerifyFull, enabled = !busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Verified, contentDescription = null, modifier = Modifier.padding(end = 2.dp))
                        Text(stringResource(R.string.backup_detail_verify_full))
                    }
                }
                TextButton(onClick = { confirmDelete = true }, enabled = !busy) {
                    Text(stringResource(R.string.backup_detail_delete_remote), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.backup_detail_delete_remote_title),
            message = stringResource(R.string.backup_detail_delete_remote_body),
            confirmLabel = stringResource(R.string.backup_delete),
            destructive = true,
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun JobRow(job: BackupJob, dateFormat: DateFormat) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(kindLabel(job.kind)), style = MaterialTheme.typography.bodyLarge)
            val sub = job.remoteName ?: job.error
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                stringResource(phaseLabel(job.phase)),
                style = MaterialTheme.typography.bodyMedium,
                color = if (job.phase == io.github.submark.core.model.BackupJobPhase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                dateFormat.format(Date.from(job.startedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RestoreModePicker(onSelect: (RestoreMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_mode_title)) },
        text = {
            Column {
                @Composable
                fun mode(label: Int, desc: Int, value: RestoreMode) {
                    TextButton(onClick = { onSelect(value) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                mode(R.string.backup_mode_merge, R.string.backup_mode_merge_desc, RestoreMode.MERGE)
                mode(R.string.backup_mode_replace, R.string.backup_mode_replace_desc, RestoreMode.REPLACE_MATCHING)
                mode(R.string.backup_mode_exact, R.string.backup_mode_exact_desc, RestoreMode.EXACT)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(io.github.submark.core.ui.R.string.ui_action_cancel)) } },
    )
}

@Composable
private fun PasswordPromptDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_detail_enc_prompt_title)) },
        text = {
            Column {
                Text(stringResource(R.string.backup_detail_enc_prompt_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotEmpty()) {
                Text(stringResource(io.github.submark.core.ui.R.string.ui_action_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(io.github.submark.core.ui.R.string.ui_action_cancel)) } },
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}
