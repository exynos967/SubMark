package io.github.submark.feature.settings.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.settings.AppLanguage
import io.github.submark.core.data.settings.AppSettings
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.feature.settings.data.AppLocales
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Plain preference editor shared by the pages that only read and write [AppSettings]
 * (appearance, fonts, interface, customization). Page-specific rules live in pure helpers next to each page.
 */
@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    /** null while loading. */
    val settings: StateFlow<AppSettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _language = MutableStateFlow<AppLanguage?>(AppLocales.current())
    /** Language actually applied by the platform; falls back to the stored value when unknown. */
    val language: StateFlow<AppLanguage?> = _language.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    fun setLanguage(language: AppLanguage) {
        _language.value = language
        viewModelScope.launch {
            repository.update { it.copy(display = it.display.copy(language = language)) }
            AppLocales.apply(language)
        }
    }
}
