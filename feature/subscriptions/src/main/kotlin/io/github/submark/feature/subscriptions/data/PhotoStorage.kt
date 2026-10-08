package io.github.submark.feature.subscriptions.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Subscription photo files in `filesDir/photos/`; database rows go through `SubscriptionExtrasRepository`. */
@Singleton
class PhotoStorage @Inject constructor(@ApplicationContext private val context: Context) {

    private val dir: File get() = File(context.filesDir, PHOTO_DIR)

    fun file(fileName: String): File = File(dir, fileName)

    /** Copies the picked image into private storage. Returns the new file name, or null when it could not be read. */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        val name = "${UUID.randomUUID()}.jpg"
        val target = File(dir.apply { mkdirs() }, name)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
            name
        } catch (e: IOException) {
            target.delete()
            null
        } catch (e: SecurityException) {
            target.delete()
            null
        }
    }

    suspend fun delete(fileNames: Collection<String>) = withContext(Dispatchers.IO) {
        fileNames.forEach { File(dir, it).delete() }
    }

    companion object {
        const val PHOTO_DIR = "photos"
    }
}
