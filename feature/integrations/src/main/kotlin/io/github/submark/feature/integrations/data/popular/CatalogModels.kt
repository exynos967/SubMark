package io.github.submark.feature.integrations.data.popular

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Category of a catalogue entry; unknown values from a repository map to [OTHER]. */
enum class RepoCategory {
    VIDEO, MUSIC, PRODUCTIVITY, UTILITY, AI, GAMING, NEWS, LIFESTYLE, ENTERTAINMENT, OTHER;

    companion object {
        fun of(raw: String?): RepoCategory =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: OTHER
    }
}

object RepoCategorySerializer : KSerializer<RepoCategory> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("RepoCategory", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: RepoCategory) = encoder.encodeString(value.name.lowercase())
    override fun deserialize(decoder: Decoder): RepoCategory = RepoCategory.of(decoder.decodeString())
}

/** Billing cycle of a pricing option; unknown values map to [CUSTOM]. */
enum class RepoBillingCycle {
    MONTHLY, QUARTERLY, SEMI_ANNUALLY, ANNUALLY, CUSTOM;

    companion object {
        fun of(raw: String?): RepoBillingCycle = when (raw?.trim()?.lowercase()) {
            "monthly" -> MONTHLY
            "quarterly" -> QUARTERLY
            "semiannually", "semiannual", "halfyearly" -> SEMI_ANNUALLY
            "annually", "yearly" -> ANNUALLY
            else -> CUSTOM
        }
    }
}

object RepoBillingCycleSerializer : KSerializer<RepoBillingCycle> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("RepoBillingCycle", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: RepoBillingCycle) =
        encoder.encodeString(
            when (value) {
                RepoBillingCycle.MONTHLY -> "monthly"
                RepoBillingCycle.QUARTERLY -> "quarterly"
                RepoBillingCycle.SEMI_ANNUALLY -> "semiAnnually"
                RepoBillingCycle.ANNUALLY -> "annually"
                RepoBillingCycle.CUSTOM -> "custom"
            },
        )

    override fun deserialize(decoder: Decoder): RepoBillingCycle = RepoBillingCycle.of(decoder.decodeString())
}

/** One purchasable plan of a catalogue entry. */
@Serializable
data class PricingOption(
    val price: Double,
    val currency: String,
    @Serializable(with = RepoBillingCycleSerializer::class)
    val billingCycle: RepoBillingCycle = RepoBillingCycle.CUSTOM,
    val isPermanent: Boolean = false,
    val description: String? = null,
)

/** A member service of a bundle entry. */
@Serializable
data class BundledSubscription(
    val name: String,
    val icon: String? = null,
    @Serializable(with = RepoCategorySerializer::class)
    val category: RepoCategory = RepoCategory.OTHER,
    val price: Double,
    val originalPrice: Double? = null,
    val currency: String,
    @Serializable(with = RepoBillingCycleSerializer::class)
    val billingCycle: RepoBillingCycle = RepoBillingCycle.CUSTOM,
    val isPermanent: Boolean = false,
    val tags: List<String> = emptyList(),
    val description: String? = null,
    val website: String? = null,
    val appStoreId: String? = null,
)

@Serializable
data class CatalogEntry(
    val id: String,
    val name: String,
    val icon: String? = null,
    @Serializable(with = RepoCategorySerializer::class)
    val category: RepoCategory = RepoCategory.OTHER,
    val commonPrices: List<PricingOption> = emptyList(),
    val tags: List<String> = emptyList(),
    /** Storefront codes (ISO alpha-2, any case) and/or "Global". */
    val region: List<String> = emptyList(),
    val website: String? = null,
    val description: String? = null,
    val appStoreId: String? = null,
    val isBundle: Boolean = false,
    val bundledSubscriptions: List<BundledSubscription>? = null,
)

@Serializable
data class PopularCatalog(
    val version: String? = null,
    val lastUpdated: String? = null,
    val subscriptions: List<CatalogEntry> = emptyList(),
)
