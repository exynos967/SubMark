package io.github.submark.feature.settings.security

import io.github.submark.core.data.settings.SecuritySettings
import java.time.Duration
import java.time.Instant

/** Pure rules for failed passcode attempts: 5 wrong entries lock input for 30 seconds. */
object LockoutPolicy {
    const val MAX_ATTEMPTS = 5
    val LOCKOUT: Duration = Duration.ofSeconds(30)

    fun lockedUntil(security: SecuritySettings, now: Instant): Instant? =
        security.lockoutUntil?.takeIf { it.isAfter(now) }

    fun afterFailure(security: SecuritySettings, now: Instant): SecuritySettings {
        val attempts = security.failedAttempts + 1
        return if (attempts >= MAX_ATTEMPTS) {
            security.copy(failedAttempts = 0, lockoutUntil = now.plus(LOCKOUT))
        } else {
            security.copy(failedAttempts = attempts, lockoutUntil = null)
        }
    }

    fun afterSuccess(security: SecuritySettings): SecuritySettings =
        security.copy(failedAttempts = 0, lockoutUntil = null)
}
