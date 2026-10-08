package io.github.submark.feature.integrations.panel.data.budget

import io.github.submark.feature.integrations.panel.data.BudgetSnapshot
import io.github.submark.feature.integrations.panel.data.PanelFetchException
import io.github.submark.feature.integrations.panel.data.PanelErrorReason
import io.github.submark.feature.integrations.panel.data.PanelHttp
import io.github.submark.feature.integrations.panel.data.intOrNull
import io.github.submark.feature.integrations.panel.data.longOrNull
import io.github.submark.feature.integrations.panel.data.parseOrThrow
import io.github.submark.feature.integrations.panel.data.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Z.ai GLM Coding Plan quota client.
 *
 * Verified against open-source clients with live captures (PowerUserZ/OpenTokenUsage docs,
 * SamyRai/go-z-ai). These endpoints are internal (not in the public API reference) but stable
 * across those clients.
 *
 * Requests (both):
 * - `GET https://api.z.ai/api/monitor/usage/quota/limit`
 * - `GET https://api.z.ai/api/biz/subscription/list`
 * - Headers: `Authorization: Bearer <api-key>` (the plain key also works; Bearer is used here),
 *   `Accept: application/json`.
 *
 * `quota/limit` response:
 * ```json
 * { "code": 200, "success": true, "data": { "limits": [
 *   { "type": "TOKENS_LIMIT", "unit": 3, "number": 5, "usage": 800000000,
 *     "currentValue": 127694464, "remaining": 672305536, "percentage": 15,
 *     "nextResetTime": 1770648402389 },
 *   { "type": "TIME_LIMIT", "unit": 5, "number": 1, "usage": 4000, "currentValue": 1828,
 *     "remaining": 2172, "percentage": 45,
 *     "usageDetails": [ { "modelCode": "search-prime", "usage": 1433 }, ... ] }
 * ] } }
 * ```
 * - `TOKENS_LIMIT` with `unit: 3, number: 5` = 5-hour rolling window; `unit: 6, number: 7` =
 *   7-day rolling window. `usage` = limit, `currentValue` = consumed, `remaining` = left,
 *   `percentage` = used %, `nextResetTime` = epoch millis (absent for the monthly window).
 * - `TIME_LIMIT` = MCP/web-search call quota (monthly; count-based), with a per-model
 *   `usageDetails` breakdown.
 *
 * `subscription/list` response: `{ "code": 200, "data": [ { "productName": "GLM Coding Max",
 * "nextRenewTime": "2026-03-12", ... } ] }` — plan name + monthly renewal date. An empty list
 * means the account has no active GLM Coding Plan.
 */
@Singleton
class ZaiClient @Inject constructor(private val http: PanelHttp) {

    data class Result(val snapshot: BudgetSnapshot, val hasCodingPlan: Boolean)

    suspend fun fetch(apiKey: String, now: Instant): Result {
        val headers = mapOf("Authorization" to "Bearer $apiKey", "Accept" to "application/json")
        val quotaRaw = http.execute(http.get(QUOTA_URL, headers))
        val subRaw = http.execute(http.get(SUBSCRIPTION_URL, headers))
        return parseOrThrow { parse(quotaRaw, subRaw, now) }
    }

    /** Pure parser — unit-tested with the captured samples above. */
    fun parse(quotaRaw: String, subRaw: String, now: Instant): Result {
        val quotaRoot = http.json.parseToJsonElement(quotaRaw) as? JsonObject
            ?: throw PanelFetchException(PanelErrorReason.PARSE)
        val limits = ((quotaRoot["data"] as? JsonObject)?.get("limits") as? JsonArray)
            ?: throw PanelFetchException(PanelErrorReason.PARSE)
        val quotas = limits.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val type = o.str("type") ?: return@mapNotNull null
            val limit = o.longOrNull("usage") ?: return@mapNotNull null
            val used = o.longOrNull("currentValue") ?: 0L
            val unit = o.intOrNull("unit")
            val number = o.intOrNull("number")
            val kind = when {
                type == "TOKENS_LIMIT" && unit == 3 -> QuotaKind.SESSION_5H
                type == "TOKENS_LIMIT" && unit == 6 -> QuotaKind.WEEKLY
                type == "TOKENS_LIMIT" && number == 5 -> QuotaKind.SESSION_5H
                type == "TOKENS_LIMIT" && number == 7 -> QuotaKind.WEEKLY
                type == "TIME_LIMIT" -> QuotaKind.WEB_SEARCH_MONTHLY
                else -> null
            } ?: return@mapNotNull null
            BudgetSnapshot.QuotaWindow(
                kind = kind,
                used = used,
                limit = limit,
                percentageUsed = o.intOrNull("percentage"),
                resetAt = o.longOrNull("nextResetTime")?.let(Instant::ofEpochMilli),
                isCount = type == "TIME_LIMIT",
            )
        }
        if (quotas.isEmpty()) throw PanelFetchException(PanelErrorReason.PARSE)

        val subRoot = http.json.parseToJsonElement(subRaw) as? JsonObject
        val subs = (subRoot?.get("data") as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
        val active = subs.firstOrNull { (it.str("status") ?: "VALID").equals("VALID", true) } ?: subs.firstOrNull()
        val planName = active?.str("productName")
        val renewAt = active?.str("nextRenewTime")?.let { t -> runCatching { Instant.parse("${t}T00:00:00Z") }.getOrNull() }

        val snapshot = BudgetSnapshot(
            serviceType = "ZAI",
            quotas = quotas,
            planName = planName,
            expiryAt = renewAt,
            fetchedAt = now,
            rawJson = quotaRaw,
        )
        return Result(snapshot, hasCodingPlan = planName != null || quotas.isNotEmpty())
    }

    companion object {
        const val QUOTA_URL = "https://api.z.ai/api/monitor/usage/quota/limit"
        const val SUBSCRIPTION_URL = "https://api.z.ai/api/biz/subscription/list"
    }
}

object QuotaKind {
    const val SESSION_5H = "SESSION_5H"
    const val WEEKLY = "WEEKLY"
    const val WEB_SEARCH_MONTHLY = "WEB_SEARCH_MONTHLY"
}
