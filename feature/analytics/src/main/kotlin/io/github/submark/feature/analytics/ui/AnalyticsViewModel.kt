package io.github.submark.feature.analytics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.currency.CurrencyRepository
import io.github.submark.core.data.repository.CategoryRepository
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.service.PaymentService
import io.github.submark.core.data.settings.AnalyticsComponent
import io.github.submark.core.data.settings.AppSettings
import io.github.submark.core.data.settings.ComponentSetting
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.settings.SpendingMode
import io.github.submark.core.data.settings.SummaryPeriod
import io.github.submark.core.data.settings.TrendPeriod
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.domain.CurrencyConverter
import io.github.submark.core.model.Category
import io.github.submark.core.model.PaymentRecord
import io.github.submark.core.model.PaymentStatus
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import io.github.submark.feature.analytics.data.CategoryTotal
import io.github.submark.feature.analytics.data.HeatmapCell
import io.github.submark.feature.analytics.data.PaidAmount
import io.github.submark.feature.analytics.data.RadarMetrics
import io.github.submark.feature.analytics.data.TrendBucket
import io.github.submark.feature.analytics.data.categoryTotals
import io.github.submark.feature.analytics.data.foldTail
import io.github.submark.feature.analytics.data.heatmap
import io.github.submark.feature.analytics.data.heatmapStats
import io.github.submark.feature.analytics.data.monthlyTrend
import io.github.submark.feature.analytics.data.radarMetrics
import io.github.submark.feature.analytics.data.trendStats
import io.github.submark.feature.analytics.data.yearlyTrend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class AnalyticsUiState(
    val loading: Boolean = true,
    /** Historical-rate preload progress 0..100 while warming the cache, null when done. */
    val rateProgress: Int? = null,
    val mode: SpendingMode = SpendingMode.SUBSCRIPTIONS,
    val components: List<ComponentSetting<AnalyticsComponent>> = emptyList(),
    val period: SummaryPeriod = SummaryPeriod.MONTH,
    val trendPeriod: TrendPeriod = TrendPeriod.MONTHLY,
    val defaultCurrencyCode: String = "USD",
    val currencySymbols: Map<String, String> = emptyMap(),
    val hideDecimalPlaces: Boolean = false,

    /** Any successful payments at all in the current mode (drives the no-data state). */
    val hasAnyPayments: Boolean = false,

    // Financial overview
    val totalSpending: BigDecimal = BigDecimal.ZERO,
    val activeCount: Int = 0,
    val lifetimeCount: Int = 0,
    val dailyAverage: BigDecimal = BigDecimal.ZERO,
    val annualBudget: BigDecimal? = null,
    val spentYearToDate: BigDecimal = BigDecimal.ZERO,

    // Trend
    val trendBuckets: List<TrendBucket> = emptyList(),
    val trendTotal: BigDecimal = BigDecimal.ZERO,
    val trendAverage: BigDecimal = BigDecimal.ZERO,
    val trendPeakIndex: Int? = null,

    // Heatmap
    val heatmapCells: List<HeatmapCell> = emptyList(),
    val heatmapTotal: Int = 0,
    val heatmapAverage: BigDecimal = BigDecimal.ZERO,
    val heatmapPeakIndex: Int? = null,

    // Category
    val categoryTotals: List<CategoryTotal> = emptyList(),
    val categoryNames: Map<String, String> = emptyMap(),
    /** Total number of distinct categories before top-7 folding. */
    val categoryShownCount: Int = 0,

    // Radar
    val radarAxes: List<String> = emptyList(),
    val radar: RadarMetrics? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val subscriptions: SubscriptionRepository,
    private val payments: PaymentService,
    private val categories: CategoryRepository,
    private val currencies: CurrencyRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val modeFlow = MutableStateFlow<SpendingMode?>(null)
    private val categoryExpandedFlow = MutableStateFlow(false)
    private val rateProgressFlow = MutableStateFlow<Int?>(null)

    init {
        // Warm the historical-rate cache in the background so per-payment conversion hits cache.
        // Progress is surfaced as a percent in the header while it runs.
        viewModelScope.launch {
            rateProgressFlow.value = 0
            currencies.preloadRecent(days = 365 * 3) { done, total ->
                rateProgressFlow.value = if (total > 0) (done * 100 / total).coerceIn(0, 100) else null
            }
            rateProgressFlow.value = null
        }
    }

    private data class Loaded(
        val settings: AppSettings,
        val mode: SpendingMode,
        val today: LocalDate,
        val subs: List<Subscription>,
        val cats: List<Category>,
        val pays: List<PaymentRecord>,
        val curs: List<io.github.submark.core.model.Currency>,
        val categoryExpanded: Boolean,
    )

    val uiState: StateFlow<AnalyticsUiState> =
        combine(settings.settings, modeFlow, categoryExpandedFlow) { s, m, expanded -> Triple(s, m, expanded) }
            .flatMapLatest { (s, modeOverride, expanded) ->
                combine(
                    subscriptions.observeAll(),
                    categories.observeAll(),
                    payments.observeBetween(LocalDate.of(time.today().year - 2, 1, 1), time.today()),
                    currencies.observeCurrencies(),
                ) { subs, cats, pays, curs ->
                    Loaded(
                        settings = s,
                        mode = modeOverride ?: s.overview.mode,
                        today = time.today(),
                        subs = subs,
                        cats = cats,
                        pays = pays,
                        curs = curs,
                        categoryExpanded = expanded,
                    )
                }
            }
            .flatMapLatest { loaded -> flow { emit(buildState(loaded)) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())

    private suspend fun buildState(loaded: Loaded): AnalyticsUiState {
        val s = loaded.settings
        val mode = loaded.mode
        val today = loaded.today
        val components = if (mode == SpendingMode.SUBSCRIPTIONS) s.analytics.subscriptionComponents else s.analytics.lifetimeComponents
        val subsById = loaded.subs.associateBy { it.id }
        val isLifetimeMode = mode == SpendingMode.LIFETIME

        fun modeOf(sub: Subscription?): Boolean = when {
            sub == null -> false
            sub.parentId != null -> modeOf(subsById[sub.parentId]) // bundle children follow their parent
            isLifetimeMode -> sub.kind == SubscriptionKind.LIFETIME
            else -> sub.kind == SubscriptionKind.REGULAR || sub.kind == SubscriptionKind.STORED_VALUE
        }

        val modePays = loaded.pays.filter { it.status == PaymentStatus.SUCCESS && modeOf(subsById[it.subscriptionId]) }
        val defaultCode = s.money.defaultCurrencyCode

        // Convert every payment with the historical converter of its date.
        val convertersByDate = mutableMapOf<LocalDate, CurrencyConverter>()
        val paid: List<PaidAmount> = modePays.map { p ->
            val converter = convertersByDate.getOrPut(p.paymentDate) { currencies.historicalConverter(p.paymentDate) }
            val sub = subsById[p.subscriptionId]
            val amount = converter.convert(p.amount, p.currencyCode, defaultCode) ?: BigDecimal.ZERO
            PaidAmount(
                date = p.paymentDate,
                amount = amount,
                categoryKey = categoryKeyOf(sub),
            )
        }

        // Financial overview --------------------------------------------------
        val (periodStart, periodEnd) = periodRange(s.analytics.defaultPeriod, today)
        val inPeriod = paid.filter { it.date in periodStart..periodEnd }
        val totalSpending = inPeriod.fold(BigDecimal.ZERO) { a, b -> a + b.amount }
        val periodDays = ChronoUnit.DAYS.between(periodStart, minOf(periodEnd, today)) + 1
        val dailyAverage = if (periodDays > 0) {
            totalSpending.divide(BigDecimal(periodDays), java.math.MathContext.DECIMAL64)
        } else BigDecimal.ZERO

        val activeCount = loaded.subs.count {
            it.parentId == null && it.status == SubscriptionStatus.ACTIVE &&
                (it.kind == SubscriptionKind.REGULAR || it.kind == SubscriptionKind.STORED_VALUE)
        }
        val lifetimeCount = loaded.subs.count { it.parentId == null && it.kind == SubscriptionKind.LIFETIME }
        val yearStart = LocalDate.of(today.year, 1, 1)
        val spentYtd = paid.filter { it.date >= yearStart }.fold(BigDecimal.ZERO) { a, b -> a + b.amount }

        // Trend ----------------------------------------------------------------
        val buckets = if (s.analytics.trendPeriod == TrendPeriod.MONTHLY) monthlyTrend(paid, today) else yearlyTrend(paid, today)
        val tStats = trendStats(buckets)

        // Heatmap ----------------------------------------------------------------
        val cells = heatmap(paid, today)
        val hStats = heatmapStats(cells)

        // Category ----------------------------------------------------------------
        val totals = categoryTotals(paid.filter { it.date in periodStart..periodEnd })
        val names: Map<String, String> = buildMap {
            loaded.cats.forEach { c -> this[c.id] = categoryName(c) }
            this["lifetime"] = "Lifetime"  // replaced by composable with localized label
            this["other"] = "Other"
            this["@other"] = "Other"
        }
        val shown = if (loaded.categoryExpanded) totals else foldTail(totals, 7)

        // Radar over the same period, top 7 named categories ----------------------
        val radarTotals = totals.filter { it.categoryKey != "@other" && it.categoryKey != "other" }.take(7)
        val metrics = radarMetrics(radarTotals)

        return AnalyticsUiState(
            loading = false,
            rateProgress = null,
            mode = mode,
            components = components,
            period = s.analytics.defaultPeriod,
            trendPeriod = s.analytics.trendPeriod,
            defaultCurrencyCode = defaultCode,
            currencySymbols = loaded.curs.associate { it.code to it.symbol },
            hideDecimalPlaces = s.money.hideDecimalPlaces,
            hasAnyPayments = paid.isNotEmpty(),
            totalSpending = totalSpending,
            activeCount = activeCount,
            lifetimeCount = lifetimeCount,
            dailyAverage = dailyAverage,
            annualBudget = s.money.annualBudget,
            spentYearToDate = spentYtd,
            trendBuckets = buckets,
            trendTotal = tStats.total,
            trendAverage = tStats.average,
            trendPeakIndex = tStats.peakIndex,
            heatmapCells = cells,
            heatmapTotal = hStats.total,
            heatmapAverage = hStats.averagePerDay,
            heatmapPeakIndex = hStats.peakIndex,
            categoryTotals = shown,
            categoryNames = names,
            categoryShownCount = totals.size,
            radarAxes = radarTotals.map { names[it.categoryKey] ?: it.categoryKey },
            radar = metrics,
        )
    }

    private fun categoryKeyOf(sub: Subscription?): String = when {
        sub == null -> "other"
        sub.kind == SubscriptionKind.LIFETIME -> "lifetime"
        else -> sub.categoryId
    }

    private fun categoryName(c: Category): String =
        c.name ?: c.systemKey?.name?.lowercase()?.replaceFirstChar { it.titlecase() } ?: "Other"

    fun setMode(mode: SpendingMode) {
        modeFlow.value = mode
    }

    fun setCategoryExpanded(expanded: Boolean) {
        categoryExpandedFlow.value = expanded
    }

    fun setPeriod(period: SummaryPeriod) {
        viewModelScope.launch {
            settings.update { it.copy(analytics = it.analytics.copy(defaultPeriod = period)) }
        }
    }

    fun setTrendPeriod(period: TrendPeriod) {
        viewModelScope.launch {
            settings.update { it.copy(analytics = it.analytics.copy(trendPeriod = period)) }
        }
    }

    companion object {
        fun periodRange(period: SummaryPeriod, today: LocalDate): Pair<LocalDate, LocalDate> = when (period) {
            SummaryPeriod.MONTH -> today.withDayOfMonth(1) to today.withDayOfMonth(today.lengthOfMonth())
            SummaryPeriod.QUARTER -> {
                val firstMonth = today.month.firstMonthOfQuarter()
                val start = LocalDate.of(today.year, firstMonth, 1)
                start to start.plusMonths(3).minusDays(1)
            }
            SummaryPeriod.YEAR -> LocalDate.of(today.year, 1, 1) to LocalDate.of(today.year, 12, 31)
        }
    }
}
