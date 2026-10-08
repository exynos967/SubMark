package io.github.submark.feature.integrations.data.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.submark.core.database.dao.IconRepositoryDao
import io.github.submark.core.model.IconRepository
import io.github.submark.core.model.newId
import io.github.submark.core.data.time.TimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class IconPackIcon(val name: String, val url: String)

/** Icon-pack document: `{ "name": String, "icons": [{ "name", "url" }] }`. */
@Serializable
data class IconPack(val name: String? = null, val icons: List<IconPackIcon> = emptyList())

sealed interface IconPackError {
    data object InvalidUrl : IconPackError
    data class Http(val code: Int) : IconPackError
    data object Network : IconPackError
    data object Parse : IconPackError
}

/** CRUD + fetch for user-added icon repositories (IconType.URL entries). */
@Singleton
class IconPackService @Inject constructor(
    private val dao: IconRepositoryDao,
    private val client: OkHttpClient,
    private val json: Json,
    private val time: TimeProvider,
) {
    fun observeRepositories() = dao.observeAll()

    suspend fun add(name: String, url: String): IconRepository {
        val repo = IconRepository(id = newId(), name = name.trim(), url = url.trim())
        dao.upsert(repo)
        return repo
    }

    suspend fun delete(repo: IconRepository) = dao.delete(repo)

    fun parse(raw: String): IconPack? =
        try {
            json.decodeFromString<IconPack>(raw)
        } catch (e: Exception) {
            null
        }

    /** Downloads and parses [repo]; updates iconCount/lastFetchedAt on success. */
    suspend fun fetch(repo: IconRepository): Result<IconPack> = withContext(Dispatchers.IO) {
        if (!repo.url.startsWith("https://") && !repo.url.startsWith("http://")) {
            return@withContext Result.failure(IconPackException(IconPackError.InvalidUrl))
        }
        val body = try {
            client.newCall(Request.Builder().url(repo.url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(IconPackException(IconPackError.Http(resp.code)))
                resp.body?.string() ?: return@withContext Result.failure(IconPackException(IconPackError.Parse))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(IconPackException(IconPackError.Network))
        }
        val pack = parse(body) ?: return@withContext Result.failure(IconPackException(IconPackError.Parse))
        dao.upsert(repo.copy(iconCount = pack.icons.size, lastFetchedAt = time.now()))
        Result.success(pack)
    }
}

class IconPackException(val error: IconPackError) : Exception(error.toString())

/** Downscales + center-crops user photos into subscription icons inside filesDir/<iconDir>. */
@Singleton
class LocalIconStorage @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    fun dir(): File = File(context.filesDir, io.github.submark.core.ui.icon.ICON_DIR).apply { mkdirs() }

    /** Saves [bytes] as `<uuid>.png`; returns the file name (IconType.FILE value). */
    suspend fun savePng(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val name = "${newId()}.png"
        File(dir(), name).writeBytes(bytes)
        name
    }

    /**
     * Decodes the photo picker stream, center-crops it square and downscales to at most
     * [maxSize] px, saving the PNG to the icon directory; null when decoding fails.
     */
    suspend fun savePhoto(uri: android.net.Uri, maxSize: Int = 512): String? = withContext(Dispatchers.IO) {
        val bitmap = runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return@withContext null
        val side = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - side) / 2
        val y = (bitmap.height - side) / 2
        val square = Bitmap.createBitmap(bitmap, x, y, side, side)
        val scaled = if (side > maxSize) Bitmap.createScaledBitmap(square, maxSize, maxSize, true) else square
        val name = "${newId()}.png"
        runCatching {
            File(dir(), name).outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.onFailure {
            File(dir(), name).delete()
            return@withContext null
        }
        name
    }

    suspend fun delete(fileName: String) = withContext(Dispatchers.IO) { File(dir(), fileName).delete() }
}
