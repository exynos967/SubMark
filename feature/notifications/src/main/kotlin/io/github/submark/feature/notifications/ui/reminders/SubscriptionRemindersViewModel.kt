package io.github.submark.feature.notifications.ui.reminders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.repository.SubscriptionExtrasRepository
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.service.SubscriptionService
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.model.Subscription
import io.github.submark.core.ui.navigation.SubscriptionRemindersRoute
import io.github.submark.feature.notifications.data.CalendarSyncManager
import io.github.submark.feature.notifications.data.CalendarSyncResult
import io.github.submark.feature.notifications.data.ReminderRebuildService
import io.github.submark.feature.notifications.data.ReminderTriggers
import io.github.submark.feature.notifications.data.ReminderTrigger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

data class ReminderRow(val daysBefore: Int, val time: LocalTime)

data class SubscriptionRemindersUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val subscriptionName: String = "",
    val customEnabled: Boolean = false,
    val reminders: List<ReminderRow> = emptyList(),
    val calendarSyncEnabled: Boolean = false,
    val globalCalendarSyncEnabled: Boolean = false,
    val calendarPermissionGranted: Boolean = false,
    val preview: List<LocalDateTime> = emptyList(),
)

sealed interface SubscriptionRemindersEvent {
    data object CalendarPermissionRequired : SubscriptionRemindersEvent
}

@HiltViewModel
class SubscriptionRemindersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val subscriptionRepository: SubscriptionRepository,
    private val extrasRepository: SubscriptionExtrasRepository,
    private val subscriptionService: SubscriptionService,
    private val settingsRepository: SettingsRepository,
    private val rebuild: ReminderRebuildService,
    private val calendarSync: CalendarSyncManager,
    private val time: TimeProvider,
) : ViewModel() {
    private val subscriptionId = savedStateHandle.toRoute<SubscriptionRemindersRoute>().subscriptionId

    private val _events = Channel<SubscriptionRemindersEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<SubscriptionRemindersUiState> = combine(
        subscriptionRepository.observe(subscriptionId),
        extrasRepository.observeReminders(subscriptionId),
        settingsRepository.settings,
    ) { sub, reminders, appSettings ->
        if (sub == null) return@combine SubscriptionRemindersUiState(loading = false, missing = true)
        val prefs = appSettings.notifications
        val zone = time.zone()
        val now = LocalDateTime.now(zone)
        val triggers: List<ReminderTrigger> = ReminderTriggers.compute(
            listOf(sub), mapOf(subscriptionId to reminders), prefs, now, zone,
        ) { "" }
        SubscriptionRemindersUiState(
            loading = false,
            subscriptionName = sub.name,
            customEnabled = sub.customReminderEnabled,
            reminders = reminders.map { ReminderRow(it.daysBefore, it.time) },
            calendarSyncEnabled = sub.calendarSyncEnabled,
            globalCalendarSyncEnabled = prefs.calendarSyncEnabled,
            calendarPermissionGranted = calendarSync.hasPermission(),
            preview = triggers.map { it.triggerAt },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionRemindersUiState())

    fun refreshPermissions() {
        // state reads the permission only when combine re-emits; force a settings touch instead.
    }

    fun setCustomEnabled(enabled: Boolean) {
        viewModelScope.launch {
            subscriptionService.setNotificationFlags(subscriptionId, customReminderEnabled = enabled, calendarSyncEnabled = null)
        }
    }

    fun addReminder(daysBefore: Int, time: LocalTime) {
        viewModelScope.launch {
            extrasRepository.setReminders(subscriptionId, current() + (daysBefore to time))
        }
    }

    fun removeReminder(row: ReminderRow) {
        viewModelScope.launch {
            extrasRepository.setReminders(subscriptionId, current() - (row.daysBefore to row.time))
        }
    }

    fun setCalendarSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !calendarSync.hasPermission()) {
                _events.trySend(SubscriptionRemindersEvent.CalendarPermissionRequired)
                return@launch
            }
            calendarSync.setEnabledFor(subscriptionId, enabled)
        }
    }

    private fun current(): List<Pair<Int, LocalTime>> =
        state.value.reminders.map { it.daysBefore to it.time }
}
