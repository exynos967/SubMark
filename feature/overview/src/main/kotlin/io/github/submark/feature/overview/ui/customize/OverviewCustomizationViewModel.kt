package io.github.submark.feature.overview.ui.customize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.settings.ClassicOverviewComponent
import io.github.submark.core.data.settings.ComponentSetting
import io.github.submark.core.data.settings.ModernOverviewComponent
import io.github.submark.core.data.settings.OverviewLayout
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.overview.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OverviewPreset { COMPLETE, ESSENTIAL }

data class OverviewCustomizationUiState(
    val layout: OverviewLayout = io.github.submark.core.data.settings.OverviewLayout.MODERN,
    val classicComponents: List<ComponentSetting<ClassicOverviewComponent>> = emptyList(),
    val modernComponents: List<ComponentSetting<ModernOverviewComponent>> = emptyList(),
) {
    val items: List<ComponentSetting<*>> get() = when (layout) {
        OverviewLayout.MODERN -> modernComponents
        OverviewLayout.CLASSIC -> classicComponents
    }
}

@HiltViewModel
class OverviewCustomizationViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    private val snackbarChannel = Channel<SnackbarMessage>(Channel.BUFFERED)
    val snackbars: Flow<SnackbarMessage> = snackbarChannel.receiveAsFlow()

    val uiState: StateFlow<OverviewCustomizationUiState> = combine(
        settings.settings.map { it.overview.layout }.distinctUntilChanged(),
        settings.settings.map { it.overview.classicComponents },
        settings.settings.map { it.overview.modernComponents },
    ) { layout, classic, modern ->
        OverviewCustomizationUiState(
            layout = layout,
            classicComponents = classic,
            modernComponents = modern,
        )
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), OverviewCustomizationUiState())

    fun setLayout(layout: OverviewLayout) {
        viewModelScope.launch {
            settings.update { it.copy(overview = it.overview.copy(layout = layout)) }
        }
    }

    fun toggleVisible(component: ComponentSetting<*>, visible: Boolean) {
        val current = uiState.value
        when {
            uiState.value.layout == OverviewLayout.MODERN ->
                saveModern(current.modernComponents.map { if (it == component) it.copy(visible = visible) else it })
            else ->
                saveClassic(current.classicComponents.map { if (it == component) it.copy(visible = visible) else it })
        }
    }

    fun move(from: Int, to: Int) {
        val current = uiState.value
        val list = current.items.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val item = list.removeAt(from)
        list.add(to, item)
        when {
            uiState.value.layout == OverviewLayout.MODERN -> saveModern(list.map { it as ComponentSetting<ModernOverviewComponent> })
            else -> saveClassic(list.map { it as ComponentSetting<ClassicOverviewComponent> })
        }
    }

    fun applyPreset(preset: OverviewPreset) {
        when (uiState.value.layout) {
            OverviewLayout.MODERN -> {
                val modern = ModernOverviewComponent.entries.map { ComponentSetting(it) }
                saveModern(modern.map {
                    when (preset) {
                        OverviewPreset.COMPLETE -> it.copy(visible = true)
                        OverviewPreset.ESSENTIAL -> it.copy(visible = it.id in setOf(
                            ModernOverviewComponent.SPENDING_HERO,
                            ModernOverviewComponent.COMING_UP,
                            ModernOverviewComponent.PAYMENT_SCHEDULE,
                        ))
                    }
                })
            }
            OverviewLayout.CLASSIC -> {
                val classic = ClassicOverviewComponent.entries.map { ComponentSetting(it) }
                saveClassic(classic.map {
                    when (preset) {
                        OverviewPreset.COMPLETE -> it.copy(visible = true)
                        OverviewPreset.ESSENTIAL -> it.copy(visible = it.id in setOf(
                            ClassicOverviewComponent.EXPENSE_OVERVIEW,
                            ClassicOverviewComponent.UPCOMING_PAYMENTS,
                            ClassicOverviewComponent.RECENT_PAID,
                        ))
                    }
                })
            }
        }
    }

    fun resetOrder() {
        val modernDefaults = ModernOverviewComponent.entries.map { ComponentSetting(it) }
        val classicDefaults = ClassicOverviewComponent.entries.map { ComponentSetting(it) }
        when (uiState.value.layout) {
            OverviewLayout.MODERN -> saveModern(modernDefaults)
            OverviewLayout.CLASSIC -> saveClassic(classicDefaults)
        }
    }

    private fun saveModern(list: List<ComponentSetting<ModernOverviewComponent>>) {
        viewModelScope.launch {
            settings.update { it.copy(overview = it.overview.copy(modernComponents = list)) }
        }
    }

    private fun saveClassic(list: List<ComponentSetting<ClassicOverviewComponent>>) {
        viewModelScope.launch {
            settings.update { it.copy(overview = it.overview.copy(classicComponents = list)) }
        }
    }
}
