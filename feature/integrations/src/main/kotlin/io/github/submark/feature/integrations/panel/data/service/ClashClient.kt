package io.github.submark.feature.integrations.panel.data.service

import io.github.submark.feature.integrations.panel.data.ClashSnapshot
import io.github.submark.feature.integrations.panel.data.PanelFetchException
import io.github.submark.feature.integrations.panel.data.PanelErrorReason
import io.github.submark.feature.integrations.panel.data.PanelHttp
import kotlinx.coroutines.delay
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clash-style subscription link client ("Server" type in the original app).
 *
 * Request: `GET <subscription-url>` with `User-Agent: clash.meta` (mihomo-compatible). Any
 * 2xx means the link is valid. Traffic info is read from the
 * `subscription-userinfo: upload=..; download=..; total=..; expire=..` response header;
 * the body (proxy config) is ignored entirely.
 *
 * Transient failures (429, 5xx, network, timeout) are retried with exponential backoff
 * (1.5x, up to [MAX_ATTEMPTS] attempts) because subscription providers rate-limit harshly.
 */
@Singleton
class ClashClient @Inject constructor(private val http: PanelHttp) {

    suspend fun fetch(url: String, now: Instant): ClashSnapshot {
        var lastError: PanelFetchException? = null
        var backoffMs = INITIAL_BACKOFF_MS
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val headers = http.executeHeaders(http.get(url, mapOf("User-Agent" to USER_AGENT)))
                val userinfo = headers.entries.firstOrNull { it.key.equals(USERINFO_HEADER, true) }?.value
                return SubscriptionUserinfo.toSnapshot(userinfo, now)
            } catch (e: PanelFetchException) {
                lastError = e
                val retriable = e.reason in setOf(
                    PanelErrorReason.RATE_LIMITED, PanelErrorReason.SERVER_ERROR,
                    PanelErrorReason.TIMEOUT, PanelErrorReason.NETWORK,
                )
                if (!retriable || attempt == MAX_ATTEMPTS - 1) throw e
                delay(backoffMs)
                backoffMs = (backoffMs * 3 / 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
        throw lastError ?: PanelFetchException(PanelErrorReason.UNKNOWN)
    }

    companion object {
        const val USER_AGENT = "clash.meta"
        const val USERINFO_HEADER = "subscription-userinfo"
        const val MAX_ATTEMPTS = 3
        private const val INITIAL_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 6_000L
    }
}
