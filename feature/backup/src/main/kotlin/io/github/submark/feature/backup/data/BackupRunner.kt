package io.github.submark.feature.backup.data

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.submark.core.data.backup.ExportService
import io.github.submark.core.data.network.AppInfo
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.secret.SecretStore
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.database.dao.BackupDao
import io.github.submark.core.model.BackupJob
import io.github.submark.core.model.BackupJobKind
import io.github.submark.core.model.BackupJobPhase
import io.github.submark.core.model.BackupProfile
import io.github.submark.core.model.ImportResult
import io.github.submark.core.model.RestoreMode
import io.github.submark.core.model.SecretKeys
import io.github.submark.core.model.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-flight backup/restore pipeline (spec §2.4–2.5). Every phase transition is persisted as a
 * [BackupJob] so an interrupted run can be resumed and the UI can show "Recent Jobs".
 *
 * A backup is committed by writing its manifest last: the server only ever contains either nothing
 * or a complete payload+manifest pair.
 */
@Singleton
class BackupRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: BackupDao,
    private val dav: WebDavClient,
    private val export: ExportService,
    private val secrets: SecretStore,
    private val appInfo: AppInfo,
    private val time: TimeProvider,
    private val json: Json,
) {
    private val flight = Mutex()

    /** True while a backup or restore is running; callers show "another operation is running" instead of launching. */
    val isBusy: Boolean get() = flight.isLocked

    private val stagingDir: File get() = File(context.filesDir, STAGING_DIR)
    private val photosDir: File get() = File(context.filesDir, PHOTOS_DIR)

    sealed interface Outcome {
        data object Busy : Outcome
        data object Canceled : Outcome
        data class Success(val job: BackupJob) : Outcome
        data class Failed(val job: BackupJob?, val error: BackupFailure) : Outcome
    }

    sealed interface BackupFailure {
        data class Dav(val error: WebDavError) : BackupFailure
        /** WebDAV password or encryption password not found in secure storage. */
        data object MissingPassword : BackupFailure
        data object MissingEncryptionPassword : BackupFailure
        /** Uploaded size differs from the staged file ("expected X, got Y"). */
        data class VerifyMismatch(val expected: Long, val actual: Long) : BackupFailure
        data object StagedMissing : BackupFailure
        data class Io(val message: String?) : BackupFailure
    }

    sealed interface RestoreFailure {
        data class Dav(val error: WebDavError) : RestoreFailure
        data object MissingPassword : RestoreFailure
        data object WrongEncryptionPassword : RestoreFailure
        data object InvalidManifest : RestoreFailure
        data object UnsupportedManifestVersion : RestoreFailure
        data class UnsupportedExportVersion(val version: Int) : RestoreFailure
        data class ChecksumMismatch(val expected: String, val actual: String) : RestoreFailure
        data class SizeMismatch(val expected: Long, val actual: Long) : RestoreFailure
        data class Import(val error: DataError) : RestoreFailure
        data class Io(val message: String?) : RestoreFailure
    }

    sealed interface RestoreOutcome {
        data object Busy : RestoreOutcome
        data class Success(val result: ImportResult) : RestoreOutcome
        data class Failed(val error: RestoreFailure) : RestoreOutcome
    }

    // ------------------------------------------------------------------ backup

    /**
     * Runs the full pipeline for [profile] unless another operation holds the single flight.
     * [jobId] resumes an interrupted job whose staged payload still exists.
     */
    suspend fun runBackup(profile: BackupProfile, jobId: String? = null): Outcome {
        if (!flight.tryLock()) return Outcome.Busy
        try {
            val password = secrets.get(SecretKeys.webDavPassword(profile.id))
                ?: return finishWithoutJob(profile, BackupFailure.MissingPassword)
            val (root, rootError) = dav.rootUrl(profile)
            if (root == null) return finishWithoutJob(profile, BackupFailure.Dav(rootError!!))

            var job = jobId?.let { id ->
                dao.getUnfinishedJobs().firstOrNull { it.id == id }
            } ?: BackupJob(
                id = newId(), profileId = profile.id, kind = BackupJobKind.BACKUP,
                phase = BackupJobPhase.PREPARING, startedAt = time.now(),
            )
            dao.upsertJob(job)

            suspend fun phase(p: BackupJobPhase, block: suspend () -> Unit) {
                job = job.copy(phase = p)
                dao.upsertJob(job)
                block()
            }

            return try {
                val staged: File
                val sha: String
                if (job.stagedPayloadPath != null) {
                    // Resume: reuse the staged payload from an interrupted run.
                    val existing = File(job.stagedPayloadPath!!)
                    if (!existing.isFile) throw BackupException(BackupFailure.StagedMissing)
                    staged = existing
                    sha = sha256(staged)
                } else {
                    var s: File? = null
                    var hash = ""
                    phase(BackupJobPhase.PREPARING) {
                        stagingDir.mkdirs()
                        val bundle = export.export()
                        val plain = export.encode(bundle).toByteArray(Charsets.UTF_8)
                        s = File(stagingDir, "payload-${job.id}.bin").apply { writeBytes(plain) }
                        hash = hex(sha256Bytes(plain))
                        job = job.copy(stagedPayloadPath = s!!.absolutePath)
                        dao.upsertJob(job)
                    }
                    staged = s!!
                    sha = hash
                }

                var payload = staged
                var crypto: BackupCrypto.Params? = null
                var payloadSha = sha
                if (profile.encrypt) {
                    val encPassword = secrets.get(SecretKeys.backupEncryption(profile.id))
                        ?: throw BackupException(BackupFailure.MissingEncryptionPassword)
                    phase(BackupJobPhase.ENCRYPTING) {
                        val (cipherBytes, params) = BackupCrypto.encrypt(staged.readBytes(), encPassword.toCharArray())
                        val enc = File(stagingDir, "payload-${job.id}.enc.bin")
                        enc.writeBytes(cipherBytes)
                        payload = enc
                        crypto = params
                        payloadSha = hex(sha256Bytes(cipherBytes))
                        job = job.copy(stagedPayloadPath = enc.absolutePath)
                        dao.upsertJob(job)
                    }
                }

                val now = time.now()
                val payloadName = payloadName(now, time.zone(), profile.encrypt)
                val payloadUrl = root.newBuilder().addPathSegment(payloadName).build()

                phase(BackupJobPhase.UPLOADING_PAYLOAD) {
                    dav.ensureCollections(profile, password)
                    dav.put(profile, password, payloadUrl, payload)
                }
                job = job.copy(bytes = payload.length(), remoteName = payloadName)
                dao.upsertJob(job)

                if (profile.verifyAfterUpload) {
                    phase(BackupJobPhase.VERIFYING_PAYLOAD) {
                        val (exists, size) = dav.propFind(profile, password, payloadUrl)
                        if (!exists || size == null || size != payload.length()) {
                            throw BackupException(BackupFailure.VerifyMismatch(payload.length(), size ?: -1))
                        }
                    }
                }

                phase(BackupJobPhase.COMMITTING_MANIFEST) {
                    val manifest = BackupManifest(
                        createdAt = now.toString(),
                        appVersion = appInfo.versionName,
                        device = deviceName(),
                        payloadFileName = payloadName,
                        sizeBytes = payload.length(),
                        sha256 = payloadSha,
                        encrypted = profile.encrypt,
                        kdfName = if (profile.encrypt) BackupCrypto.KDF_NAME else null,
                        kdfIterations = crypto?.iterations,
                        kdfSaltHex = crypto?.saltHex(),
                        nonceHex = crypto?.nonceHex(),
                    )
                    dav.putText(profile, password, root.newBuilder().addPathSegment(manifest.manifestFileName()).build(),
                        json.encodeToString(BackupManifest.serializer(), manifest))
                }

                phase(BackupJobPhase.PRUNING) {
                    runCatching { prune(profile, password, root, now) }
                }

                job = job.copy(phase = BackupJobPhase.COMPLETED, finishedAt = time.now())
                dao.upsertJob(job)
                updateProfile(profile) { it.copy(lastSuccessAt = time.now(), lastAttemptAt = job.startedAt, lastError = null) }
                staged.delete()
                Outcome.Success(job)
            } catch (e: BackupException) {
                fail(profile, job, e.failure)
            } catch (e: WebDavException) {
                fail(profile, job, BackupFailure.Dav(e.error))
            } catch (e: kotlinx.coroutines.CancellationException) {
                job = job.copy(phase = BackupJobPhase.CANCELED, finishedAt = time.now())
                dao.upsertJob(job)
                throw e
            } catch (e: Exception) {
                fail(profile, job, BackupFailure.Io(e.message))
            }
        } finally {
            flight.unlock()
        }
    }

    private suspend fun fail(profile: BackupProfile, job: BackupJob, failure: BackupFailure): Outcome {
        val failed = job.copy(phase = BackupJobPhase.FAILED, finishedAt = time.now(), error = failure.toString())
        dao.upsertJob(failed)
        updateProfile(profile) { it.copy(lastAttemptAt = failed.startedAt, lastError = failed.error) }
        return Outcome.Failed(failed, failure)
    }

    private suspend fun finishWithoutJob(profile: BackupProfile, failure: BackupFailure): Outcome {
        updateProfile(profile) { it.copy(lastAttemptAt = time.now(), lastError = failure.toString()) }
        return Outcome.Failed(null, failure)
    }

    private suspend fun updateProfile(profile: BackupProfile, transform: (BackupProfile) -> BackupProfile) {
        dao.getProfile(profile.id)?.let { dao.upsert(transform(it)) }
    }

    private suspend fun prune(profile: BackupProfile, password: String, root: okhttp3.HttpUrl, now: Instant) {
        val entries = dav.list(profile, password, root)
        val manifests = mutableListOf<Pair<String, Instant>>()
        entries.filter { it.name.endsWith(MANIFEST_SUFFIX) }.forEach { entry ->
            val payload = entry.name.removeSuffix(MANIFEST_SUFFIX)
            val created = runCatching {
                Instant.parse(
                    json.decodeFromString(BackupManifest.serializer(), dav.getText(profile, password, root.newBuilder().addPathSegment(entry.name).build())).createdAt
                )
            }.getOrNull() ?: return@forEach
            manifests += payload to created
        }
        val doomed = PrunePolicy.selectForDeletion(manifests, profile.keepCount, profile.keepDays, now)
        doomed.forEach { payload ->
            runCatching { dav.delete(profile, password, root.newBuilder().addPathSegment(payload).build()) }
            runCatching { dav.delete(profile, password, root.newBuilder().addPathSegment(payload + MANIFEST_SUFFIX).build()) }
        }
    }

    // ------------------------------------------------------------------ listing / verify / delete

    /** Remote manifests, newest first; corrupt entries are skipped. */
    suspend fun listRemote(profile: BackupProfile): DataResult<List<BackupManifest>> {
        val password = secrets.get(SecretKeys.webDavPassword(profile.id))
            ?: return DataResult.Failure(DataError.Network("missing password"))
        val (root, rootError) = dav.rootUrl(profile)
        if (root == null) return DataResult.Failure(DataError.Network(rootError.toString()))
        return try {
            val entries = dav.list(profile, password, root)
            val manifests = entries.filter { it.name.endsWith(MANIFEST_SUFFIX) }.mapNotNull { entry ->
                runCatching {
                    json.decodeFromString(BackupManifest.serializer(), dav.getText(profile, password, root.newBuilder().addPathSegment(entry.name).build()))
                }.getOrNull()
            }
            DataResult.Success(manifests.sortedByDescending { it.createdAt })
        } catch (e: WebDavException) {
            DataResult.Failure(DataError.Network(e.error.toString()))
        }
    }

    sealed interface VerifyResult {
        data object Ok : VerifyResult
        data class SizeMismatch(val expected: Long, val actual: Long) : VerifyResult
        data class ChecksumMismatch(val expected: String, val actual: String) : VerifyResult
        data class Dav(val error: WebDavError) : VerifyResult
    }

    /** Quick verify: remote size vs manifest. Full verify also downloads and checks sha256. */
    suspend fun verifyRemote(profile: BackupProfile, manifest: BackupManifest, full: Boolean): VerifyResult {
        val password = secrets.get(SecretKeys.webDavPassword(profile.id)) ?: return VerifyResult.Dav(WebDavError.MissingCredentials)
        val root = dav.rootUrl(profile).let { (url, err) -> url ?: return VerifyResult.Dav(err!!) }
        val payloadUrl = root.newBuilder().addPathSegment(manifest.payloadFileName).build()
        return try {
            if (!full) {
                val (exists, size) = dav.propFind(profile, password, payloadUrl)
                if (!exists || size == null) VerifyResult.SizeMismatch(manifest.sizeBytes, -1)
                else if (size != manifest.sizeBytes) VerifyResult.SizeMismatch(manifest.sizeBytes, size)
                else VerifyResult.Ok
            } else {
                val tmp = File.createTempFile("verify-", ".bin", context.cacheDir)
                try {
                    dav.get(profile, password, payloadUrl, tmp)
                    val actual = sha256(tmp)
                    if (actual != manifest.sha256) VerifyResult.ChecksumMismatch(manifest.sha256, actual) else VerifyResult.Ok
                } finally {
                    tmp.delete()
                }
            }
        } catch (e: WebDavException) {
            VerifyResult.Dav(e.error)
        }
    }

    /** Deletes payload + manifest of one remote backup. */
    suspend fun deleteRemote(profile: BackupProfile, manifest: BackupManifest): WebDavError? {
        val password = secrets.get(SecretKeys.webDavPassword(profile.id)) ?: return WebDavError.MissingCredentials
        val root = dav.rootUrl(profile).let { (url, err) -> url ?: return err }
        return try {
            dav.delete(profile, password, root.newBuilder().addPathSegment(manifest.payloadFileName).build())
            dav.delete(profile, password, root.newBuilder().addPathSegment(manifest.manifestFileName()).build())
            null
        } catch (e: WebDavException) {
            e.error
        }
    }

    // ------------------------------------------------------------------ restore

    /**
     * Downloads, validates, decrypts and imports [manifest]. All validation happens before any
     * local write (see [ExportService.import]). [encryptionPassword] overrides the stored one
     * (user prompt when the secret is missing).
     */
    suspend fun restore(profile: BackupProfile, manifest: BackupManifest, mode: RestoreMode, encryptionPassword: String? = null): RestoreOutcome {
        if (!flight.tryLock()) return RestoreOutcome.Busy
        val job = BackupJob(
            id = newId(), profileId = profile.id, kind = BackupJobKind.RESTORE,
            phase = BackupJobPhase.DOWNLOADING, startedAt = time.now(), remoteName = manifest.payloadFileName,
        )
        dao.upsertJob(job)
        suspend fun phase(p: BackupJobPhase) {
            dao.upsertJob(job.copy(phase = p))
        }
        fun fail(f: RestoreFailure): RestoreOutcome {
            // job row is best-effort; the UI reads the error from the outcome.
            return RestoreOutcome.Failed(f)
        }
        try {
            if (manifest.manifestVersion > BackupManifest.CURRENT_MANIFEST_VERSION) {
                return fail(RestoreFailure.UnsupportedManifestVersion).also { dao.upsertJob(job.copy(phase = BackupJobPhase.FAILED, finishedAt = time.now())) }
            }
            if (manifest.exportVersion > io.github.submark.core.model.ExportBundle.CURRENT_VERSION) {
                return fail(RestoreFailure.UnsupportedExportVersion(manifest.exportVersion)).also { dao.upsertJob(job.copy(phase = BackupJobPhase.FAILED, finishedAt = time.now())) }
            }
            val password = secrets.get(SecretKeys.webDavPassword(profile.id)) ?: return fail(RestoreFailure.MissingPassword)
                .also { dao.upsertJob(job.copy(phase = BackupJobPhase.FAILED, finishedAt = time.now())) }
            val (root0, rootError) = dav.rootUrl(profile)
            val root = root0 ?: return fail(RestoreFailure.Dav(rootError!!))
                .also { dao.upsertJob(job.copy(phase = BackupJobPhase.FAILED, finishedAt = time.now())) }
            val payloadUrl = root.newBuilder().addPathSegment(manifest.payloadFileName).build()

            val tmp = File.createTempFile("restore-", ".bin", context.cacheDir)
            try {
                phase(BackupJobPhase.DOWNLOADING)
                try {
                    dav.get(profile, password, payloadUrl, tmp)
                } catch (e: WebDavException) {
                    return fail(RestoreFailure.Dav(e.error)).also { dao.upsertJob(job.copy(phase = BackupJobPhase.FAILED, finishedAt = time.now())) }
                }
                if (tmp.length() != manifest.sizeBytes) {
                    return fail(RestoreFailure.SizeMismatch(manifest.sizeBytes, tmp.length())).also { dao.upsertJob(job.copy(phase = BackupJobPhase.FAILED, finishedAt = time.now())) }
                }
                val actualSha = sha256(tmp)
                if (actualSha != manifest.sha256) {
                    return fail(RestoreFailure.ChecksumMismatch(manifest.sha256, actualSha)).also { dao.upsertJob(job.copy(phase = BackupJobPhase.FAILED, finishedAt = time.now())) }
                }

                var bytes = tmp.readBytes()
                if (manifest.encrypted) {
                    phase(BackupJobPhase.DECRYPTING)
                    val pass = encryptionPassword ?: secrets.get(SecretKeys.backupEncryption(profile.id))
                    if (pass == null) {
                        return fail(RestoreFailure.MissingPassword).also { dao.upsertJob(job.copy(phase = BackupJobPhase.FAILED, finishedAt = time.now())) }
                    }
                    val params = BackupCrypto.Params.fromHex(
                        manifest.kdfIterations ?: BackupCrypto.DEFAULT_ITERATIONS,
                        manifest.kdfSaltHex ?: return fail(RestoreFailure.InvalidManifest).also { dao.upsertJob(job.copy(phase = BackupJobPhase.FAILED, finishedAt = time.now())) },
                        manifest.nonceHex ?: return fail(RestoreFailure.InvalidManifest).also { dao.upsertJob(job.copy(phase = BackupJobPhase.FAILED, finishedAt = time.now())) },
                    )
                    bytes = try {
                        BackupCrypto.decrypt(bytes, pass.toCharArray(), params)
                    } catch (e: BackupCrypto.WrongPasswordException) {
                        return fail(RestoreFailure.WrongEncryptionPassword).also { dao.upsertJob(job.copy(phase = BackupJobPhase.FAILED, finishedAt = time.now())) }
                    }
                }

                val bundle = when (val decoded = export.decode(bytes.toString(Charsets.UTF_8))) {
                    is DataResult.Failure -> {
                        val error = decoded.error
                        val failure = if (error is DataError.Invalid && error.reason == io.github.submark.core.data.result.InvalidReason.UNSUPPORTED_EXPORT_VERSION) {
                            RestoreFailure.UnsupportedExportVersion(manifest.exportVersion)
                        } else RestoreFailure.Import(error)
                        return fail(failure).also { dao.upsertJob(job.copy(phase = BackupJobPhase.FAILED, finishedAt = time.now())) }
                    }
                    is DataResult.Success -> decoded.value
                }

                phase(BackupJobPhase.RESTORING)
                return when (val imported = export.import(bundle, mode)) {
                    is DataResult.Failure -> fail(RestoreFailure.Import(imported.error)).also { dao.upsertJob(job.copy(phase = BackupJobPhase.FAILED, finishedAt = time.now())) }
                    is DataResult.Success -> {
                        dao.upsertJob(job.copy(phase = BackupJobPhase.COMPLETED, finishedAt = time.now(), bytes = manifest.sizeBytes))
                        RestoreOutcome.Success(imported.value)
                    }
                }
            } finally {
                tmp.delete()
            }
        } finally {
            flight.unlock()
        }
    }

    /** Resumes an interrupted staged job for [profile] if one exists; otherwise runs fresh. */
    suspend fun resumeIfInterrupted(profile: BackupProfile): Outcome? {
        val job = dao.getUnfinishedJobs().firstOrNull { it.profileId == profile.id && it.kind == BackupJobKind.BACKUP } ?: return null
        val staged = job.stagedPayloadPath?.let(::File)
        return if (staged != null && staged.isFile) runBackup(profile, jobId = job.id) else null
    }

    // ------------------------------------------------------------------ helpers

    private class BackupException(val failure: BackupFailure) : Exception()

    companion object {
        const val STAGING_DIR = "backup-staging"
        const val PHOTOS_DIR = "photos"
        const val MANIFEST_SUFFIX = ".manifest.json"

        private val NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

        fun payloadName(at: Instant, zone: ZoneId, encrypted: Boolean): String =
            "submark-${NAME_FORMAT.format(at.atZone(zone))}${if (encrypted) ".bin" else ".json"}"

        fun deviceName(): String = listOfNotNull(Build.MANUFACTURER, Build.MODEL).joinToString(" ") { it.trim() }.ifBlank { "Android" }

        fun sha256Bytes(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

        fun sha256(file: File): String = hex(MessageDigest.getInstance("SHA-256").let { digest ->
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var read = input.read(buffer)
                while (read >= 0) {
                    if (read > 0) digest.update(buffer, 0, read)
                    read = input.read(buffer)
                }
            }
            digest.digest()
        })

        fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    }
}
