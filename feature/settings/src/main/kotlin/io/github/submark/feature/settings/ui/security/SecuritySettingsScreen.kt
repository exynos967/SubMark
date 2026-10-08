package io.github.submark.feature.settings.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.NoEncryption
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.ui.component.SettingsGroup
import io.github.submark.core.ui.component.SettingsNavRow
import io.github.submark.core.ui.component.SettingsSwitchRow
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.feature.settings.R
import io.github.submark.feature.settings.security.BiometricAvailability
import io.github.submark.feature.settings.security.Biometrics
import io.github.submark.feature.settings.ui.common.SettingsPage
import io.github.submark.core.ui.R as UiR

@Composable
internal fun SecuritySettingsScreenRoute(onBack: () -> Unit, viewModel: SecuritySettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    SnackbarEffect(viewModel.messages, snackbarHostState)
    LifecycleResumeEffect(Unit) {
        viewModel.refreshAvailability()
        onPauseOrDispose { }
    }
    val context = LocalContext.current
    val promptTitle = stringResource(R.string.settings_security_biometric_confirm_title)
    val cancel = stringResource(UiR.string.ui_action_cancel)
    SecuritySettingsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onStartFlow = viewModel::startFlow,
        onBiometricToggle = { enable ->
            if (!enable) {
                viewModel.setBiometricEnabled(false)
            } else {
                Biometrics.authenticate(
                    context = context,
                    title = promptTitle,
                    subtitle = null,
                    negativeText = cancel,
                    onSuccess = { viewModel.setBiometricEnabled(true) },
                    onError = { cancelled, message -> if (!cancelled) viewModel.onBiometricError(message) },
                )
            }
        },
    )
    state.flow?.let { flow ->
        PasscodeFlowDialog(
            state = flow,
            onDigit = viewModel::onDigit,
            onBackspace = viewModel::onBackspace,
            onCancel = viewModel::cancelFlow,
        )
    }
}

@Composable
internal fun SecuritySettingsScreen(
    state: SecurityUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onStartFlow: (PasscodeFlowMode) -> Unit,
    onBiometricToggle: (Boolean) -> Unit,
) {
    SettingsPage(
        title = stringResource(R.string.settings_security_title),
        onBack = onBack,
        loading = state.loading,
        snackbarHostState = snackbarHostState,
    ) {
        SettingsGroup(
            title = stringResource(R.string.settings_security_passcode_group),
            footer = stringResource(R.string.settings_security_passcode_footer),
        ) {
            if (!state.hasPasscode) {
                SettingsNavRow(
                    title = stringResource(R.string.settings_security_set_passcode),
                    subtitle = stringResource(R.string.settings_security_passcode_off),
                    icon = Icons.Rounded.Password,
                    onClick = { onStartFlow(PasscodeFlowMode.SET) },
                )
            } else {
                SettingsNavRow(
                    title = stringResource(R.string.settings_security_change_passcode),
                    subtitle = stringResource(R.string.settings_security_passcode_on),
                    icon = Icons.Rounded.LockReset,
                    onClick = { onStartFlow(PasscodeFlowMode.CHANGE) },
                )
                SettingsNavRow(
                    title = stringResource(R.string.settings_security_remove_passcode),
                    icon = Icons.Rounded.NoEncryption,
                    onClick = { onStartFlow(PasscodeFlowMode.REMOVE) },
                )
            }
        }
        val biometricSubtitle = when {
            state.biometricAvailability == BiometricAvailability.UNAVAILABLE -> stringResource(R.string.settings_security_biometric_unavailable)
            state.biometricAvailability == BiometricAvailability.NOT_ENROLLED -> stringResource(R.string.settings_security_biometric_not_enrolled)
            !state.hasPasscode -> stringResource(R.string.settings_security_biometric_needs_passcode)
            else -> stringResource(R.string.settings_security_biometric_subtitle)
        }
        SettingsGroup(footer = stringResource(R.string.settings_security_biometric_footer)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_security_biometric),
                subtitle = biometricSubtitle,
                icon = Icons.Rounded.Fingerprint,
                checked = state.biometricEnabled,
                enabled = state.hasPasscode && (state.biometricAvailability == BiometricAvailability.AVAILABLE || state.biometricEnabled),
                onCheckedChange = onBiometricToggle,
            )
        }
    }
}

@Composable
private fun PasscodeFlowDialog(
    state: PasscodeFlowState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val title = when (state.step) {
                    PasscodeStep.VERIFY_CURRENT -> R.string.settings_passcode_enter_current
                    PasscodeStep.ENTER_NEW -> R.string.settings_passcode_enter_new
                    PasscodeStep.CONFIRM_NEW -> R.string.settings_passcode_confirm_new
                }
                Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                val error = when (state.error) {
                    PasscodeFlowError.WRONG -> pluralStringResource(R.plurals.settings_passcode_wrong_attempts, state.errorCount, state.errorCount)
                    PasscodeFlowError.MISMATCH -> stringResource(R.string.settings_passcode_mismatch)
                    PasscodeFlowError.LOCKED_OUT -> pluralStringResource(R.plurals.settings_lock_locked_out, state.errorCount, state.errorCount)
                    null -> stringResource(R.string.settings_passcode_hint)
                }
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(Modifier.height(28.dp))
                PasscodeDots(state.entered.length, state.shakeKey, isError = state.error != null)
                Spacer(Modifier.height(36.dp))
                PasscodeKeypad(onDigit = onDigit, onBackspace = onBackspace, enabled = !state.busy)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onCancel) { Text(stringResource(UiR.string.ui_action_cancel)) }
            }
        }
    }
}
