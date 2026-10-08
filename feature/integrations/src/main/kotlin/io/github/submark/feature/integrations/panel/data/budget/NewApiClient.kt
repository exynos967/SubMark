package io.github.submark.feature.integrations.panel.data.budget

import io.github.submark.feature.integrations.panel.data.BudgetSnapshot
import io.github.submark.feature.integrations.panel.data.PanelFetchException
import io.github.submark.feature.integrations.panel.data.PanelErrorReason
import io.github.submark.feature.integrations.panel.data.PanelHttp
import io.github.submark.feature.integrations.panel.data.longOrNull
import io.github.submark.feature.integrations.panel.data.parseOrThrow
import io.github.submark.feature.integrations.panel.data.str
import kotlinx.serialization.json.JsonObject
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NewAPI-compatible quota client, also used for V-API (default base `https://api.gpt.ge`).
 *
 * Verified against the New API project source/docs (QuantumNous/new-api):
 *
 * Request: `GET <baseUrl>/api/user/self`
 * - Header `Authorization: Bearer <access-token>` — the web "personal access token"
 *   (`Authorization` is also accepted bare, but Bearer is the documented form).
 * - Header `New-Api-User: <userId>` — required on newer versions when authenticating with
 *   an access token (see QuantumNous/new-api issue #5430). [userId] must be the numeric
 *   account id shown in the user's dashboard.
 *
 * Response:
 * ```json
 * {
 *   "success": true,
 *   "data": {
 *     "id": 3, "username": "…", "role": 1, "status": 1, "group": "default",
 *     "quota": 5000000, "used_quota": 123456, "request_count": 42, ...
 *   }
 * }
 * ```
 * - `quota` = remaining quota units, `used_quota` = consumed units, `request_count` = total
 *   requests. 500000 units = 1 USD (configurable server-side; the default is assumed).
 * - `role`: 1 normal, 10 admin, 100 super admin (constants `RoleCommonUser` etc.).
 * - `status`: 1 enabled, 2 disabled.
 * - Older forks (one-api lineage) wrap data the same way but may omit some fields —
 *   parsing is defensive.
 */
@Singleton
class NewApiClient @Inject constructor(private val http: PanelHttp) {

    suspend fun fetch(apiKey: String, baseUrl: String, userId: String?, now: Instant): BudgetSnapshot {
        val headers = buildMap {
            put("Authorization", "Bearer $apiKey")
            if (!userId.isNullOrBlank()) put(USER_ID_HEADER, userId.trim())
        }
        val raw = http.execute(http.get(endpoint(baseUrl), headers))
        return parseOrThrow { parse(raw, now) }
    }

    /** Pure parser — unit-tested. */
    fun parse(raw: String, now: Instant): BudgetSnapshot {
        val root = http.json.parseToJsonElement(raw) as? JsonObject
            ?: throw PanelFetchException(PanelErrorReason.PARSE)
        val data = root["data"] as? JsonObject ?: throw PanelFetchException(PanelErrorReason.PARSE)
        val usedQuota = data.longOrNull("used_quota")
        val quotaRemaining = data.longOrNull("quota")
        val requestCount = data.longOrNull("request_count")
        if (usedQuota == null && quotaRemaining == null) throw PanelFetchException(PanelErrorReason.PARSE)
        val usedUsd = usedQuota?.let { unitsToUsd(it) }
        val limitUsd = if (usedQuota != null && quotaRemaining != null) unitsToUsd(usedQuota + quotaRemaining) else null
        return BudgetSnapshot(
            serviceType = "NEWAPI",
            usedAmount = usedUsd?.toPlainString(),
            limitAmount = limitUsd?.toPlainString(),
            currencyCode = "USD",
            totalRequests = requestCount,
            userRole = data.longOrNull("role")?.let(::roleName),
            userStatus = data.longOrNull("status")?.let(::statusName),
            accountId = data.longOrNull("id")?.toString() ?: data.str("username"),
            fetchedAt = now,
            rawJson = raw,
        )
    }

    private fun roleName(role: Long): String = when (role.toInt()) {
        100 -> "super_admin"
        10 -> "admin"
        else -> "user"
    }

    private fun statusName(status: Long): String = when (status.toInt()) {
        1 -> "enabled"
        2 -> "disabled"
        else -> "unknown"
    }

    companion object {
        const val USER_ID_HEADER = "New-Api-User"
        const val VAPI_DEFAULT_BASE_URL = "https://api.gpt.ge"
        val QUOTA_UNITS_PER_USD = BigDecimal(500_000)

        fun unitsToUsd(units: Long): BigDecimal =
            BigDecimal(units).divide(QUOTA_UNITS_PER_USD, 6, java.math.RoundingMode.HALF_UP)

        fun endpoint(baseUrl: String): String = "${baseUrl.trim().trimEnd('/')}/api/user/self"
    }
}
