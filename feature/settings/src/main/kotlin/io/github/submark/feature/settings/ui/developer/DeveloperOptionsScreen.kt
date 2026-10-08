package io.github.submark.feature.settings.ui.developer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.core.ui.component.SettingsGroup
import io.github.submark.core.ui.component.SettingsNavRow
import io.github.submark.core.ui.component.SettingsValueRow
import io.github.submark.core.ui.navigation.NotificationDiagnosticsRoute
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.feature.settings.R
import io.github.submark.feature.settings.ui.common.SettingsPage
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun DeveloperOptionsScreenRoute(
    onBack: () -> Unit,
    onNavigate: (Any) -> Unit,
    viewModel: DeveloperOptionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    SnackbarEffect(viewModel.messages, snackbarHostState)
    LifecycleResumeEffect(Unit) {
        viewModel.refreshStatus()
        onPauseOrDispose { }
    }
    val context = LocalContext.current
    DeveloperOptionsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onOpenDiagnostics = { onNavigate(NotificationDiagnosticsRoute) },
        onOpenBatterySettings = { openBatterySettings(context) },
        onProcessDue = viewModel::processDue,
        onResetOnboarding = viewModel::resetOnboarding,
        onHide = { viewModel.hideDeveloperOptions(onBack) },
    )
}

@Composable
internal fun DeveloperOptionsScreen(
    state: DeveloperUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onProcessDue: () -> Unit,
    onResetOnboarding: () -> Unit,
    onHide: () -> Unit,
) {
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val yes = stringResource(R.string.settings_yes)
    val no = stringResource(R.string.settings_no)
    SettingsPage(
        title = stringResource(R.string.settings_dev_title),
        onBack = onBack,
        loading = state.loading,
        snackbarHostState = snackbarHostState,
    ) {
        SettingsGroup(title = stringResource(R.string.settings_dev_notifications_group)) {
            SettingsValueRow(
                title = stringResource(R.string.settings_dev_notification_permission),
                value = stringResource(if (state.status.notificationsAllowed) R.string.settings_dev_allowed else R.string.settings_dev_blocked),
                icon = if (state.status.notificationsAllowed) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
            )
            SettingsValueRow(
                title = stringResource(R.string.settings_dev_reminders_enabled),
                value = if (state.remindersEnabled) yes else no,
            )
            SettingsValueRow(
                title = stringResource(R.string.settings_dev_user_preference_set),
                value = if (state.userPreferenceSet) yes else no,
            )
            SettingsNavRow(
                title = stringResource(R.string.settings_dev_diagnostics),
                subtitle = stringResource(R.string.settings_dev_diagnostics_desc),
                icon = Icons.Rounded.BugReport,
                onClick = onOpenDiagnostics,
            )
        }

        SettingsGroup(
            title = stringResource(R.string.settings_dev_background_group),
            footer = stringResource(R.string.settings_dev_battery_footer),
        ) {
            SettingsNavRow(
                title = stringResource(R.string.settings_dev_battery),
                value = stringResource(
                    if (state.status.ignoringBatteryOptimizations) R.string.settings_dev_battery_unrestricted else R.string.settings_dev_battery_optimized,
                ),
                icon = if (state.status.ignoringBatteryOptimizations) Icons.Rounded.BatteryFull else Icons.Rounded.BatteryAlert,
                onClick = onOpenBatterySettings,
            )
        }

        val works = state.works
        SettingsGroup(
            title = stringResource(R.string.settings_dev_work_group),
            footer = when {
                works == null -> stringResource(R.string.settings_dev_work_unavailable)
                works.isEmpty() -> stringResource(R.string.settings_dev_work_empty)
                else -> pluralStringResource(R.plurals.settings_dev_work_count, works.size, works.size)
            },
        ) {
            val formatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()) }
            works.orEmpty().forEach { work ->
                val stateLabel = stringResource(work.state.labelRes)
                val detail = buildList {
                    work.nextRun?.let { add(stringResource(R.string.settings_dev_work_next, formatter.format(it))) }
                    if (work.attempts > 0) add(pluralStringResource(R.plurals.settings_dev_work_attempts, work.attempts, work.attempts))
                }.joinToString(" · ").ifEmpty { null }
                SettingsValueRow(title = work.name, subtitle = detail, value = stateLabel, icon = Icons.Rounded.Work)
            }
        }

        SettingsGroup(title = stringResource(R.string.settings_dev_tools_group)) {
            SettingsNavRow(
                title = stringResource(R.string.settings_dev_process_due),
                subtitle = stringResource(R.string.settings_dev_process_due_desc),
                icon = Icons.Rounded.PlayCircle,
                enabled = !state.processing,
                onClick = onProcessDue,
            )
            if (state.processing) {
                CircularProgressIndicator(Modifier.padding(start = 56.dp, bottom = 12.dp).size(20.dp), strokeWidth = 2.dp)
            }
            SettingsNavRow(
                title = stringResource(R.string.settings_dev_reset_onboarding),
                icon = Icons.Rounded.RestartAlt,
                onClick = { confirmReset = true },
            )
            SettingsNavRow(
                title = stringResource(R.string.settings_dev_hide),
                icon = Icons.Rounded.VisibilityOff,
                onClick = onHide,
            )
        }
    }
    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.settings_dev_reset_onboarding),
            message = stringResource(R.string.settings_dev_reset_onboarding_message),
            onConfirm = { onResetOnboarding(); confirmReset = false },
            onDismiss = { confirmReset = false },
        )
    }
}

private val WorkInfo.State.labelRes: Int
    get() = when (this) {
        WorkInfo.State.ENQUEUED -> R.string.settings_dev_work_enqueued
        WorkInfo.State.RUNNING -> R.string.settings_dev_work_running
        WorkInfo.State.SUCCEEDED -> R.string.settings_dev_work_succeeded
        WorkInfo.State.FAILED -> R.string.settings_dev_work_failed
        WorkInfo.State.BLOCKED -> R.string.settings_dev_work_blocked
        WorkInfo.State.CANCELLED -> R.string.settings_dev_work_cancelled
    }

private fun openBatterySettings(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // try the next screen
        }
    }
}
