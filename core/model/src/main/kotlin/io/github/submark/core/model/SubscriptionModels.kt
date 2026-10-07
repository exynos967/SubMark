package io.github.submark.core.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Serializable
@Entity(
    tableName = "subscriptions",
    indices = [Index("categoryId"), Index("parentId"), Index("nextPaymentDate"), Index("kind", "status")],
)
data class Subscription(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val kind: SubscriptionKind = SubscriptionKind.REGULAR,
    val bundleRole: BundleRole = BundleRole.NONE,
    /** Bundle main when [bundleRole] is CHILD. */
    val parentId: String? = null,
    /** Price per cycle, or the one-time price for LIFETIME. */
    val price: Money,
    val currencyCode: String,
    /** Catalogue price of a bundle child, for "total value / you save" display. */
    val originalPrice: Money? = null,
    /** Null for LIFETIME; optional for WISHLIST. */
    val billingCycle: BillingCycle? = BillingCycle.MONTHLY,
    val customCycleCount: Int? = null,
    val customCycleUnit: CycleUnit? = null,
    /** start..end is the one and only cycle; requires [endDate]. */
    val isSingleCycle: Boolean = false,
    /** 1..31, month-based cycles only; clamps to month end. */
    val fixedPaymentDay: Int? = null,
    val renewalType: RenewalType = RenewalType.AUTO,
    val trialStartDate: Day? = null,
    val trialDays: Int? = null,
    /** Billing start, or purchase date for LIFETIME. */
    val startDate: Day,
    val endDate: Day? = null,
    /** Occurrence k is anchor + k cycles; re-based on NEW_CYCLE marks and archive restores. */
    val cycleAnchorDate: Day? = null,
    val nextPaymentDate: Day? = null,
    val lastPaymentDate: Day? = null,
    val status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    val pausedAt: Timestamp? = null,
    val categoryId: String,
    val iconType: IconType? = null,
    val iconValue: String? = null,
    val website: String? = null,
    val appStoreId: String? = null,
    val note: String? = null,
    val paymentMethodId: String? = null,
    /** Wallet charged by payments of this subscription. */
    val walletId: String? = null,
    /** Cached; only changed through stored-value records. May be negative (debt). */
    val storedValueBalance: Money = BigDecimal.ZERO,
    val isShared: Boolean = false,
    val customReminderEnabled: Boolean = false,
    val calendarSyncEnabled: Boolean = false,
    val calendarEventId: Long? = null,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
)

@Serializable
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String = newId(),
    /** Non-null for presets; [name] null then means "use the localized preset name". */
    val systemKey: SystemCategory? = null,
    val name: String? = null,
    val iconType: IconType = IconType.SYMBOL,
    val iconValue: String,
    val colorHex: String,
    val isHidden: Boolean = false,
    val sortOrder: Int = 0,
) {
    val isSystem: Boolean get() = systemKey != null
}

@Serializable
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class Tag(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val color: TagColor = TagColor.BLUE,
    val iconType: IconType? = null,
    val iconValue: String? = null,
    val createdAt: Timestamp,
)

@Serializable
@Entity(
    tableName = "subscription_tags",
    primaryKeys = ["subscriptionId", "tagId"],
    indices = [Index("tagId")],
    foreignKeys = [
        ForeignKey(entity = Subscription::class, parentColumns = ["id"], childColumns = ["subscriptionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Tag::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class SubscriptionTag(val subscriptionId: String, val tagId: String)

@Serializable
@Entity(tableName = "tag_folders")
data class TagFolder(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val iconValue: String? = null,
    val colorHex: String? = null,
    val matchMode: TagMatchMode = TagMatchMode.ANY,
    /** Off = matching subscriptions are hidden from the main list. */
    val showInMainList: Boolean = true,
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Timestamp,
)

@Serializable
@Entity(
    tableName = "tag_folder_tags",
    primaryKeys = ["folderId", "tagId"],
    indices = [Index("tagId")],
    foreignKeys = [
        ForeignKey(entity = TagFolder::class, parentColumns = ["id"], childColumns = ["folderId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Tag::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class TagFolderTag(val folderId: String, val tagId: String)

@Serializable
@Entity(tableName = "custom_field_definitions", indices = [Index("categoryId")])
data class CustomFieldDefinition(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val type: CustomFieldType,
    val placeholder: String? = null,
    val helpText: String? = null,
    val isRequired: Boolean = false,
    val isActive: Boolean = true,
    /** Null = global field; otherwise only shown for subscriptions in this category. */
    val categoryId: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Timestamp,
)

@Serializable
@Entity(
    tableName = "custom_field_options",
    indices = [Index("fieldId")],
    foreignKeys = [ForeignKey(entity = CustomFieldDefinition::class, parentColumns = ["id"], childColumns = ["fieldId"], onDelete = ForeignKey.CASCADE)],
)
data class CustomFieldOption(
    @PrimaryKey val id: String = newId(),
    val fieldId: String,
    val label: String,
    val sortOrder: Int = 0,
)

/**
 * Value encoding by type: NUMBER/DECIMAL plain text, DATE ISO date, DATETIME ISO instant,
 * BOOLEAN "true"/"false", DROPDOWN option id, RATING "1".."5", others raw text.
 */
@Serializable
@Entity(
    tableName = "custom_field_values",
    primaryKeys = ["subscriptionId", "fieldId"],
    indices = [Index("fieldId")],
    foreignKeys = [
        ForeignKey(entity = Subscription::class, parentColumns = ["id"], childColumns = ["subscriptionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CustomFieldDefinition::class, parentColumns = ["id"], childColumns = ["fieldId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class CustomFieldValue(val subscriptionId: String, val fieldId: String, val value: String)

@Serializable
@Entity(
    tableName = "subscription_photos",
    indices = [Index("subscriptionId")],
    foreignKeys = [ForeignKey(entity = Subscription::class, parentColumns = ["id"], childColumns = ["subscriptionId"], onDelete = ForeignKey.CASCADE)],
)
data class SubscriptionPhoto(
    @PrimaryKey val id: String = newId(),
    val subscriptionId: String,
    /** File name inside the app's private photo directory. */
    val fileName: String,
    val sortOrder: Int = 0,
    val createdAt: Timestamp,
)

@Serializable
@Entity(
    tableName = "custom_reminders",
    indices = [Index("subscriptionId")],
    foreignKeys = [ForeignKey(entity = Subscription::class, parentColumns = ["id"], childColumns = ["subscriptionId"], onDelete = ForeignKey.CASCADE)],
)
data class CustomReminder(
    @PrimaryKey val id: String = newId(),
    val subscriptionId: String,
    /** 0 = on the due date. */
    val daysBefore: Int,
    val time: Time,
)
