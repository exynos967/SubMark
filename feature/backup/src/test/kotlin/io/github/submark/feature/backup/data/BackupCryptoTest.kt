package io.github.submark.feature.backup.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Random

class BackupCryptoTest {

    @Test
    fun `round trip restores the plaintext`() {
        val plain = Random(42).let { r -> ByteArray(4096).also(r::nextBytes) }
        val (cipher, params) = BackupCrypto.encrypt(plain, "correct horse battery staple".toCharArray(), iterations = 1_000)
        assertThat(cipher).isNotEqualTo(plain)
        val decrypted = BackupCrypto.decrypt(cipher, "correct horse battery staple".toCharArray(), params)
        assertThat(decrypted).isEqualTo(plain)
    }

    @Test
    fun `wrong password fails`() {
        val plain = "secret data".toByteArray()
        val (cipher, params) = BackupCrypto.encrypt(plain, "right-password-1".toCharArray(), iterations = 1_000)
        try {
            BackupCrypto.decrypt(cipher, "wrong-password-2".toCharArray(), params)
            throw AssertionError("expected WrongPasswordException")
        } catch (e: BackupCrypto.WrongPasswordException) {
            // expected
        }
    }

    @Test
    fun `damaged payload fails`() {
        val plain = "secret data".toByteArray()
        val (cipher, params) = BackupCrypto.encrypt(plain, "right-password-1".toCharArray(), iterations = 1_000)
        cipher[0] = (cipher[0] + 1).toByte()
        try {
            BackupCrypto.decrypt(cipher, "right-password-1".toCharArray(), params)
            throw AssertionError("expected WrongPasswordException")
        } catch (e: BackupCrypto.WrongPasswordException) {
            // expected
        }
    }

    @Test
    fun `salt and nonce differ between runs`() {
        val plain = "x".toByteArray()
        val (_, p1) = BackupCrypto.encrypt(plain, "right-password-1".toCharArray(), iterations = 1_000)
        val (_, p2) = BackupCrypto.encrypt(plain, "right-password-1".toCharArray(), iterations = 1_000)
        assertThat(p1.salt).isNotEqualTo(p2.salt)
        assertThat(p1.nonce).isNotEqualTo(p2.nonce)
    }

    @Test
    fun `hex round trip of params`() {
        val (_, params) = BackupCrypto.encrypt("x".toByteArray(), "right-password-1".toCharArray(), iterations = 1_000)
        val restored = BackupCrypto.Params.fromHex(params.iterations, params.saltHex(), params.nonceHex())
        assertThat(restored).isEqualTo(params)
    }

    @Test
    fun `params from manifest decrypt what was encrypted`() {
        val plain = "another secret".toByteArray()
        val (cipher, params) = BackupCrypto.encrypt(plain, "right-password-1".toCharArray(), iterations = 2_000)
        val restored = BackupCrypto.Params.fromHex(params.iterations, params.saltHex(), params.nonceHex())
        assertThat(BackupCrypto.decrypt(cipher, "right-password-1".toCharArray(), restored)).isEqualTo(plain)
    }
}
