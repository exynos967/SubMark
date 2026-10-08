package io.github.submark.feature.integrations.panel.data

import java.math.BigDecimal
import java.time.Instant

/** Fetch outcome class, used to map provider errors to localized UI text. */
enum class PanelErrorReason {
    /** 401/403 — bad key, token or credentials. */
    AUTH,
    /** 429 — rate limited. */
    RATE_LIMITED,
    /** 404 — bad base URL / subscription link. */
    NOT_FOUND,
    /** 5xx — remote server error. */
    SERVER_ERROR,
    TIMEOUT,
    NETWORK,
    /** Response could not be parsed. */
    PARSE,
    /** Required form field missing (e.g. base URL). */
    MISSING_CONFIG,
    UNKNOWN,
}

class PanelFetchException(
    val reason: PanelErrorReason,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message ?: reason.name, cause)

/** Thrown when saving a budget config whose service type already has one (one per type). */
class DuplicateServiceTypeException(val serviceType: io.github.submark.core.model.ApiServiceType) : Exception("duplicate $serviceType")

/**
 * Normalized provider payload, persisted as JSON in `ApiBudgetConfig.snapshotJson`.
 * Amounts are decimal strings to keep BigDecimal precision through serialization.
 */
data class BudgetSnapshot(
    val serviceType: String,
    /** Balance info per currency (DeepSeek-style). */
    val balances: List<Balance> = emptyList(),
    /** Amount used in the current period, in [currencyCode], when reported. */
    val usedAmount: String? = null,
    /** Total quota/budget in the current period, in [currencyCode], when reported. */
    val limitAmount: String? = null,
    val currencyCode: String = "USD",
    /** Provider-reported daily spent, when available (Packy). */
    val dailySpentAmount: String? = null,
    val totalRequests: Long? = null,
    val totalTokens: Long? = null,
    /** Quota windows (Z.ai-style: 5-hour / weekly / web search). */
    val quotas: List<QuotaWindow> = emptyList(),
    /** Subscription/plan expiry, when reported. */
    val expiryAt: Instant? = null,
    val accessUntil: Instant? = null,
    val accessUnlimited: Boolean = false,
    /** Account meta for cards that show it (NewAPI role/status, z.ai plan name, user id). */
    val planName: String? = null,
    val userRole: String? = null,
    val userStatus: String? = null,
    val accountId: String? = null,
    val fetchedAt: Instant,
    /** DeepSeek: whether the account can still make API calls. */
    val isBalanceAvailable: Boolean = true,
    /** Provider payload kept for debugging/future fields. */
    val rawJson: String,
) {
    data class Balance(
        val currencyCode: String,
        /** Total = granted + topped-up. */
        val total: String,
        val granted: String? = null,
        val toppedUp: String? = null,
    ) {
        val totalDecimal: BigDecimal get() = total.toBigDecimalOrNull() ?: BigDecimal.ZERO
    }

    data class QuotaWindow(
        /** Free-form label key: SESSION_5H, WEEKLY, WEB_SEARCH_MONTHLY (see QuotaKind). */
        val kind: String,
        val used: Long,
        val limit: Long,
        val percentageUsed: Int? = null,
        val resetAt: Instant? = null,
        /** True when this is a count quota (requests/searches), false for tokens. */
        val isCount: Boolean = false,
    )

    /** Spent in the current period, if the provider reports it. */
    val usedDecimal: BigDecimal? get() = usedAmount?.toBigDecimalOrNull()
    val limitDecimal: BigDecimal? get() = limitAmount?.toBigDecimalOrNull()
}

enum class BudgetStatus { NORMAL, WARNING, OVER }

/** Usage math vs the user-configured budget (snapshot stays provider truth; budget is ours). */
data class BudgetComputation(
    val status: BudgetStatus,
    /** 0.. can exceed 100; null when not computable. */
    val usagePercent: Int?,
    /** Remaining against configured monthly budget. */
    val monthlyRemaining: BigDecimal?,
    /** Remaining against user-configured daily budget (only when daily budget set). */
    val dailyRemaining: BigDecimal?,
) {
    companion object {
        fun compute(
            snapshot: BudgetSnapshot?,
            monthlyBudget: BigDecimal?,
            dailyBudget: BigDecimal?,
            alertThresholdPercent: Int,
        ): BudgetComputation {
            val spentMonthly = snapshot?.usedDecimal
            val usagePercent = when {
                monthlyBudget == null || monthlyBudget <= BigDecimal.ZERO || spentMonthly == null -> null
                else -> spentMonthly.multiply(BigDecimal(100)).divide(monthlyBudget, 0, java.math.RoundingMode.HALF_UP).toInt()
            }
            val monthlyRemaining = if (monthlyBudget != null && spentMonthly != null) {
                monthlyBudget - spentMonthly
            } else null
            val dailyRemaining = if (dailyBudget != null && snapshot?.dailySpentAmount != null) {
                dailyBudget - (snapshot.dailySpentAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO)
            } else null
            val status = when {
                (monthlyRemaining != null && monthlyRemaining <= BigDecimal.ZERO) ||
                    (dailyRemaining != null && dailyRemaining <= BigDecimal.ZERO) -> BudgetStatus.OVER
                (usagePercent != null && usagePercent >= alertThresholdPercent) -> BudgetStatus.WARNING
                else -> BudgetStatus.NORMAL
            }
            return BudgetComputation(status, usagePercent, monthlyRemaining, dailyRemaining)
        }
    }
}

/** Normalized service panel snapshot, persisted as JSON in `ServiceConnection.snapshotJson`. */
sealed interface ServiceSnapshot

/** Parsed `subscription-userinfo` values + derived display numbers. */
data class ClashSnapshot(
    /** Has server-provided traffic stats; false = link valid but no stats. */
    val hasTrafficInfo: Boolean = true,
    val uploadBytes: Long = 0,
    val downloadBytes: Long = 0,
    /** null = no cap reported. */
    val totalBytes: Long? = null,
    /** null = no expiry reported. */
    val expireAt: Instant? = null,
    val fetchedAt: Instant,
) : ServiceSnapshot {
    val usedBytes: Long get() = uploadBytes + downloadBytes
    val remainingBytes: Long? get() = totalBytes?.let { (it - usedBytes).coerceAtLeast(0) }
    val usagePercent: Int? get() = totalBytes?.takeIf { it > 0 }?.let {
        ((usedBytes * 100) / it).toInt().coerceIn(0, 100)
    }
    val expired: Boolean get() = expireAt != null && expireAt <= fetchedAt
}

data class EmbySnapshot(
    val serverName: String? = null,
    val version: String? = null,
    val operatingSystem: String? = null,
    val userName: String? = null,
    val isAdmin: Boolean = false,
    val lastActiveAt: Instant? = null,
    val latencyMs: Long? = null,
    val reachable: Boolean = true,
    val fetchedAt: Instant,
) : ServiceSnapshot
