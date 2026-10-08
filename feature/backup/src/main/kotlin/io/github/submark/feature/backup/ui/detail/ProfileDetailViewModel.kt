package io.github.submark.feature.backup.ui.detail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.secret.SecretStore
import io.github.submark.core.database.dao.BackupDao
import io.github.submark.core.model.BackupJob
import io.github.submark.core.model.BackupProfile
import io.github.submark.core.model.RestoreMode
import io.github.submark.core.model.SecretKeys
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.navigation.WebDavProfileRoute
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.backup.R
import io.github.submark.feature.backup.data.BackupManifest
import io.github.submark.feature.backup.data.BackupRunner
import io.github.submark.feature.backup.data.BackupScheduler
import io.github.submark.feature.backup.ui.common.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

data class ProfileDetailUiState(
    val loading: Boolean = true,
    val profile: BackupProfile? = null,
    val jobs: List<BackupJob> = emptyList(),
    val remote: List<BackupManifest>? = null, // null = not loaded
    val remoteLoading: Boolean = false,
    val busy: Boolean = false,
    /** Set to prompt for the encryption password of an encrypted backup whose secret is missing. */
    val pendingPasswordPrompt: BackupManifest? = null,
    val pendingModePick: BackupManifest? = null,
    /** Confirmation dialog for the destructive EXACT restore of this manifest. */
    val pendingExactConfirm: BackupManifest? = null,
)

private data class Extra(
    val remote: List<BackupManifest>? = null,
    val remoteLoading: Boolean = false,
    val busy: Boolean = false,
    val passwordPrompt: BackupManifest? = null,
    val modePick: BackupManifest? = null,
    val exactConfirm: Pair<BackupManifest, RestoreMode>? = null,
)

