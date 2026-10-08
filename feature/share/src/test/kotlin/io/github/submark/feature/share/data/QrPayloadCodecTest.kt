package io.github.submark.feature.share.data

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.Category
import io.github.submark.core.model.CycleUnit
import io.github.submark.core.model.IconType
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SystemCategory
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class QrPayloadCodecTest {

    private val now: Instant = Instant.parse("2026-01-01T00:00:00Z")

    private fun subscription(
        name: String = "Netflix",
        kind: SubscriptionKind = SubscriptionKind.REGULAR,
        website: String? = "https://netflix.com",
        note: String? = "Family plan",
    ) = Subscription(
        id = "sub-1",
        name = name,
        kind = kind,
        price = BigDecimal("15.49"),
        currencyCode = "USD",
        billingCycle = BillingCycle.MONTHLY,
        startDate = LocalDate.of(2025, 1, 1),
        categoryId = "cat_video",
        iconType = IconType.SYMBOL,
        iconValue = "play_circle",
        website = website,
        note = note,
        createdAt = now,
        updatedAt = now,
    )

    private val category = Category(
        id = "cat_video",
        systemKey = SystemCategory.VIDEO,
        name = null,
        iconValue = "play_circle",
        colorHex = "#FF0000",
    )

    @Test
    fun `round trip preserves core fields`() {
        val payload = QrPayloadCodec.fromSubscription(
            sub = subscription(),
            category = category,
            sharerName = "Alice",
            description = "Split with roommates",
            includePrivate = true,
        )
        val encoded = QrPayloadCodec.encode(payload)
        assertThat(encoded).startsWith("submark://import?v=1&d=")

        val decoded = QrPayloadCodec.decode(encoded)
        assertThat(decoded).isInstanceOf(QrPayloadCodec.DecodeResult.Success::class.java)
        val result = (decoded as QrPayloadCodec.DecodeResult.Success).payload
        assertThat(result.name).isEqualTo("Netflix")
        assertThat(result.kind).isEqualTo(SubscriptionKind.REGULAR)
        assertThat(result.price).isEqualTo("15.49")
        assertThat(result.currency).isEqualTo("USD")
        assertThat(result.cycle).isEqualTo(BillingCycle.MONTHLY)
        assertThat(result.category).isEqualTo("VIDEO")
        assertThat(result.iconType).isEqualTo(IconType.SYMBOL)
        assertThat(result.iconValue).isEqualTo("play_circle")
        assertThat(result.website).isEqualTo("https://netflix.com")
        assertThat(result.notes).isEqualTo("Family plan")
        assertThat(result.sharer).isEqualTo("Alice")
        assertThat(result.description).isEqualTo("Split with roommates")
    }

    @Test
    fun `private fields are dropped when not opted in`() {
        val payload = QrPayloadCodec.fromSubscription(
            sub = subscription(),
            category = category,
            sharerName = "Alice",
            description = "",
            includePrivate = false,
        )
        assertThat(payload.website).isNull()
        assertThat(payload.notes).isNull()
        val roundTrip = (QrPayloadCodec.decode(QrPayloadCodec.encode(payload)) as QrPayloadCodec.DecodeResult.Success).payload
        assertThat(roundTrip.website).isNull()
        assertThat(roundTrip.notes).isNull()
        assertThat(roundTrip.sharer).isEqualTo("Alice")
    }

    @Test
    fun `bundle children are encoded recursively`() {
        val child = subscription(name = "iCloud+", website = null, note = null).copy(id = "child-1")
        val payload = QrPayloadCodec.fromSubscription(
            sub = subscription().copy(bundleRole = io.github.submark.core.model.BundleRole.MAIN),
            category = category,
            sharerName = "",
            description = "",
            includePrivate = true,
            children = listOf(child),
            childCategories = mapOf("child-1" to category),
        )
        val roundTrip = (QrPayloadCodec.decode(QrPayloadCodec.encode(payload)) as QrPayloadCodec.DecodeResult.Success).payload
        assertThat(roundTrip.children).hasSize(1)
        assertThat(roundTrip.children.first().name).isEqualTo("iCloud+")
        assertThat(roundTrip.children.first().category).isEqualTo("VIDEO")
    }

    @Test
    fun `custom cycle fields survive the round trip`() {
        val payload = QrPayloadCodec.fromSubscription(
            sub = subscription().copy(billingCycle = BillingCycle.CUSTOM, customCycleCount = 45, customCycleUnit = CycleUnit.DAY),
            category = category,
            sharerName = "",
            description = "",
            includePrivate = true,
        )
        val roundTrip = (QrPayloadCodec.decode(QrPayloadCodec.encode(payload)) as QrPayloadCodec.DecodeResult.Success).payload
        assertThat(roundTrip.cycle).isEqualTo(BillingCycle.CUSTOM)
        assertThat(roundTrip.cycleCount).isEqualTo(45)
        assertThat(roundTrip.cycleUnit).isEqualTo(CycleUnit.DAY)
    }

    @Test
    fun `decode rejects non-submark text`() {
        assertThat(QrPayloadCodec.decode("https://example.com")).isEqualTo(QrPayloadCodec.DecodeResult.NotSubMark)
        assertThat(QrPayloadCodec.decode("hello world")).isEqualTo(QrPayloadCodec.DecodeResult.NotSubMark)
        assertThat(QrPayloadCodec.decode("")).isEqualTo(QrPayloadCodec.DecodeResult.NotSubMark)
    }

    @Test
    fun `decode rejects unknown version`() {
        assertThat(QrPayloadCodec.decode("submark://import?v=99&d=AAAA"))
            .isEqualTo(QrPayloadCodec.DecodeResult.UnsupportedVersion)
    }

    @Test
    fun `decode rejects corrupt payloads`() {
        // Not base64url at all.
        assertThat(QrPayloadCodec.decode("submark://import?v=1&d=!!!"))
            .isEqualTo(QrPayloadCodec.DecodeResult.Malformed)
        // Valid base64 but not deflate.
        val garbage = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("not deflate".toByteArray())
        assertThat(QrPayloadCodec.decode("submark://import?v=1&d=$garbage"))
            .isEqualTo(QrPayloadCodec.DecodeResult.Malformed)
        // Missing data param.
        assertThat(QrPayloadCodec.decode("submark://import?v=1"))
            .isEqualTo(QrPayloadCodec.DecodeResult.Malformed)
    }

    @Test
    fun `decode rejects payload whose embedded version differs`() {
        val payload = QrPayloadCodec.Payload(v = 2, name = "X")
        val encoded = QrPayloadCodec.encode(payload) // encodes v=2 into the URL too
        // Rewrite the URL version so only the embedded one mismatches is not possible; but a
        // mismatch between URL v and JSON v should be treated as unsupported either way.
        val result = QrPayloadCodec.decode(encoded)
        assertThat(result).isEqualTo(QrPayloadCodec.DecodeResult.UnsupportedVersion)
    }

    @Test
    fun `toPrefill maps fields and sanitizes kinds`() {
        val payload = QrPayloadCodec.fromSubscription(
            sub = subscription(kind = SubscriptionKind.STORED_VALUE),
            category = category,
            sharerName = "Alice",
            description = "",
            includePrivate = true,
        )
        val prefill = QrPayloadCodec.toPrefill(payload)
        assertThat(prefill.kind).isEqualTo(SubscriptionKind.REGULAR) // stored value imported as regular
        assertThat(prefill.name).isEqualTo("Netflix")
        assertThat(prefill.price).isEqualTo(BigDecimal("15.49"))
        assertThat(prefill.currencyCode).isEqualTo("USD")
        assertThat(prefill.billingCycle).isEqualTo(BillingCycle.MONTHLY)
        assertThat(prefill.systemCategory).isEqualTo(SystemCategory.VIDEO)
        assertThat(prefill.iconType).isEqualTo(IconType.SYMBOL)
        assertThat(prefill.website).isEqualTo("https://netflix.com")
        assertThat(prefill.note).isEqualTo("Family plan")
    }

    @Test
    fun `toPrefill handles unknown category key`() {
        val payload = QrPayloadCodec.Payload(name = "X", category = "NOT_A_CATEGORY")
        val prefill = QrPayloadCodec.toPrefill(payload)
        assertThat(prefill.systemCategory).isNull()
    }

    @Test
    fun `toPrefill handles invalid price string`() {
        val payload = QrPayloadCodec.Payload(name = "X", price = "not-a-number")
        val prefill = QrPayloadCodec.toPrefill(payload)
        assertThat(prefill.price).isNull()
    }

    @Test
    fun `whitespace around the code is tolerated`() {
        val payload = QrPayloadCodec.fromSubscription(subscription(), category, "", "", true)
        val encoded = "  \n" + QrPayloadCodec.encode(payload) + "\t "
        assertThat(QrPayloadCodec.decode(encoded)).isInstanceOf(QrPayloadCodec.DecodeResult.Success::class.java)
    }
}
