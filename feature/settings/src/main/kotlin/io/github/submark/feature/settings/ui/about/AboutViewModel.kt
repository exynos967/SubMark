package io.github.submark.feature.settings.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.network.AppInfo
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.settings.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AboutUiState(val version: String, val developerOptionsUnlocked: Boolean = false)

/** Counts taps on the version row; the 7th unlocks developer options. */
internal class UnlockCounter(private val required: Int = REQUIRED_TAPS) {
    private var taps = 0

    /** Taps still needed after this one; 0 = unlock now. */
    fun tap(): Int {
        taps = (taps + 1).coerceAtMost(required)
        return required - taps
    }

    companion object {
        const val REQUIRED_TAPS = 7
        /** Start telling the user how many taps remain once this few are left. */
        const val HINT_FROM = 4
    }
}

@HiltViewModel
class AboutViewModel @Inject constructor(
    appInfo: AppInfo,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val counter = UnlockCounter()
    private val snackbar = Channel<SnackbarMessage>(Channel.CONFLATED)
    val messages: Flow<SnackbarMessage> = snackbar.receiveAsFlow()

    val state: StateFlow<AboutUiState> = settings.settings
        .map { AboutUiState(appInfo.versionName, it.developerOptionsUnlocked) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AboutUiState(appInfo.versionName))

    fun onVersionTap() {
        viewModelScope.launch {
            if (settings.settings.first().developerOptionsUnlocked) {
                snackbar.send(SnackbarMessage(UiText.res(R.string.settings_about_dev_already)))
                return@launch
            }
            val left = counter.tap()
            when {
                left == 0 -> {
                    settings.update { it.copy(developerOptionsUnlocked = true) }
                    snackbar.send(SnackbarMessage(UiText.res(R.string.settings_about_dev_unlocked)))
                }
                left < UnlockCounter.HINT_FROM ->
                    snackbar.send(SnackbarMessage(UiText.plural(R.plurals.settings_about_dev_taps_left, left)))
            }
        }
    }
}
