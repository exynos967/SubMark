package io.github.submark.feature.settings.security

import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.time.TimeProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed interface VerifyResult {
    data object Success : VerifyResult
    data class Wrong(val attemptsLeft: Int) : VerifyResult
    data class LockedOut(val until: Instant) : VerifyResult
}

/**
 * App lock state and passcode operations shared by the lock screen, the security settings and the app shell.
 * The app shell locks on cold start and when [shouldLock] says the background grace period has passed,
 * provided [isLockEnabled] is true.
 */
@Singleton
class AppLockController internal constructor(
    private val settings: SettingsRepository,
    private val time: TimeProvider,
    private val hashDispatcher: CoroutineDispatcher,
) {
    @Inject constructor(settings: SettingsRepository, time: TimeProvider) : this(settings, time, Dispatchers.Default)

    val isLockEnabled: Flow<Boolean> = settings.settings.map { it.security.hasPasscode }.distinctUntilChanged()

    /** Biometric unlock is only honoured while a passcode exists. */
    val isBiometricEnabled: Flow<Boolean> =
        settings.settings.map { it.security.hasPasscode && it.security.biometricEnabled }.distinctUntilChanged()

    /** [backgroundedAt] null = cold start. Does not check [isLockEnabled]. */
    fun shouldLock(backgroundedAt: Instant?, now: Instant): Boolean =
        backgroundedAt == null || Duration.between(backgroundedAt, now) >= GRACE_PERIOD

    suspend fun lockedUntil(): Instant? = LockoutPolicy.lockedUntil(settings.settings.first().security, time.now())

    suspend fun verify(code: String): VerifyResult {
        val security = settings.settings.first().security
        val now = time.now()
        LockoutPolicy.lockedUntil(security, now)?.let { return VerifyResult.LockedOut(it) }
        val hash = security.passcodeHash ?: return VerifyResult.Success
        val salt = security.passcodeSalt.orEmpty()
        val ok = withContext(hashDispatcher) { PasscodeHasher.verify(code, hash, salt) }
        if (ok) {
            settings.update { it.copy(security = LockoutPolicy.afterSuccess(it.security)) }
            return VerifyResult.Success
        }
        settings.update { it.copy(security = LockoutPolicy.afterFailure(it.security, now)) }
        val updated = settings.settings.first().security
        return LockoutPolicy.lockedUntil(updated, now)?.let { VerifyResult.LockedOut(it) }
            ?: VerifyResult.Wrong(LockoutPolicy.MAX_ATTEMPTS - updated.failedAttempts)
    }

    suspend fun setPasscode(code: String) {
        val hashed = withContext(hashDispatcher) { PasscodeHasher.hash(code) }
        settings.update {
            it.copy(security = it.security.copy(passcodeHash = hashed.hash, passcodeSalt = hashed.salt, failedAttempts = 0, lockoutUntil = null))
        }
    }

    /** Also turns biometric unlock off, since it requires a passcode. */
    suspend fun removePasscode() {
        settings.update {
            it.copy(
                security = it.security.copy(
                    passcodeHash = null, passcodeSalt = null, biometricEnabled = false, failedAttempts = 0, lockoutUntil = null,
                ),
            )
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        settings.update { s ->
            s.copy(security = s.security.copy(biometricEnabled = enabled && s.security.hasPasscode))
        }
    }

    /** A successful biometric unlock also clears failed passcode attempts. */
    suspend fun onBiometricSuccess() {
        settings.update { it.copy(security = LockoutPolicy.afterSuccess(it.security)) }
    }

    companion object {
        val GRACE_PERIOD: Duration = Duration.ofSeconds(30)
    }
}
