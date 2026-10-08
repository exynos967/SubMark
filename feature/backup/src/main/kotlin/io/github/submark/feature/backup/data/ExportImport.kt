package io.github.submark.feature.backup.data

import io.github.submark.core.data.backup.ExportService
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.result.InvalidReason
import io.github.submark.core.model.ExportBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Export document = `data.json` (the ExportBundle) optionally wrapped in a ZIP with
 * `photos/<fileName>` entries copied from `filesDir/photos`.
 *
 * Pure file IO — instantiate with the app's photo directory. Hilt provides [PhotoArchive] through
 * the constructor with `@ApplicationContext`; ViewModels hand SAF streams to it.
 */
object ExportImport {
    const val DATA_ENTRY = "data.json"
    const val PHOTOS_PREFIX = "photos/"

    data class Document(
        val bundleJson: String,
        /** photo file name → bytes (already read). */
        val photos: Map<String, ByteArray>,
    )

    data class Outcome(
        val bundle: ExportBundle,
        /** photo file name → bytes to restore under the photos directory. */
        val photos: Map<String, ByteArray>,
    )

    /** Decides JSON vs ZIP by whether any referenced photo actually exists on disk. */
    fun hasPhotos(bundle: ExportBundle, photosDir: File): Boolean =
        bundle.subscriptionPhotos.any { File(photosDir, it.fileName).isFile }

    /** Writes [bundle] plus the referenced photos as ZIP (photos present) or plain JSON. */
    fun write(service: ExportService, bundle: ExportBundle, photosDir: File, out: OutputStream) {
        val text = service.encode(bundle)
        if (!hasPhotos(bundle, photosDir)) {
            out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            return
        }
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(DATA_ENTRY))
            zip.write(text.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            bundle.subscriptionPhotos.forEach { photo ->
                val file = File(photosDir, photo.fileName)
                if (file.isFile) {
                    zip.putNextEntry(ZipEntry(PHOTOS_PREFIX + photo.fileName))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Reads a JSON or ZIP document. JSON = whole input; ZIP = data.json + photo entries under `photos/`. */
    suspend fun read(service: ExportService, input: InputStream): DataResult<Outcome> = withContext(Dispatchers.IO) {
        val bytes = input.use { it.readBytes() }
        val isZip = bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (!isZip) return@withContext decodeJson(service, bytes, emptyMap())

        var jsonText: String? = null
        val photos = mutableMapOf<String, ByteArray>()
        try {
            ZipInputStream(bytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.removePrefix("./")
                    when {
                        name == DATA_ENTRY -> jsonText = zip.readBytes().toString(Charsets.UTF_8)
                        name.startsWith(PHOTOS_PREFIX) && !entry.isDirectory -> {
                            val file = name.removePrefix(PHOTOS_PREFIX)
                            if (isSafeFileName(file)) photos[file] = zip.readBytes()
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            return@withContext DataResult.Failure(DataError.Invalid(InvalidReason.MALFORMED_EXPORT, e.message))
        }
        val text = jsonText
            ?: return@withContext DataResult.Failure(DataError.Invalid(InvalidReason.MALFORMED_EXPORT, "missing $DATA_ENTRY"))
        decodeJson(service, text.toByteArray(Charsets.UTF_8), photos)
    }

    private fun decodeJson(service: ExportService, bytes: ByteArray, photos: Map<String, ByteArray>): DataResult<Outcome> =
        when (val decoded = service.decode(bytes.toString(Charsets.UTF_8))) {
            is DataResult.Failure -> decoded
            is DataResult.Success -> DataResult.Success(Outcome(decoded.value, photos))
        }

    fun isSafeFileName(name: String): Boolean =
        name.isNotBlank() && !name.contains('/') && !name.contains('\\') && name != "." && name != ".."
}
