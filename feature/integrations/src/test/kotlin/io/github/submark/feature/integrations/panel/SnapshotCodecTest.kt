package io.github.submark.feature.integrations.panel

import com.google.common.truth.Truth.assertThat
import io.github.submark.feature.integrations.panel.data.BudgetSnapshot
import io.github.submark.feature.integrations.panel.data.ClashSnapshot
import io.github.submark.feature.integrations.panel.data.EmbySnapshot
import io.github.submark.feature.integrations.panel.data.SnapshotCodec
import org.junit.Test
import java.time.Instant

class SnapshotCodecTest {

    private val now: Instant = Instant.ofEpochSecond(1_700_000_000)

    @Test
    fun budgetRoundTrip() {
        val snapshot = BudgetSnapshot(
            serviceType = "NEWAPI",
            balances = listOf(
                BudgetSnapshot.Balance("CNY", "110.00", "10.00", "100.00"),
                BudgetSnapshot.Balance("USD", "0.50"),
            ),
            usedAmount = "2.5",
            limitAmount = "10",
            currencyCode = "USD",
            dailySpentAmount = "0.37",
            totalRequests = 231,
            totalTokens = null,
            quotas = listOf(
                BudgetSnapshot.QuotaWindow("SESSION_5H", used = 12, limit = 800, percentageUsed = 15, resetAt = now),
                BudgetSnapshot.QuotaWindow("WEB_SEARCH_MONTHLY", used = 1433, limit = 4000, isCount = true),
            ),
            expiryAt = now.plusSeconds(86400),
            accessUnlimited = false,
            planName = "GLM Coding Max",
            userRole = "user",
            userStatus = "enabled",
            accountId = "12",
            fetchedAt = now,
            isBalanceAvailable = false,
            rawJson = "{\"a\":1}",
        )
        val decoded = SnapshotCodec.decodeBudget(SnapshotCodec.encodeBudget(snapshot))
        assertThat(decoded).isEqualTo(snapshot)
    }

    @Test
    fun clashRoundTrip_andUnknownTypeIsNull() {
        val clash = ClashSnapshot(
            hasTrafficInfo = true,
            uploadBytes = 1,
            downloadBytes = 2,
            totalBytes = 10,
            expireAt = now,
            fetchedAt = now,
        )
        assertThat(SnapshotCodec.decodeService(SnapshotCodec.encodeClash(clash))).isEqualTo(clash)
        assertThat(SnapshotCodec.decodeService("{}")).isNull()
        assertThat(SnapshotCodec.decodeService("not json")).isNull()
    }

    @Test
    fun embyRoundTrip() {
        val emby = EmbySnapshot(
            serverName = "Home",
            version = "4.8.3.0",
            operatingSystem = "Linux",
            userName = "alice",
            isAdmin = true,
            lastActiveAt = now,
            latencyMs = 42,
            reachable = true,
            fetchedAt = now,
        )
        assertThat(SnapshotCodec.decodeService(SnapshotCodec.encodeEmby(emby))).isEqualTo(emby)
    }

    @Test
    fun decodeBudgetGarbageIsNull() {
        assertThat(SnapshotCodec.decodeBudget("")).isNull()
        assertThat(SnapshotCodec.decodeBudget("[1,2,3]")).isNull()
        assertThat(SnapshotCodec.decodeBudget("{\"st\":\"X\"}")).isNull() // fetchedAt missing
    }
}
