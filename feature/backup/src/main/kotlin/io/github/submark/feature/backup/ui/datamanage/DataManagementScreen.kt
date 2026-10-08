package io.github.submark.feature.backup.ui.datamanage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.model.ImportResult
import io.github.submark.core.model.RestoreMode
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.core.ui.component.SettingsGroup
import io.github.submark.core.ui.component.SettingsNavRow
import io.github.submark.core.ui.component.SettingsSwitchRow
import io.github.submark.core.ui.navigation.QrImportRoute
import io.github.submark.core.ui.navigation.WebDavProfilesRoute
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.feature.backup.R
import io.github.submark.feature.backup.ui.common.BackupPage

@Composable
fun DataManagementRoute(
    onBack: () -> Unit,
    onNavigate: (Any) -> Unit,
    viewModel: DataManagementViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let(viewModel::exportTo)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importFrom)
    }

    val hostState = remember { androidx.compose.material3.SnackbarHostState() }
    SnackbarEffect(viewModel.messages, hostState)
    BackupPage(title = stringResource(R.string.backup_data_title), onBack = onBack, snackbarHostState = hostState) {
        DataManagementContent(
            state = state,
            onExport = { exportLauncher.launch("submark-backup") },
            onImport = { importLauncher.launch(arrayOf("application/json", "application/zip", "application/octet-stream", "text/*")) },
            onOpenWebDav = { onNavigate(WebDavProfilesRoute) },
            onOpenQrImport = { onNavigate(QrImportRoute) },
            onArchiveMode = viewModel::setArchiveMode,
            onCustomCycleYmd = viewModel::setShowCustomCycleYmd,
            onEndDateFixed = viewModel::setShowEndDateFixed,
            onIapTotal = viewModel::setShowIapTotal,
            onLifetimeLabel = viewModel::setShowLifetimeLabel,
            onCleanOrphans = viewModel::cleanOrphans,
            onCleanDuplicates = viewModel::cleanDuplicates,
            onClearRateCache = viewModel::clearRateCache,
            onClearAll = viewModel::clearAllData,
            onImportMode = viewModel::confirmImport,
            onCancelImport = viewModel::cancelImport,
            onDismissImportResult = viewModel::dismissImportResult,
        )
    }
}

