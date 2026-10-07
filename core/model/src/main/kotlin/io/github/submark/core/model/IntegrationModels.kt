package io.github.submark.core.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/*
 * Credentials (API keys, passwords, tokens) are never stored in these tables.
 * They live in the encrypted SecretStore under SecretKeys.* derived from the row id.
 */

/** App Store price monitoring for a wishlist subscription with an App Store id. */
@Serializable
@Entity(
    tableName = "price_monitors",
    foreignKeys = [ForeignKey(entity = Subscription::class, parentColumns = ["id"], childColumns = ["subscriptionId"], onDelete = ForeignKey.CASCADE)],
)
data class PriceMonitor(
    @PrimaryKey val subscriptionId: String,
    val enabled: Boolean = true,
    val appStoreId: String,
    /** ISO-3166 alpha-2 storefront codes, lowercase, e.g. ["us","cn"]. */
    val regions: List<String>,
    val lastCheckAt: Timestamp? = null,
    val lastCheckResult: PriceCheckResult? = null,
)

@Serializable
@Entity(
    tableName = "price_records",
    indices = [Index("subscriptionId", "region", "checkedAt")],
    foreignKeys = [ForeignKey(entity = Subscription::class, parentColumns = ["id"], childColumns = ["subscriptionId"], onDelete = ForeignKey.CASCADE)],
)
data class PriceRecord(
    @PrimaryKey val id: String = newId(),
    val subscriptionId: String,
    val region: String,
    val price: Money,
    val currencyCode: String,
    val formattedPrice: String? = null,
    val checkedAt: Timestamp,
)

/** User-added catalogue of popular subscriptions (MarkBuyRepo-compatible JSON). Cached payload lives in a file. */
@Serializable
@Entity(tableName = "popular_repositories")
data class PopularRepository(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val url: String,
    val description: String? = null,
    val enabled: Boolean = true,
    val lastFetchedAt: Timestamp? = null,
    val entryCount: Int = 0,
    val lastError: String? = null,
    val sortOrder: Int = 0,
)

/** User-added icon pack: JSON `{ "name": String, "icons": [{ "name": String, "url": String }] }`. */
@Serializable
@Entity(tableName = "icon_repositories")
data class IconRepository(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val url: String,
    val iconCount: Int = 0,
    val lastFetchedAt: Timestamp? = null,
)

/** One per service type. Secret: SecretKeys.apiBudget(id). */
@Serializable
@Entity(tableName = "api_budget_configs", indices = [Index(value = ["serviceType"], unique = true)])
data class ApiBudgetConfig(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val serviceType: ApiServiceType,
    val baseUrl: String? = null,
    val userId: String? = null,
    val currencyCode: String = "USD",
    val monthlyBudget: Money? = null,
    val dailyBudget: Money? = null,
    /** Percent: 50, 75, 90 or 95. */
    val alertThreshold: Int = 90,
    val enabled: Boolean = true,
    val lastUpdatedAt: Timestamp? = null,
    val lastError: String? = null,
    /** Normalized provider payload of the last successful fetch. */
    val snapshotJson: String? = null,
    val createdAt: Timestamp,
)

/** Secrets: SecretKeys.servicePassword(id), SecretKeys.serviceApiKey(id). */
@Serializable
@Entity(tableName = "service_connections")
data class ServiceConnection(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val type: ServiceType,
    val url: String,
    val username: String? = null,
    val enabled: Boolean = true,
    val autoRefresh: Boolean = false,
    val refreshIntervalMinutes: Int = 15,
    val colorHex: String? = null,
    val lastRefreshAt: Timestamp? = null,
    val lastError: String? = null,
    val snapshotJson: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Timestamp,
)

/** Secrets: SecretKeys.webDavPassword(id), SecretKeys.backupEncryption(id). */
@Serializable
@Entity(tableName = "backup_profiles")
data class BackupProfile(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val serverUrl: String,
    val remotePath: String = "SubMark",
    val username: String,
    val enabled: Boolean = true,
    /** Plain HTTP is accepted only for loopback / private / .local hosts and only when this is on. */
    val allowHttpLocal: Boolean = false,
    val encrypt: Boolean = true,
    val frequency: BackupFrequency = BackupFrequency.WEEKLY,
    val keepCount: Int = 10,
    val keepDays: Int = 90,
    val wifiOnly: Boolean = false,
    val verifyAfterUpload: Boolean = true,
    val lastSuccessAt: Timestamp? = null,
    val lastAttemptAt: Timestamp? = null,
    val lastError: String? = null,
    val createdAt: Timestamp,
)

@Serializable
@Entity(
    tableName = "backup_jobs",
    indices = [Index("profileId", "startedAt")],
    foreignKeys = [ForeignKey(entity = BackupProfile::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class BackupJob(
    @PrimaryKey val id: String = newId(),
    val profileId: String,
    val kind: BackupJobKind,
    val phase: BackupJobPhase,
    val startedAt: Timestamp,
    val finishedAt: Timestamp? = null,
    val bytes: Long? = null,
    val remoteName: String? = null,
    val stagedPayloadPath: String? = null,
    val error: String? = null,
)

object SecretKeys {
    fun apiBudget(id: String) = "api_budget:$id"
    fun servicePassword(id: String) = "service_password:$id"
    fun serviceApiKey(id: String) = "service_api_key:$id"
    fun webDavPassword(id: String) = "webdav_password:$id"
    fun backupEncryption(id: String) = "backup_encryption:$id"
    const val AI_API_KEY = "ai_api_key"
    const val RAWG_API_KEY = "rawg_api_key"
}
