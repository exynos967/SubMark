package io.github.submark.feature.integrations.panel.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

/**
 * Shared OkHttp plumbing for the service panel / API budget clients.
 * Maps transport and status failures to [PanelFetchException]; JSON helpers are tolerant
 * of both string and number scalars (providers disagree).
 */
@Singleton
class PanelHttp @Inject constructor(
    private val client: OkHttpClient,
) {
    val json: Json = Json { ignoreUnknownKeys = true }

    private class HttpStatus(val code: Int, val body: String?) : Exception("HTTP $code")

    internal suspend fun execute(request: Request, dropBody: Boolean = false): String = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful) throw HttpStatus(response.code, body)
                if (dropBody) "" else (body ?: throw PanelFetchException(PanelErrorReason.PARSE))
            }
        } catch (e: PanelFetchException) {
            throw e
        } catch (e: HttpStatus) {
            throw mapStatus(e.code)
        } catch (e: SocketTimeoutException) {
            throw PanelFetchException(PanelErrorReason.TIMEOUT, cause = e)
        } catch (e: UnknownHostException) {
            throw PanelFetchException(PanelErrorReason.NETWORK, e.message, e)
        } catch (e: SSLException) {
            throw PanelFetchException(PanelErrorReason.NETWORK, e.message, e)
        } catch (e: IOException) {
            throw PanelFetchException(PanelErrorReason.NETWORK, e.message, e)
        }
    }

    /** Executes the request and returns response headers without reading/interpreting the body. */
    internal suspend fun executeHeaders(request: Request): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    response.body?.close()
                    throw HttpStatus(response.code, null)
                }
                response.body?.close()
                response.headers.names().associateWith { response.headers[it]!! }
            }
        } catch (e: PanelFetchException) {
            throw e
        } catch (e: HttpStatus) {
            throw mapStatus(e.code)
        } catch (e: SocketTimeoutException) {
            throw PanelFetchException(PanelErrorReason.TIMEOUT, cause = e)
        } catch (e: UnknownHostException) {
            throw PanelFetchException(PanelErrorReason.NETWORK, e.message, e)
        } catch (e: SSLException) {
            throw PanelFetchException(PanelErrorReason.NETWORK, e.message, e)
        } catch (e: IOException) {
            throw PanelFetchException(PanelErrorReason.NETWORK, e.message, e)
        }
    }

    internal fun get(url: String, headers: Map<String, String> = emptyMap()): Request =
        Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.get().build()

    internal fun mapStatus(code: Int): PanelFetchException = when (code) {
        401, 403 -> PanelFetchException(PanelErrorReason.AUTH)
        404 -> PanelFetchException(PanelErrorReason.NOT_FOUND)
        429 -> PanelFetchException(PanelErrorReason.RATE_LIMITED)
        in 500..599 -> PanelFetchException(PanelErrorReason.SERVER_ERROR)
        else -> PanelFetchException(PanelErrorReason.UNKNOWN)
    }
}

/** Tolerant scalar: `str("a")` reads strings and numbers; JSON null/absent -> null. */
internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.content

/** Tolerant decimal text: accepts number or string scalars; blank/null -> null. */
internal fun JsonObject.decimalText(key: String): String? = str(key)?.takeIf { it.isNotBlank() }

internal fun JsonObject.longOrNull(key: String): Long? =
    (this[key] as? JsonPrimitive)?.longOrNull ?: str(key)?.toLongOrNull()

internal fun JsonObject.intOrNull(key: String): Int? =
    (this[key] as? JsonPrimitive)?.longOrNull?.toInt() ?: str(key)?.toIntOrNull()

internal fun JsonObject.boolOrNull(key: String): Boolean? = when (val v = this[key]) {
    is JsonPrimitive -> if (v.isString) v.content.equals("true", true) || v.content == "1" else v.content.equals("true", true)
    else -> null
}

internal inline fun <T> parseOrThrow(block: () -> T): T = try {
    block()
} catch (e: PanelFetchException) {
    throw e
} catch (e: Exception) {
    throw PanelFetchException(PanelErrorReason.PARSE, e.message, e)
}

internal inline fun <T> JsonElement.parseOrNull(block: (JsonObject) -> T?): T? = try {
    (this as? JsonObject)?.let(block)
} catch (e: Exception) {
    null
}
