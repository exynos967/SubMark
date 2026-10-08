package io.github.submark.feature.notifications.ui.diagnostics

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.feature.notifications.data.NotificationChannels
import io.github.submark.feature.notifications.data.ReminderRebuildService
import io.github.submark.feature.notifications.data.ScheduledReminder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiagnosticsUiState(
    val loading: Boolean = true,
    val permissionGranted: Boolean = false,
    val exactAlarmsGranted: Boolean = false,
    val masterEnabled: Boolean = false,
    val userPreferenceSet: Boolean = false,
    val channels: List<Pair<String, Boolean>> = emptyList(),
    val scheduled: List<ScheduledReminder> = emptyList(),
)

sealed interface DiagnosticsEvent {
    data object Rebuilt : DiagnosticsEvent
    data class Copied(val text: String) : DiagnosticsEvent
}

@HiltViewModel
class NotificationDiagnosticsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val rebuild: ReminderRebuildService,
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticsUiState())
    val state: StateFlow<DiagnosticsUiState> = _state.asStateFlow()

    private val _events = Channel<DiagnosticsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val prefs = settingsRepository.settings.first().notifications
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            val channelStates = NotificationChannels.ALL.map { id ->
                val channel = manager?.getNotificationChannel(id)
                id to (channel == null || channel.importance != android.app.NotificationManager.IMPORTANCE_NONE)
            }
            _state.value = DiagnosticsUiState(
                loading = false,
                permissionGranted = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                exactAlarmsGranted = rebuild.canScheduleExact(),
                masterEnabled = prefs.enabled,
                userPreferenceSet = prefs.userPreferenceSet,
                channels = channelStates,
                scheduled = rebuild.scheduled().sortedBy { it.triggerAtMillis },
            )
        }
    }

    fun rebuildAll() {
        viewModelScope.launch {
            rebuild.rebuildAll()
            refresh()
            _events.trySend(DiagnosticsEvent.Rebuilt)
        }
    }

    fun copyDiagnostics() {
        val state = _state.value
        val text = buildString {
            appendLine("permission=${state.permissionGranted}")
            appendLine("exact=${state.exactAlarmsGranted}")
            appendLine("master=${state.masterEnabled}")
            appendLine("prefSet=${state.userPreferenceSet}")
            state.channels.forEach { appendLine("channel/${it.first}=${if (it.second) "on" else "off"}") }
            state.scheduled.forEach {
                appendLine("scheduled[${it.requestCode}] ${it.kind} ${it.subscriptionName} due=${it.cycleDueDate} at=${it.triggerAtMillis}")
            }
        }
        _events.trySend(DiagnosticsEvent.Copied(text))
    }

    fun sendTest() = rebuild.scheduleTest()
}
