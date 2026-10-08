package io.github.submark.feature.settings.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.settings.AppLanguage
import io.github.submark.core.data.settings.FontTheme
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.settings.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SettingsRootUiState(
    val loading: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val fontTheme: FontTheme = FontTheme.MODERN,
    val defaultCurrency: String = "",
    val notificationsEnabled: Boolean = false,
    val appLockEnabled: Boolean = false,
    val developerOptionsUnlocked: Boolean = false,
)

@HiltViewModel
class SettingsRootViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<SettingsRootUiState> = settings.settings.map { s ->
        SettingsRootUiState(
            loading = false,
            theme = s.display.theme,
            language = s.display.language,
            fontTheme = s.font.theme,
            defaultCurrency = s.money.defaultCurrencyCode,
            notificationsEnabled = s.notifications.enabled,
            appLockEnabled = s.security.hasPasscode,
            developerOptionsUnlocked = s.developerOptionsUnlocked,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRootUiState())
}
