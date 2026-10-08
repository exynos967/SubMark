package io.github.submark.feature.integrations.data.popular

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.submark.core.database.dao.PopularRepositoryDao
import io.github.submark.core.model.PopularRepository
import io.github.submark.core.model.newId
import io.github.submark.core.data.time.TimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** Errors surfaced by [PopularService]; mapping to localized strings happens in the UI layer. */
sealed interface CatalogError {
    data object InvalidUrl : CatalogError
    data class Http(val code: Int) : CatalogError
    data object Network : CatalogError
    data object Parse : CatalogError
}

typealias CatalogResult<T> = Result<T>

/** Fetches, caches (raw JSON on disk, 24 h TTL) and merges popular-subscription catalogues. */
@Singleton
class PopularService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: PopularRepositoryDao,
    private val client: OkHttpClient,
    private val time: TimeProvider,
) {
    fun observeRepositories() = dao.observeAll()

    suspend fun getRepository(id: String): PopularRepository? = dao.getAll().firstOrNull { it.id == id }

    suspend fun addRepository(name: String, url: String, description: String?): PopularRepository {
        val order = (dao.getAll().maxOfOrNull { it.sortOrder } ?: -1) + 1
        val repo = PopularRepository(id = newId(), name = name.trim(), url = url.trim(), description = description?.trim()?.takeIf { it.isNotEmpty() }, sortOrder = order)
        dao.upsert(repo)
        return repo
    }

    suspend fun updateRepository(repo: PopularRepository) = dao.upsert(repo)

    suspend fun setEnabled(repo: PopularRepository, enabled: Boolean) = dao.upsert(repo.copy(enabled = enabled))

    suspend fun reorder(orderedIds: List<String>) {
        val existing = dao.getAll().associateBy { it.id }
        orderedIds.mapIndexedNotNull { index, id -> existing[id]?.copy(sortOrder = index) }
            .takeIf { it.isNotEmpty() }?.let { dao.upsertAll(it) }
    }

    suspend fun deleteRepository(repo: PopularRepository) {
        dao.delete(repo)
        cacheFile(repo.id).delete()
    }

    suspend fun clearCaches() {
        cacheDir().listFiles()?.forEach { it.delete() }
        dao.getAll().forEach { dao.upsert(it.copy(lastFetchedAt = null, lastError = null)) }
    }

    /** Fetches [url] and parses it; used both by "validate URL" and by refresh. */
    suspend fun fetchAndParse(url: String): CatalogResult<PopularCatalog> = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            return@withContext Result.failure(CatalogFetchException(CatalogError.InvalidUrl))
        }
        val body = try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(CatalogFetchException(CatalogError.Http(resp.code)))
                }
                resp.body?.string() ?: return@withContext Result.failure(CatalogFetchException(CatalogError.Parse))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(CatalogFetchException(CatalogError.Network))
        }
        val catalog = CatalogParser.parse(body)
            ?: return@withContext Result.failure(CatalogFetchException(CatalogError.Parse))
        Result.success(catalog)
    }

    /** Raw cached JSON for [repositoryId], fresh or (when allowStale) any age; null when absent. */
    fun readCache(repositoryId: String, allowStale: Boolean = false): String? {
        val file = cacheFile(repositoryId)
        if (!file.exists()) return null
        if (!allowStale) {
            val age = Duration.ofMillis(System.currentTimeMillis() - file.lastModified())
            if (age > TTL) return null
        }
        return runCatching { file.readText() }.getOrNull()
    }

    /**
     * Loads the merged catalogue of all enabled repositories.
     * Fresh disk cache wins; per-repository failures are recorded on the row and skipped.
     * @return entries merged (first repository wins per id) + count of repositories that failed to load.
     */
    suspend fun loadCatalog(forceRefresh: Boolean = false): LoadedCatalog = withContext(Dispatchers.IO) {
        val repos = dao.getAll().filter { it.enabled }
        val catalogs = ArrayList<PopularCatalog>()
        var failures = 0
        for (repo in repos) {
            val cached = if (forceRefresh) null else readCache(repo.id)
            if (cached != null) {
                val parsed = CatalogParser.parse(cached)
                if (parsed != null) {
                    catalogs.add(parsed)
                    continue
                }
                cacheFile(repo.id).delete()
            }
            fetchAndParse(repo.url)
                .onSuccess { catalog ->
                    catalogs.add(catalog)
                    runCatching { cacheFile(repo.id).writeText(serialize(catalog)) }
                    dao.upsert(
                        repo.copy(
                            lastFetchedAt = time.now(),
                            entryCount = catalog.subscriptions.size,
                            lastError = null,
                        ),
                    )
                }
                .onFailure { e ->
                    failures++
                    val message = (e as? CatalogFetchException)?.error?.let { it.toString() } ?: "Network"
                    dao.upsert(repo.copy(lastError = message, lastFetchedAt = time.now()))
                }
        }
        LoadedCatalog(CatalogParser.merge(catalogs), failures, repos.isEmpty())
    }

    data class LoadedCatalog(val entries: List<CatalogEntry>, val failures: Int, val noRepositories: Boolean)

    /** Serializes a parsed catalogue back to JSON for the disk cache. */
    fun serialize(catalog: PopularCatalog): String =
        kotlinx.serialization.json.Json { encodeDefaults = true }.encodeToString(PopularCatalog.serializer(), catalog)

    private fun cacheDir(): File = File(context.filesDir, "popular").apply { mkdirs() }

    private fun cacheFile(id: String) = File(cacheDir(), "$id.json")

    companion object {
        val TTL: Duration = Duration.ofHours(24)
    }
}

class CatalogFetchException(val error: CatalogError) : Exception(error.toString())
