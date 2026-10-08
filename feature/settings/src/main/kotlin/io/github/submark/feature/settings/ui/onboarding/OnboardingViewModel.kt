package io.github.submark.feature.settings.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.settings.SettingsRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    /** Persists completion before [onDone] so the shell does not show onboarding again. */
    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.update { it.copy(onboardingCompleted = true) }
            onDone()
        }
    }
}
