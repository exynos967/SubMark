package io.github.submark.feature.backup.ui.profiles

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.submark.core.database.dao.BackupDao
import io.github.submark.core.model.BackupProfile
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.backup.R
import io.github.submark.feature.backup.data.BackupRunner
import io.github.submark.feature.backup.data.BackupScheduler
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfilesUiState(
    val profiles: List<BackupProfile> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class WebDavProfilesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: BackupDao,
    private val scheduler: BackupScheduler,
    private val runner: BackupRunner,
) : ViewModel() {

    private val snackbar = Channel<SnackbarMessage>(Channel.CONFLATED)
    val messages: Flow<SnackbarMessage> = snackbar.receiveAsFlow()

    val state: StateFlow<ProfilesUiState> = dao.observeProfiles()
        .map { ProfilesUiState(profiles = it, loading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfilesUiState())

    /** "Back up now" — an expedited one-shot worker; the periodic schedule stays intact. */
    fun backUpNow(profile: BackupProfile) {
        viewModelScope.launch {
            if (runner.isBusy) {
                snackbar.send(SnackbarMessage(UiText.res(R.string.backup_running)))
                return@launch
            }
            scheduler.enqueueRunNow(context, profile.id)
            snackbar.send(SnackbarMessage(UiText.res(R.string.backup_backup_started)))
        }
    }

    /** Rotate schedules after a profile has been saved elsewhere (editor calls [sync] on save). */
    fun sync() {
        viewModelScope.launch { scheduler.reconcile(context, dao.getProfiles()) }
    }
}
