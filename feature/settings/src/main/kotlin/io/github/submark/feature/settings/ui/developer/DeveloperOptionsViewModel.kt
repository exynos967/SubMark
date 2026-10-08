package io.github.submark.feature.settings.ui.developer

import android.content.Context
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.submark.core.data.service.SubscriptionService
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.settings.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class WorkItem(val id: String, val name: String, val state: WorkInfo.State, val attempts: Int, val nextRun: Instant?)

data class SystemStatus(val notificationsAllowed: Boolean = false, val ignoringBatteryOptimizations: Boolean = false)

data class DeveloperUiState(
    val loading: Boolean = true,
    val status: SystemStatus = SystemStatus(),
    val remindersEnabled: Boolean = false,
    val userPreferenceSet: Boolean = false,
    /** null = WorkManager unavailable. */
    val works: List<WorkItem>? = emptyList(),
    val processing: Boolean = false,
)

@HiltViewModel
class DeveloperOptionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val subscriptionService: SubscriptionService,
) : ViewModel() {

    private val status = MutableStateFlow(readStatus())
    private val processing = MutableStateFlow(false)
    private val snackbar = Channel<SnackbarMessage>(Channel.BUFFERED)
    val messages: Flow<SnackbarMessage> = snackbar.receiveAsFlow()

    private val works: Flow<List<WorkItem>?> = runCatching {
        WorkManager.getInstance(context)
            .getWorkInfosFlow(WorkQuery.fromStates(WorkInfo.State.entries))
            .map<List<WorkInfo>, List<WorkItem>?> { infos -> infos.map { it.toItem() }.sortedWith(compareBy({ it.state.isFinished }, { it.name })) }
            .catch { emit(null) }
    }.getOrElse { flowOf(null) }

    val state: StateFlow<DeveloperUiState> = combine(settings.settings, status, works, processing) { s, st, w, p ->
        DeveloperUiState(
            loading = false,
            status = st,
            remindersEnabled = s.notifications.enabled,
            userPreferenceSet = s.notifications.userPreferenceSet,
            works = w,
            processing = p,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeveloperUiState())

    /** Called on resume: permissions and battery settings change outside the app. */
    fun refreshStatus() {
        status.value = readStatus()
    }

    fun resetOnboarding() {
        viewModelScope.launch {
            settings.update { it.copy(onboardingCompleted = false) }
            snackbar.send(SnackbarMessage(UiText.res(R.string.settings_dev_onboarding_reset)))
        }
    }

    fun hideDeveloperOptions(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.update { it.copy(developerOptionsUnlocked = false) }
            onDone()
        }
    }

    fun processDue() {
        if (processing.value) return
        processing.value = true
        viewModelScope.launch {
            val message = try {
                val summary = subscriptionService.processDue()
                UiText.res(
                    R.string.settings_dev_process_due_result,
                    summary.autoMarkedCount,
                    summary.expiredIds.size,
                    summary.trialEndedIds.size,
                    summary.walletFailures.size,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiText.res(R.string.settings_dev_process_due_failed, e.message ?: e.javaClass.simpleName)
            } finally {
                processing.value = false
            }
            snackbar.send(SnackbarMessage(message))
        }
    }

    private fun readStatus(): SystemStatus {
        val power = context.getSystemService(PowerManager::class.java)
        return SystemStatus(
            notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            ignoringBatteryOptimizations = power?.isIgnoringBatteryOptimizations(context.packageName) == true,
        )
    }

    private fun WorkInfo.toItem(): WorkItem {
        val className = tags.firstOrNull { it.contains('.') }
        val name = className?.substringAfterLast('.') ?: tags.firstOrNull() ?: id.toString()
        val next = nextScheduleTimeMillis.takeIf { it != Long.MAX_VALUE && it > 0 }?.let(Instant::ofEpochMilli)
        return WorkItem(id.toString(), name, state, runAttemptCount, next)
    }
}
