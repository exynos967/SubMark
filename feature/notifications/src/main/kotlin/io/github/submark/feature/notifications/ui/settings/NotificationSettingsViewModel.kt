package io.github.submark.feature.notifications.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.repository.SubscriptionExtrasRepository
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.settings.NotificationSettings
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.model.Subscription
import io.github.submark.feature.notifications.data.CalendarSyncManager
import io.github.submark.feature.notifications.data.ReminderRebuildService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

/** One row in the custom-reminder list of the settings screen. */
data class CustomReminderRow(
    val id: String,
    val name: String,
    val nextReminder: java.time.LocalDateTime?,
    val today: Boolean,
)

data class NotificationSettingsUiState(
    val loading: Boolean = true,
    val settings: NotificationSettings = NotificationSettings(),
    val permissionGranted: Boolean = false,
    val exactAlarmsGranted: Boolean = true,
    val calendarPermissionGranted: Boolean = false,
    val customRows: List<CustomReminderRow> = emptyList(),
    val permissionEvent: Int = 0,
)

sealed interface NotificationSettingsEvent {
    data object Saved : NotificationSettingsEvent
    data object TestScheduled : NotificationSettingsEvent
    data object TestNoPermission : NotificationSettingsEvent
}

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val extrasRepository: SubscriptionExtrasRepository,
    private val rebuild: ReminderRebuildService,
    private val calendarSync: CalendarSyncManager,
    private val time: TimeProvider,
) : ViewModel() {

    private val permissions = MutableStateFlow(Triple(false, true, false))
    private val _events = Channel<NotificationSettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<NotificationSettingsUiState> = combine(
        settingsRepository.settings,
        subscriptionRepository.observeAll(),
        extrasRepository.observeAllReminders(),
        permissions,
    ) { appSettings, subs, reminders, perm ->
        val prefs = appSettings.notifications
        val zone = time.zone()
        val now = java.time.LocalDateTime.now(zone)
        val remindersBySub = reminders.groupBy { it.subscriptionId }
        val triggers = io.github.submark.feature.notifications.data.ReminderTriggers.compute(
            subs, remindersBySub, prefs, now, zone,
        ) { sub -> sub.price.toPlainString() + " " + sub.currencyCode }
        val nextBySub = triggers.groupBy { it.subscriptionId }
        val custom = subs.filter { it.customReminderEnabled }
        NotificationSettingsUiState(
            loading = false,
            settings = prefs,
            permissionGranted = perm.first,
            exactAlarmsGranted = perm.second,
            calendarPermissionGranted = perm.third,
            customRows = custom.map { sub ->
                val next = nextBySub[sub.id]?.firstOrNull()?.triggerAt
                CustomReminderRow(
                    id = sub.id,
                    name = sub.name,
                    nextReminder = next,
                    today = next?.toLocalDate() == time.today(),
                )
            }.sortedBy { it.name.lowercase() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationSettingsUiState())

    /** Called from the screen whenever it resumes: re-check runtime state. */
    fun refreshPermissions() {
        viewModelScope.launch {
            permissions.value = Triple(
                rebuild.hasPermission(),
                rebuild.canScheduleExact(),
                calendarSync.hasPermission(),
            )
        }
    }

    fun onPermissionResult() = refreshPermissions()

    private fun update(transform: (NotificationSettings) -> NotificationSettings) {
        viewModelScope.launch {
            settingsRepository.update { app ->
                app.copy(notifications = transform(app.notifications).copy(userPreferenceSet = true))
            }
            rebuild.rebuildAll()
            _events.trySend(NotificationSettingsEvent.Saved)
        }
    }

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }
    fun setAdvanceDays(days: Int?) = update { it.copy(advanceDays = days) }
    fun setSlot(slot: Int, time: LocalTime) = update { prefs ->
        when (slot) {
            0 -> prefs.copy(firstTime = time)
            1 -> prefs.copy(secondTime = time)
            else -> prefs.copy(thirdTime = time)
        }
    }

    fun setSlotEnabled(slot: Int, enabled: Boolean) = update { prefs ->
        when (slot) {
            0 -> prefs.copy(firstSlotEnabled = enabled)
            1 -> prefs.copy(secondSlotEnabled = enabled)
            else -> prefs.copy(thirdSlotEnabled = enabled)
        }
    }

    fun setCalendarSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { app ->
                app.copy(notifications = app.notifications.copy(calendarSyncEnabled = enabled))
            }
            if (enabled) calendarSync.syncAll()
            refreshPermissions()
            rebuild.rebuildAll()
            _events.trySend(NotificationSettingsEvent.Saved)
        }
    }

    fun sendTest() {
        if (!state.value.permissionGranted) {
            _events.trySend(NotificationSettingsEvent.TestNoPermission)
        } else {
            rebuild.scheduleTest()
            _events.trySend(NotificationSettingsEvent.TestScheduled)
        }
    }
}
