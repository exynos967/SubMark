package io.github.submark.feature.integrations.data.popular

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.IconType
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SystemCategory
import org.junit.Test
import java.math.BigDecimal

class CatalogParserTest {

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private fun parseOrThrow(raw: String): PopularCatalog =
        json.decodeFromString<PopularCatalog>(raw)

    private val sample = """
        {
          "version": "1.3",
          "lastUpdated": "2026-01-01T00:00:00Z",
          "subscriptions": [
            {
              "id": "netflix",
              "name": "Netflix",
              "icon": "https://cdn.example.com/netflix.png",
              "category": "video",
              "commonPrices": [
                { "price": 15.49, "currency": "USD", "billingCycle": "monthly", "isPermanent": false },
                { "price": 149.0, "currency": "USD", "billingCycle": "annually", "isPermanent": false }
              ],
              "tags": ["streaming", "movies"],
              "region": ["US", "Global"],
              "website": "https://netflix.com",
              "appStoreId": "363590051"
            },
            {
              "id": "some-tool",
              "name": "Some Tool",
              "icon": "https://cdn.example.com/tool.png",
              "category": "nonexistent-category",
              "commonPrices": [
                { "price": 9.99, "currency": "usd", "billingCycle": "whenever", "isPermanent": true }
              ],
              "tags": ["utility"],
              "region": ["CN"]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses valid catalogue`() {
        val catalog = parseOrThrow(sample)
        assertThat(catalog).isNotNull()
        assertThat(catalog!!.version).isEqualTo("1.3")
        assertThat(catalog.subscriptions).hasSize(2)
        val netflix = catalog.subscriptions[0]
        assertThat(netflix.commonPrices).hasSize(2)
        assertThat(netflix.tags).containsExactly("streaming", "movies")
    }

    @Test
    fun `debug enum parse`() {
        val c = kotlinx.serialization.json.Json.decodeFromString(RepoCategorySerializer, "\"nonexistent-category\"")
        assertThat(c).isEqualTo(RepoCategory.OTHER)
        assertThat(RepoCategory.of("GAMING")).isEqualTo(RepoCategory.GAMING)
        assertThat(RepoBillingCycle.of("semiannually")).isEqualTo(RepoBillingCycle.SEMI_ANNUALLY)
    }

    @Test
    fun `unknown enum values tolerated`() {
        val catalog = parseOrThrow(sample)
        val tool = catalog.subscriptions[1]
        assertThat(tool.category).isEqualTo(RepoCategory.OTHER)
        assertThat(tool.commonPrices[0].billingCycle).isEqualTo(RepoBillingCycle.CUSTOM)
    }

    @Test
    fun `rejects invalid json`() {
        assertThat(CatalogParser.parse("not json")).isNull()
        assertThat(CatalogParser.parse("{\"foo\":")).isNull()
        assertThat(CatalogParser.parse("[]")).isNull()
    }

    @Test
    fun `tolerates unknown keys`() {
        val raw = """
            {"version":"1.0","extra":42,"subscriptions":[{"id":"a","name":"A","newField":{"x":1},
             "commonPrices":[{"price":1,"currency":"USD","billingCycle":"monthly","isPermanent":false,"unknown":"y"}]}]}
        """.trimIndent()
        val catalog = CatalogParser.parse(raw)
        assertThat(catalog).isNotNull()
        assertThat(catalog!!.subscriptions).hasSize(1)
    }

    @Test
    fun `merge dedupes by id - first wins`() {
        val a = parseOrThrow(sample)
        val b = PopularCatalog(
            subscriptions = listOf(
                CatalogEntry(id = "netflix", name = "Netflix Dup"),
                CatalogEntry(id = "unique", name = "Unique"),
            ),
        )
        val merged = CatalogParser.merge(listOf(a, b))
        assertThat(merged.map { it.id }).containsExactly("netflix", "some-tool", "unique")
        assertThat(merged.first { it.id == "netflix" }.name).isEqualTo("Netflix")
    }

    @Test
    fun `prefill maps regular subscription`() {
        val entry = parseOrThrow(sample).subscriptions[0]
        val option = entry.commonPrices[0]
        val prefill = CatalogParser.toPrefill(entry, option)
        assertThat(prefill.name).isEqualTo("Netflix")
        assertThat(prefill.kind).isEqualTo(SubscriptionKind.REGULAR)
        assertThat(prefill.price).isEqualTo(BigDecimal.valueOf(15.49))
        assertThat(prefill.currencyCode).isEqualTo("USD")
        assertThat(prefill.billingCycle).isEqualTo(BillingCycle.MONTHLY)
        assertThat(prefill.systemCategory).isEqualTo(SystemCategory.VIDEO)
        assertThat(prefill.iconType).isEqualTo(IconType.URL)
        assertThat(prefill.website).isEqualTo("https://netflix.com")
        assertThat(prefill.appStoreId).isEqualTo("363590051")
        assertThat(prefill.tags).containsExactly("streaming", "movies")
        assertThat(prefill.children).isEmpty()
    }

    @Test
    fun `prefill maps permanent price to lifetime`() {
        val entry = parseOrThrow(sample).subscriptions[1]
        val prefill = CatalogParser.toPrefill(entry, entry.commonPrices[0])
        assertThat(prefill.kind).isEqualTo(SubscriptionKind.LIFETIME)
        assertThat(prefill.billingCycle).isNull()
        assertThat(prefill.currencyCode).isEqualTo("USD") // upper-cased
        assertThat(prefill.systemCategory).isEqualTo(SystemCategory.OTHER) // unknown → Other
    }

    @Test
    fun `bundle prefill - full bundle includes children, main only does not`() {
        val bundle = CatalogEntry(
            id = "bundle",
            name = "Bundle",
            icon = "https://x/icon.png",
            category = RepoCategory.ENTERTAINMENT,
            commonPrices = listOf(PricingOption(30.0, "USD", RepoBillingCycle.MONTHLY)),
            tags = listOf("bundle"),
            region = listOf("Global"),
            isBundle = true,
            bundledSubscriptions = listOf(
                BundledSubscription(
                    name = "Music", price = 10.0, originalPrice = 12.0, currency = "USD",
                    billingCycle = RepoBillingCycle.MONTHLY, tags = listOf("music"),
                ),
                BundledSubscription(
                    name = "Cloud", price = 25.0, currency = "USD",
                    billingCycle = RepoBillingCycle.ANNUALLY, tags = emptyList(),
                ),
            ),
        )
        val option = bundle.commonPrices[0]
        val full = CatalogParser.toPrefill(bundle, option, BundlePrefillMode.FULL_BUNDLE)
        assertThat(full.children).hasSize(2)
        assertThat(full.children[0].name).isEqualTo("Music")
        assertThat(full.children[0].originalPrice).isEqualTo(BigDecimal.valueOf(12.0))
        assertThat(full.children[1].billingCycle).isEqualTo(BillingCycle.ANNUALLY)

        val mainOnly = CatalogParser.toPrefill(bundle, option, BundlePrefillMode.MAIN_ONLY)
        assertThat(mainOnly.children).isEmpty()
    }

    @Test
    fun `bundle totals and savings`() {
        val bundle = CatalogEntry(
            id = "bundle",
            name = "Bundle",
            category = RepoCategory.OTHER,
            commonPrices = emptyList(),
            tags = emptyList(),
            region = emptyList(),
            isBundle = true,
            bundledSubscriptions = listOf(
                BundledSubscription("A", price = 5.0, originalPrice = 8.0, currency = "USD", billingCycle = RepoBillingCycle.MONTHLY),
                BundledSubscription("B", price = 5.0, currency = "USD", billingCycle = RepoBillingCycle.MONTHLY),
            ),
        )
        assertThat(CatalogParser.bundleTotalValue(bundle, "USD")).isEqualTo(BigDecimal("13.0"))
        assertThat(CatalogParser.bundleActualTotal(bundle, "USD")).isEqualTo(BigDecimal("10.0"))
        // Mixed currencies → null.
        val mixed = bundle.copy(
            bundledSubscriptions = bundle.bundledSubscriptions!! +
                BundledSubscription("C", price = 1.0, currency = "EUR", billingCycle = RepoBillingCycle.MONTHLY),
        )
        assertThat(CatalogParser.bundleTotalValue(mixed, "USD")).isNull()
    }
}
