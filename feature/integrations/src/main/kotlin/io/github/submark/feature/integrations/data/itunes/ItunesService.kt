package io.github.submark.feature.integrations.data.itunes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ItunesResult(
    @SerialName("trackId") val trackId: Long? = null,
    @SerialName("trackName") val trackName: String? = null,
    val price: Double? = null,
    @SerialName("formattedPrice") val formattedPrice: String? = null,
    val currency: String? = null,
    @SerialName("artworkUrl512") val artworkUrl512: String? = null,
    @SerialName("artworkUrl100") val artworkUrl100: String? = null,
    @SerialName("trackViewUrl") val trackViewUrl: String? = null,
    @SerialName("sellerName") val sellerName: String? = null,
    @SerialName("primaryGenreName") val primaryGenreName: String? = null,
)

@Serializable
private data class ItunesResponse(
    val resultCount: Int = 0,
    val results: List<ItunesResult> = emptyList(),
)

/** Price of an app in one storefront, or null when the store data is incomplete. */
data class AppPrice(
    val region: String,
    val trackName: String,
    val price: BigDecimal,
    val currency: String,
    val formattedPrice: String?,
)

sealed interface ItunesError {
    data object Network : ItunesError
    data object Parse : ItunesError
    /** No result for the requested id in that storefront. */
    data object NoItems : ItunesError
    /** App found but price/currency missing → no record may be written. */
    data object IncompletePrice : ItunesError
}

/** Pure iTunes response handling, unit-tested without a server. */
object ItunesParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseResults(body: String): List<ItunesResult>? = try {
        json.decodeFromString<ItunesResponse>(body).results
    } catch (e: Exception) {
        null
    }

    /** Maps lookup payload → price; mirrors [ItunesService.lookupPrice] rules. */
    fun toAppPrice(body: String, region: String): Result<AppPrice> {
        val results = parseResults(body) ?: return Result.failure(ItunesException(ItunesError.Parse))
        val first = results.firstOrNull { it.trackId != null }
            ?: return Result.failure(ItunesException(ItunesError.NoItems))
        val price = first.price
        val currency = first.currency
        if (price == null || currency.isNullOrBlank()) {
            return Result.failure(ItunesException(ItunesError.IncompletePrice))
        }
        return Result.success(
            AppPrice(
                region = region.uppercase(),
                trackName = first.trackName ?: "",
                price = BigDecimal.valueOf(price),
                currency = currency,
                formattedPrice = first.formattedPrice,
            ),
        )
    }
}

@Singleton
class ItunesService @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {
    private suspend fun get(url: okhttp3.HttpUrl): Result<String> = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(ItunesException(ItunesError.Network))
                val body = resp.body?.string() ?: return@withContext Result.failure(ItunesException(ItunesError.Parse))
                Result.success(body)
            }
        } catch (e: Exception) {
            Result.failure(ItunesException(ItunesError.Network))
        }
    }

    /** Searches the App Store; [region] is ISO alpha-2 (any case). Empty list = no matches. */
    suspend fun searchApps(term: String, region: String, limit: Int = 25): Result<List<ItunesResult>> {
        val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", term)
            .addQueryParameter("country", region.lowercase())
            .addQueryParameter("entity", "software")
            .addQueryParameter("limit", limit.toString())
            .build()
        return get(url).mapCatching { ItunesParser.parseResults(it) ?: throw ItunesException(ItunesError.Parse) }
    }

    /**
     * Looks up [appStoreId] in storefront [region].
     * Success carries [AppPrice]; failure carries [ItunesException] with one of [ItunesError].
     */
    suspend fun lookupPrice(appStoreId: String, region: String): Result<AppPrice> {
        val url = "https://itunes.apple.com/lookup".toHttpUrl().newBuilder()
            .addQueryParameter("id", appStoreId)
            .addQueryParameter("country", region.lowercase())
            .build()
        return get(url).mapCatching { body ->
            ItunesParser.toAppPrice(body, region).getOrThrow()
        }
    }
}

class ItunesException(val error: ItunesError) : Exception(error.toString())
