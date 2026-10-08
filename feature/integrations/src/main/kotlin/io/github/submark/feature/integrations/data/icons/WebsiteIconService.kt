package io.github.submark.feature.integrations.data.icons

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a website domain to a favicon URL: DuckDuckGo, then Google S2, then the site's own
 * `/favicon.ico`; the first URL that actually serves an image wins.
 */
@Singleton
class WebsiteIconService @Inject constructor(
    private val client: OkHttpClient,
) {
    /** Strips scheme/path/whitespace; null when nothing domain-like is left. */
    fun normalizeDomain(input: String): String? {
        var s = input.trim().lowercase()
        s = s.removePrefix("http://").removePrefix("https://")
        s = s.substringBefore('/').substringBefore('?')
        return s.takeIf { it.isNotEmpty() && it.contains('.') && it.all { c -> c.isLetterOrDigit() || c == '.' || c == '-' } }
    }

    fun candidates(domain: String): List<String> = listOf(
        "https://icons.duckduckgo.com/ip3/$domain.ico",
        "https://www.google.com/s2/favicons?domain=$domain&sz=128",
        "https://$domain/favicon.ico",
    )

    /** First candidate that serves an image content type; null when none work. */
    suspend fun resolve(domain: String): String? = withContext(Dispatchers.IO) {
        for (url in candidates(domain)) {
            val ok = try {
                val request = Request.Builder().url(url.toHttpUrl()).head().build()
                client.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful && resp.header("Content-Type").orEmpty().startsWith("image")) true
                    else {
                        // Some hosts reject HEAD; fall back to a ranged GET probe.
                        client.newCall(
                            Request.Builder().url(url.toHttpUrl()).get().header("Range", "bytes=0-0").build(),
                        ).execute().use { getResp ->
                            getResp.isSuccessful && getResp.header("Content-Type").orEmpty().startsWith("image")
                        }
                    }
                }
            } catch (e: Exception) {
                false
            }
            if (ok) return@withContext url
        }
        null
    }
}
