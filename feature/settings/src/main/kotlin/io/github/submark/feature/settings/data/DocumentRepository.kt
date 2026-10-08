package io.github.submark.feature.settings.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AppDocument(
    val title: String,
    val intro: String? = null,
    /** ISO date of the last content change, shown as-is. */
    val updated: String? = null,
    val sections: List<DocumentSection> = emptyList(),
)

/** A section has free [content], FAQ [items], or both. */
@Serializable
data class DocumentSection(
    val title: String,
    val content: String? = null,
    val items: List<FaqItem> = emptyList(),
)

@Serializable
data class FaqItem(val question: String, val answer: String)

enum class DocumentType(val key: String) {
    FAQ("faq"), PRIVACY("privacy"), TERMS("terms");

    companion object {
        fun from(key: String): DocumentType? = entries.firstOrNull { it.key == key }
    }
}

sealed interface DocumentLoad {
    data class Loaded(val document: AppDocument) : DocumentLoad
    data object NotFound : DocumentLoad
    data class Failed(val message: String?) : DocumentLoad
}

/** Bundled documents in `assets/docs/<type>.<lang>.json`; falls back to English when a translation is missing. */
@Singleton
class DocumentRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(type: DocumentType, language: String): DocumentLoad = withContext(Dispatchers.IO) {
        val candidates = listOf(language, FALLBACK_LANGUAGE).distinct().map { "$DIR/${type.key}.$it.json" }
        for (path in candidates) {
            val text = try {
                context.assets.open(path).bufferedReader().use { it.readText() }
            } catch (_: FileNotFoundException) {
                continue
            } catch (e: java.io.IOException) {
                return@withContext DocumentLoad.Failed(e.message)
            }
            return@withContext try {
                DocumentLoad.Loaded(parse(text))
            } catch (e: SerializationException) {
                DocumentLoad.Failed(e.message)
            } catch (e: IllegalArgumentException) {
                DocumentLoad.Failed(e.message)
            }
        }
        DocumentLoad.NotFound
    }

    internal fun parse(text: String): AppDocument = json.decodeFromString(AppDocument.serializer(), text)

    private companion object {
        const val DIR = "docs"
        const val FALLBACK_LANGUAGE = "en"
    }
}