@HiltViewModel
class ProfileDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedState: SavedStateHandle,
    private val dao: BackupDao,
    private val runner: BackupRunner,
    private val scheduler: BackupScheduler,
    private val secrets: SecretStore,
) : ViewModel() {

    private val route = savedState.toRoute<WebDavProfileRoute>()
    private val profileId: String? = route.id

    private val snackbar = Channel<SnackbarMessage>(Channel.CONFLATED)
    val messages: Flow<SnackbarMessage> = snackbar.receiveAsFlow()

    private val extra = MutableStateFlow(Extra())

    val state: StateFlow<ProfileDetailUiState> = combine(
        kotlinx.coroutines.flow.flow { emit(profileId?.let { dao.getProfile(it) }) },
        if (profileId != null) dao.observeJobs(profileId) else kotlinx.coroutines.flow.flowOf(emptyList()),
        extra,
    ) { profile, jobs, e ->
        ProfileDetailUiState(
            loading = false,
            profile = profile,
            jobs = jobs,
            remote = e.remote,
            remoteLoading = e.remoteLoading,
            busy = e.busy,
            pendingPasswordPrompt = e.passwordPrompt,
            pendingModePick = e.modePick,
            pendingExactConfirm = e.exactConfirm?.first,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileDetailUiState())

    init {
        viewModelScope.launch {
            cachedProfile = profileId?.let { dao.getProfile(it) }
            if (profileId != null) refreshRemote()
        }
    }

    private var cachedProfile: BackupProfile? = null

    // ------------------------------------------------------------------ run now

    fun backUpNow() {
        val id = profileId ?: return
        viewModelScope.launch {
            if (runner.isBusy) {
                snack(R.string.backup_running)
                return@launch
            }
            scheduler.enqueueRunNow(context, id)
            snack(R.string.backup_backup_started)
        }
    }

    // ------------------------------------------------------------------ remote list

    fun refreshRemote() {
        val profile = state.value.profile ?: cachedProfile ?: return
        if (extra.value.remoteLoading) return
        extra.value = extra.value.copy(remoteLoading = true)
        viewModelScope.launch {
            when (val result = runner.listRemote(profile)) {
                is DataResult.Success -> extra.value = extra.value.copy(remote = result.value, remoteLoading = false)
                is DataResult.Failure -> {
                    extra.value = extra.value.copy(remote = null, remoteLoading = false)
                    snackbar.send(SnackbarMessage(UiText.res(R.string.backup_error_network, result.error.toString())))
                }
            }
        }
    }

    /** The profile flow needs a refresh after the row itself changes (jobs update lastSuccess). */
    private fun snack(res: Int, vararg args: Any) {
        viewModelScope.launch { snackbar.send(SnackbarMessage(UiText.res(res, *args))) }
    }
    private fun snackText(text: UiText) {
        viewModelScope.launch { snackbar.send(SnackbarMessage(text)) }
    }

    // ------------------------------------------------------------------ verify

    fun verifyQuick(manifest: BackupManifest) = verify(manifest, full = false)
    fun verifyFull(manifest: BackupManifest) = verify(manifest, full = true)

    private fun verify(manifest: BackupManifest, full: Boolean) {
        val profile = state.value.profile ?: cachedProfile ?: return
        if (extra.value.busy) return
        extra.value = extra.value.copy(busy = true)
        viewModelScope.launch {
            when (val r = runner.verifyRemote(profile, manifest, full)) {
                BackupRunner.VerifyResult.Ok -> snack(R.string.backup_detail_verify_ok)
                is BackupRunner.VerifyResult.SizeMismatch -> snack(R.string.backup_error_verify_size, r.expected, r.actual)
                is BackupRunner.VerifyResult.ChecksumMismatch -> snack(R.string.backup_error_checksum)
                is BackupRunner.VerifyResult.Dav -> snackText(r.error.toUiText())
            }
            extra.value = extra.value.copy(busy = false)
        }
    }

    // ------------------------------------------------------------------ delete remote

    fun deleteRemote(manifest: BackupManifest) {
        val profile = state.value.profile ?: cachedProfile ?: return
        viewModelScope.launch {
            val error = runner.deleteRemote(profile, manifest)
            if (error == null) {
                refreshRemote()
            } else {
                snackText(error.toUiText())
            }
        }
    }

    // ------------------------------------------------------------------ restore flow

    /** Entry: pick the restore mode. */
    fun requestRestore(manifest: BackupManifest) {
        extra.value = extra.value.copy(modePick = manifest)
    }

    fun cancelDialogs() {
        extra.value = extra.value.copy(modePick = null, exactConfirm = null, passwordPrompt = null)
    }

    fun modeChosen(mode: RestoreMode) {
        val manifest = extra.value.modePick ?: return
        if (mode == RestoreMode.EXACT) {
            extra.value = extra.value.copy(modePick = null, exactConfirm = manifest to mode)
        } else {
            extra.value = extra.value.copy(modePick = null)
            launchRestore(manifest, mode, null)
        }
    }

    fun exactConfirmed() {
        val (manifest, mode) = extra.value.exactConfirm ?: return
        extra.value = extra.value.copy(exactConfirm = null)
        launchRestore(manifest, mode, null)
    }

    private fun launchRestore(manifest: BackupManifest, mode: RestoreMode, password: String?) {
        val profile = state.value.profile ?: cachedProfile ?: return
        viewModelScope.launch {
            if (manifest.encrypted && password == null && profileId?.let { secrets.get(SecretKeys.backupEncryption(it)) == null } != false) {
                extra.value = extra.value.copy(passwordPrompt = manifest)
                pendingMode = mode
                return@launch
            }
            runRestore(profile, manifest, mode, password)
        }
    }

    private var pendingMode: RestoreMode? = null

    fun passwordEntered(password: String) {
        val manifest = extra.value.passwordPrompt ?: return
        val mode = pendingMode ?: RestoreMode.MERGE
        extra.value = extra.value.copy(passwordPrompt = null)
        val profile = state.value.profile ?: cachedProfile ?: return
        viewModelScope.launch { runRestore(profile, manifest, mode, password) }
    }

    private suspend fun runRestore(profile: BackupProfile, manifest: BackupManifest, mode: RestoreMode, password: String?) {
        if (extra.value.busy) return
        extra.value = extra.value.copy(busy = true)
        when (val outcome = runner.restore(profile, manifest, mode, password)) {
            BackupRunner.RestoreOutcome.Busy -> snack(R.string.backup_running)
            is BackupRunner.RestoreOutcome.Success -> {
                if (outcome.result.isEmpty) snack(R.string.backup_import_nothing) else snack(R.string.backup_detail_restore_done)
            }
            is BackupRunner.RestoreOutcome.Failed -> {
                if (outcome.error is BackupRunner.RestoreFailure.MissingPassword ||
                    outcome.error is BackupRunner.RestoreFailure.WrongEncryptionPassword
                ) {
                    pendingMode = mode
                    extra.value = extra.value.copy(passwordPrompt = manifest)
                } else {
                    snackText(outcome.error.toUiText())
                }
            }
        }
        extra.value = extra.value.copy(busy = false)
    }
}
