package io.github.submark.feature.integrations.panel.ui.panel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.settings.ServicePanelTab
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.model.ServiceType
import io.github.submark.feature.integrations.panel.data.BudgetComputation
import io.github.submark.feature.integrations.panel.data.BudgetSnapshot
import io.github.submark.feature.integrations.panel.data.PanelFetchException
import io.github.submark.feature.integrations.panel.data.PanelErrorReason
import io.github.submark.feature.integrations.panel.data.PanelRepository
import io.github.submark.feature.integrations.panel.data.ServiceSnapshot
import io.github.submark.feature.integrations.panel.data.SnapshotCodec
import io.github.submark.core.model.ApiBudgetConfig
import io.github.submark.core.model.ServiceConnection
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BudgetItem(
    val config: ApiBudgetConfig,
    val snapshot: BudgetSnapshot?,
    val computation: BudgetComputation,
    val refreshing: Boolean,
)

data class ServiceItem(
    val connection: ServiceConnection,
    val snapshot: ServiceSnapshot?,
    val refreshing: Boolean,
    /** Seconds until manual refresh is allowed again (0 = allowed). */
    val refreshCooldownSeconds: Int,
)

data class PanelUiState(
    val loading: Boolean = true,
    val tab: ServicePanelTab = ServicePanelTab.SERVICE,
    val budgets: List<BudgetItem> = emptyList(),
    val services: List<ServiceItem> = emptyList(),
    val refreshingAll: Boolean = false,
    val nowEpochSeconds: Long = 0,
)

@HiltViewModel
class PanelViewModel @Inject constructor(
    private val repository: PanelRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val refreshingBudgets = MutableStateFlow<Set<String>>(emptySet())
    private val refreshingServices = MutableStateFlow<Set<String>>(emptySet())
    private val refreshingAll = MutableStateFlow(false)
    /** Manual Clash refreshes per connection (epoch seconds); cooldown lives only in memory. */
    private val manualRefreshAt = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** Bumped after any refresh so relative timestamps recompute. */
    private val tick = MutableStateFlow(0L)

    private val _messages = Channel<PanelErrorReason>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private data class Flags(
        val budgets: Set<String>,
        val services: Set<String>,
        val all: Boolean,
        val manual: Map<String, Long>,
        val tick: Long,
    )

    val state: StateFlow<PanelUiState> = combine(
        combine(
            repository.observeBudgets(),
            repository.observeServices(),
            settings.settings,
        ) { budgets, services, s -> Triple(budgets, services, s.integrations.servicePanelTab) },
        combine(refreshingBudgets, refreshingServices, refreshingAll) { b, s, a -> Triple(b, s, a) },
        combine(manualRefreshAt, tick) { m, t -> m to t },
    ) { data, inFlight, manualAndTick ->
        val (budgets, services, tab) = data
        val now = time.now().epochSecond
        PanelUiState(
            loading = false,
            tab = tab,
            budgets = budgets.map { config ->
                val snapshot = config.snapshotJson?.let(SnapshotCodec::decodeBudget)
                BudgetItem(
                    config = config,
                    snapshot = snapshot,
                    computation = BudgetComputation.compute(
                        snapshot, config.monthlyBudget, config.dailyBudget, config.alertThreshold,
                    ),
                    refreshing = config.id in inFlight.first,
                )
            },
            services = services.map { connection ->
                val snapshot = connection.snapshotJson?.let(SnapshotCodec::decodeService)
                val lastManual = manualAndTick.first[connection.id]
                val cooldown = if (connection.type == ServiceType.CLASH && lastManual != null) {
                    (MANUAL_COOLDOWN_SECONDS - (now - lastManual)).toInt().coerceAtLeast(0)
                } else 0
                ServiceItem(
                    connection = connection,
                    snapshot = snapshot,
                    refreshing = connection.id in inFlight.second,
                    refreshCooldownSeconds = cooldown,
                )
            },
            refreshingAll = inFlight.third,
            nowEpochSeconds = now,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PanelUiState())

    fun selectTab(tab: ServicePanelTab) {
        viewModelScope.launch {
            settings.update { it.copy(integrations = it.integrations.copy(servicePanelTab = tab)) }
        }
    }

    fun refreshBudget(id: String) {
        if (id in refreshingBudgets.value) return
        viewModelScope.launch {
            refreshingBudgets.value += id
            try {
                val config = repository.getBudget(id) ?: return@launch
                repository.refreshBudget(config).onFailure { notifyError(it) }
            } finally {
                refreshingBudgets.value -= id
                tick.value++
            }
        }
    }

    fun refreshService(id: String, manual: Boolean = false) {
        if (id in refreshingServices.value) return
        if (manual) {
            val last = manualRefreshAt.value[id]
            val now = time.now().epochSecond
            if (last != null && now - last < MANUAL_COOLDOWN_SECONDS) return
            manualRefreshAt.value += id to now
            tick.value++ // show "Wait Ns" immediately
        }
        viewModelScope.launch {
            refreshingServices.value += id
            try {
                val connection = repository.getService(id) ?: return@launch
                repository.refreshService(connection).onFailure { notifyError(it) }
            } finally {
                refreshingServices.value -= id
                tick.value++
            }
        }
    }

    fun refreshAll() {
        if (refreshingAll.value) return
        viewModelScope.launch {
            refreshingAll.value = true
            try {
                val s = state.value
                s.budgets.filter { it.config.enabled }.forEach { item ->
                    refreshingBudgets.value += item.config.id
                    repository.getBudget(item.config.id)?.let { repository.refreshBudget(it).onFailure(::notifyError) }
                    refreshingBudgets.value -= item.config.id
                }
                s.services.filter { it.connection.enabled }.forEach { item ->
                    refreshingServices.value += item.connection.id
                    repository.getService(item.connection.id)?.let { repository.refreshService(it).onFailure(::notifyError) }
                    refreshingServices.value -= item.connection.id
                }
            } finally {
                refreshingAll.value = false
                tick.value++
            }
        }
    }

    fun setBudgetEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { repository.setBudgetEnabled(id, enabled) }
    }

    fun setServiceEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { repository.setServiceEnabled(id, enabled) }
    }

    private fun notifyError(error: Throwable) {
        val reason = (error as? PanelFetchException)?.reason ?: PanelErrorReason.UNKNOWN
        _messages.trySend(reason)
    }

    companion object {
        const val MANUAL_COOLDOWN_SECONDS = 30
    }
}
