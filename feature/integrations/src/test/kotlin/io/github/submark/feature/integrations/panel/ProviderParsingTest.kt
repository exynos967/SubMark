package io.github.submark.feature.integrations.panel

import com.google.common.truth.Truth.assertThat
import io.github.submark.feature.integrations.panel.data.PanelErrorReason
import io.github.submark.feature.integrations.panel.data.PanelFetchException
import io.github.submark.feature.integrations.panel.data.PanelHttp
import io.github.submark.feature.integrations.panel.data.budget.DeepSeekClient
import io.github.submark.feature.integrations.panel.data.budget.NewApiClient
import io.github.submark.feature.integrations.panel.data.budget.PackyClient
import io.github.submark.feature.integrations.panel.data.budget.QuotaKind
import io.github.submark.feature.integrations.panel.data.budget.ZaiClient
import io.github.submark.feature.integrations.panel.data.service.EmbyClient
import io.github.submark.feature.integrations.panel.data.service.SubscriptionUserinfo
import okhttp3.OkHttpClient
import org.junit.Test
import java.time.Instant

/** Parsing tests use the documented/captured response samples recorded in each client's KDoc. */
class ProviderParsingTest {

    private val now: Instant = Instant.ofEpochSecond(1_700_000_000)
    private val http = PanelHttp(OkHttpClient())

    // ---- DeepSeek ----

    @Test
    fun deepSeek_parsesDocumentedSample() {
        val raw = """
            {
              "is_available": true,
              "balance_infos": [
                { "currency": "CNY", "total_balance": "110.00", "granted_balance": "10.00", "topped_up_balance": "100.00" },
                { "currency": "USD", "total_balance": "0.00", "granted_balance": "0.00", "topped_up_balance": "0.00" }
              ]
            }
        """.trimIndent()
        val snapshot = DeepSeekClient(http).parse(raw, now)
        assertThat(snapshot.isBalanceAvailable).isTrue()
        assertThat(snapshot.balances).hasSize(2)
        val cny = snapshot.balances.first { it.currencyCode == "CNY" }
        assertThat(cny.total).isEqualTo("110.00")
        assertThat(cny.granted).isEqualTo("10.00")
        assertThat(cny.toppedUp).isEqualTo("100.00")
        assertThat(snapshot.currencyCode).isEqualTo("USD")
    }

    @Test
    fun deepSeek_emptyBalanceList_failsParse() {
        val raw = """{"is_available": false, "balance_infos": []}"""
        val error = runCatching { DeepSeekClient(http).parse(raw, now) }.exceptionOrNull()
        assertThat(error).isInstanceOf(PanelFetchException::class.java)
        assertThat((error as PanelFetchException).reason).isEqualTo(PanelErrorReason.PARSE)
    }

    // ---- NewAPI / V-API ----

    @Test
    fun newApi_parsesUserSelfResponse() {
        val raw = """
            {
              "success": true,
              "message": "",
              "data": {
                "id": 12, "username": "alice", "role": 1, "status": 1,
                "group": "default", "quota": 3750000, "used_quota": 1250000, "request_count": 231
              }
            }
        """.trimIndent()
        val snapshot = NewApiClient(http).parse(raw, now)
        // used = 1250000 / 500000 = 2.5 USD; total = (1250000+3750000)/500000 = 10 USD
        assertThat(snapshot.usedAmount).isEqualTo("2.500000")
        assertThat(snapshot.limitAmount).isEqualTo("10.000000")
        assertThat(snapshot.totalRequests).isEqualTo(231)
        assertThat(snapshot.userRole).isEqualTo("user")
        assertThat(snapshot.userStatus).isEqualTo("enabled")
        assertThat(snapshot.accountId).isEqualTo("12")
    }

    @Test
    fun newApi_missingQuotaFields_failsParse() {
        val raw = """{"success": true, "data": {"id": 1, "username": "x"}}"""
        val error = runCatching { NewApiClient(http).parse(raw, now) }.exceptionOrNull()
        assertThat((error as? PanelFetchException)?.reason).isEqualTo(PanelErrorReason.PARSE)
    }

