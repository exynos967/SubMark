package io.github.submark.feature.backup.ui.datamanage

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.submark.core.data.backup.DataResetService
import io.github.submark.core.data.backup.ExportService
import io.github.submark.core.data.currency.CurrencyRepository
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.result.InvalidReason
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.model.ImportResult
import io.github.submark.core.model.RestoreMode
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.backup.R
import io.github.submark.feature.backup.data.ExportImport
import io.github.submark.feature.backup.data.MemberCleanupService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Data display toggles mirrored from AppSettings plus one-shot export/import results. */
data class DataManagementUiState(
    val archiveMode: Boolean = true,
    val showCustomCycleYmd: Boolean = false,
    val showEndDateFixedCycle: Boolean = false,
    val showIapTotal: Boolean = false,
    val showLifetimeLabel: Boolean = true,
    val busy: Boolean = false,
    val importResult: ImportResult? = null,
    val pendingImportModePick: Boolean = false,
)

@HiltViewModel
class DataManagementViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val export: ExportService,
    private val reset: DataResetService,
    private val currencies: CurrencyRepository,
    private val cleanup: MemberCleanupService,
) : ViewModel() {

    private val snackbar = Channel<SnackbarMessage>(Channel.CONFLATED)
    val messages: Flow<SnackbarMessage> = snackbar.receiveAsFlow()

    private val _ephemeral = kotlinx.coroutines.flow.MutableStateFlow(ExtraState())

    data class ExtraState(
        val busy: Boolean = false,
        val importResult: ImportResult? = null,
        val importing: Boolean = false,
    )

    val state: StateFlow<DataManagementUiState> = kotlinx.coroutines.flow.combine(
        settings.settings, _ephemeral,
    ) { s, e ->
        DataManagementUiState(
            archiveMode = s.subscriptions.archiveMode,
            showCustomCycleYmd = s.list.showCustomCycleAsYmd,
            showEndDateFixedCycle = s.list.showEndDateForFixedCycle,
            showIapTotal = s.list.showIapTotalPrice,
            showLifetimeLabel = s.list.showLifetimeLabel,
            busy = e.busy,
            importResult = e.importResult,
            pendingImportModePick = e.importing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DataManagementUiState())

    // ---------------------------------------------------------------- toggles

    fun setArchiveMode(value: Boolean) = updateSettings { it.copy(subscriptions = it.subscriptions.copy(archiveMode = value)) }
    fun setShowCustomCycleYmd(value: Boolean) = updateSettings { it.copy(list = it.list.copy(showCustomCycleAsYmd = value)) }
    fun setShowEndDateFixed(value: Boolean) = updateSettings { it.copy(list = it.list.copy(showEndDateForFixedCycle = value)) }
    fun setShowIapTotal(value: Boolean) = updateSettings { it.copy(list = it.list.copy(showIapTotalPrice = value)) }
    fun setShowLifetimeLabel(value: Boolean) = updateSettings { it.copy(list = it.list.copy(showLifetimeLabel = value)) }

    private fun updateSettings(transform: (io.github.submark.core.data.settings.AppSettings) -> io.github.submark.core.data.settings.AppSettings) {
        viewModelScope.launch { settings.update(transform) }
    }

    // ---------------------------------------------------------------- export

    /** SAF CreateDocument returned a target; write the bundle (JSON or ZIP when photos exist). */
    fun exportTo(uri: Uri) {
        if (_ephemeral.value.busy) return
        _ephemeral.value = _ephemeral.value.copy(busy = true)
        viewModelScope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    val bundle = export.export()
                    val stream = context.contentResolver.openOutputStream(uri) ?: error("no stream")
                    ExportImport.write(export, bundle, photosDir(), stream)
                }
            }.isSuccess
            _ephemeral.value = _ephemeral.value.copy(busy = false)
            snack(UiText.res(if (ok) R.string.backup_export_done else R.string.backup_export_failed))
        }
    }

    // ---------------------------------------------------------------- import

    private var pendingDocument: ExportImport.Outcome? = null

    /** SAF OpenDocument returned a source; read + validate, then ask the UI for the restore mode. */
    fun importFrom(uri: Uri) {
        if (_ephemeral.value.busy) return
        _ephemeral.value = _ephemeral.value.copy(busy = true)
        viewModelScope.launch {
            val stream = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri) }.getOrNull() }
            if (stream == null) {
                _ephemeral.value = _ephemeral.value.copy(busy = false)
                snack(UiText.res(R.string.backup_import_file_failed))
                return@launch
            }
            when (val read = ExportImport.read(export, stream)) {
                is DataResult.Failure -> {
                    _ephemeral.value = _ephemeral.value.copy(busy = false)
                    snack(importErrorText(read.error))
                }
                is DataResult.Success -> {
                    pendingDocument = read.value
                    _ephemeral.value = _ephemeral.value.copy(busy = false, importing = true)
                }
            }
        }
    }

    /** Mode chosen (MERGE/REPLACE_MATCHING without confirm, or EXACT after the destructive dialog). */
    fun confirmImport(mode: RestoreMode) {
        val doc = pendingDocument ?: return
        _ephemeral.value = _ephemeral.value.copy(importing = false, busy = true)
        viewModelScope.launch {
            // Restore any photos the archive brought; the DB import below links them.
            runCatching {
                withContext(Dispatchers.IO) {
                    val dir = File(context.filesDir, "photos").apply { mkdirs() }
                    doc.photos.forEach { (name, bytes) ->
                        if (ExportImport.isSafeFileName(name)) File(dir, name).writeBytes(bytes)
                    }
                }
            }
            when (val imported = export.import(doc.bundle, mode)) {
                is DataResult.Failure -> {
                    _ephemeral.value = _ephemeral.value.copy(busy = false)
                    snack(importErrorText(imported.error))
                }
                is DataResult.Success -> {
                    _ephemeral.value = _ephemeral.value.copy(busy = false, importResult = imported.value)
                    if (imported.value.isEmpty) snack(UiText.res(R.string.backup_import_nothing))
                }
            }
            pendingDocument = null
        }
    }

    fun cancelImport() {
        pendingDocument = null
        _ephemeral.value = _ephemeral.value.copy(importing = false)
    }

    fun dismissImportResult() {
        _ephemeral.value = _ephemeral.value.copy(importResult = null)
    }

    private fun importErrorText(error: DataError): UiText = when (error) {
        is DataError.Invalid -> when (error.reason) {
            InvalidReason.MALFORMED_EXPORT -> UiText.res(R.string.backup_import_malformed)
            InvalidReason.UNSUPPORTED_EXPORT_VERSION -> UiText.res(R.string.backup_import_unsupported)
            else -> UiText.res(R.string.backup_import_failed)
        }
        else -> UiText.res(R.string.backup_import_failed)
    }

    // ---------------------------------------------------------------- cleanup

    fun cleanOrphans() {
        viewModelScope.launch {
            val r = cleanup.cleanOrphans()
            snack(UiText.res(R.string.backup_orphan_report, r.checked, r.orphaned, r.kept))
        }
    }

    fun cleanDuplicates() {
        viewModelScope.launch {
            val r = cleanup.cleanDuplicates()
            snack(UiText.res(R.string.backup_duplicate_report, r.checked, r.deleted, r.duplicateGroups, r.affectedSubscriptions, r.remaining))
        }
    }

    fun clearRateCache() {
        viewModelScope.launch {
            currencies.clearHistorical()
            snack(UiText.res(R.string.backup_rate_cache_cleared))
        }
    }

    // ---------------------------------------------------------------- danger zone

    fun clearAllData() {
        if (_ephemeral.value.busy) return
        _ephemeral.value = _ephemeral.value.copy(busy = true)
        viewModelScope.launch {
            runCatching {
                reset.clearAll()
                withContext(Dispatchers.IO) {
                    photosDir().deleteRecursively()
                    iconsDir().deleteRecursively()
                    File(context.filesDir, io.github.submark.feature.backup.data.BackupRunner.STAGING_DIR).deleteRecursively()
                }
            }
            _ephemeral.value = _ephemeral.value.copy(busy = false)
            snack(UiText.res(R.string.backup_clear_all_done))
        }
    }

    private fun photosDir(): File = File(context.filesDir, "photos")
    private fun iconsDir(): File = File(context.filesDir, io.github.submark.core.ui.icon.ICON_DIR)

    private fun snack(text: UiText) {
        viewModelScope.launch { snackbar.send(SnackbarMessage(text)) }
    }
}
