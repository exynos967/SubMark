package io.github.submark.feature.backup.data

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class BackupManifestTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `round trip keeps every field`() {
        val manifest = BackupManifest(
            createdAt = "2024-06-01T12:00:00Z",
            appVersion = "1.2.3",
            device = "Pixel 8",
            payloadFileName = "submark-20240601-120000.bin",
            sizeBytes = 123456,
            sha256 = "abcd".repeat(16),
            encrypted = true,
            kdfName = BackupCrypto.KDF_NAME,
            kdfIterations = 210_000,
            kdfSaltHex = "00ff".repeat(8),
            nonceHex = "0102".repeat(6),
            recordCounts = mapOf("subscriptions" to 12, "paymentRecords" to 99),
        )
        val decoded = json.decodeFromString(BackupManifest.serializer(), json.encodeToString(BackupManifest.serializer(), manifest))
        assertThat(decoded).isEqualTo(manifest)
        assertThat(decoded.manifestFileName()).isEqualTo("submark-20240601-120000.bin.manifest.json")
    }

    @Test
    fun `unencrypted manifest has no kdf fields`() {
        val manifest = BackupManifest(
            createdAt = "2024-06-01T12:00:00Z",
            appVersion = "1.0.0",
            device = "Android",
            payloadFileName = "submark-x.json",
            sizeBytes = 1,
            sha256 = "ff",
            encrypted = false,
        )
        val decoded = json.decodeFromString(BackupManifest.serializer(), json.encodeToString(BackupManifest.serializer(), manifest))
        assertThat(decoded.kdfName).isNull()
        assertThat(decoded.kdfSaltHex).isNull()
        assertThat(decoded.encrypted).isFalse()
    }

    @Test
    fun `unknown fields are ignored (forward compatibility)`() {
        val text = """{"manifestVersion":1,"exportVersion":1,"createdAt":"t","appVersion":"1","device":"d","payloadFileName":"p","sizeBytes":1,"sha256":"aa","encrypted":false,"futureField":{"x":1}}"""
        val decoded = json.decodeFromString(BackupManifest.serializer(), text)
        assertThat(decoded.payloadFileName).isEqualTo("p")
    }
}
