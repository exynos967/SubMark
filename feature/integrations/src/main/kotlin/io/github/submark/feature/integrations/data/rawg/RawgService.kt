package io.github.submark.feature.integrations.data.rawg

import io.github.submark.core.data.secret.SecretStore
import io.github.submark.core.model.SecretKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class RawgGame(
    val id: Long? = null,
    val name: String? = null,
    @SerialName("background_image") val backgroundImage: String? = null,
    val released: String? = null,
)

@Serializable
private data class RawgResponse(
    val count: Int = 0,
    val results: List<RawgGame> = emptyList(),
)

sealed interface RawgError {
    data object NoApiKey : RawgError
    data object Network : RawgError
    data class Http(val code: Int) : RawgError
    data object Parse : RawgError
    data object NoResults : RawgError
}

/** RAWG game database (cover images); key comes from the user's SecretStore entry. */
@Singleton
class RawgService @Inject constructor(
    private val client: OkHttpClient,
    private val secrets: SecretStore,
    private val json: Json,
) {
    suspend fun apiKey(): String? = secrets.get(SecretKeys.RAWG_API_KEY)?.takeIf { it.isNotBlank() }

    suspend fun setApiKey(key: String) = secrets.put(SecretKeys.RAWG_API_KEY, key.trim())

    suspend fun clearApiKey() = secrets.remove(SecretKeys.RAWG_API_KEY)

    private suspend fun get(url: okhttp3.HttpUrl): Result<String> = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(RawgException(RawgError.Http(resp.code)))
                Result.success(resp.body?.string() ?: return@withContext Result.failure(RawgException(RawgError.Parse)))
            }
        } catch (e: Exception) {
            Result.failure(RawgException(RawgError.Network))
        }
    }

    /** Searches games; an empty query is rejected with [RawgError.NoResults]. */
    suspend fun search(query: String, pageSize: Int = 20): Result<List<RawgGame>> {
        val key = apiKey() ?: return Result.failure(RawgException(RawgError.NoApiKey))
        if (query.isBlank()) return Result.success(emptyList())
        val url = "https://api.rawg.io/api/games".toHttpUrl().newBuilder()
            .addQueryParameter("key", key)
            .addQueryParameter("search", query.trim())
            .addQueryParameter("page_size", pageSize.toString())
            .build()
        return get(url).mapCatching { body ->
            val parsed = try {
                json.decodeFromString<RawgResponse>(body)
            } catch (e: Exception) {
                throw RawgException(RawgError.Parse)
            }
            parsed.results.filter { it.name != null }
        }
    }

    /** Probes the key with a 1-result query, used by the settings "test" action. */
    suspend fun testConnection(): Result<Unit> {
        val key = apiKey() ?: return Result.failure(RawgException(RawgError.NoApiKey))
        val url = "https://api.rawg.io/api/games".toHttpUrl().newBuilder()
            .addQueryParameter("key", key)
            .addQueryParameter("page_size", "1")
            .build()
        return get(url).map { }
    }
}

class RawgException(val error: RawgError) : Exception(error.toString())
