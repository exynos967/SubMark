package io.github.submark.feature.integrations.panel.data.budget

import io.github.submark.feature.integrations.panel.data.BudgetSnapshot
import io.github.submark.feature.integrations.panel.data.PanelFetchException
import io.github.submark.feature.integrations.panel.data.PanelErrorReason
import io.github.submark.feature.integrations.panel.data.PanelHttp
import io.github.submark.feature.integrations.panel.data.boolOrNull
import io.github.submark.feature.integrations.panel.data.decimalText
import io.github.submark.feature.integrations.panel.data.parseOrThrow
import io.github.submark.feature.integrations.panel.data.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DeepSeek balance client.
 *
 * Verified against the official docs (https://api-docs.deepseek.com/api/get-user-balance):
 *
 * Request: `GET https://api.deepseek.com/user/balance`, header `Authorization: Bearer <sk-...>`.
 *
 * Response (200):
 * ```json
 * {
 *   "is_available": true,
 *   "balance_infos": [
 *     { "currency": "CNY", "total_balance": "110.00", "granted_balance": "10.00", "topped_up_balance": "100.00" }
 *   ]
 * }
 * ```
 * - `is_available` boolean: whether the balance is sufficient for API calls.
 * - `balance_infos[]` per-currency: `currency` (CNY/USD), and string-decimal
 *   `total_balance`, `granted_balance`, `topped_up_balance` (total = granted + topped-up).
 * - Failure codes documented only via 200 schema; we map 401/403 → invalid key as usual.
 */
@Singleton
class DeepSeekClient @Inject constructor(private val http: PanelHttp) {

    suspend fun fetch(apiKey: String, now: Instant): BudgetSnapshot {
        val raw = http.execute(http.get(BALANCE_URL, mapOf("Authorization" to "Bearer $apiKey")))
        return parseOrThrow { parse(raw, now) }
    }

    /** Pure parser — unit-tested with the documented sample. */
    fun parse(raw: String, now: Instant): BudgetSnapshot {
        val root = http.json.parseToJsonElement(raw) as? JsonObject
            ?: throw PanelFetchException(PanelErrorReason.PARSE)
        val infos = root["balance_infos"] as? JsonArray
            ?: throw PanelFetchException(PanelErrorReason.PARSE)
        val balances = infos.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val currency = obj.str("currency")?.uppercase() ?: return@mapNotNull null
            BudgetSnapshot.Balance(
                currencyCode = currency,
                total = obj.decimalText("total_balance") ?: BigDecimal.ZERO.toPlainString(),
                granted = obj.decimalText("granted_balance"),
                toppedUp = obj.decimalText("topped_up_balance"),
            )
        }
        if (balances.isEmpty()) throw PanelFetchException(PanelErrorReason.PARSE)
        val isAvailable = root.boolOrNull("is_available") ?: true
        // Remaining balance is what a DeepSeek user spends against; use total of primary currency.
        val primary = balances.firstOrNull { it.currencyCode == "USD" } ?: balances.first()
        return BudgetSnapshot(
            serviceType = "DEEPSEEK",
            balances = balances,
            usedAmount = null,
            limitAmount = null,
            currencyCode = primary.currencyCode,
            isBalanceAvailable = isAvailable,
            fetchedAt = now,
            rawJson = raw,
        )
    }

    companion object {
        const val BALANCE_URL = "https://api.deepseek.com/user/balance"
    }
}
