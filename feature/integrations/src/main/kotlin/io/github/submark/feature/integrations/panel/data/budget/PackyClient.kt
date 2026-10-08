package io.github.submark.feature.integrations.panel.data.budget

import io.github.submark.feature.integrations.panel.data.BudgetSnapshot
import io.github.submark.feature.integrations.panel.data.PanelFetchException
import io.github.submark.feature.integrations.panel.data.PanelErrorReason
import io.github.submark.feature.integrations.panel.data.PanelHttp
import io.github.submark.feature.integrations.panel.data.decimalText
import io.github.submark.feature.integrations.panel.data.parseOrThrow
import io.github.submark.feature.integrations.panel.data.str
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Packy (packycode) budget client.
 *
 * Verified against the open-source packycode-cost browser extension
 * (github.com/wuwenrui/packycode-cost):
 *
 * Request: `GET <baseUrl>/api/backend/users/info`, default base `https://www.packycode.com`
 * (the "shared account" site; custom base URLs are supported for self-hosted mirrors).
 * Header `Authorization: Bearer <token>` (JWT or permanent API token).
 *
 * Response: the extension types the body as:
 * ```json
 * {
 *   "daily_budget_usd": "5.00", "daily_spent_usd": "0.37",
 *   "monthly_budget_usd": "80.00", "monthly_spent_usd": "12.40",
 *   "opus_enabled": true
 * }
 * ```
 * Field names are snake_case USD-suffixed and may be numbers or strings — parsing is
 * tolerant. UNVERIFIED: whether a plan expiry field is present in this payload; we read a
 * few plausible names (`plan_expires_at`, `expired_at`, `expires_at`) defensively and treat
 * absence as "unknown". Values may also be wrapped in `data` — both layouts are accepted.
 */
@Singleton
class PackyClient @Inject constructor(private val http: PanelHttp) {

    suspend fun fetch(apiKey: String, baseUrl: String?, now: Instant): BudgetSnapshot {
        val url = endpoint(baseUrl ?: DEFAULT_BASE_URL) ?: throw PanelFetchException(PanelErrorReason.MISSING_CONFIG)
        val raw = http.execute(http.get(url, mapOf("Authorization" to "Bearer $apiKey")))
        return parseOrThrow { parse(raw, now) }
    }

    /** Pure parser — unit-tested. */
    fun parse(raw: String, now: Instant): BudgetSnapshot {
        val root = http.json.parseToJsonElement(raw) as? JsonObject
            ?: throw PanelFetchException(PanelErrorReason.PARSE)
        val data = (root["data"] as? JsonObject) ?: root
        val dailyBudget = data.decimalText("daily_budget_usd")
        val dailySpent = data.decimalText("daily_spent_usd")
        val monthlyBudget = data.decimalText("monthly_budget_usd")
        val monthlySpent = data.decimalText("monthly_spent_usd")
        if (monthlyBudget == null && monthlySpent == null && dailyBudget == null && dailySpent == null) {
            throw PanelFetchException(PanelErrorReason.PARSE)
        }
        val expiry = listOf("plan_expires_at", "expired_at", "expires_at").firstNotNullOfOrNull { k ->
            data.str(k)?.let(::parseDateTime)
        }
        return BudgetSnapshot(
            serviceType = "PACKY",
            usedAmount = monthlySpent,
            limitAmount = monthlyBudget,
            dailySpentAmount = dailySpent,
            currencyCode = "USD",
            expiryAt = expiry,
            fetchedAt = now,
            rawJson = raw,
        )
    }

    private fun parseDateTime(text: String): Instant? = runCatching {
        when {
            text.matches(Regex("\\d{10,13}")) -> {
                val v = text.toLong()
                Instant.ofEpochMilli(if (v < 10_000_000_000L) v * 1000 else v)
            }
            text.length >= 19 && text[4] == '-' && text[10] == ' ' ->
                LocalDateTime.parse(text.take(19), SPACE_FORMAT).atZone(ZoneId.of("Asia/Shanghai")).toInstant()
            else -> Instant.parse(text)
        }
    }.getOrNull()

    companion object {
        const val DEFAULT_BASE_URL = "https://www.packycode.com"
        private val SPACE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        /** Returns null when [baseUrl] is blank or malformed. */
        fun endpoint(baseUrl: String): String? {
            val b = baseUrl.trim().trimEnd('/')
            if (b.isEmpty() || (!b.startsWith("https://") && !b.startsWith("http://"))) return null
            return "$b/api/backend/users/info"
        }
    }
}
