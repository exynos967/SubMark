package io.github.submark.feature.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.feature.settings.security.BiometricAvailability
import io.github.submark.feature.settings.security.Biometrics
import io.github.submark.feature.settings.ui.lock.AppLockUiState
import io.github.submark.feature.settings.ui.lock.AppLockViewModel
import io.github.submark.feature.settings.ui.security.PasscodeDots
import io.github.submark.feature.settings.ui.security.PasscodeKeypad

/**
 * Full-screen app lock. Tries biometric unlock automatically when enabled, otherwise (or on cancel) the passcode.
 * Five wrong codes lock input for 30 seconds. Back sends the app to the background instead of bypassing the lock.
 */
@Composable
fun AppLockScreen(onUnlocked: () -> Unit) {
    val viewModel: AppLockViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentOnUnlocked by rememberUpdatedState(onUnlocked)

    LaunchedEffect(state.unlocked) { if (state.unlocked) currentOnUnlocked() }
    BackHandler { context.findActivity()?.moveTaskToBack(true) }

    val title = stringResource(R.string.settings_lock_biometric_title)
    val negative = stringResource(R.string.settings_lock_use_passcode)
    val biometricUsable = state.biometricEnabled &&
        remember(state.biometricEnabled) { Biometrics.availability(context) } == BiometricAvailability.AVAILABLE
    val promptBiometric: () -> Unit = {
        Biometrics.authenticate(
            context = context,
            title = title,
            subtitle = null,
            negativeText = negative,
            onSuccess = viewModel::onBiometricSuccess,
            onError = { _, _ -> },
        )
    }
    var autoPrompted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(biometricUsable) {
        if (biometricUsable && !autoPrompted && state.lockoutSeconds <= 0) {
            autoPrompted = true
            promptBiometric()
        }
    }

    AppLockContent(
        state = state,
        onDigit = viewModel::onDigit,
        onBackspace = viewModel::onBackspace,
        onBiometric = if (biometricUsable) promptBiometric else null,
    )
}

@Composable
internal fun AppLockContent(
    state: AppLockUiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: (() -> Unit)?,
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
            Icon(Icons.Rounded.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.settings_lock_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            val message = when {
                state.lockoutSeconds > 0 ->
                    pluralStringResource(R.plurals.settings_lock_locked_out, state.lockoutSeconds.toInt(), state.lockoutSeconds.toInt())
                state.attemptsLeft != null ->
                    pluralStringResource(R.plurals.settings_passcode_wrong_attempts, state.attemptsLeft, state.attemptsLeft)
                else -> stringResource(R.string.settings_lock_prompt)
            }
            val isError = state.lockoutSeconds > 0 || state.attemptsLeft != null
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(28.dp))
            PasscodeDots(filled = state.entered.length, shakeKey = state.shakeKey, isError = isError)
            Spacer(Modifier.height(36.dp))
            PasscodeKeypad(
                onDigit = onDigit,
                onBackspace = onBackspace,
                enabled = state.inputEnabled,
                onBiometric = onBiometric,
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
