package io.github.submark.feature.share.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Result of persisting a poster bitmap. */
sealed interface PosterSaveResult {
    data class Saved(val uri: Uri) : PosterSaveResult
    data object Failed : PosterSaveResult
}

/**
 * Saves poster bitmaps to the gallery (MediaStore, no permission on API 29+; the app's minSdk is
 * 29 so no WRITE_EXTERNAL_STORAGE is ever needed) and stages cache files for FileProvider sharing.
 */
@Singleton
class PosterStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Inserts the bitmap into Pictures/SubMark. */
    suspend fun saveToGallery(bitmap: Bitmap, displayName: String): PosterSaveResult = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SubMark")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return@withContext PosterSaveResult.Failed
        try {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return@withContext PosterSaveResult.Failed
            } ?: return@withContext PosterSaveResult.Failed
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            PosterSaveResult.Saved(uri)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            PosterSaveResult.Failed
        }
    }

    /** Writes the bitmap into cacheDir/share/ (granted to the feature FileProvider) and returns its URI. */
    suspend fun shareableUri(bitmap: Bitmap, displayName: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "$displayName.png")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            FileProvider.getUriForFile(context, authority(context), file)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        fun authority(context: Context): String = "${context.packageName}.share.fileprovider"
    }
}
