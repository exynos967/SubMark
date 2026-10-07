package io.github.submark.core.data.settings

import kotlinx.serialization.Serializable

@Serializable enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable enum class AppLanguage { SYSTEM, ENGLISH, SIMPLIFIED_CHINESE }

@Serializable enum class FontTheme { MODERN, COMFORTABLE, COMPACT, ELEGANT, ACCESSIBLE, CUSTOM }

@Serializable enum class FontFamilyOption { SYSTEM, ROUNDED, SERIF, MONOSPACE, CJK_OPTIMIZED }

@Serializable enum class FontSize { SMALL, MEDIUM, LARGE, EXTRA_LARGE }

@Serializable enum class AppIcon { DEFAULT, STYLE_1, STYLE_2, STYLE_3, STYLE_4, STYLE_5, STYLE_6 }

@Serializable enum class StartupTab { OVERVIEW, SUBSCRIPTIONS, CALENDAR, ANALYTICS }

@Serializable enum class FloatingTabWidth { NARROW, COMPACT, STANDARD, COMFORTABLE, WIDE }

@Serializable enum class ListStyle { LIST, GRID }

/** Default list style preference; [LAST_USED] restores [ListSettings.lastStyle]. */
@Serializable enum class DefaultListStyle { LIST, GRID, LAST_USED }

@Serializable enum class CalendarMode { MONTH, WEEK, TIMELINE }

@Serializable enum class TimelinePeriod { ONE_MONTH, THREE_MONTHS, SIX_MONTHS, ONE_YEAR }

@Serializable enum class OverviewLayout { MODERN, CLASSIC }

@Serializable enum class SummaryPeriod { MONTH, QUARTER, YEAR }

/** Subscriptions vs lifetime purchases, used by overview and analytics. */
@Serializable enum class SpendingMode { SUBSCRIPTIONS, LIFETIME }

@Serializable enum class TrendPeriod { MONTHLY, YEARLY }

@Serializable
enum class ClassicOverviewComponent {
    EXPENSE_OVERVIEW, UPCOMING_PAYMENTS, RECENT_PAID, MONTHLY_TIMELINE, RECENT_PAYMENT_TIMELINE,
    CATEGORY_BREAKDOWN, TREND, GLOBAL_WALLET, PRICE_MONITORING,
}

@Serializable
enum class ModernOverviewComponent {
    SPENDING_HERO, COMING_UP, PAYMENT_SCHEDULE, RECENT_PAYMENTS, WALLET_BALANCES, WISHLIST_PRICES,
    MY_SUBSCRIPTIONS, SPENDING_INSIGHTS,
}

/** FINANCIAL_OVERVIEW is always shown; its visibility flag is ignored by the UI. */
@Serializable
enum class AnalyticsComponent { FINANCIAL_OVERVIEW, TREND, HEATMAP, CATEGORY, MULTI_DIMENSION, STORED_VALUE }

@Serializable enum class PosterStyle { MINIMAL, MODERN, GRADIENT, COLORFUL }

@Serializable enum class PosterDisplayMode { SIMPLE, DETAILED }

@Serializable enum class SortField { NAME, PRICE, DATE }

@Serializable enum class SortDirection { ASC, DESC }

@Serializable enum class ListSegment { SUBSCRIPTIONS, LIFETIME, WISHLIST }

@Serializable enum class FinancialDetailMode { SIMPLE, DETAILED }

@Serializable enum class FinancialDetailFilter { INCLUDE_LIFETIME, SUBSCRIPTIONS_ONLY, LIFETIME_ONLY }

@Serializable enum class PopularRegionScope { ALL, LOCAL }

@Serializable enum class PopularSort { NAME, CATEGORY }

@Serializable enum class AiProvider { OPENAI, QWEN, CUSTOM }

@Serializable enum class PanelMode { SUBSCRIPTIONS, SERVICE }

@Serializable enum class ServicePanelTab { SERVICE, API_BUDGET }
