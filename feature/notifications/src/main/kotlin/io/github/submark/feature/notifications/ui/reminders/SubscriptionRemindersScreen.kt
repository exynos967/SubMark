package io.github.submark.feature.notifications.ui.reminders

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.SettingsGroup
import io.github.submark.core.ui.component.SettingsSwitchRow
import io.github.submark.core.ui.component.SettingsValueRow
import io.github.submark.core.ui.component.TimePickerField
import io.github.submark.feature.notifications.R
import io.github.submark.feature.notifications.ui.common.ChoiceDialog
import io.github.submark.feature.notifications.ui.common.NotificationPage
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import io.github.submark.core.ui.R as UiR

@Composable
fun SubscriptionRemindersScreenRoute(
    onBack: () -> Unit,
    viewModel: SubscriptionRemindersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.READ_CALENDAR] == true && grants[Manifest.permission.WRITE_CALENDAR] == true
        if (granted) viewModel.setCalendarSyncEnabled(true)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                SubscriptionRemindersEvent.CalendarPermissionRequired -> calendarPermissionLauncher.launch(
                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                )
            }
        }
    }

    if (state.missing) {
        NotificationPage(title = stringResource(R.string.notifications_reminders_title), onBack = onBack, loading = false) {
            EmptyState(title = stringResource(UiR.string.ui_error_title))
        }
        return
    }

    SubscriptionRemindersScreen(
        state = state,
        onBack = onBack,
        onCustomEnabled = viewModel::setCustomEnabled,
        onAdd = viewModel::addReminder,
        onRemove = viewModel::removeReminder,
        onCalendarSync = viewModel::setCalendarSyncEnabled,
    )
}

@Composable
internal fun SubscriptionRemindersScreen(
    state: SubscriptionRemindersUiState,
    onBack: () -> Unit,
    onCustomEnabled: (Boolean) -> Unit,
    onAdd: (Int, LocalTime) -> Unit,
    onRemove: (ReminderRow) -> Unit,
    onCalendarSync: (Boolean) -> Unit,
) {
    NotificationPage(
        title = stringResource(R.string.notifications_reminders_title),
        subtitle = state.subscriptionName,
        onBack = onBack,
        loading = state.loading,
    ) {
        SettingsGroup(footer = stringResource(R.string.notifications_custom_switch_footer)) {
            SettingsSwitchRow(
                title = stringResource(R.string.notifications_custom_switch),
                checked = state.customEnabled,
                onCheckedChange = onCustomEnabled,
            )
            if (state.customEnabled) {
                var addOpen by rememberSaveable { mutableStateOf(false) }
                var newDays by rememberSaveable { mutableStateOf(0) }
                var newTime by remember { mutableStateOf(LocalTime.of(9, 0)) }
                state.reminders.sortedWith(compareBy({ it.daysBefore }, { it.time })).forEach { row ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            if (row.daysBefore == 0) {
                                stringResource(R.string.notifications_reminder_same_day)
                            } else {
                                pluralStringResource(R.plurals.notifications_due_in_days_unit, row.daysBefore, row.daysBefore)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            row.time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { onRemove(row) }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.notifications_reminder_remove),
                            )
                        }
                    }
                }
                if (state.reminders.isEmpty()) {
                    Text(
                        stringResource(R.string.notifications_custom_default_if_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { addOpen = true }) {
                        Text(stringResource(R.string.notifications_add_reminder))
                    }
                }
                if (addOpen) {
                    AlertDialog(
                        onDismissRequest = {
                            addOpen = false
                            newDays = 0
                            newTime = LocalTime.of(9, 0)
                        },
                        title = { Text(stringResource(R.string.notifications_add_reminder)) },
                        text = {
                            Column {
                                var daysOpen by rememberSaveable { mutableStateOf(false) }
                                SettingsValueRow(
                                    title = stringResource(R.string.notifications_reminder_days_before),
                                    value = if (newDays == 0) {
                                        stringResource(R.string.notifications_reminder_same_day)
                                    } else {
                                        pluralStringResource(R.plurals.notifications_due_in_days_unit, newDays, newDays)
                                    },
                                    onClick = { daysOpen = true },
                                )
                                TimePickerField(
                                    label = stringResource(R.string.notifications_reminder_time),
                                    time = newTime,
                                    onTimeChange = { newTime = it },
                                )
                                if (daysOpen) {
                                    ChoiceDialog(
                                        title = stringResource(R.string.notifications_reminder_days_before),
                                        options = (0..30).toList(),
                                        selected = newDays,
                                        label = {
                                            if (it == 0) stringResource(R.string.notifications_reminder_same_day)
                                            else pluralStringResource(R.plurals.notifications_due_in_days_unit, it, it)
                                        },
                                        onSelect = { newDays = it },
                                        onDismiss = { daysOpen = false },
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                onAdd(newDays, newTime)
                                addOpen = false
                                newDays = 0
                                newTime = LocalTime.of(9, 0)
                            }) {
                                Text(stringResource(UiR.string.ui_action_add))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { addOpen = false }) {
                                Text(stringResource(UiR.string.ui_action_cancel))
                            }
                        },
                    )
                }
            }
        }

        SettingsGroup {
            SettingsSwitchRow(
                title = stringResource(R.string.notifications_calendar_sync_sub),
                checked = state.calendarSyncEnabled,
                onCheckedChange = onCalendarSync,
                enabled = state.globalCalendarSyncEnabled,
                subtitle = if (!state.calendarPermissionGranted) {
                    stringResource(R.string.notifications_calendar_permission_missing)
                } else {
                    null
                },
            )
        }

        SettingsGroup(title = stringResource(R.string.notifications_section_preview)) {
            if (state.preview.isEmpty()) {
                Text(
                    stringResource(R.string.notifications_preview_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                state.preview.forEach { at ->
                    SettingsValueRow(
                        title = at.format(formatter),
                        value = "",
                    )
                }
            }
        }
    }
}
