package io.github.submark.feature.integrations.data.popular

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Category of a catalogue entry; unknown values from a repository map to [OTHER]. */
@Serializable(with = RepoCategory.Serializer::class)
enum class RepoCategory {
    @SerialName("video") VIDEO,
    @SerialName("music") MUSIC,
    @SerialName("productivity") PRODUCTIVITY,
    @SerialName("utility") UTILITY,
    @SerialName("ai") AI,
    @SerialName("gaming") GAMING,
    @SerialName("news") NEWS,
    @SerialName("lifestyle") LIFESTYLE,
    @SerialName("entertainment") ENTERTAINMENT,
    @SerialName("other") OTHER;

    object Serializer : KSerializer<RepoCategory> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("RepoCategory", PrimitiveKind.STRING)
        override fun serialize(encoder: Encoder, value: RepoCategory) = encoder.encodeString(value.name.lowercase())
        override fun deserialize(decoder: Decoder): RepoCategory =
            entries.firstOrNull { it.name.equals(decoder.decodeString(), ignoreCase = true) } ?: OTHER
    }
}

/** Billing cycle of a pricing option; unknown values map to [CUSTOM]. */
@Serializable(with = RepoBillingCycle.Serializer::class)
enum class RepoBillingCycle {
    @SerialName("monthly") MONTHLY,
    @SerialName("quarterly") QUARTERLY,
    @SerialName("semiAnnually") SEMI_ANNUALLY,
    @SerialName("annually") ANNUALLY,
    @SerialName("custom") CUSTOM;

    object Serializer : KSerializer<RepoBillingCycle> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("RepoBillingCycle", PrimitiveKind.STRING)
        override fun serialize(encoder: Encoder, value: RepoBillingCycle) =
            encoder.encodeString(
                when (value) {
                    MONTHLY -> "monthly"
                    QUARTERLY -> "quarterly"
                    SEMI_ANNUALLY -> "semiAnnually"
                    ANNUALLY -> "annually"
                    CUSTOM -> "custom"
                },
            )

        override fun deserialize(decoder: Decoder): RepoBillingCycle =
            when (decoder.decodeString().trim().lowercase()) {
                "monthly" -> MONTHLY
                "quarterly" -> QUARTERLY
                "semiannually", "semiannual", "halfyearly" -> SEMI_ANNUALLY
                "annually", "yearly" -> ANNUALLY
                else -> CUSTOM
            }
    }
}

/** One purchasable plan of a catalogue entry. */
@Serializable
data class PricingOption(
    val price: Double,
    val currency: String,
    val billingCycle: RepoBillingCycle = RepoBillingCycle.CUSTOM,
    val isPermanent: Boolean = false,
    val description: String? = null,
)

/** A member service of a bundle entry. */
@Serializable
data class BundledSubscription(
    val name: String,
    val icon: String? = null,
    val category: RepoCategory = RepoCategory.OTHER,
    val price: Double,
    val originalPrice: Double? = null,
    val currency: String,
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
