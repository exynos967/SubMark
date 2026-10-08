package io.github.submark.feature.backup.data

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM payload encryption with a PBKDF2-HMAC-SHA256 key derived from the profile password.
 * Per-backup random salt and nonce; both go into the manifest next to the KDF parameters so older
 * backups still decrypt with the password that created them.
 *
 * Wire format: nonce (12 bytes) | ciphertext+GCM tag.
 */
object BackupCrypto {
    const val KDF_NAME = "PBKDF2WithHmacSHA256"
    const val KEY_BITS = 256
    const val SALT_BYTES = 16
    const val NONCE_BYTES = 12
    private const val TAG_BITS = 128

    /** OWASP-recommended floor for PBKDF2-HMAC-SHA256. */
    const val DEFAULT_ITERATIONS = 210_000

    /** Minimum accepted encryption password length (validated in the editor). */
    const val MIN_PASSWORD_LENGTH = 10

    class WrongPasswordException : Exception("decryption failed")

    private val random = SecureRandom()

    data class Params(
        val iterations: Int = DEFAULT_ITERATIONS,
        val salt: ByteArray,
        val nonce: ByteArray,
    ) {
        fun saltHex(): String = salt.toHex()
        fun nonceHex(): String = nonce.toHex()

        companion object {
            fun fromHex(iterations: Int, saltHex: String, nonceHex: String): Params =
                Params(iterations, saltHex.fromHex(), nonceHex.fromHex())
        }

        override fun equals(other: Any?): Boolean =
            other is Params && iterations == other.iterations && salt.contentEquals(other.salt) && nonce.contentEquals(other.nonce)

        override fun hashCode(): Int = 31 * iterations + salt.contentHashCode() * 17 + nonce.contentHashCode()
    }

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    fun newNonce(): ByteArray = ByteArray(NONCE_BYTES).also(random::nextBytes)

    private fun key(password: CharArray, params: Params): SecretKeySpec {
        val spec = PBEKeySpec(password, params.salt, params.iterations, KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance(KDF_NAME).generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /** Encrypts [plain]; the returned [Params] hold the fresh salt+nonce actually used. */
    fun encrypt(plain: ByteArray, password: CharArray, iterations: Int = DEFAULT_ITERATIONS): Pair<ByteArray, Params> {
        val params = Params(iterations, newSalt(), newNonce())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, params), GCMParameterSpec(TAG_BITS, params.nonce))
        return cipher.doFinal(plain) to params
    }

    /** Decrypts [cipherBytes]; [WrongPasswordException] on a wrong password or damaged payload. */
    fun decrypt(cipherBytes: ByteArray, password: CharArray, params: Params): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        return try {
            cipher.init(Cipher.DECRYPT_MODE, key(password, params), GCMParameterSpec(TAG_BITS, params.nonce))
            cipher.doFinal(cipherBytes)
        } catch (e: Exception) {
            throw WrongPasswordException()
        }
    }

    internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    internal fun String.fromHex(): ByteArray {
        require(length % 2 == 0)
        return ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
