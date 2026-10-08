package io.github.submark.feature.backup.data

import io.github.submark.core.model.ExportBundle
import kotlinx.serialization.Serializable

/**
 * The JSON descriptor stored on the server next to a backup payload. Written last so a backup
 * "counts" only once its manifest exists (atomic commit). Contains no credentials.
 */
@Serializable
data class BackupManifest(
    val manifestVersion: Int = CURRENT_MANIFEST_VERSION,
    val exportVersion: Int = ExportBundle.CURRENT_VERSION,
    val createdAt: String, // ISO-8601 Instant
    val appVersion: String,
    val device: String,
    val payloadFileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val encrypted: Boolean,
    /** Present only when [encrypted]: PBKDF2 parameters + per-backup salt/nonce, hex-encoded. */
    val kdfName: String? = null,
    val kdfIterations: Int? = null,
    val kdfSaltHex: String? = null,
    val nonceHex: String? = null,
    val recordCounts: Map<String, Int> = emptyMap(),
) {
    /** Manifest filename: `<payload>.manifest.json`. */
    fun manifestFileName(): String = "$payloadFileName.manifest.json"

    companion object {
        const val CURRENT_MANIFEST_VERSION = 1
        fun manifestNameFor(payloadFileName: String) = "$payloadFileName.manifest.json"
    }
}

/** Manifest plus the server-side slot it came from; the UI lists these newest first. */
data class RemoteBackup(
    val manifest: BackupManifest,
)