    @Test
    fun newApi_quotaConversion() {
        assertThat(NewApiClient.unitsToUsd(500_000).toPlainString()).isEqualTo("1.000000")
        assertThat(NewApiClient.unitsToUsd(1).toPlainString()).isEqualTo("0.000002")
        assertThat(NewApiClient.endpoint("https://api.gpt.ge/")).isEqualTo("https://api.gpt.ge/api/user/self")
    }

    // ---- Packy ----

    @Test
    fun packy_parsesUserInfoResponse_stringOrNumber() {
        val raw = """
            {
              "daily_budget_usd": 5,
              "daily_spent_usd": "0.37",
              "monthly_budget_usd": "80.00",
              "monthly_spent_usd": 12.4,
              "opus_enabled": true
            }
        """.trimIndent()
        val snapshot = PackyClient(http).parse(raw, now)
        assertThat(snapshot.limitAmount).isEqualTo("80.00")
        assertThat(snapshot.dailySpentAmount).isEqualTo("0.37")
        assertThat(snapshot.usedAmount).isEqualTo("12.4")
        assertThat(snapshot.expiryAt).isNull()
    }

    @Test
    fun packy_readsDataWrappedPayloadAndExpiry() {
        val raw = """
            { "data": { "monthly_budget_usd": 10, "monthly_spent_usd": 1, "plan_expires_at": "2026-11-01 00:00:00" } }
        """.trimIndent()
        val snapshot = PackyClient(http).parse(raw, now)
        assertThat(snapshot.limitAmount).isEqualTo("10")
        assertThat(snapshot.expiryAt).isNotNull()
    }

    @Test
    fun packy_unrelatedPayload_failsParse() {
        val raw = """{"user": {"name": "alice"}}"""
        val error = runCatching { PackyClient(http).parse(raw, now) }.exceptionOrNull()
        assertThat((error as? PanelFetchException)?.reason).isEqualTo(PanelErrorReason.PARSE)
    }

    @Test
    fun packy_endpointValidation() {
        assertThat(PackyClient.endpoint("https://www.packycode.com/"))
            .isEqualTo("https://www.packycode.com/api/backend/users/info")
        assertThat(PackyClient.endpoint("  ")).isNull()
        assertThat(PackyClient.endpoint("files://evil")).isNull()
    }

    // ---- Z.ai ----

    @Test
    fun zai_parsesCapturedSamples() {
        val quotaRaw = """
            { "code": 200, "success": true, "data": { "limits": [
              { "type": "TOKENS_LIMIT", "unit": 3, "number": 5, "usage": 800000000,
                "currentValue": 127694464, "remaining": 672305536, "percentage": 15,
                "nextResetTime": 1770648402389 },
              { "type": "TIME_LIMIT", "unit": 5, "number": 1, "usage": 4000, "currentValue": 1828,
                "remaining": 2172, "percentage": 45,
                "usageDetails": [ { "modelCode": "search-prime", "usage": 1433 } ] }
            ] } }
        """.trimIndent()
        val subRaw = """
            { "code": 200, "success": true, "data": [
              { "productName": "GLM Coding Max", "status": "VALID", "nextRenewTime": "2026-03-12" }
            ] }
        """.trimIndent()
        val result = ZaiClient(http).parse(quotaRaw, subRaw, now)
        assertThat(result.hasCodingPlan).isTrue()
        assertThat(result.snapshot.planName).isEqualTo("GLM Coding Max")
        assertThat(result.snapshot.quotas).hasSize(2)
        val session = result.snapshot.quotas.first { it.kind == QuotaKind.SESSION_5H }
        assertThat(session.used).isEqualTo(127694464)
        assertThat(session.limit).isEqualTo(800000000)
        assertThat(session.percentageUsed).isEqualTo(15)
        assertThat(session.resetAt).isEqualTo(Instant.ofEpochMilli(1770648402389))
        val search = result.snapshot.quotas.first { it.kind == QuotaKind.WEB_SEARCH_MONTHLY }
        assertThat(search.isCount).isTrue()
        assertThat(search.used).isEqualTo(1828)
    }

