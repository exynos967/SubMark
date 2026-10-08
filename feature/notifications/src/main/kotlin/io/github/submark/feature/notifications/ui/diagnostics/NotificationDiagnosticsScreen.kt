package io.github.submark.feature.notifications.ui.diagnostics

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.ui.component.SettingsGroup
import io.github.submark.core.ui.component.SettingsValueRow
import io.github.submark.feature.notifications.R
import io.github.submark.feature.notifications.data.ReminderKind
import io.github.submark.feature.notifications.ui.common.NotificationPage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun NotificationDiagnosticsScreenRoute(
    onBack: () -> Unit,
    viewModel: NotificationDiagnosticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val copiedLabel = stringResource(R.string.notifications_diag_copied)
    val rebuiltLabel = stringResource(R.string.notifications_diag_rebuilt)
    val snackbarState = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DiagnosticsEvent.Copied -> {
                    clipboard.setText(AnnotatedString(event.text))
                    snackbarState.showSnackbar(copiedLabel)
                }
                DiagnosticsEvent.Rebuilt -> snackbarState.showSnackbar(rebuiltLabel)
            }
        }
    }

    NotificationDiagnosticsScreen(
        state = state,
        snackbarState = snackbarState,
        onBack = onBack,
        onCopy = viewModel::copyDiagnostics,
        onRebuild = viewModel::rebuildAll,
        onTest = viewModel::sendTest,
    )
}

@Composable
internal fun NotificationDiagnosticsScreen(
    state: DiagnosticsUiState,
    snackbarState: androidx.compose.material3.SnackbarHostState,
    onBack: () -> Unit,
    onCopy: () -> Unit,
    onRebuild: () -> Unit,
    onTest: () -> Unit,
) {
    NotificationPage(
        title = stringResource(R.string.notifications_diagnostics_title),
        onBack = onBack,
        loading = state.loading,
        snackbarHostState = snackbarState,
    ) {
        SettingsGroup(title = stringResource(R.string.notifications_diag_state)) {
            SettingsValueRow(
                title = stringResource(R.string.notifications_permission_row),
                value = onOff(state.permissionGranted),
            )
            SettingsValueRow(
                title = stringResource(R.string.notifications_exact_alarm_row),
                value = stringResource(
                    if (state.exactAlarmsGranted) R.string.notifications_exact_granted else R.string.notifications_exact_denied,
                ),
                subtitle = if (state.exactAlarmsGranted) null else stringResource(R.string.notifications_exact_denied_hint),
            )
            SettingsValueRow(
                title = stringResource(R.string.notifications_diag_master),
                value = onOff(state.masterEnabled),
            )
            SettingsValueRow(
                title = stringResource(R.string.notifications_diag_user_pref),
                value = stringResource(
                    if (state.userPreferenceSet) R.string.notifications_diag_yes else R.string.notifications_diag_no,
                ),
            )
        }
        SettingsGroup(title = stringResource(R.string.notifications_diag_channels)) {
            state.channels.forEach { (id, enabled) ->
                SettingsValueRow(
                    title = id,
                    value = stringResource(
                        if (enabled) R.string.notifications_diag_enabled else R.string.notifications_diag_disabled,
                    ),
                )
            }
        }
        SettingsGroup(title = stringResource(R.string.notifications_diag_scheduled, state.scheduled.size)) {
            if (state.scheduled.isEmpty()) {
                Text(
                    stringResource(R.string.notifications_diag_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                state.scheduled.forEach { item ->
                    val at = Instant.ofEpochMilli(item.triggerAtMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
                    SettingsValueRow(
                        title = item.subscriptionName,
                        subtitle = at.format(formatter),
                        value = kindLabel(item.kind),
                    )
                }
            }
        }
        SettingsGroup(title = stringResource(R.string.notifications_diag_actions)) {
            SettingsValueRow(
                title = stringResource(R.string.notifications_diag_rebuild),
                value = "",
                onClick = onRebuild,
            )
            SettingsValueRow(
                title = stringResource(R.string.notifications_diag_copy),
                value = "",
                onClick = onCopy,
            )
            SettingsValueRow(
                title = stringResource(R.string.notifications_send_test),
                value = "",
                onClick = onTest,
            )
        }
    }
}

@Composable
private fun onOff(value: Boolean): String =
    stringResource(if (value) R.string.notifications_diag_on else R.string.notifications_diag_off)

@Composable
private fun kindLabel(kind: String): String = when (runCatching { ReminderKind.valueOf(kind) }.getOrNull()) {
    ReminderKind.ADVANCE -> stringResource(R.string.notifications_kind_advance)
    ReminderKind.PAYDAY_FIRST -> stringResource(R.string.notifications_kind_payday_first)
    ReminderKind.PAYDAY_SECOND -> stringResource(R.string.notifications_kind_payday_second)
    ReminderKind.PAYDAY_THIRD -> stringResource(R.string.notifications_kind_payday_third)
    ReminderKind.CUSTOM -> stringResource(R.string.notifications_kind_custom)
    null -> kind
}
