package io.github.submark.core.data.settings

import io.github.submark.core.model.BundlePaymentSyncMode
import io.github.submark.core.model.Money
import io.github.submark.core.model.Time
import io.github.submark.core.model.Timestamp
import kotlinx.serialization.Serializable
import java.time.LocalTime

/*
 * Every field has a default so settings written by older versions keep decoding when fields are added.
 * Enum values must only ever be appended; removing one would drop the whole file back to defaults.
 */

@Serializable
data class AppSettings(
    val display: DisplaySettings = DisplaySettings(),
    val font: FontSettings = FontSettings(),
    val navigation: NavigationSettings = NavigationSettings(),
    val list: ListSettings = ListSettings(),
    val calendar: CalendarSettings = CalendarSettings(),
    val overview: OverviewSettings = OverviewSettings(),
    val analytics: AnalyticsSettings = AnalyticsSettings(),
    val poster: PosterSettings = PosterSettings(),
    val share: ShareSettings = ShareSettings(),
    val search: SearchSettings = SearchSettings(),
    val addForm: AddFormSettings = AddFormSettings(),
    val subscriptions: SubscriptionPrefs = SubscriptionPrefs(),
    val money: MoneySettings = MoneySettings(),
    val notifications: NotificationSettings = NotificationSettings(),
    val security: SecuritySettings = SecuritySettings(),
    val integrations: IntegrationSettings = IntegrationSettings(),
    val onboardingCompleted: Boolean = false,
    val developerOptionsUnlocked: Boolean = false,
)

@Serializable
data class DisplaySettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val colorfulMode: Boolean = true,
    val appIcon: AppIcon = AppIcon.DEFAULT,
    val animatedBackground: Boolean = true,
    val haptics: Boolean = true,
    /** Material You wallpaper colors on API 31+. */
    val dynamicColor: Boolean = true,
)

@Serializable
data class FontSettings(
    val theme: FontTheme = FontTheme.MODERN,
    val family: FontFamilyOption = FontFamilyOption.SYSTEM,
    val size: FontSize = FontSize.MEDIUM,
    /** Line height multiplier, 0.9..1.5. */
    val lineSpacing: Float = 1.0f,
    val followSystemSize: Boolean = true,
)

@Serializable
data class NavigationSettings(
    val startupTab: StartupTab = StartupTab.OVERVIEW,
    val showOverviewTab: Boolean = true,
    val showCalendarTab: Boolean = true,
    val showAnalyticsTab: Boolean = true,
    val showPanelTab: Boolean = false,
    val floatingTabWidth: FloatingTabWidth = FloatingTabWidth.STANDARD,
    val globalSearchButton: Boolean = true,
)

/** Subscription list display options. */
@Serializable
data class ListSettings(
    val defaultStyle: DefaultListStyle = DefaultListStyle.LAST_USED,
    val lastStyle: ListStyle = ListStyle.LIST,
    val showCustomCycleAsYmd: Boolean = false,
    val showEndDateForFixedCycle: Boolean = false,
    val showIapTotalPrice: Boolean = false,
    val showLifetimeLabel: Boolean = true,
    val storedValueBalanceMode: Boolean = false,
    val showCopyNotesButton: Boolean = false,
)

@Serializable
data class CalendarSettings(
    val defaultMode: CalendarMode = CalendarMode.MONTH,
    val timelinePeriod: TimelinePeriod = TimelinePeriod.ONE_MONTH,
)

/** One entry of an orderable component list; list order is display order. */
@Serializable
data class ComponentSetting<T>(val id: T, val visible: Boolean = true)

@Serializable
data class OverviewSettings(
    val layout: OverviewLayout = OverviewLayout.MODERN,
    val period: SummaryPeriod = SummaryPeriod.MONTH,
    val mode: SpendingMode = SpendingMode.SUBSCRIPTIONS,
    val classicComponents: List<ComponentSetting<ClassicOverviewComponent>> =
        ClassicOverviewComponent.entries.map { ComponentSetting(it) },
    val modernComponents: List<ComponentSetting<ModernOverviewComponent>> =
        ModernOverviewComponent.entries.map { ComponentSetting(it) },
)

@Serializable
data class AnalyticsSettings(
    val subscriptionComponents: List<ComponentSetting<AnalyticsComponent>> =
        AnalyticsComponent.entries.map { ComponentSetting(it) },
    val lifetimeComponents: List<ComponentSetting<AnalyticsComponent>> =
        listOf(AnalyticsComponent.FINANCIAL_OVERVIEW, AnalyticsComponent.TREND, AnalyticsComponent.HEATMAP, AnalyticsComponent.CATEGORY)
            .map { ComponentSetting(it) },
    val defaultPeriod: SummaryPeriod = SummaryPeriod.MONTH,
    val trendPeriod: TrendPeriod = TrendPeriod.MONTHLY,
)

