package io.github.submark.core.model

import kotlinx.serialization.Serializable

/**
 * Single serialization format shared by manual export, WebDAV backup and import.
 * Bump [CURRENT_VERSION] on incompatible changes and migrate in the importer.
 */
@Serializable
data class ExportBundle(
    val exportVersion: Int = CURRENT_VERSION,
    val appVersion: String,
    val createdAt: Timestamp,
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val tagFolders: List<TagFolder> = emptyList(),
    val tagFolderTags: List<TagFolderTag> = emptyList(),
    val customFields: List<CustomFieldDefinition> = emptyList(),
    val customFieldOptions: List<CustomFieldOption> = emptyList(),
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val currencies: List<Currency> = emptyList(),
    val wallets: List<Wallet> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
    val subscriptionTags: List<SubscriptionTag> = emptyList(),
    val customFieldValues: List<CustomFieldValue> = emptyList(),
    val subscriptionPhotos: List<SubscriptionPhoto> = emptyList(),
    val customReminders: List<CustomReminder> = emptyList(),
    val paymentRecords: List<PaymentRecord> = emptyList(),
    val walletTransactions: List<WalletTransaction> = emptyList(),
    val storedValueRecords: List<StoredValueRecord> = emptyList(),
    val sharedConfigs: List<SharedConfig> = emptyList(),
    val sharedMembers: List<SharedMember> = emptyList(),
    val priceMonitors: List<PriceMonitor> = emptyList(),
    val priceRecords: List<PriceRecord> = emptyList(),
    val popularRepositories: List<PopularRepository> = emptyList(),
    val iconRepositories: List<IconRepository> = emptyList(),
    val apiBudgetConfigs: List<ApiBudgetConfig> = emptyList(),
    val serviceConnections: List<ServiceConnection> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

/** Per-type counts shown after an import or restore. */
@Serializable
data class ImportResult(val counts: Map<String, Int>) {
    val isEmpty: Boolean get() = counts.values.all { it == 0 }
}
