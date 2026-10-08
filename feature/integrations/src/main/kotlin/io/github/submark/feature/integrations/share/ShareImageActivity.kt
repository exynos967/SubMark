package io.github.submark.feature.integrations.share

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import io.github.submark.core.model.newId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * UI-less share target for screenshots. Copies the shared image into `cacheDir/shared/`, then
 * hands off to MainActivity via the `submark://ai-recognize` deep link and finishes.
 */
class ShareImageActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri: Uri? = if (intent?.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            (intent.getParcelableExtra(Intent.EXTRA_STREAM) ?: intent.data)
        } else null
        if (uri == null) {
            finish()
            return
        }
        val appContext = applicationContext
        CoroutineScope(Dispatchers.Main).launch {
            val path = copyToCache(appContext, uri)
            if (path != null) {
                val deepLink = Uri.Builder()
                    .scheme("submark")
                    .authority("ai-recognize")
                    .appendQueryParameter("imagePath", path)
                    .build()
                val view = Intent(Intent.ACTION_VIEW, deepLink).apply {
                    setPackage(packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                runCatching { startActivity(view) }
            }
            finish()
        }
    }

    private suspend fun copyToCache(context: android.content.Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                val file = File(dir, "${newId()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { input.copyTo(it) }
                } ?: return@runCatching null
                file.absolutePath
            }.getOrNull()
        }
}
