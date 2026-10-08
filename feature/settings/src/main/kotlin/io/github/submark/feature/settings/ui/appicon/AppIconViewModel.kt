package io.github.submark.feature.settings.ui.appicon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.settings.R
import io.github.submark.feature.settings.data.AppIconManager
import io.github.submark.feature.settings.data.LauncherIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AppIconUiState(val selected: LauncherIcon? = null, val applying: Boolean = false)

@HiltViewModel
class AppIconViewModel @Inject constructor(
    private val manager: AppIconManager,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AppIconUiState())
    /** Reflects the package manager, which is the real source of truth for the launcher entry. */
    val state: StateFlow<AppIconUiState> = _state.asStateFlow()

    private val snackbar = Channel<SnackbarMessage>(Channel.BUFFERED)
    val messages: Flow<SnackbarMessage> = snackbar.receiveAsFlow()

    init {
        viewModelScope.launch {
            val current = withContext(Dispatchers.IO) { manager.current() }
            _state.value = AppIconUiState(selected = current)
        }
    }

    fun select(icon: LauncherIcon) {
        if (_state.value.selected == icon || _state.value.applying) return
        _state.value = _state.value.copy(applying = true)
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { manager.apply(icon) }.isSuccess }
            if (ok) {
                settings.update { it.copy(display = it.display.copy(appIcon = icon.setting)) }
                _state.value = AppIconUiState(selected = icon)
                snackbar.send(SnackbarMessage(UiText.res(R.string.settings_app_icon_changed)))
            } else {
                _state.value = _state.value.copy(applying = false)
                snackbar.send(SnackbarMessage(UiText.res(R.string.settings_app_icon_failed)))
            }
        }
    }
}
