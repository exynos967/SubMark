package io.github.submark.feature.notifications.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.ui.component.SettingsGroup
import io.github.submark.core.ui.component.SettingsNavRow
import io.github.submark.core.ui.component.SettingsSwitchRow
import io.github.submark.core.ui.component.SettingsValueRow
import io.github.submark.core.ui.component.TimePickerField
import io.github.submark.core.ui.navigation.NotificationDiagnosticsRoute
import io.github.submark.core.ui.navigation.SubscriptionRemindersRoute
import io.github.submark.feature.notifications.R
import io.github.submark.feature.notifications.ui.common.ChoiceDialog
import io.github.submark.feature.notifications.ui.common.NotificationPage
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun NotificationSettingsScreenRoute(
    onBack: () -> Unit,
    onNavigate: (Any) -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.onPermissionResult() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val testLabel = stringResource(R.string.notifications_test_no_permission)
    val openSettingsLabel = stringResource(R.string.notifications_open_settings)
    val savedLabel = stringResource(R.string.notifications_snackbar_saved)
    val testScheduledLabel = stringResource(R.string.notifications_test_scheduled)
    val snackbarState = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                NotificationSettingsEvent.Saved -> snackbarState.showSnackbar(savedLabel)
                NotificationSettingsEvent.TestScheduled -> snackbarState.showSnackbar(testScheduledLabel)
                NotificationSettingsEvent.TestNoPermission -> {
                    val result = snackbarState.showSnackbar(testLabel, actionLabel = openSettingsLabel)
                    if (result == SnackbarResult.ActionPerformed) openAppNotificationSettings(context)
                }
            }
        }
    }

    NotificationSettingsScreen(
        state = state,
        snackbarState = snackbarState,
        onBack = onBack,
        onRequestPermission = {
            if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else openAppNotificationSettings(context)
        },
        onOpenSettings = { openAppNotificationSettings(context) },
        onOpenExactAlarmSettings = { openExactAlarmSettings(context) },
        onEvent = viewModel::apply,
        onOpenDiagnostics = { onNavigate(NotificationDiagnosticsRoute) },
        onOpenSubscription = { id -> onNavigate(SubscriptionRemindersRoute(id)) },
    )
}

private fun openAppNotificationSettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
}

