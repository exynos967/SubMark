package io.github.submark.core.ui.format

import androidx.annotation.StringRes
import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.CycleUnit
import io.github.submark.core.model.MarkTiming
import io.github.submark.core.model.MemberStatus
import io.github.submark.core.model.PaymentKind
import io.github.submark.core.model.PaymentSource
import io.github.submark.core.model.PaymentStatus
import io.github.submark.core.model.RenewalType
import io.github.submark.core.model.SplitMode
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import io.github.submark.core.model.SystemCategory
import io.github.submark.core.model.WalletKind
import io.github.submark.core.model.WalletTxnType
import io.github.submark.core.ui.R

/** Semantic color role of a badge; resolved to theme colors by [io.github.submark.core.ui.component.StatusBadge]. */
enum class BadgeTone { NEUTRAL, PRIMARY, SECONDARY, TERTIARY, SUCCESS, WARNING, ERROR }

@get:StringRes
val SubscriptionKind.labelRes: Int
    get() = when (this) {
        SubscriptionKind.REGULAR -> R.string.ui_kind_regular
        SubscriptionKind.STORED_VALUE -> R.string.ui_kind_stored_value
        SubscriptionKind.LIFETIME -> R.string.ui_kind_lifetime
        SubscriptionKind.WISHLIST -> R.string.ui_kind_wishlist
    }

@get:StringRes
val SubscriptionStatus.labelRes: Int
    get() = when (this) {
        SubscriptionStatus.ACTIVE -> R.string.ui_status_active
        SubscriptionStatus.PAUSED -> R.string.ui_status_paused
    }

@get:StringRes
val RenewalType.labelRes: Int
    get() = when (this) {
        RenewalType.AUTO -> R.string.ui_renewal_auto
        RenewalType.MANUAL -> R.string.ui_renewal_manual
        RenewalType.TRIAL -> R.string.ui_renewal_trial
    }

@get:StringRes
val BillingCycle.labelRes: Int
    get() = when (this) {
        BillingCycle.WEEKLY -> R.string.ui_cycle_weekly
        BillingCycle.MONTHLY -> R.string.ui_cycle_monthly
        BillingCycle.QUARTERLY -> R.string.ui_cycle_quarterly
        BillingCycle.SEMIANNUALLY -> R.string.ui_cycle_semiannually
        BillingCycle.ANNUALLY -> R.string.ui_cycle_annually
        BillingCycle.CUSTOM -> R.string.ui_cycle_custom
    }

@get:StringRes
val CycleUnit.labelRes: Int
    get() = when (this) {
        CycleUnit.DAY -> R.string.ui_unit_day
        CycleUnit.WEEK -> R.string.ui_unit_week
        CycleUnit.MONTH -> R.string.ui_unit_month
        CycleUnit.YEAR -> R.string.ui_unit_year
    }

@get:StringRes
val PaymentStatus.labelRes: Int
    get() = when (this) {
        PaymentStatus.SUCCESS -> R.string.ui_payment_status_success
        PaymentStatus.PENDING -> R.string.ui_payment_status_pending
        PaymentStatus.FAILED -> R.string.ui_payment_status_failed
    }

val PaymentStatus.tone: BadgeTone
    get() = when (this) {
        PaymentStatus.SUCCESS -> BadgeTone.SUCCESS
        PaymentStatus.PENDING -> BadgeTone.WARNING
        PaymentStatus.FAILED -> BadgeTone.ERROR
    }

@get:StringRes
val PaymentKind.labelRes: Int
    get() = when (this) {
        PaymentKind.REGULAR -> R.string.ui_payment_kind_regular
        PaymentKind.IN_APP_PURCHASE -> R.string.ui_payment_kind_iap
        PaymentKind.EXTENSION -> R.string.ui_payment_kind_extension
        PaymentKind.LIFETIME_PURCHASE -> R.string.ui_payment_kind_lifetime
        PaymentKind.STORED_VALUE_DEPOSIT -> R.string.ui_payment_kind_deposit
    }

@get:StringRes
val PaymentSource.labelRes: Int
    get() = when (this) {
        PaymentSource.USER_MANUAL -> R.string.ui_payment_source_manual
        PaymentSource.SYSTEM_AUTO -> R.string.ui_payment_source_auto
        PaymentSource.SYSTEM_OVERDUE -> R.string.ui_payment_source_overdue
        PaymentSource.TRIAL_EXPIRED -> R.string.ui_payment_source_trial
        PaymentSource.WIDGET -> R.string.ui_payment_source_widget
        PaymentSource.HISTORY_GENERATED -> R.string.ui_payment_source_history
        PaymentSource.BUNDLE_SYNC -> R.string.ui_payment_source_bundle
        PaymentSource.IMPORT -> R.string.ui_payment_source_import
    }

@get:StringRes
val MarkTiming.labelRes: Int
    get() = when (this) {
        MarkTiming.ON_TIME -> R.string.ui_mark_timing_on_time
        MarkTiming.EARLY_NEW_CYCLE -> R.string.ui_mark_timing_early_new
        MarkTiming.EARLY_ORIGINAL_CYCLE -> R.string.ui_mark_timing_early_original
        MarkTiming.OVERDUE_NEW_CYCLE -> R.string.ui_mark_timing_overdue_new
        MarkTiming.OVERDUE_ORIGINAL_CYCLE -> R.string.ui_mark_timing_overdue_original
    }

