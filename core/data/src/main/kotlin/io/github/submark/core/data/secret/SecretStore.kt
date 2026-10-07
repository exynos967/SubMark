package io.github.submark.core.data.secret

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Credentials store. Keys come from `io.github.submark.core.model.SecretKeys`. Never part of exports. */
interface SecretStore {
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String)
    suspend fun remove(key: String)
    suspend fun clear()
}

/**
 * Values are AES-256-GCM encrypted with a non-exportable Android Keystore key and kept in a JSON file
 * under `noBackupFilesDir`, which Android excludes from auto backup and device transfer.
 * If the key is lost (e.g. data copied to another device) values become unreadable and read as null.
 */
@Singleton
class KeystoreSecretStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SecretStore {
    private val mutex = Mutex()
    private val file: File get() = File(context.noBackupFilesDir, FILE_NAME)

    override suspend fun get(key: String): String? = mutex.withLock {
        val encoded = readAll()[key] ?: return@withLock null
        try {
            decrypt(encoded)
        } catch (e: GeneralSecurityException) {
            Log.w(TAG, "cannot decrypt secret", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "corrupt secret", e)
            null
        }
    }

    override suspend fun put(key: String, value: String) = mutex.withLock {
        writeAll(readAll() + (key to encrypt(value)))
    }

    override suspend fun remove(key: String) = mutex.withLock {
        val all = readAll()
        if (key in all) writeAll(all - key)
    }

    override suspend fun clear() = mutex.withLock {
        withContext(Dispatchers.IO) { file.delete() }
        Unit
    }

    private suspend fun readAll(): Map<String, String> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyMap()
        runCatching { Json.decodeFromString(mapSerializer, file.readText()) }.getOrElse { emptyMap() }
    }

    private suspend fun writeAll(values: Map<String, String>) = withContext(Dispatchers.IO) {
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(Json.encodeToString(mapSerializer, values))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = cipher.iv + cipher.doFinal(plain.encodeToByteArray())
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES))
        return cipher.doFinal(payload, IV_BYTES, payload.size - IV_BYTES).decodeToString()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "SecretStore"
        const val FILE_NAME = "secrets.json"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "submark_secret_store"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        val mapSerializer = MapSerializer(String.serializer(), String.serializer())
    }
}
