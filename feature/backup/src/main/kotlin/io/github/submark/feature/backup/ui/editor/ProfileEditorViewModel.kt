package io.github.submark.feature.backup.ui.editor

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.submark.core.data.secret.SecretStore
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.database.dao.BackupDao
import io.github.submark.core.model.BackupFrequency
import io.github.submark.core.model.BackupProfile
import io.github.submark.core.model.SecretKeys
import io.github.submark.core.model.newId
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.navigation.WebDavProfileRoute
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.backup.R
import io.github.submark.feature.backup.data.BackupCrypto
import io.github.submark.feature.backup.data.BackupScheduler
import io.github.submark.feature.backup.data.HostRules
import io.github.submark.feature.backup.data.WebDavClient
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** All user-editable fields of a profile, in view state (passwords stay in memory only). */
data class ProfileEditorUiState(
    val loading: Boolean = true,
    val profileId: String? = null, // null = creating
    val name: String = "",
    val serverUrl: String = "",
    val remotePath: String = "SubMark",
    val username: String = "",
    val password: String = "", // entered now; empty on edit = keep stored
    val hasStoredPassword: Boolean = false,
    val allowHttpLocal: Boolean = false,
    val encrypt: Boolean = true,
    val encPassword: String = "",
    val encPasswordConfirm: String = "",
    val hasStoredEncPassword: Boolean = false,
    val frequency: BackupFrequency = BackupFrequency.WEEKLY,
    val keepCount: String = "10",
    val keepDays: String = "90",
    val wifiOnly: Boolean = false,
    val verifyAfterUpload: Boolean = true,
    val enabled: Boolean = true,
    val nameError: Int? = null,
    val urlError: Int? = null,
    val pathError: Int? = null,
    val usernameError: Int? = null,
    val passwordError: Int? = null,
    val encError: Int? = null,
    val testResults: List<WebDavClient.TestStepResult>? = null,
    val testRunning: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
)

