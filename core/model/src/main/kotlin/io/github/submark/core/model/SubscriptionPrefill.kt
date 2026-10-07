package io.github.submark.core.model

import kotlinx.serialization.Serializable

/**
 * Partial subscription used to open the add form pre-filled (popular catalogue, AI recognition,
 * QR import, App Store search). Passed between features as JSON in a navigation argument.
 */
@Serializable
data class SubscriptionPrefill(
    val name: String? = null,
    val kind: SubscriptionKind? = null,
    val price: Money? = null,
    val currencyCode: String? = null,
    val billingCycle: BillingCycle? = null,
    val customCycleCount: Int? = null,
    val customCycleUnit: CycleUnit? = null,
    val systemCategory: SystemCategory? = null,
    val iconType: IconType? = null,
    val iconValue: String? = null,
    val website: String? = null,
    val appStoreId: String? = null,
    val note: String? = null,
    val tags: List<String> = emptyList(),
    val startDate: Day? = null,
    val endDate: Day? = null,
    val renewalType: RenewalType? = null,
    val originalPrice: Money? = null,
    /** Bundle children; non-empty means create a MAIN with these as CHILD subscriptions. */
    val children: List<SubscriptionPrefill> = emptyList(),
)