@get:StringRes
val SplitMode.labelRes: Int
    get() = when (this) {
        SplitMode.EQUAL -> R.string.ui_split_equal
        SplitMode.RATIO -> R.string.ui_split_ratio
        SplitMode.FIXED_AMOUNT -> R.string.ui_split_fixed
        SplitMode.CREATOR_PAYS -> R.string.ui_split_creator_pays
    }

@get:StringRes
val SplitMode.descriptionRes: Int
    get() = when (this) {
        SplitMode.EQUAL -> R.string.ui_split_equal_desc
        SplitMode.RATIO -> R.string.ui_split_ratio_desc
        SplitMode.FIXED_AMOUNT -> R.string.ui_split_fixed_desc
        SplitMode.CREATOR_PAYS -> R.string.ui_split_creator_pays_desc
    }

@get:StringRes
val MemberStatus.labelRes: Int
    get() = when (this) {
        MemberStatus.ACTIVE -> R.string.ui_member_active
        MemberStatus.INACTIVE -> R.string.ui_member_inactive
        MemberStatus.PENDING -> R.string.ui_member_pending
    }

@get:StringRes
val WalletKind.labelRes: Int
    get() = when (this) {
        WalletKind.BALANCE_TRACKED -> R.string.ui_wallet_kind_balance
        WalletKind.CREDIT -> R.string.ui_wallet_kind_credit
        WalletKind.SETTLEMENT_ONLY -> R.string.ui_wallet_kind_settlement
    }

@get:StringRes
val WalletKind.descriptionRes: Int
    get() = when (this) {
        WalletKind.BALANCE_TRACKED -> R.string.ui_wallet_kind_balance_desc
        WalletKind.CREDIT -> R.string.ui_wallet_kind_credit_desc
        WalletKind.SETTLEMENT_ONLY -> R.string.ui_wallet_kind_settlement_desc
    }

@get:StringRes
val WalletTxnType.labelRes: Int
    get() = when (this) {
        WalletTxnType.TOP_UP -> R.string.ui_wallet_txn_top_up
        WalletTxnType.EXPENSE -> R.string.ui_wallet_txn_expense
        WalletTxnType.REFUND -> R.string.ui_wallet_txn_refund
        WalletTxnType.ADJUSTMENT -> R.string.ui_wallet_txn_adjustment
    }

@get:StringRes
val SystemCategory.labelRes: Int
    get() = when (this) {
        SystemCategory.VIDEO -> R.string.ui_category_video
        SystemCategory.MUSIC -> R.string.ui_category_music
        SystemCategory.ENTERTAINMENT -> R.string.ui_category_entertainment
        SystemCategory.GAMING -> R.string.ui_category_gaming
        SystemCategory.PRODUCTIVITY -> R.string.ui_category_productivity
        SystemCategory.UTILITY -> R.string.ui_category_utility
        SystemCategory.AI -> R.string.ui_category_ai
        SystemCategory.NEWS -> R.string.ui_category_news
        SystemCategory.LIFESTYLE -> R.string.ui_category_lifestyle
        SystemCategory.OTHER -> R.string.ui_category_other
    }

/** [io.github.submark.core.ui.icon.IconCatalog] name used when seeding preset categories. */
val SystemCategory.defaultIconName: String
    get() = when (this) {
        SystemCategory.VIDEO -> "movie"
        SystemCategory.MUSIC -> "music"
        SystemCategory.ENTERTAINMENT -> "theater"
        SystemCategory.GAMING -> "gamepad"
        SystemCategory.PRODUCTIVITY -> "work"
        SystemCategory.UTILITY -> "build"
        SystemCategory.AI -> "sparkle"
        SystemCategory.NEWS -> "newspaper"
        SystemCategory.LIFESTYLE -> "spa"
        SystemCategory.OTHER -> "category"
    }

/** "#RRGGBB" used when seeding preset categories. */
val SystemCategory.defaultColorHex: String
    get() = when (this) {
        SystemCategory.VIDEO -> "#E5484D"
        SystemCategory.MUSIC -> "#D6409F"
        SystemCategory.ENTERTAINMENT -> "#F76B15"
        SystemCategory.GAMING -> "#8E4EC6"
        SystemCategory.PRODUCTIVITY -> "#3E63DD"
        SystemCategory.UTILITY -> "#12A594"
        SystemCategory.AI -> "#6E56CF"
        SystemCategory.NEWS -> "#0090FF"
        SystemCategory.LIFESTYLE -> "#30A46C"
        SystemCategory.OTHER -> "#8B8D98"
    }

val CountdownLevel.tone: BadgeTone
    get() = when (this) {
        CountdownLevel.OVERDUE -> BadgeTone.ERROR
        CountdownLevel.TODAY, CountdownLevel.TOMORROW -> BadgeTone.WARNING
        CountdownLevel.SOON -> BadgeTone.PRIMARY
        CountdownLevel.LATER -> BadgeTone.NEUTRAL
    }
