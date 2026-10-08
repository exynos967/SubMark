package io.github.submark.feature.analytics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.settings.AnalyticsComponent
import io.github.submark.core.data.settings.ComponentSetting
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.settings.SpendingMode
import io.github.submark.core.data.settings.SummaryPeriod
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnalyticsCustomizationUiState(
    val subscriptionComponents: List<ComponentSetting<AnalyticsComponent>> = emptyList(),
    val lifetimeComponents: List<ComponentSetting<AnalyticsComponent>> = emptyList(),
    val defaultPeriod: SummaryPeriod = SummaryPeriod.MONTH,
)

enum class AnalyticsPreset { MINIMAL, DETAILED }

@HiltViewModel
class AnalyticsCustomizationViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<AnalyticsCustomizationUiState> = settings.settings
        .map { s ->
            AnalyticsCustomizationUiState(
                subscriptionComponents = complete(s.analytics.subscriptionComponents),
                lifetimeComponents = complete(s.analytics.lifetimeComponents),
                defaultPeriod = s.analytics.defaultPeriod,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsCustomizationUiState())

    /** Persisted lists may omit components (e.g. lifetime default); append them invisible. */
    private fun complete(list: List<ComponentSetting<AnalyticsComponent>>): List<ComponentSetting<AnalyticsComponent>> =
        if (list.size == AnalyticsComponent.entries.size) list
        else list + AnalyticsComponent.entries.filter { id -> list.none { it.id == id } }.map { ComponentSetting(it, visible = false) }

    fun setVisible(mode: SpendingMode, id: AnalyticsComponent, visible: Boolean) {
        updateComponents(mode) { list ->
            list.map { if (it.id == id) it.copy(visible = visible) else it }
        }
    }

    fun move(mode: SpendingMode, from: Int, to: Int) {
        updateComponents(mode) { list ->
            list.toMutableList().apply { add(to.coerceIn(0, list.size - 1), removeAt(from)) }
        }
    }

    fun applyPreset(mode: SpendingMode, preset: AnalyticsPreset) {
        updateComponents(mode) { list ->
            val visible: Set<AnalyticsComponent> = when (preset) {
                AnalyticsPreset.MINIMAL -> setOf(
                    AnalyticsComponent.FINANCIAL_OVERVIEW,
                    AnalyticsComponent.TREND,
                    AnalyticsComponent.CATEGORY,
                )
                AnalyticsPreset.DETAILED -> AnalyticsComponent.entries.toSet()
            }
            list.map { it.copy(visible = it.id in visible) }
        }
    }

    fun reset(mode: SpendingMode) {
        updateComponents(mode) { list ->
            val defaultVisible = when (mode) {
                SpendingMode.SUBSCRIPTIONS -> AnalyticsComponent.entries.toSet()
                SpendingMode.LIFETIME -> setOf(
                    AnalyticsComponent.FINANCIAL_OVERVIEW,
                    AnalyticsComponent.TREND,
                    AnalyticsComponent.HEATMAP,
                    AnalyticsComponent.CATEGORY,
                )
            }
            AnalyticsComponent.entries.map { ComponentSetting(it, visible = it in defaultVisible) }
        }
    }

    fun setDefaultPeriod(period: SummaryPeriod) {
        viewModelScope.launch {
            settings.update { it.copy(analytics = it.analytics.copy(defaultPeriod = period)) }
        }
    }

    private fun updateComponents(
        mode: SpendingMode,
        transform: (List<ComponentSetting<AnalyticsComponent>>) -> List<ComponentSetting<AnalyticsComponent>>,
    ) {
        viewModelScope.launch {
            settings.update { s ->
                when (mode) {
                    SpendingMode.SUBSCRIPTIONS -> s.copy(
                        analytics = s.analytics.copy(subscriptionComponents = transform(complete(s.analytics.subscriptionComponents))),
                    )
                    SpendingMode.LIFETIME -> s.copy(
                        analytics = s.analytics.copy(lifetimeComponents = transform(complete(s.analytics.lifetimeComponents))),
                    )
                }
            }
        }
    }
}
