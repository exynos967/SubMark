package io.github.submark.feature.notifications.ui.settings

import java.time.LocalTime

/** User actions on the notification settings screen. */
sealed interface NotificationSettingsAction {
    data class SetEnabled(val enabled: Boolean) : NotificationSettingsAction
    data class SetAdvanceDays(val days: Int?) : NotificationSettingsAction
    data class SetSlotTime(val slot: Int, val time: LocalTime) : NotificationSettingsAction
    data class SetSlotEnabled(val slot: Int, val enabled: Boolean) : NotificationSettingsAction
    data class SetCalendarSync(val enabled: Boolean) : NotificationSettingsAction
    data object SendTest : NotificationSettingsAction
}

fun NotificationSettingsViewModel.apply(action: NotificationSettingsAction) {
    when (action) {
        is NotificationSettingsAction.SetEnabled -> setEnabled(action.enabled)
        is NotificationSettingsAction.SetAdvanceDays -> setAdvanceDays(action.days)
        is NotificationSettingsAction.SetSlotTime -> setSlot(action.slot, action.time)
        is NotificationSettingsAction.SetSlotEnabled -> setSlotEnabled(action.slot, action.enabled)
        is NotificationSettingsAction.SetCalendarSync -> setCalendarSyncEnabled(action.enabled)
        NotificationSettingsAction.SendTest -> sendTest()
    }
}
