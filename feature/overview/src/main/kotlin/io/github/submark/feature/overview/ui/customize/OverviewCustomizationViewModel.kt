package io.github.submark.feature.overview.ui.customize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.settings.ComponentSetting
import io.github.submark.core.data.settings.ModernOverviewComponent
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.overview.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OverviewPreset { COMPLETE, ESSENTIAL }

data class OverviewCustomizationUiState(
    val items: List<ComponentSetting<ModernOverviewComponent>> = emptyList(),
)

@HiltViewModel
class OverviewCustomizationViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    private val snackbarChannel = Channel<SnackbarMessage>(Channel.BUFFERED)
    val snackbars: Flow<SnackbarMessage> = snackbarChannel.receiveAsFlow()

    val uiState: StateFlow<OverviewCustomizationUiState> = settings.settings
        .map { OverviewCustomizationUiState(items = it.overview.modernComponents) }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), OverviewCustomizationUiState())

    fun toggleVisible(component: ComponentSetting<ModernOverviewComponent>, visible: Boolean) {
        save(uiState.value.items.map { if (it == component) it.copy(visible = visible) else it })
    }

    fun move(from: Int, to: Int) {
        val list = uiState.value.items.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val item = list.removeAt(from)
        list.add(to, item)
        save(list)
    }

    fun applyPreset(preset: OverviewPreset) {
        save(ModernOverviewComponent.entries.map {
            ComponentSetting(
                id = it,
                visible = when (preset) {
                    OverviewPreset.COMPLETE -> true
                    OverviewPreset.ESSENTIAL -> it in setOf(
                        ModernOverviewComponent.SPENDING_HERO,
                        ModernOverviewComponent.COMING_UP,
                        ModernOverviewComponent.PAYMENT_SCHEDULE,
                    )
                },
            )
        })
    }

    fun resetOrder() {
        save(ModernOverviewComponent.entries.map { ComponentSetting(it) })
    }

    private fun save(list: List<ComponentSetting<ModernOverviewComponent>>) {
        viewModelScope.launch {
            settings.update { it.copy(overview = it.overview.copy(modernComponents = list)) }
        }
    }
}
