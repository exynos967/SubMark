package io.github.submark.core.ui.navigation

import kotlinx.serialization.Serializable

/*
 * Navigation contract shared by all features. A feature registers composables for the routes it owns
 * (see the owner comment on each group) and may navigate to any route. Routes are type-safe
 * Navigation Compose destinations; complex payloads travel as JSON strings.
 */

// ---- Top-level tabs (owner noted per route) ----
@Serializable data object OverviewRoute // feature:overview
@Serializable data object SubscriptionsRoute // feature:subscriptions
@Serializable data object CalendarRoute // feature:calendar
@Serializable data object AnalyticsRoute // feature:analytics
@Serializable data object PanelRoute // feature:integrations
@Serializable data object SettingsRoute // feature:settings

// ---- feature:subscriptions ----
@Serializable data class SubscriptionDetailRoute(val id: String)
/** [id] null = add. [prefillJson] = JSON of SubscriptionPrefill. */
@Serializable data class SubscriptionEditRoute(val id: String? = null, val prefillJson: String? = null)
@Serializable data class ArchiveRoute(val lifetime: Boolean = false)
@Serializable data class TagFolderRoute(val id: String)
@Serializable data object CategoryManagementRoute
@Serializable data object TagManagementRoute
@Serializable data object CustomFieldManagementRoute

// ---- feature:money ----
@Serializable data class PaymentHistoryRoute(val subscriptionId: String)
@Serializable data class PaymentRecordRoute(val id: String)
/** [paymentId] non-null = edit. */
@Serializable data class AddPaymentRoute(val subscriptionId: String, val paymentId: String? = null)
@Serializable data class StoredValueRecordsRoute(val subscriptionId: String)
@Serializable data class SharedSettingsRoute(val subscriptionId: String)
@Serializable data object CurrencyManagementRoute
@Serializable data object HistoricalRatesRoute
@Serializable data object PaymentMethodManagementRoute
@Serializable data object WalletManagementRoute
/** [walletId] null = all wallets' activity. */
@Serializable data class WalletActivityRoute(val walletId: String? = null)
@Serializable data object BudgetSettingsRoute
@Serializable data object FinancialDetailRoute
@Serializable data object StoredValueStatsRoute

// ---- feature:overview ----
@Serializable data object OverviewCustomizationRoute
@Serializable data object GlobalSearchRoute

// ---- feature:analytics ----
@Serializable data object AnalyticsCustomizationRoute
@Serializable data class SubscriptionAnalyticsRoute(val subscriptionId: String)

// ---- feature:share ----
@Serializable data class ShareSubscriptionRoute(val subscriptionId: String)
@Serializable data object FinancialReportPosterRoute
@Serializable data object QrImportRoute

// ---- feature:notifications ----
@Serializable data object NotificationSettingsRoute
@Serializable data class SubscriptionRemindersRoute(val subscriptionId: String)
@Serializable data object NotificationDiagnosticsRoute

// ---- feature:backup ----
@Serializable data object DataManagementRoute
@Serializable data object WebDavProfilesRoute
/** [id] null = create. */
@Serializable data class WebDavProfileRoute(val id: String? = null)

// ---- feature:settings ----
@Serializable data object AppearanceSettingsRoute
@Serializable data object FontSettingsRoute
@Serializable data object AppIconRoute
@Serializable data object InterfaceSettingsRoute
@Serializable data object CustomizationRoute
@Serializable data object RegionCurrencySettingsRoute
@Serializable data object SecuritySettingsRoute
@Serializable data object DeveloperOptionsRoute
@Serializable data object AboutRoute
/** [type] one of "faq", "privacy", "terms". */
@Serializable data class DocumentRoute(val type: String)
@Serializable data object OnboardingRoute

// ---- feature:integrations ----
@Serializable data object PopularSubscriptionsRoute
@Serializable data object PopularRepositoriesRoute
@Serializable data class PriceMonitorRoute(val subscriptionId: String)
@Serializable data object PriceMonitorSettingsRoute
/** [imagePath] = image copied into cacheDir by the share target; null = let the user pick one. */
@Serializable data class AiRecognitionRoute(val imagePath: String? = null)
@Serializable data object AiSettingsRoute
@Serializable data object RawgSettingsRoute
@Serializable data class ApiBudgetEditRoute(val id: String? = null)
@Serializable data class ServiceConnectionEditRoute(val id: String? = null)
/**
 * Picks an icon and returns it to the previous back stack entry's SavedStateHandle under
 * [NavResults.ICON] as "TYPE|value" (IconType name + value). [query] seeds searches (e.g. subscription name).
 */
@Serializable data class IconPickerRoute(val query: String? = null)

/** Deep-link scheme handled by MainActivity, e.g. `submark://ai-recognize?imagePath=...`. */
const val DEEP_LINK_SCHEME = "submark"

object NavResults {
    const val ICON = "nav_result_icon"
}
