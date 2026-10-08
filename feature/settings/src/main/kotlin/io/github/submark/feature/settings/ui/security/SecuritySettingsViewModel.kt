package io.github.submark.feature.settings.ui.security

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.settings.R
import io.github.submark.feature.settings.security.AppLockController
import io.github.submark.feature.settings.security.BiometricAvailability
import io.github.submark.feature.settings.security.Biometrics
import io.github.submark.feature.settings.security.VerifyResult
import io.github.submark.feature.settings.ui.lock.AppLockViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SecurityUiState(
    val loading: Boolean = true,
    val hasPasscode: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricAvailability: BiometricAvailability = BiometricAvailability.UNAVAILABLE,
    val flow: PasscodeFlowState? = null,
)

@HiltViewModel
class SecuritySettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    settings: SettingsRepository,
    private val controller: AppLockController,
    private val time: TimeProvider,
) : ViewModel() {

    private val flowState = MutableStateFlow<PasscodeFlowState?>(null)
    private val availability = MutableStateFlow(Biometrics.availability(context))
    private val snackbar = Channel<SnackbarMessage>(Channel.BUFFERED)
    val messages: Flow<SnackbarMessage> = snackbar.receiveAsFlow()

    val state: StateFlow<SecurityUiState> = combine(settings.settings, flowState, availability) { s, flow, avail ->
        SecurityUiState(
            loading = false,
            hasPasscode = s.security.hasPasscode,
            biometricEnabled = s.security.hasPasscode && s.security.biometricEnabled,
            biometricAvailability = avail,
            flow = flow,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SecurityUiState())

    fun refreshAvailability() {
        availability.value = Biometrics.availability(context)
    }

    fun startFlow(mode: PasscodeFlowMode) {
        flowState.value = PasscodeFlowState(mode)
    }

    fun cancelFlow() {
        flowState.value = null
    }

    fun onDigit(digit: Char) {
        val current = flowState.value ?: return
        val next = PasscodeFlowReducer.append(current, digit)
        flowState.value = next
        if (next !== current && PasscodeFlowReducer.isComplete(next)) onComplete(next)
    }

    fun onBackspace() {
        flowState.update { it?.let(PasscodeFlowReducer::backspace) }
    }

    /** Called after the biometric prompt succeeded (enabling) or directly (disabling). */
    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            controller.setBiometricEnabled(enabled)
            if (enabled) snackbar.send(SnackbarMessage(UiText.res(R.string.settings_security_biometric_enabled)))
        }
    }

    fun onBiometricError(message: CharSequence) {
        viewModelScope.launch {
            snackbar.send(SnackbarMessage(UiText.res(R.string.settings_security_biometric_failed, message.toString())))
        }
    }

    private fun onComplete(state: PasscodeFlowState) {
        when (state.step) {
            PasscodeStep.VERIFY_CURRENT -> verifyCurrent(state)
            PasscodeStep.ENTER_NEW -> flowState.value = PasscodeFlowReducer.newEntered(state)
            PasscodeStep.CONFIRM_NEW -> {
                val (next, code) = PasscodeFlowReducer.confirm(state)
                flowState.value = next
                if (code != null) save(state.mode, code)
            }
        }
    }

    private fun verifyCurrent(state: PasscodeFlowState) {
        flowState.value = state.copy(busy = true)
        viewModelScope.launch {
            when (val result = controller.verify(state.entered)) {
                VerifyResult.Success -> if (state.mode == PasscodeFlowMode.REMOVE) {
                    controller.removePasscode()
                    flowState.value = null
                    snackbar.send(SnackbarMessage(UiText.res(R.string.settings_security_passcode_removed)))
                } else {
                    flowState.update { it?.let(PasscodeFlowReducer::verified) }
                }
                is VerifyResult.Wrong -> flowState.update {
                    it?.let { s -> PasscodeFlowReducer.verifyFailed(s, PasscodeFlowError.WRONG, result.attemptsLeft) }
                }
                is VerifyResult.LockedOut -> {
                    val seconds = AppLockViewModel.secondsLeft(result.until, time.now()).toInt()
                    flowState.update { it?.let { s -> PasscodeFlowReducer.verifyFailed(s, PasscodeFlowError.LOCKED_OUT, seconds) } }
                }
            }
        }
    }

    private fun save(mode: PasscodeFlowMode, code: String) {
        viewModelScope.launch {
            controller.setPasscode(code)
            flowState.value = null
            val msg = if (mode == PasscodeFlowMode.CHANGE) R.string.settings_security_passcode_changed else R.string.settings_security_passcode_set
            snackbar.send(SnackbarMessage(UiText.res(msg)))
        }
    }
}
