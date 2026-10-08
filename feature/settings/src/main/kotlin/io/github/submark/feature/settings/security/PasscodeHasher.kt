package io.github.submark.feature.settings.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PBKDF2-HMAC-SHA256 hashing for the 4-digit app passcode. The plain passcode is never stored. */
object PasscodeHasher {
    const val LENGTH = 4
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    data class Hashed(val hash: String, val salt: String)

    fun isWellFormed(code: String): Boolean = code.length == LENGTH && code.all { it in '0'..'9' }

    fun newSalt(random: SecureRandom = SecureRandom()): String =
        Base64.getEncoder().encodeToString(ByteArray(SALT_BYTES).also(random::nextBytes))

    fun hash(code: String, salt: String = newSalt()): Hashed {
        require(isWellFormed(code)) { "passcode must be $LENGTH digits" }
        return Hashed(Base64.getEncoder().encodeToString(derive(code, salt)), salt)
    }

    /** Constant-time comparison; false for malformed stored values. */
    fun verify(code: String, hash: String, salt: String): Boolean {
        if (!isWellFormed(code)) return false
        val expected = runCatching { Base64.getDecoder().decode(hash) }.getOrNull() ?: return false
        val actual = runCatching { derive(code, salt) }.getOrNull() ?: return false
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(code: String, salt: String): ByteArray {
        val spec = PBEKeySpec(code.toCharArray(), Base64.getDecoder().decode(salt), ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
