package io.github.submark.feature.settings.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.feature.settings.security.AppLockController
import io.github.submark.feature.settings.security.PasscodeHasher
import io.github.submark.feature.settings.security.VerifyResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class AppLockUiState(
    val entered: String = "",
    val verifying: Boolean = false,
    /** Attempts left after the last wrong code; null = no error shown. */
    val attemptsLeft: Int? = null,
    val lockoutSeconds: Long = 0,
    val shakeKey: Int = 0,
    val biometricEnabled: Boolean = false,
    val unlocked: Boolean = false,
) {
    val inputEnabled: Boolean get() = !verifying && lockoutSeconds <= 0 && !unlocked
}

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val controller: AppLockController,
    private val time: TimeProvider,
) : ViewModel() {

    private val local = MutableStateFlow(AppLockUiState())
    private var countdown: Job? = null

    val state: StateFlow<AppLockUiState> = combine(local, controller.isBiometricEnabled) { s, bio ->
        s.copy(biometricEnabled = bio)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLockUiState())

    init {
        viewModelScope.launch { controller.lockedUntil()?.let(::startCountdown) }
    }

    fun onDigit(digit: Char) {
        val s = local.value
        if (!s.inputEnabled || s.entered.length >= PasscodeHasher.LENGTH) return
        val entered = s.entered + digit
        local.update { it.copy(entered = entered) }
        if (entered.length == PasscodeHasher.LENGTH) submit(entered)
    }

    fun onBackspace() {
        if (!local.value.inputEnabled) return
        local.update { it.copy(entered = it.entered.dropLast(1)) }
    }

    fun onBiometricSuccess() {
        viewModelScope.launch {
            controller.onBiometricSuccess()
            countdown?.cancel()
            local.update { it.copy(unlocked = true, lockoutSeconds = 0) }
        }
    }

    private fun submit(code: String) {
        local.update { it.copy(verifying = true) }
        viewModelScope.launch {
            when (val result = controller.verify(code)) {
                VerifyResult.Success -> local.update { it.copy(verifying = false, unlocked = true, attemptsLeft = null) }
                is VerifyResult.Wrong -> local.update {
                    it.copy(verifying = false, entered = "", attemptsLeft = result.attemptsLeft, shakeKey = it.shakeKey + 1)
                }
                is VerifyResult.LockedOut -> {
                    local.update { it.copy(verifying = false, entered = "", attemptsLeft = null, shakeKey = it.shakeKey + 1) }
                    startCountdown(result.until)
                }
            }
        }
    }

    private fun startCountdown(until: Instant) {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            while (true) {
                val left = secondsLeft(until, time.now())
                local.update { it.copy(lockoutSeconds = left) }
                if (left <= 0) break
                delay(1_000)
            }
        }
    }

    companion object {
        internal fun secondsLeft(until: Instant, now: Instant): Long {
            val millis = Duration.between(now, until).toMillis()
            return if (millis <= 0) 0 else (millis + 999) / 1_000
        }
    }
}