@HiltViewModel
class ProfileEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedState: SavedStateHandle,
    private val dao: BackupDao,
    private val secrets: SecretStore,
    private val dav: WebDavClient,
    private val scheduler: BackupScheduler,
    private val time: TimeProvider,
) : ViewModel() {

    private val route = savedState.toRoute<WebDavProfileRoute>()
    private val _state = MutableStateFlow(ProfileEditorUiState())
    val state: StateFlow<ProfileEditorUiState> = _state.asStateFlow()

    private val snackbar = Channel<SnackbarMessage>(Channel.CONFLATED)
    val messages: Flow<SnackbarMessage> = snackbar.receiveAsFlow()

    init {
        viewModelScope.launch {
            val id = route.id
            val existing = id?.let { dao.getProfile(it) }
            if (id != null && existing == null) {
                _state.update { it.copy(loading = false, deleted = true) } // treat as deleted → pop
                return@launch
            }
            if (existing != null) {
                _state.update {
                    it.copy(
                        loading = false,
                        profileId = existing.id,
                        name = existing.name,
                        serverUrl = existing.serverUrl,
                        remotePath = existing.remotePath,
                        username = existing.username,
                        allowHttpLocal = existing.allowHttpLocal,
                        encrypt = existing.encrypt,
                        frequency = existing.frequency,
                        keepCount = existing.keepCount.toString(),
                        keepDays = existing.keepDays.toString(),
                        wifiOnly = existing.wifiOnly,
                        verifyAfterUpload = existing.verifyAfterUpload,
                        enabled = existing.enabled,
                        hasStoredPassword = secrets.get(SecretKeys.webDavPassword(existing.id)) != null,
                        hasStoredEncPassword = secrets.get(SecretKeys.backupEncryption(existing.id)) != null,
                    )
                }
            } else {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    // ------------------------------------------------------------- field updates (clear their error)

    fun onName(v: String) = _state.update { it.copy(name = v, nameError = null) }
    fun onServerUrl(v: String) = _state.update { it.copy(serverUrl = v, urlError = null) }
    fun onRemotePath(v: String) = _state.update { it.copy(remotePath = v, pathError = null) }
    fun onUsername(v: String) = _state.update { it.copy(username = v, usernameError = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, passwordError = null) }
    fun onAllowHttpLocal(v: Boolean) = _state.update { it.copy(allowHttpLocal = v, urlError = null) }
    fun onEncrypt(v: Boolean) = _state.update { it.copy(encrypt = v, encError = null) }
    fun onEncPassword(v: String) = _state.update { it.copy(encPassword = v, encError = null) }
    fun onEncPasswordConfirm(v: String) = _state.update { it.copy(encPasswordConfirm = v, encError = null) }
    fun onFrequency(v: BackupFrequency) = _state.update { it.copy(frequency = v) }
    fun onKeepCount(v: String) = _state.update { it.copy(keepCount = v.filter(Char::isDigit).take(4)) }
    fun onKeepDays(v: String) = _state.update { it.copy(keepDays = v.filter(Char::isDigit).take(5)) }
    fun onWifiOnly(v: Boolean) = _state.update { it.copy(wifiOnly = v) }
    fun onVerifyAfterUpload(v: Boolean) = _state.update { it.copy(verifyAfterUpload = v) }
    fun onEnabled(v: Boolean) = _state.update { it.copy(enabled = v) }
    fun dismissTestResults() = _state.update { it.copy(testResults = null) }

    // ------------------------------------------------------------- save

    fun save() {
        val s = _state.value
        var nameErr: Int? = null
        var urlErr: Int? = null
        var pathErr: Int? = null
        var userErr: Int? = null
        var passErr: Int? = null
        var encErr: Int? = null

        if (s.name.isBlank()) nameErr = R.string.backup_editor_name_required
        if (s.username.isBlank()) userErr = R.string.backup_editor_username_required
        when (HostRules.check(s.serverUrl.trim(), s.allowHttpLocal)) {
            HostRules.Verdict.Ok -> Unit
            HostRules.Verdict.InvalidUrl -> urlErr = R.string.backup_editor_url_invalid
            HostRules.Verdict.PlainHttpNotAllowed -> urlErr = R.string.backup_editor_https_required
            HostRules.Verdict.HostNotLocal -> urlErr = R.string.backup_editor_http_host_not_local
        }
        val path = s.remotePath.trim().trim('/')
        if (path.isEmpty() || path.split('/').any { it.isBlank() || it == "." || it == ".." || it.contains('\\') }) {
            pathErr = R.string.backup_editor_remote_path_invalid
        }
        val creating = s.profileId == null
        if (creating && s.password.isEmpty()) passErr = R.string.backup_editor_password_required
        if (s.encrypt) {
            val newEnc = s.encPassword.isNotEmpty()
            val keepsOld = !newEnc && !creating && s.hasStoredEncPassword
            if (newEnc) {
                if (s.encPassword.length < BackupCrypto.MIN_PASSWORD_LENGTH) encErr = R.string.backup_editor_enc_too_short
                else if (s.encPassword != s.encPasswordConfirm) encErr = R.string.backup_editor_enc_mismatch
            } else if (!keepsOld) {
                encErr = R.string.backup_editor_enc_too_short
            }
        }
        if (listOf(nameErr, urlErr, pathErr, userErr, passErr, encErr).any { it != null }) {
            _state.update {
                it.copy(nameError = nameErr, urlError = urlErr, pathError = pathErr, usernameError = userErr, passwordError = passErr, encError = encErr)
            }
            return
        }

        viewModelScope.launch {
            val id = s.profileId ?: newId()
            val profile = BackupProfile(
                id = id,
                name = s.name.trim(),
                serverUrl = s.serverUrl.trim(),
                remotePath = path,
                username = s.username.trim(),
                enabled = s.enabled,
                allowHttpLocal = s.allowHttpLocal,
                encrypt = s.encrypt,
                frequency = s.frequency,
                keepCount = s.keepCount.toIntOrNull() ?: 10,
                keepDays = s.keepDays.toIntOrNull() ?: 90,
                wifiOnly = s.wifiOnly,
                verifyAfterUpload = s.verifyAfterUpload,
                lastSuccessAt = null,
                lastAttemptAt = null,
                lastError = null,
                createdAt = time.now(),
            ).let { p ->
                if (s.profileId != null) dao.getProfile(id)?.let { old ->
                    p.copy(createdAt = old.createdAt, lastSuccessAt = old.lastSuccessAt, lastAttemptAt = old.lastAttemptAt, lastError = old.lastError)
                } ?: p
                else p
            }
            dao.upsert(profile)
            if (s.password.isNotEmpty()) secrets.put(SecretKeys.webDavPassword(id), s.password)
            if (s.encrypt) {
                if (s.encPassword.isNotEmpty()) secrets.put(SecretKeys.backupEncryption(id), s.encPassword)
            } else {
                secrets.remove(SecretKeys.backupEncryption(id))
            }
            scheduler.reconcile(context, dao.getProfiles())
            _state.update { it.copy(saved = true) }
            snackbar.send(SnackbarMessage(UiText.res(R.string.backup_editor_saved)))
        }
    }

    // ------------------------------------------------------------- connection test

    fun testConnection() {
        if (_state.value.testRunning) return
        _state.update { it.copy(testRunning = true) }
        viewModelScope.launch {
            val s = _state.value
            val profile = buildDraft(s) ?: run {
                _state.update { it.copy(testRunning = false, testResults = listOf(WebDavClient.TestStepResult(WebDavClient.TestStep.READ, false, io.github.submark.feature.backup.data.WebDavError.InvalidUrl))) }
                return@launch
            }
            // Prefer the freshly typed password; fall back to the stored one when editing.
            val password = s.password.ifEmpty {
                s.profileId?.let { secrets.get(SecretKeys.webDavPassword(it)) }
            }
            val results = runCatching { dav.testConnection(profile, password) }
                .getOrElse { listOf(WebDavClient.TestStepResult(WebDavClient.TestStep.READ, false, io.github.submark.feature.backup.data.WebDavError.Io(it.message))) }
            _state.update { it.copy(testRunning = false, testResults = results) }
        }
    }

    private fun buildDraft(s: ProfileEditorUiState): BackupProfile? {
        if (HostRules.check(s.serverUrl.trim(), s.allowHttpLocal) != HostRules.Verdict.Ok) return null
        return BackupProfile(
            id = s.profileId ?: "draft",
            name = s.name.ifBlank { "draft" },
            serverUrl = s.serverUrl.trim(),
            remotePath = s.remotePath.ifBlank { "SubMark" },
            username = s.username,
            allowHttpLocal = s.allowHttpLocal,
            createdAt = time.now(),
        )
    }

    // ------------------------------------------------------------- delete (server files stay)

    fun deleteProfile() {
        val id = _state.value.profileId ?: return
        viewModelScope.launch {
            dao.getProfile(id)?.let { dao.delete(it) }
            secrets.remove(SecretKeys.webDavPassword(id))
            secrets.remove(SecretKeys.backupEncryption(id))
            scheduler.reconcile(context, dao.getProfiles())
            _state.update { it.copy(deleted = true) }
        }
    }
}