    @Test
    fun zai_subscriptionListEmpty_stillParsesQuota() {
        val quotaRaw = """
            { "code": 200, "data": { "limits": [
              { "type": "TOKENS_LIMIT", "unit": 6, "number": 7, "usage": 100, "currentValue": 40, "percentage": 40 }
            ] } }
        """.trimIndent()
        val subRaw = """{ "code": 200, "data": [] }"""
        val result = ZaiClient(http).parse(quotaRaw, subRaw, now)
        assertThat(result.snapshot.planName).isNull()
        // weekly window matched by unit 6
        assertThat(result.snapshot.quotas.single().kind).isEqualTo(QuotaKind.WEEKLY)
    }

    // ---- Emby ----

    @Test
    fun emby_authResponse_parsesTokenUserPolicy() {
        val raw = """
            {
              "User": {
                "Name": "alice",
                "Policy": { "IsAdministrator": true },
                "LastActivityDate": "2024-02-25T13:45:12.1234567Z"
              },
              "AccessToken": "a75f6286aa0c48a98ef076f9d9babc21",
              "ServerId": "srv"
            }
        """.trimIndent()
        val auth = EmbyClient(http).parseAuth(raw)
        assertThat(auth.token).isEqualTo("a75f6286aa0c48a98ef076f9d9babc21")
        assertThat(auth.userName).isEqualTo("alice")
        assertThat(auth.isAdmin).isTrue()
        assertThat(auth.lastActiveAt).isNotNull()
    }

    @Test
    fun emby_systemInfo_parsesIntoSnapshot() {
        val infoRaw = """{ "ServerName": "Home", "Version": "4.8.3.0", "OperatingSystem": "Linux" }"""
        val snapshot = EmbyClient(http).parse(infoRaw, "alice", true, null, 42, now)
        assertThat(snapshot.serverName).isEqualTo("Home")
        assertThat(snapshot.version).isEqualTo("4.8.3.0")
        assertThat(snapshot.operatingSystem).isEqualTo("Linux")
        assertThat(snapshot.userName).isEqualTo("alice")
        assertThat(snapshot.isAdmin).isTrue()
        assertThat(snapshot.latencyMs).isEqualTo(42)
        assertThat(snapshot.reachable).isTrue()
    }

    @Test
    fun emby_baseUrlNormalization() {
        assertThat(EmbyClient.normalizeBase("http://192.168.1.10:8096/")).isEqualTo("http://192.168.1.10:8096")
        assertThat(EmbyClient.normalizeBase("  ")).isNull()
        assertThat(EmbyClient.normalizeBase("emby.local")).isNull()
    }

    // ---- subscription-userinfo ----

    @Test
    fun userinfo_parsesStandardHeader() {
        val snapshot = SubscriptionUserinfo.toSnapshot(
            "upload=1073741824; download=5368709120; total=109951162777; expire=1735689600",
            now,
        )
        assertThat(snapshot.hasTrafficInfo).isTrue()
        assertThat(snapshot.uploadBytes).isEqualTo(1073741824)
        assertThat(snapshot.downloadBytes).isEqualTo(5368709120)
        assertThat(snapshot.usedBytes).isEqualTo(6442450944)
        assertThat(snapshot.totalBytes).isEqualTo(109951162777)
        assertThat(snapshot.remainingBytes).isEqualTo(109951162777 - 6442450944)
        assertThat(snapshot.usagePercent).isEqualTo(5)
        assertThat(snapshot.expireAt).isEqualTo(Instant.ofEpochSecond(1735689600))
        assertThat(snapshot.expired).isFalse()
    }

    @Test
    fun userinfo_missingHeader_noTrafficInfo() {
        assertThat(SubscriptionUserinfo.toSnapshot(null, now).hasTrafficInfo).isFalse()
        assertThat(SubscriptionUserinfo.toSnapshot("   ", now).hasTrafficInfo).isFalse()
    }

    @Test
    fun userinfo_garbageAndMissingKeys_areTolerated() {
        val parsed = SubscriptionUserinfo.parse(";;;foo=bar;upload=oops;download=10")
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.download).isEqualTo(10)
        assertThat(parsed.upload).isNull()
        val snapshot = SubscriptionUserinfo.toSnapshot("expire=0; total=abc", now)
        assertThat(snapshot.hasTrafficInfo).isFalse()
    }

    @Test
    fun userinfo_expiredFlag() {
        val snapshot = SubscriptionUserinfo.toSnapshot("upload=1; total=2; expire=1000", now)
        assertThat(snapshot.expired).isTrue()
    }
}