@Serializable
data class PosterSettings(
    val style: PosterStyle = PosterStyle.MODERN,
    val displayMode: PosterDisplayMode = PosterDisplayMode.SIMPLE,
    val hideAmounts: Boolean = false,
    val hideNames: Boolean = false,
    val hideNotes: Boolean = true,
    val hidePaymentDetails: Boolean = false,
    val showLogo: Boolean = true,
    val showGeneratedDate: Boolean = true,
    val showStatistics: Boolean = true,
)

@Serializable
data class ShareSettings(
    val sharerName: String = "",
    val qrPrivacyAcknowledged: Boolean = false,
)

@Serializable
data class SearchSettings(val recentSearches: List<String> = emptyList()) {
    companion object {
        const val MAX_RECENT = 10
    }
}

/** Add-subscription form modules; basic info and dates are always shown. */
@Serializable
data class AddFormSettings(
    val showPopularButton: Boolean = true,
    val showPopularBundles: Boolean = true,
    val showLocalBundleOption: Boolean = true,
    val showTrialOption: Boolean = true,
    val showEndDateOptions: Boolean = true,
    val showHistoricalPaymentsOption: Boolean = true,
    val showFixedPaymentDay: Boolean = true,
    val showPaymentMethodSelector: Boolean = true,
    val showWebsiteField: Boolean = true,
    val showNotesField: Boolean = true,
    val showAdditionalOptionsCard: Boolean = true,
    val showSharedSection: Boolean = true,
    val showTagManagement: Boolean = true,
) {
    companion object {
        val FULL = AddFormSettings()
        val MINIMAL = AddFormSettings(
            showPopularButton = true,
            showPopularBundles = false,
            showLocalBundleOption = false,
            showTrialOption = false,
            showEndDateOptions = false,
            showHistoricalPaymentsOption = false,
            showFixedPaymentDay = false,
            showPaymentMethodSelector = false,
            showWebsiteField = false,
            showNotesField = true,
            showAdditionalOptionsCard = false,
            showSharedSection = false,
            showTagManagement = true,
        )
    }
}

@Serializable
data class SubscriptionPrefs(
    /** Paused subscriptions leave the main list and show in the archive. */
    val archiveMode: Boolean = true,
    val wishlistEnabled: Boolean = true,
    val showChildSubscriptions: Boolean = false,
    val bundlePaymentSyncMode: BundlePaymentSyncMode = BundlePaymentSyncMode.SMART,
    val autoGenerateHistoryDefault: Boolean = true,
    val sortField: SortField = SortField.DATE,
    val sortDirection: SortDirection = SortDirection.ASC,
    val lastSegment: ListSegment = ListSegment.SUBSCRIPTIONS,
)

@Serializable
data class MoneySettings(
    /** Replaced by the device-locale currency on first launch (see AppSettingsSerializer). */
    val defaultCurrencyCode: String = "USD",
    val showCurrencyConversion: Boolean = true,
    /** In the default currency; null = no budget. */
    val annualBudget: Money? = null,
    val hideDecimalPlaces: Boolean = false,
    val financialDetailMode: FinancialDetailMode = FinancialDetailMode.SIMPLE,
    val financialDetailFilter: FinancialDetailFilter = FinancialDetailFilter.INCLUDE_LIFETIME,
)

@Serializable
data class NotificationSettings(
    val enabled: Boolean = false,
    /** 0 = payment day only; null = no advance reminder. */
    val advanceDays: Int? = 3,
    val firstTime: Time = LocalTime.of(9, 0),
    val secondTime: Time = LocalTime.of(14, 0),
    val thirdTime: Time = LocalTime.of(20, 0),
    val firstSlotEnabled: Boolean = true,
    val secondSlotEnabled: Boolean = true,
    val thirdSlotEnabled: Boolean = true,
    val userPreferenceSet: Boolean = false,
    val calendarSyncEnabled: Boolean = false,
)

@Serializable
data class SecuritySettings(
    /** Base64 salted hash of the passcode; null = no app lock. */
    val passcodeHash: String? = null,
    val passcodeSalt: String? = null,
    val biometricEnabled: Boolean = false,
    val failedAttempts: Int = 0,
    val lockoutUntil: Timestamp? = null,
) {
    val hasPasscode: Boolean get() = passcodeHash != null
}

@Serializable
data class IntegrationSettings(
    val priceMonitorEnabled: Boolean = false,
    val priceMonitorIntervalHours: Int = 6,
    val priceDropThresholdPercent: Int = 10,
    /** App Store storefront (ISO-3166 alpha-2, lowercase); null = device country. */
    val storeRegion: String? = null,
    val popularRegionScope: PopularRegionScope = PopularRegionScope.LOCAL,
    val popularSort: PopularSort = PopularSort.NAME,
    val aiEnabled: Boolean = false,
    val aiProvider: AiProvider? = null,
    val aiEndpoint: String? = null,
    val aiModel: String? = null,
    val panelMode: PanelMode = PanelMode.SUBSCRIPTIONS,
    val servicePanelTab: ServicePanelTab = ServicePanelTab.SERVICE,
)
