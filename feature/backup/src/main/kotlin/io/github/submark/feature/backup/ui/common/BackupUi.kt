package io.github.submark.feature.backup.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.unit.dp
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.format.UiText
import io.github.submark.feature.backup.R
import io.github.submark.feature.backup.data.BackupRunner
import io.github.submark.feature.backup.data.WebDavClient
import io.github.submark.feature.backup.data.WebDavError

/** Standard page scaffold for this feature: pinned top bar, snackbar host, scrollable column. */
@Composable
internal fun BackupPage(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { SubMarkTopAppBar(title = title, onBack = onBack, actions = actions, scrollBehavior = scrollBehavior) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (loading) {
            LoadingState(Modifier.padding(padding))
        } else {
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            ) {
                content()
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Localized label for a [WebDavError]. */
internal fun WebDavError.toUiText(): UiText = when (this) {
    WebDavError.InvalidUrl -> UiText.res(R.string.backup_error_invalid_url)
    WebDavError.PlainHttpNotAllowed -> UiText.res(R.string.backup_error_http_plain)
    WebDavError.HostNotLocal -> UiText.res(R.string.backup_error_host_not_local)
    is WebDavError.Http -> UiText.res(R.string.backup_error_http_status, code, message)
    WebDavError.MalformedResponse -> UiText.res(R.string.backup_error_malformed)
    is WebDavError.Io -> UiText.res(R.string.backup_error_network, message ?: "")
    WebDavError.Canceled -> UiText.res(R.string.backup_error_canceled)
    WebDavError.MissingCredentials -> UiText.res(R.string.backup_error_missing_password)
}

internal fun BackupRunner.BackupFailure.toUiText(): UiText = when (this) {
    is BackupRunner.BackupFailure.Dav -> error.toUiText()
    BackupRunner.BackupFailure.MissingPassword -> UiText.res(R.string.backup_error_missing_password)
    BackupRunner.BackupFailure.MissingEncryptionPassword -> UiText.res(R.string.backup_error_missing_enc_password)
    is BackupRunner.BackupFailure.VerifyMismatch -> UiText.res(R.string.backup_error_verify_size, expected, actual)
    BackupRunner.BackupFailure.StagedMissing -> UiText.res(R.string.backup_error_staged_missing)
    is BackupRunner.BackupFailure.Io -> UiText.res(R.string.backup_error_network, message ?: "")
}

internal fun BackupRunner.RestoreFailure.toUiText(): UiText = when (this) {
    is BackupRunner.RestoreFailure.Dav -> error.toUiText()
    BackupRunner.RestoreFailure.MissingPassword -> UiText.res(R.string.backup_error_missing_enc_password)
    BackupRunner.RestoreFailure.WrongEncryptionPassword -> UiText.res(R.string.backup_error_wrong_password)
    BackupRunner.RestoreFailure.InvalidManifest -> UiText.res(R.string.backup_error_manifest)
    BackupRunner.RestoreFailure.UnsupportedManifestVersion -> UiText.res(R.string.backup_error_manifest_version)
    is BackupRunner.RestoreFailure.UnsupportedExportVersion -> UiText.res(R.string.backup_error_export_version)
    is BackupRunner.RestoreFailure.ChecksumMismatch -> UiText.res(R.string.backup_error_checksum)
    is BackupRunner.RestoreFailure.SizeMismatch -> UiText.res(R.string.backup_error_size)
    is BackupRunner.RestoreFailure.Import -> UiText.res(R.string.backup_import_failed)
    is BackupRunner.RestoreFailure.Io -> UiText.res(R.string.backup_error_network, message ?: "")
}

/** Human label for a [io.github.submark.core.model.BackupJobPhase]. */
@StringRes
internal fun phaseLabel(phase: io.github.submark.core.model.BackupJobPhase): Int = when (phase) {
    io.github.submark.core.model.BackupJobPhase.PREPARING -> R.string.backup_phase_PREPARING
    io.github.submark.core.model.BackupJobPhase.ENCRYPTING -> R.string.backup_phase_ENCRYPTING
    io.github.submark.core.model.BackupJobPhase.UPLOADING_PAYLOAD -> R.string.backup_phase_UPLOADING_PAYLOAD
    io.github.submark.core.model.BackupJobPhase.VERIFYING_PAYLOAD -> R.string.backup_phase_VERIFYING_PAYLOAD
    io.github.submark.core.model.BackupJobPhase.COMMITTING_MANIFEST -> R.string.backup_phase_COMMITTING_MANIFEST
    io.github.submark.core.model.BackupJobPhase.PRUNING -> R.string.backup_phase_PRUNING
    io.github.submark.core.model.BackupJobPhase.DOWNLOADING -> R.string.backup_phase_DOWNLOADING
    io.github.submark.core.model.BackupJobPhase.DECRYPTING -> R.string.backup_phase_DECRYPTING
    io.github.submark.core.model.BackupJobPhase.RESTORING -> R.string.backup_phase_RESTORING
    io.github.submark.core.model.BackupJobPhase.COMPLETED -> R.string.backup_phase_COMPLETED
    io.github.submark.core.model.BackupJobPhase.FAILED -> R.string.backup_phase_FAILED
    io.github.submark.core.model.BackupJobPhase.CANCELED -> R.string.backup_phase_CANCELED
}

@StringRes
internal fun kindLabel(kind: io.github.submark.core.model.BackupJobKind): Int = when (kind) {
    io.github.submark.core.model.BackupJobKind.BACKUP -> R.string.backup_kind_BACKUP
    io.github.submark.core.model.BackupJobKind.RESTORE -> R.string.backup_kind_RESTORE
    io.github.submark.core.model.BackupJobKind.VERIFY -> R.string.backup_kind_VERIFY
}

/** Translates a [WebDavClient.TestStepResult.list] into one line per step for the dialog. */
@StringRes
internal fun testStepLabel(step: WebDavClient.TestStep): Int = when (step) {
    WebDavClient.TestStep.READ -> R.string.backup_test_step_read
    WebDavClient.TestStep.CREATE_DIRECTORY -> R.string.backup_test_step_mkcol
    WebDavClient.TestStep.WRITE_PROBE -> R.string.backup_test_step_put
    WebDavClient.TestStep.VERIFY_PROBE -> R.string.backup_test_step_get
    WebDavClient.TestStep.DELETE_PROBE -> R.string.backup_test_step_delete
}