private fun openExactAlarmSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
internal fun NotificationSettingsScreen(
    state: NotificationSettingsUiState,
    snackbarState: androidx.compose.material3.SnackbarHostState,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onEvent: (NotificationSettingsAction) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSubscription: (String) -> Unit,
) {
    val prefs = state.settings
    NotificationPage(
        title = stringResource(R.string.notifications_settings_title),
        onBack = onBack,
        loading = state.loading,
        snackbarHostState = snackbarState,
    ) {
        SettingsGroup(title = stringResource(R.string.notifications_section_status)) {
            SettingsValueRow(
                title = stringResource(R.string.notifications_permission_row),
                value = stringResource(
                    if (state.permissionGranted) R.string.notifications_permission_granted
                    else R.string.notifications_permission_denied,
                ),
                onClick = if (state.permissionGranted) onOpenSettings else onRequestPermission,
            )
            SettingsValueRow(
                title = stringResource(R.string.notifications_exact_alarm_row),
                value = stringResource(
                    if (state.exactAlarmsGranted) R.string.notifications_exact_granted
                    else R.string.notifications_exact_denied,
                ),
                subtitle = if (state.exactAlarmsGranted) null else stringResource(R.string.notifications_exact_denied_hint),
                onClick = onOpenExactAlarmSettings,
            )
            if (!state.permissionGranted) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    TextButton(onClick = onRequestPermission) {
                        Text(stringResource(R.string.notifications_permission_request))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.notifications_open_settings))
                    }
                }
            }
        }

        SettingsGroup(title = stringResource(R.string.notifications_section_default)) {
            SettingsSwitchRow(
                title = stringResource(R.string.notifications_master_switch),
                checked = prefs.enabled,
                onCheckedChange = { onEvent(NotificationSettingsAction.SetEnabled(it)) },
            )
            var advancePickerOpen by rememberSaveable { mutableStateOf(false) }
            SettingsValueRow(
                title = stringResource(R.string.notifications_advance_row),
                value = advanceLabel(prefs.advanceDays),
                enabled = prefs.enabled,
                onClick = { advancePickerOpen = true },
            )
            if (advancePickerOpen) {
                val options: List<Int?> = listOf(null, 0) + (1..30).toList()
                ChoiceDialog(
                    title = stringResource(R.string.notifications_advance_row),
                    options = options,
                    selected = prefs.advanceDays,
                    label = { advanceLabel(it) },
                    onSelect = { onEvent(NotificationSettingsAction.SetAdvanceDays(it)) },
                    onDismiss = { advancePickerOpen = false },
                )
            }
        }

        SettingsGroup(title = stringResource(R.string.notifications_section_times)) {
            val timeLabels = listOf(
                R.string.notifications_time_first,
                R.string.notifications_time_second,
                R.string.notifications_time_third,
            )
            val times = listOf(prefs.firstTime, prefs.secondTime, prefs.thirdTime)
            times.forEachIndexed { index, time ->
                TimePickerField(
                    label = stringResource(timeLabels[index]),
                    time = time,
                    onTimeChange = { onEvent(NotificationSettingsAction.SetSlotTime(index, it)) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    enabled = prefs.enabled,
                )
            }
        }

        SettingsGroup(
            title = stringResource(R.string.notifications_section_slots),
            footer = stringResource(R.string.notifications_slots_footer),
        ) {
            val slotLabels = listOf(
                R.string.notifications_slot_first,
                R.string.notifications_slot_second,
                R.string.notifications_slot_third,
            )
            val slotStates = listOf(prefs.firstSlotEnabled, prefs.secondSlotEnabled, prefs.thirdSlotEnabled)
            slotStates.forEachIndexed { index, enabled ->
                SettingsSwitchRow(
                    title = stringResource(slotLabels[index]),
                    checked = enabled,
                    onCheckedChange = { onEvent(NotificationSettingsAction.SetSlotEnabled(index, it)) },
                    enabled = prefs.enabled,
                )
            }
        }

        SettingsGroup(title = stringResource(R.string.notifications_section_integrations)) {
            SettingsSwitchRow(
                title = stringResource(R.string.notifications_calendar_sync),
                subtitle = if (!state.calendarPermissionGranted) {
                    stringResource(R.string.notifications_calendar_permission_missing)
                } else {
                    null
                },
                checked = prefs.calendarSyncEnabled,
                onCheckedChange = { onEvent(NotificationSettingsAction.SetCalendarSync(it)) },
            )
        }

        SettingsGroup(
            title = stringResource(R.string.notifications_section_custom),
            footer = stringResource(R.string.notifications_custom_footer),
        ) {
            if (state.customRows.isEmpty()) {
                Text(
                    stringResource(R.string.notifications_custom_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                var expanded by rememberSaveable { mutableStateOf(false) }
                val visible = if (expanded) state.customRows else state.customRows.take(5)
                visible.forEach { row ->
                    SettingsValueRow(
                        title = row.name,
                        subtitle = row.nextReminder?.let {
                            stringResource(R.string.notifications_next_reminder, formatDateTime(it))
                        } ?: stringResource(R.string.notifications_not_scheduled),
                        value = if (row.today) stringResource(R.string.notifications_today_badge) else "",
                        onClick = { onOpenSubscription(row.id) },
                    )
                }
                if (state.customRows.size > 5) {
                    Text(
                        if (expanded) {
                            stringResource(R.string.notifications_show_less)
                        } else {
                            stringResource(R.string.notifications_show_all, state.customRows.size)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button) { expanded = !expanded }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }

        SettingsGroup(
            title = stringResource(R.string.notifications_section_test),
        ) {
            SettingsValueRow(
                title = stringResource(R.string.notifications_send_test),
                value = "",
                onClick = { onEvent(NotificationSettingsAction.SendTest) },
            )
            SettingsNavRow(
                title = stringResource(R.string.notifications_diagnostics_link),
                onClick = onOpenDiagnostics,
            )
        }
    }
}

@Composable
private fun advanceLabel(days: Int?): String = when (days) {
    null -> stringResource(R.string.notifications_advance_none)
    0 -> stringResource(R.string.notifications_advance_same_day)
    else -> pluralStringResource(R.plurals.notifications_advance_days, days, days)
}

@Composable
private fun formatDateTime(dateTime: java.time.LocalDateTime): String {
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    return dateTime.format(formatter)
}
