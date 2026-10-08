package io.github.submark.feature.integrations.panel.data.service

import io.github.submark.feature.integrations.panel.data.EmbySnapshot
import io.github.submark.feature.integrations.panel.data.PanelFetchException
import io.github.submark.feature.integrations.panel.data.PanelErrorReason
import io.github.submark.feature.integrations.panel.data.PanelHttp
import io.github.submark.feature.integrations.panel.data.boolOrNull
import io.github.submark.feature.integrations.panel.data.parseOrThrow
import io.github.submark.feature.integrations.panel.data.str
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Emby server client.
 *
 * Verified against the official Emby API documentation / dev wiki:
 *
 * Auth (two modes):
 * - API key: header `X-Emby-Token: <key>` on every authenticated request.
 * - Username/password: `POST /Users/AuthenticateByName` with header
 *   `X-Emby-Authorization: MediaBrowser Client="SubMark", Device="<device>", DeviceId="<id>", Version="1.0"`
 *   and JSON body `{"Username": "...", "Pw": "..."}` → returns
 *   `{ "User": { "Name": ..., "Policy": { "IsAdministrator": true, "IsDisabled": false },
 *       "LastLoginDate"/"LastActivityDate": ... }, "AccessToken": "<64-hex>" }`;
 *   the access token is then passed as `X-Emby-Token` exactly like an API key.
 *
 * Info: `GET /System/Info` → `{ "ServerName": ..., "Version": "4.8.x", "OperatingSystem": ... }`.
 * Latency: round-trip time of `GET /System/Ping` (requires auth; returns "Ping succeeded").
 */
@Singleton
class EmbyClient @Inject constructor(private val http: PanelHttp) {

    /** Result of a successful AuthenticateByName call, or a synthetic one for API keys. */
    data class AuthInfo(
        val token: String,
        val userName: String?,
        val isAdmin: Boolean,
        val lastActiveAt: Instant?,
    )

    suspend fun fetch(baseUrl: String, username: String?, password: String?, apiKey: String?, now: Instant): EmbySnapshot {
        val base = normalizeBase(baseUrl) ?: throw PanelFetchException(PanelErrorReason.MISSING_CONFIG)
        val auth = when {
            !apiKey.isNullOrBlank() -> AuthInfo(apiKey.trim(), null, false, null)
            !username.isNullOrBlank() && !password.isNullOrBlank() -> authenticate(base, username.trim(), password)
            else -> throw PanelFetchException(PanelErrorReason.MISSING_CONFIG)
        }
        val latencyMs = measureLatency(base, auth.token)
        val infoRaw = http.execute(http.get("$base/System/Info", mapOf(TOKEN_HEADER to auth.token)))
        return parseOrThrow { parse(infoRaw, auth.userName, auth.isAdmin, auth.lastActiveAt, latencyMs, now) }
    }

    /** Auth response parsing — unit-tested. */
    fun parseAuth(raw: String): AuthInfo {
        val root = http.json.parseToJsonElement(raw) as? JsonObject
            ?: throw PanelFetchException(PanelErrorReason.PARSE)
        val token = root.str("AccessToken") ?: throw PanelFetchException(PanelErrorReason.PARSE)
        val user = root["User"] as? JsonObject
        return AuthInfo(
            token = token,
            userName = user?.str("Name"),
            isAdmin = (user?.get("Policy") as? JsonObject)?.boolOrNull("IsAdministrator") ?: false,
            lastActiveAt = user?.str("LastActivityDate")?.let(::parseEmbyDate),
        )
    }

    /** System/Info parsing combined with auth info — unit-tested. */
    fun parse(infoRaw: String, userName: String?, isAdmin: Boolean, lastActiveAt: Instant?, latencyMs: Long?, now: Instant): EmbySnapshot {
        val info = http.json.parseToJsonElement(infoRaw) as? JsonObject
            ?: throw PanelFetchException(PanelErrorReason.PARSE)
        return EmbySnapshot(
            serverName = info.str("ServerName"),
            version = info.str("Version"),
            operatingSystem = info.str("OperatingSystem"),
            userName = userName,
            isAdmin = isAdmin,
            lastActiveAt = lastActiveAt,
            latencyMs = latencyMs,
            reachable = true,
            fetchedAt = now,
        )
    }

    private fun parse(infoRaw: String, auth: AuthInfo, latencyMs: Long?, now: Instant): EmbySnapshot =
        parse(infoRaw, auth.userName, auth.isAdmin, auth.lastActiveAt, latencyMs, now)

    private suspend fun authenticate(base: String, username: String, password: String): AuthInfo {
        val body = buildJsonObject { put("Username", username); put("Pw", password) }.toString()
        val request = Request.Builder()
            .url("$base/Users/AuthenticateByName")
            .header("X-Emby-Authorization", AUTH_HEADER_VALUE)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val raw = http.execute(request)
        return parseOrThrow { parseAuth(raw) }
    }

    private suspend fun measureLatency(base: String, token: String): Long? = try {
        val start = System.nanoTime()
        http.execute(http.get("$base/System/Ping", mapOf(TOKEN_HEADER to token)))
        (System.nanoTime() - start) / 1_000_000
    } catch (e: PanelFetchException) {
        null
    }

    /** Emby dates come back as "2024-02-25T13:45:12.1234567Z" (up to 7 fractional digits). */
    private fun parseEmbyDate(text: String): Instant? = runCatching { Instant.parse(text) }.getOrElse {
        runCatching {
            val (main, rest) = text.split(".", limit = 2).let { it[0] to it.getOrElse(1) { "Z" } }
            val frac = rest.trimEnd('Z')
            Instant.parse("${main}.${frac.take(9).padEnd(9, '0')}Z")
        }.getOrNull()
    }

    companion object {
        const val TOKEN_HEADER = "X-Emby-Token"
        const val AUTH_HEADER_VALUE = "MediaBrowser Client=\"SubMark\", Device=\"Android\", DeviceId=\"submark\", Version=\"1.0\""

        /** Returns null when the URL is blank or not http(s). */
        fun normalizeBase(url: String): String? {
            val b = url.trim().trimEnd('/')
            if (b.isEmpty() || (!b.startsWith("https://") && !b.startsWith("http://"))) return null
            return b
        }
    }
}