@Composable
internal fun DataManagementContent(
    state: DataManagementUiState,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onOpenWebDav: () -> Unit,
    onOpenQrImport: () -> Unit,
    onArchiveMode: (Boolean) -> Unit,
    onCustomCycleYmd: (Boolean) -> Unit,
    onEndDateFixed: (Boolean) -> Unit,
    onIapTotal: (Boolean) -> Unit,
    onLifetimeLabel: (Boolean) -> Unit,
    onCleanOrphans: () -> Unit,
    onCleanDuplicates: () -> Unit,
    onClearRateCache: () -> Unit,
    onClearAll: () -> Unit,
    onImportMode: (RestoreMode) -> Unit,
    onCancelImport: () -> Unit,
    onDismissImportResult: () -> Unit,
) {
    var showExactConfirm by remember { mutableStateOf(false) }
    var dangerStep by remember { mutableStateOf(0) }

    SettingsGroup(title = stringResource(R.string.backup_group_backup_restore)) {
        SettingsNavRow(
            title = stringResource(R.string.backup_export),
            subtitle = stringResource(R.string.backup_export_subtitle),
            icon = Icons.Rounded.FileUpload,
            onClick = onExport,
            enabled = !state.busy,
        )
        SettingsNavRow(
            title = stringResource(R.string.backup_import),
            subtitle = stringResource(R.string.backup_import_subtitle),
            icon = Icons.Rounded.FileDownload,
            onClick = onImport,
            enabled = !state.busy,
        )
        SettingsNavRow(
            title = stringResource(R.string.backup_webdav),
            subtitle = stringResource(R.string.backup_webdav_subtitle),
            icon = Icons.Rounded.CloudUpload,
            onClick = onOpenWebDav,
        )
        SettingsNavRow(
            title = stringResource(R.string.backup_qr_import),
            subtitle = stringResource(R.string.backup_qr_import_subtitle),
            icon = Icons.Rounded.QrCodeScanner,
            onClick = onOpenQrImport,
        )
    }

    SettingsGroup(title = stringResource(R.string.backup_group_display)) {
        SettingsSwitchRow(
            title = stringResource(R.string.backup_toggle_archive_mode),
            subtitle = stringResource(R.string.backup_toggle_archive_mode_subtitle),
            icon = Icons.Rounded.Archive,
            checked = state.archiveMode,
            onCheckedChange = onArchiveMode,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.backup_toggle_custom_cycle_ymd),
            checked = state.showCustomCycleYmd,
            onCheckedChange = onCustomCycleYmd,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.backup_toggle_end_date_fixed),
            checked = state.showEndDateFixedCycle,
            onCheckedChange = onEndDateFixed,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.backup_toggle_iap_total),
            checked = state.showIapTotal,
            onCheckedChange = onIapTotal,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.backup_toggle_lifetime_label),
            checked = state.showLifetimeLabel,
            onCheckedChange = onLifetimeLabel,
        )
    }

    SettingsGroup(title = stringResource(R.string.backup_group_cleanup)) {
        SettingsNavRow(
            title = stringResource(R.string.backup_clean_orphans),
            subtitle = stringResource(R.string.backup_clean_orphans_subtitle),
            icon = Icons.Rounded.CleaningServices,
            onClick = onCleanOrphans,
            enabled = !state.busy,
        )
        SettingsNavRow(
            title = stringResource(R.string.backup_clean_duplicates),
            subtitle = stringResource(R.string.backup_clean_duplicates_subtitle),
            icon = Icons.Rounded.CleaningServices,
            onClick = onCleanDuplicates,
            enabled = !state.busy,
        )
        SettingsNavRow(
            title = stringResource(R.string.backup_clear_rate_cache),
            subtitle = stringResource(R.string.backup_clear_rate_cache_subtitle),
            icon = Icons.Rounded.Download,
            onClick = onClearRateCache,
            enabled = !state.busy,
        )
    }

    SettingsGroup(title = stringResource(R.string.backup_group_danger)) {
        SettingsNavRow(
            title = stringResource(R.string.backup_clear_all),
            subtitle = stringResource(R.string.backup_clear_all_subtitle),
            icon = Icons.Rounded.DeleteForever,
            onClick = { dangerStep = 1 },
            enabled = !state.busy,
        )
    }

    if (state.pendingImportModePick) {
        RestoreModeDialog(
            onSelect = { mode ->
                if (mode == RestoreMode.EXACT) showExactConfirm = true else onImportMode(mode)
            },
            onDismiss = onCancelImport,
        )
    }

    if (showExactConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.backup_mode_exact_confirm_title),
            message = stringResource(R.string.backup_mode_exact_confirm_body),
            confirmLabel = stringResource(R.string.backup_mode_exact_confirm_action),
            destructive = true,
            icon = Icons.Rounded.Warning,
            onConfirm = { showExactConfirm = false; onImportMode(RestoreMode.EXACT) },
            onDismiss = { showExactConfirm = false },
        )
    }

    state.importResult?.let { result ->
        ImportResultDialog(result, onDismiss = onDismissImportResult)
    }

    when (dangerStep) {
        1 -> ConfirmDialog(
            title = stringResource(R.string.backup_clear_all_confirm1_title),
            message = stringResource(R.string.backup_clear_all_confirm1_body),
            confirmLabel = stringResource(R.string.backup_delete),
            destructive = true,
            icon = Icons.Rounded.Warning,
            onConfirm = { dangerStep = 2 },
            onDismiss = { dangerStep = 0 },
        )
        2 -> ConfirmDialog(
            title = stringResource(R.string.backup_clear_all_confirm2_title),
            message = stringResource(R.string.backup_clear_all_confirm2_body),
            confirmLabel = stringResource(R.string.backup_erase),
            destructive = true,
            icon = Icons.Rounded.DeleteForever,
            onConfirm = { dangerStep = 0; onClearAll() },
            onDismiss = { dangerStep = 0 },
        )
    }
}

@Composable
private fun RestoreModeDialog(onSelect: (RestoreMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_mode_title)) },
        text = {
            androidx.compose.foundation.layout.Column {
                @Composable
                fun mode(label: Int, desc: Int, value: RestoreMode) {
                    TextButton(
                        onClick = { onSelect(value) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
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

/** Per-type counts from [ImportResult]; hides zero-count groups. */
@Composable
private fun ImportResultDialog(result: ImportResult, onDismiss: () -> Unit) {
    val labelFor: (String) -> Int? = { key ->
        when (key) {
            "categories" -> R.string.backup_count_categories
            "tags" -> R.string.backup_count_tags
            "tagFolders" -> R.string.backup_count_tagFolders
            "customFields" -> R.string.backup_count_customFields
            "paymentMethods" -> R.string.backup_count_paymentMethods
            "currencies" -> R.string.backup_count_currencies
            "wallets" -> R.string.backup_count_wallets
            "subscriptions" -> R.string.backup_count_subscriptions
            "subscriptionPhotos" -> R.string.backup_count_subscriptionPhotos
            "customReminders" -> R.string.backup_count_customReminders
            "paymentRecords" -> R.string.backup_count_paymentRecords
            "walletTransactions" -> R.string.backup_count_walletTransactions
            "storedValueRecords" -> R.string.backup_count_storedValueRecords
            "sharedConfigs" -> R.string.backup_count_sharedConfigs
            "sharedMembers" -> R.string.backup_count_sharedMembers
            else -> null
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_import_result_title)) },
        text = {
            if (result.isEmpty) {
                Text(stringResource(R.string.backup_import_nothing))
            } else {
                androidx.compose.foundation.layout.Column {
                    result.counts.filter { it.value > 0 }.forEach { (key, count) ->
                        val label = labelFor(key)
                        Text(
                            if (label != null) "${stringResource(label)}: $count" else "$key: $count",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(io.github.submark.core.ui.R.string.ui_action_ok)) } },
    )
}
