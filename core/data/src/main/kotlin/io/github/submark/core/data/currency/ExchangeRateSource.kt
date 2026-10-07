package io.github.submark.core.data.currency

import io.github.submark.core.model.RateProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate

/** Rates as units of each currency per 1 USD, as published for [date]. */
data class RateSnapshot(val provider: RateProvider, val date: LocalDate, val usdRates: Map<String, BigDecimal>)

/** One public exchange-rate API. Implementations throw [IOException] on network or format errors. */
interface ExchangeRateSource {
    val provider: RateProvider
    suspend fun latest(): RateSnapshot
    suspend fun historical(date: LocalDate): RateSnapshot

    /** Daily snapshots for a range; sources without a range endpoint return an empty list. */
    suspend fun range(from: LocalDate, to: LocalDate): List<RateSnapshot> = emptyList()
}

internal abstract class HttpRateSource(private val client: OkHttpClient, protected val json: Json) : ExchangeRateSource {
    protected suspend fun getJson(url: String): JsonObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            val body = response.body?.string() ?: throw IOException("empty body")
            try {
                json.parseToJsonElement(body).jsonObject
            } catch (e: IllegalArgumentException) {
                throw IOException("malformed rate payload", e)
            }
        }
    }

    protected fun JsonObject.toRates(upperCase: Boolean): Map<String, BigDecimal> = buildMap {
        for ((key, value) in this@toRates) {
            val rate = (value as? JsonPrimitive)?.contentOrNull?.toBigDecimalOrNull() ?: continue
            if (rate.signum() > 0) put(if (upperCase) key.uppercase() else key, rate)
        }
        put("USD", BigDecimal.ONE)
    }

    protected fun JsonObject.date(key: String): LocalDate =
        this[key]?.jsonPrimitive?.contentOrNull?.let(LocalDate::parse) ?: throw IOException("missing $key")
}

/** ECB reference rates (~30 currencies, business days only). `api.frankfurter.app` now redirects here. */
internal class FrankfurterSource(client: OkHttpClient, json: Json, private val baseUrl: String = BASE_URL) :
    HttpRateSource(client, json) {
    override val provider = RateProvider.FRANKFURTER

    override suspend fun latest() = parse(getJson("$baseUrl/latest?from=USD"))

    /** Weekends and holidays resolve to the previous business day. */
    override suspend fun historical(date: LocalDate) = parse(getJson("$baseUrl/$date?from=USD"))

    override suspend fun range(from: LocalDate, to: LocalDate): List<RateSnapshot> {
        val root = getJson("$baseUrl/$from..$to?from=USD")
        val rates = root["rates"]?.jsonObject ?: throw IOException("missing rates")
        return rates.map { (day, values) -> RateSnapshot(provider, LocalDate.parse(day), values.jsonObject.toRates(false)) }
            .sortedBy { it.date }
    }

    private fun parse(root: JsonObject): RateSnapshot {
        val rates = root["rates"]?.jsonObject ?: throw IOException("missing rates")
        return RateSnapshot(provider, root.date("date"), rates.toRates(upperCase = false))
    }

    companion object {
        const val BASE_URL = "https://api.frankfurter.dev/v1"
    }
}

/** fawazahmed0/exchange-api: 200+ codes (lowercase keys, includes crypto). */
internal class FawazSource(
    client: OkHttpClient,
    json: Json,
    override val provider: RateProvider,
    private val latestUrl: String,
    private val historicalUrl: (LocalDate) -> String,
) : HttpRateSource(client, json) {
    override suspend fun latest() = parse(getJson(latestUrl))
    override suspend fun historical(date: LocalDate) = parse(getJson(historicalUrl(date)))

    private fun parse(root: JsonObject): RateSnapshot {
        val rates = root["usd"]?.jsonObject ?: throw IOException("missing usd")
        return RateSnapshot(provider, root.date("date"), rates.toRates(upperCase = true))
    }

    companion object {
        fun jsDelivr(client: OkHttpClient, json: Json) = FawazSource(
            client, json, RateProvider.FAWAZ_JSDELIVR,
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json",
        ) { "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@$it/v1/currencies/usd.json" }

        fun pagesDev(client: OkHttpClient, json: Json) = FawazSource(
            client, json, RateProvider.FAWAZ_PAGES_DEV,
            "https://latest.currency-api.pages.dev/v1/currencies/usd.json",
        ) { "https://$it.currency-api.pages.dev/v1/currencies/usd.json" }
    }
}
