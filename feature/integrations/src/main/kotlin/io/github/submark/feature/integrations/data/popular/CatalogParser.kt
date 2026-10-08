package io.github.submark.feature.integrations.data.popular

import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.IconType
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionPrefill
import io.github.submark.core.model.SystemCategory
import kotlinx.serialization.json.Json
import java.math.BigDecimal

/** Pure parsing/mapping between the repository catalogue JSON and SubMark models. */
object CatalogParser {

    private val json = Json { ignoreUnknownKeys = true }

    /** @return null when [raw] is not a valid catalogue document. */
    fun parse(raw: String): PopularCatalog? =
        try {
            json.decodeFromString<PopularCatalog>(raw)
        } catch (e: Exception) {
            null
        }

    /** Merges catalogues from several repositories; first catalogue wins on duplicate entry ids. */
    fun merge(catalogs: List<PopularCatalog>): List<CatalogEntry> {
        val seen = LinkedHashSet<String>()
        val out = ArrayList<CatalogEntry>()
        for (catalog in catalogs) {
            for (entry in catalog.subscriptions) {
                if (seen.add(entry.id)) out.add(entry)
            }
        }
        return out
    }

    fun toSystemCategory(category: RepoCategory): SystemCategory = when (category) {
        RepoCategory.VIDEO -> SystemCategory.VIDEO
        RepoCategory.MUSIC -> SystemCategory.MUSIC
        RepoCategory.PRODUCTIVITY -> SystemCategory.PRODUCTIVITY
        RepoCategory.UTILITY -> SystemCategory.UTILITY
        RepoCategory.AI -> SystemCategory.AI
        RepoCategory.GAMING -> SystemCategory.GAMING
        RepoCategory.NEWS -> SystemCategory.NEWS
        RepoCategory.LIFESTYLE -> SystemCategory.LIFESTYLE
        RepoCategory.ENTERTAINMENT -> SystemCategory.ENTERTAINMENT
        RepoCategory.OTHER -> SystemCategory.OTHER
    }

    fun toBillingCycle(cycle: RepoBillingCycle): BillingCycle = when (cycle) {
        RepoBillingCycle.MONTHLY -> BillingCycle.MONTHLY
        RepoBillingCycle.QUARTERLY -> BillingCycle.QUARTERLY
        RepoBillingCycle.SEMI_ANNUALLY -> BillingCycle.SEMIANNUALLY
        RepoBillingCycle.ANNUALLY -> BillingCycle.ANNUALLY
        RepoBillingCycle.CUSTOM -> BillingCycle.CUSTOM
    }

    /**
     * Maps a catalogue entry + chosen price to an add-form prefill.
     * Permanent prices become LIFETIME purchases; bundles get MAIN/CHILD structure via children.
     */
    fun toPrefill(
        entry: CatalogEntry,
        option: PricingOption,
        bundleMode: BundlePrefillMode = BundlePrefillMode.FULL_BUNDLE,
    ): SubscriptionPrefill {
        val base = SubscriptionPrefill(
            name = entry.name,
            kind = if (option.isPermanent) SubscriptionKind.LIFETIME else SubscriptionKind.REGULAR,
            price = BigDecimal.valueOf(option.price),
            currencyCode = option.currency.uppercase(),
            billingCycle = if (option.isPermanent) null else toBillingCycle(option.billingCycle),
            systemCategory = toSystemCategory(entry.category),
            iconType = entry.icon?.takeIf { it.startsWith("http") }?.let { IconType.URL },
            iconValue = entry.icon?.takeIf { it.startsWith("http") },
            website = entry.website,
            appStoreId = entry.appStoreId,
            note = entry.description,
            tags = entry.tags,
        )
        if (!entry.isBundle || bundleMode == BundlePrefillMode.MAIN_ONLY) return base
        val children = entry.bundledSubscriptions.orEmpty().map { child ->
            SubscriptionPrefill(
                name = child.name,
                kind = if (child.isPermanent) SubscriptionKind.LIFETIME else SubscriptionKind.REGULAR,
                price = BigDecimal.valueOf(child.price),
                currencyCode = child.currency.uppercase(),
                billingCycle = if (child.isPermanent) null else toBillingCycle(child.billingCycle),
                systemCategory = toSystemCategory(child.category),
                iconType = child.icon?.takeIf { it.startsWith("http") }?.let { IconType.URL },
                iconValue = child.icon?.takeIf { it.startsWith("http") },
                website = child.website,
                appStoreId = child.appStoreId,
                note = child.description,
                tags = child.tags,
                originalPrice = child.originalPrice?.let { BigDecimal.valueOf(it) },
            )
        }
        return base.copy(children = children)
    }

    /** Sum of `originalPrice` (fallback `price`) over bundle children, when currencies all match [currencyCode]. */
    fun bundleTotalValue(entry: CatalogEntry, currencyCode: String): BigDecimal? {
        val children = entry.bundledSubscriptions.orEmpty()
        if (children.isEmpty()) return null
        if (!children.all { it.currency.equals(currencyCode, ignoreCase = true) }) return null
        return children.fold(BigDecimal.ZERO) { acc, c -> acc + BigDecimal.valueOf(c.originalPrice ?: c.price) }
    }

    /** Sum of actual prices (what the user pays) over bundle children in one currency. */
    fun bundleActualTotal(entry: CatalogEntry, currencyCode: String): BigDecimal? {
        val children = entry.bundledSubscriptions.orEmpty()
        if (children.isEmpty()) return null
        if (!children.all { it.currency.equals(currencyCode, ignoreCase = true) }) return null
        return children.fold(BigDecimal.ZERO) { acc, c -> acc + BigDecimal.valueOf(c.price) }
    }
}

enum class BundlePrefillMode { MAIN_ONLY, FULL_BUNDLE }
