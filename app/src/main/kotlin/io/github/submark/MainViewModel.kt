package io.github.submark

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.service.ProcessDueSummary
import io.github.submark.core.data.settings.AppSettings
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.feature.settings.security.AppLockController
import io.github.submark.startup.AppStartup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** One-time alerts derived from the launch-time due processing. */
data class StartupAlerts(
    val expiredNames: List<String>,
    /** id to name of trials that ended and need a renewal decision. */
    val endedTrials: List<Pair<String, String>>,
    val walletFailureNames: List<String>,
) {
    val isEmpty: Boolean get() = expiredNames.isEmpty() && endedTrials.isEmpty() && walletFailureNames.isEmpty()
}

data class MainUiState(
    val ready: Boolean = false,
    val settings: AppSettings? = null,
    val locked: Boolean = false,
    val alerts: StartupAlerts? = null,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val appStartup: AppStartup,
    private val lockController: AppLockController,
    private val subscriptionRepository: SubscriptionRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val locked = MutableStateFlow(true)
    private val alerts = MutableStateFlow<StartupAlerts?>(null)
    private var backgroundedAt: Instant? = null
    private var lastForegroundDay: LocalDate = timeProvider.today()

    val uiState: StateFlow<MainUiState> = combine(
        appStartup.ready,
        settingsRepository.settings,
        lockController.isLockEnabled,
        locked,
        alerts,
    ) { ready, settings, lockEnabled, isLocked, pendingAlerts ->
        MainUiState(ready = ready, settings = settings, locked = lockEnabled && isLocked, alerts = pendingAlerts)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            backgroundedAt = timeProvider.now()
        }

        override fun onStart(owner: LifecycleOwner) {
            val since = backgroundedAt ?: return
            if (lockController.shouldLock(since, timeProvider.now())) locked.value = true
            val today = timeProvider.today()
            if (today != lastForegroundDay) {
                lastForegroundDay = today
                appStartup.refreshDue()
            }
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
        viewModelScope.launch {
            appStartup.dueSummary.collect { summary -> if (summary != null) alerts.value = summary.toAlerts() }
        }
    }

    fun onUnlocked() {
        locked.value = false
    }

    fun dismissAlerts() {
        alerts.value = null
        appStartup.consumeSummary()
    }

    private suspend fun ProcessDueSummary.toAlerts(): StartupAlerts? {
        val all = subscriptionRepository.getAll().associateBy { it.id }
        val result = StartupAlerts(
            expiredNames = expiredNames,
            endedTrials = trialEndedIds.mapNotNull { id -> all[id]?.let { id to it.name } },
            walletFailureNames = walletFailures.map { it.subscriptionName }.distinct(),
        )
        return result.takeUnless { it.isEmpty }
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver)
    }

}
