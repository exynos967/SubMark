package io.github.submark.core.model

import kotlinx.serialization.Serializable

/** What kind of spending a subscription row represents. A bundle is not a kind: see [BundleRole]. */
@Serializable
enum class SubscriptionKind { REGULAR, STORED_VALUE, LIFETIME, WISHLIST }

@Serializable
enum class BundleRole { NONE, MAIN, CHILD }

@Serializable
enum class BillingCycle { WEEKLY, MONTHLY, QUARTERLY, SEMIANNUALLY, ANNUALLY, CUSTOM }

/** Unit of a [BillingCycle.CUSTOM] cycle; stored with a count so calendar math stays exact. */
@Serializable
enum class CycleUnit { DAY, WEEK, MONTH, YEAR }

/** TRIAL switches to AUTO or MANUAL when the user resolves the trial-ended prompt. */
@Serializable
enum class RenewalType { AUTO, MANUAL, TRIAL }

/** "Archived" is not stored: it is PAUSED while the archive-mode preference is on. */
@Serializable
enum class SubscriptionStatus { ACTIVE, PAUSED }

@Serializable
enum class SystemCategory { VIDEO, MUSIC, ENTERTAINMENT, GAMING, PRODUCTIVITY, UTILITY, AI, NEWS, LIFESTYLE, OTHER }

/** Source of an icon reference. [IconRef.value] meaning depends on the type. */
@Serializable
enum class IconType {
    /** Material symbol name from the bundled icon catalogue. */
    SYMBOL,
    EMOJI,
    /** Remote image URL (website favicon, App Store artwork, repo icon, RAWG cover). Cached by Coil. */
    URL,
    /** File name inside the app's private icon directory. */
    FILE,
}

@Serializable
enum class TagColor { BLUE, BROWN, CYAN, GRAY, GREEN, INDIGO, MINT, ORANGE, PINK, PURPLE, RED, TEAL, YELLOW }

@Serializable
enum class TagMatchMode { ANY, ALL }

@Serializable
enum class CustomFieldType { TEXT, MULTILINE, NUMBER, DECIMAL, DATE, DATETIME, BOOLEAN, DROPDOWN, EMAIL, PHONE, URL, RATING }

@Serializable
enum class PaymentStatus { SUCCESS, PENDING, FAILED }

@Serializable
enum class PaymentKind {
    REGULAR,
    IN_APP_PURCHASE,
    /** End-date extension; never moves the billing schedule, never charges a wallet. */
    EXTENSION,
    LIFETIME_PURCHASE,
    /** Money put into a stored-value subscription. Counted as spending; deductions are not. */
    STORED_VALUE_DEPOSIT,
}

@Serializable
enum class PaymentSource { USER_MANUAL, SYSTEM_AUTO, SYSTEM_OVERDUE, TRIAL_EXPIRED, WIDGET, HISTORY_GENERATED, BUNDLE_SYNC, IMPORT }

@Serializable
enum class MarkTiming { ON_TIME, EARLY_NEW_CYCLE, EARLY_ORIGINAL_CYCLE, OVERDUE_NEW_CYCLE, OVERDUE_ORIGINAL_CYCLE }

@Serializable
enum class DateAdjustmentMode { NONE, END_DATE, NEXT_BILLING }

@Serializable
enum class RateMode { MANUAL, AUTO }

@Serializable
enum class RateProvider { FRANKFURTER, FAWAZ_JSDELIVR, FAWAZ_PAGES_DEV }

@Serializable
enum class WalletKind {
    /** Tracks a real balance, counted in assets, cannot go negative. */
    BALANCE_TRACKED,
    /** Spend first, settle later; may go negative down to the credit limit (null = unlimited). */
    CREDIT,
    /** Records flow only, overdraft allowed, excluded from assets. */
    SETTLEMENT_ONLY,
}

@Serializable
enum class WalletTxnType { TOP_UP, EXPENSE, REFUND, ADJUSTMENT }

@Serializable
enum class WalletTxnStatus { COMMITTED, REVERSED }

@Serializable
enum class StoredValueRecordType { DEPOSIT, DEDUCTION }

@Serializable
enum class SplitMode { EQUAL, RATIO, FIXED_AMOUNT, CREATOR_PAYS }

@Serializable
enum class MemberStatus { ACTIVE, INACTIVE, PENDING }

@Serializable
enum class BundlePaymentSyncMode { ALWAYS, SMART, NEVER }

@Serializable
enum class PriceCheckResult { SUCCESS, PARTIAL, FAILED, SKIPPED }

@Serializable
enum class ApiServiceType { DEEPSEEK, NEWAPI, VAPI, PACKY, ZAI }

@Serializable
enum class ServiceType { CLASH, EMBY }

@Serializable
enum class BackupFrequency { MANUAL, DAILY, WEEKLY, EVERY_30_DAYS }

@Serializable
enum class BackupJobKind { BACKUP, RESTORE, VERIFY }

@Serializable
enum class BackupJobPhase {
    PREPARING, ENCRYPTING, UPLOADING_PAYLOAD, VERIFYING_PAYLOAD, COMMITTING_MANIFEST, PRUNING,
    DOWNLOADING, DECRYPTING, RESTORING, COMPLETED, FAILED, CANCELED,
}

@Serializable
enum class RestoreMode { MERGE, REPLACE_MATCHING, EXACT }
