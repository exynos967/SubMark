package io.github.submark.feature.overview.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.currency.CurrencyRepository
import io.github.submark.core.data.repository.CategoryRepository
import io.github.submark.core.data.repository.PaymentMethodRepository
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.service.PaymentService
import io.github.submark.core.data.service.WalletService
import io.github.submark.core.data.service.SharedService
import io.github.submark.core.data.service.SubscriptionService
import io.github.submark.core.data.settings.ClassicOverviewComponent
import io.github.submark.core.data.settings.ComponentSetting
import io.github.submark.core.data.settings.ModernOverviewComponent
import io.github.submark.core.data.settings.OverviewLayout
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.settings.SummaryPeriod
import io.github.submark.core.data.settings.SpendingMode
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.domain.BillingCalculator
import io.github.submark.core.domain.CurrencyConverter
import io.github.submark.core.model.Currency
import io.github.submark.core.model.PaymentRecord
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.overview.R
import io.github.submark.feature.overview.data.DailyBucket
import io.github.submark.feature.overview.data.PriceMonitorReader
import io.github.submark.feature.overview.data.MonthlyPoint
import io.github.submark.feature.overview.data.PaymentOccurrence
import io.github.submark.feature.overview.data.PaymentProjection
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Section visibility + order driven by settings. */
data class OverviewUiState(
    val loading: Boolean = true,
    val layout: OverviewLayout = OverviewLayout.MODERN,
    val period: SummaryPeriod = SummaryPeriod.MONTH,
    val spendingMode: SpendingMode = SpendingMode.SUBSCRIPTIONS,

    // ---- Settings-driven component lists ----
    val classicComponents: List<ComponentSetting<ClassicOverviewComponent>> = emptyList(),
    val modernComponents: List<ComponentSetting<ModernOverviewComponent>> = emptyList(),

    // ---- Currency ----
    val defaultCurrencyCode: String = "USD",
    val currencySymbols: Map<String, String> = emptyMap(),
    val hideDecimalPlaces: Boolean = false,

    // ---- Shared math ----
    val paidThisPeriod: BigDecimal = BigDecimal.ZERO,
    val scheduledThisPeriod: BigDecimal = BigDecimal.ZERO,
    val periodProjectedTotal: BigDecimal = BigDecimal.ZERO,
    val monthlyAvg: BigDecimal = BigDecimal.ZERO,
    val annualTotal: BigDecimal = BigDecimal.ZERO,
    val lifetimeTotal: BigDecimal = BigDecimal.ZERO,

    // ---- Counts ----
    val subscriptionCount: Int = 0,
    val purchaseCount: Int = 0,

    // ---- Coming up ----
    val comingUp: List<PaymentOccurrence> = emptyList(),

    // ---- Payment calendar strip ----
    val scheduleStrip: Map<LocalDate, List<PaymentOccurrence>> = emptyMap(),
    /** Selected day for the mini-strip; defaults to null (= no strip selection). */
    val stripSelectedDate: LocalDate? = null,
    val stripAgenda: List<PaymentOccurrence> = emptyList(),

    // ---- Recent payments ----
    val recentPayments: List<PaymentOccurrence> = emptyList(),

    /** Raw occurrences map, used internally by classic cards (not exposed to UI). */
    val rawByDate: Map<LocalDate, List<PaymentOccurrence>> = emptyMap(),

    // ---- By category ----
    val categoryBreakdown: List<CategorySpend> = emptyList(),

    // ---- Trend ----
    val monthlyTrend: List<MonthlyPoint> = emptyList(),
    val hasEnoughTrendData: Boolean = false,

    // ---- Wallets ----
    val wallets: List<io.github.submark.core.model.Wallet> = emptyList(),
    val monthlyTimeline: List<DailyBucket> = emptyList(),

    // ---- Price monitor summary ----
    val wishlistPriceStatus: WishlistPriceState = WishlistPriceState.EMPTY,

    // ---- Mark-paid dialog ----
    val markTarget: MarkTarget? = null,

    // ---- Subscriptions for icon rendering ----
    val subscriptionById: Map<String, Subscription> = emptyMap(),

    /** Days since the first subscription was created (for the "Added N days" hero text). */
    val addedDays: Long = 0,

    /** Count of active recurring subscriptions (for the "Subscriptions: N" hero). */
    val activeCount: Int = 0,

    /** Overview budget fields; null = no budget set. */
    val annualBudget: BigDecimal? = null,
    val annualSpentYtd: BigDecimal = BigDecimal.ZERO,
    val budgetUsagePercent: Int? = null,

    /** Allows showing the payment records individually (e.g. classic mode). */
    val recentActivity: List<ActivityItem> = emptyList(),
)

data class MarkTarget(
    val subscriptionId: String,
    val name: String,
    val amount: BigDecimal,
    val currencyCode: String,
    val dueDate: LocalDate,
)

data class CategorySpend(
    val categoryId: String,
    val categoryName: String,
    val amount: BigDecimal,
    val colorHex: String?,
)

sealed interface ActivityItem {
    data class Row(val occurrence: PaymentOccurrence) : ActivityItem
}

sealed interface WishlistPriceState {
    data object EMPTY : WishlistPriceState
    data class Count(val count: Int) : WishlistPriceState
}

@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val subscriptions: SubscriptionRepository,
    private val categories: CategoryRepository,
    private val paymentMethods: PaymentMethodRepository,
    private val payments: PaymentService,
    private val shared: SharedService,
    private val subscriptionService: SubscriptionService,
    private val settings: SettingsRepository,
    private val currencies: CurrencyRepository,
    private val walletService: WalletService,
    private val priceMonitors: PriceMonitorReader,
    private val time: TimeProvider,
) : ViewModel() {

    private val markTargetFlow = MutableStateFlow<MarkTarget?>(null)
    private val stripSelectedDateFlow = MutableStateFlow<LocalDate?>(null)
    private val snackbarChannel = Channel<SnackbarMessage>(Channel.BUFFERED)
    val snackbars: Flow<SnackbarMessage> = snackbarChannel.receiveAsFlow()

    private data class Env(
        val settings: io.github.submark.core.data.settings.AppSettings,
        val today: LocalDate,
        val mark: MarkTarget?,
        val stripSelected: LocalDate?,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<OverviewUiState> = combine(
        settings.settings,
        markTargetFlow,
        stripSelectedDateFlow,
    ) { s, mark, strip -> Env(s, time.today(), mark, strip) }
        .flatMapLatest { env ->
            val (periodStart, periodEnd) = periodRange(env.settings.overview.period, env.today)
            combine(
                subscriptions.observeAll(),
                categories.observeAll(),
                payments.observeBetween(periodStart.minusMonths(3), periodEnd),
                currencies.observeCurrencies(),
                currencies.observeConverter(),
                walletService.observeWallets(),
                priceMonitors.observeSummary(),
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val subs = values[0] as List<Subscription>
                @Suppress("UNCHECKED_CAST")
                val cats = values[1] as List<io.github.submark.core.model.Category>
                @Suppress("UNCHECKED_CAST")
                val payList = values[2] as List<PaymentRecord>
                @Suppress("UNCHECKED_CAST")
                val curList = values[3] as List<Currency>
                @Suppress("UNCHECKED_CAST")
                val converter = values[4] as CurrencyConverter
                @Suppress("UNCHECKED_CAST")
                val wallets = values[5] as List<io.github.submark.core.model.Wallet>
                @Suppress("UNCHECKED_CAST")
                val priceSummary = values[6] as io.github.submark.feature.overview.data.PriceMonitorSummary
                buildState(env, subs, cats, payList, curList, converter, wallets, priceSummary)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())

    private fun buildState(
        env: Env,
        subs: List<Subscription>,
        cats: List<io.github.submark.core.model.Category>,
        payList: List<PaymentRecord>,
        curList: List<Currency>,
        converter: CurrencyConverter,
        wallets: List<io.github.submark.core.model.Wallet> = emptyList(),
        priceSummary: io.github.submark.feature.overview.data.PriceMonitorSummary? = null,
    ): OverviewUiState {
        val s = env.settings
        val today = env.today
        val includeChildren = s.subscriptions.showChildSubscriptions
        val visible = subs.filter { includeChildren || it.parentId == null }
        val defaultCode = s.money.defaultCurrencyCode
        val converterSafe = converter

        val (periodStart, periodEnd) = periodRange(s.overview.period, today)

        // Project occurrences over the visible span (today-3month .. periodEnd)
        val projection = PaymentProjection.project(
            subs = visible,
            payments = payList.filter { p -> visible.any { v -> v.id == p.subscriptionId } },
            from = periodStart.minusMonths(3),
            to = periodEnd,
            today = today,
            defaultCode = defaultCode,
            converter = converterSafe,
            includeChildren = includeChildren,
        )

        val byDate = projection.byDate
        val paidThisPeriod = projection.paid
        val scheduledThisPeriod = projection.scheduled
        val comingUp = PaymentProjection.comingUp(projection, today)
        val recentPayments = PaymentProjection.recentPaid(projection, today)

        // Category breakdown (SUBSCRIPTIONS mode: scheduled spending this period)
        val categoryMap = cats.associateBy { it.id }
        val subById = visible.associateBy { it.id }

        val subsMode = s.overview.mode == SpendingMode.SUBSCRIPTIONS
        val categorySpend = byDate
            .toList()
            .flatMap { (date, list) ->
                list.filter { occ ->
                    if (subsMode) {
                        occ.scheduled && !occ.paid && occ.date in periodStart..periodEnd
                    } else {
                        occ.paid && occ.subscription.kind == SubscriptionKind.LIFETIME
                    }
                }
            }
            .groupBy { occ ->
                if (occ.subscription.kind == SubscriptionKind.LIFETIME) "lifetime"
                else occ.subscription.categoryId
            }
            .mapNotNull { (id, list) ->
                val total = list.mapNotNull { it.amount }
                    .fold(BigDecimal.ZERO) { a, b -> a + b }
                if (total.signum() <= 0) return@mapNotNull null
                val name = when {
                    id == "lifetime" -> "Lifetime"
                    else -> categoryMap[id]?.name
                        ?: categoryMap[id]?.systemKey?.name?.let { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
                        ?: id.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
                CategorySpend(id, name, total, categoryMap[id]?.colorHex)
            }
            .sortedByDescending { it.amount }

        // Monthly trend: last 6 months of paid amounts
        val trendBuckets = lastSixMonths(today)
        val trend = trendBuckets.map { ym ->
            val ms = ym.atDay(1)
            val me = ym.atEndOfMonth()
            val paid = byDate.filterKeys { it in ms..me }
                .values.flatMap { it }.filter { it.paid }
                .mapNotNull { it.amount }.fold(BigDecimal.ZERO) { a, b -> a + b }
            MonthlyPoint(ms, paid)
        }

        // Monthly timeline (current calendar month, current week day by day)
        val monthStart = YearMonth.from(today).atDay(1)
        val monthEnd = YearMonth.from(today).atEndOfMonth()
        val monthlyTimeline = (0..ChronoUnit.DAYS.between(monthStart, monthEnd)).map { offset ->
            val date = monthStart.plusDays(offset)
            val dayOccurrences = byDate[date].orEmpty()
            val dayPaid = dayOccurrences.filter { it.paid }.mapNotNull { it.amount }.fold(BigDecimal.ZERO, BigDecimal::add)
            val dayScheduled = dayOccurrences.filter { it.scheduled && !it.paid }.mapNotNull { it.amount }.fold(BigDecimal.ZERO, BigDecimal::add)
            DailyBucket(date, dayPaid, dayScheduled, dayOccurrences.isNotEmpty())
        }

        // Wallet balances
        val walletBalances: List<io.github.submark.core.model.Wallet> = wallets

        // Annual budget usage (paid in the current calendar year, default currency).
        val yearStart = LocalDate.of(today.year, 1, 1)
        val annualBudget = s.money.annualBudget
        val annualSpentYtd = payList
            .filter { it.status == io.github.submark.core.model.PaymentStatus.SUCCESS && it.paymentDate in yearStart..today }
            .mapNotNull { converterSafe.convert(it.amount, it.currencyCode, defaultCode) }
            .fold(BigDecimal.ZERO) { a, b -> a + b }
        val budgetUsagePercent = annualBudget?.takeIf { it.signum() > 0 }?.let { budget ->
            annualSpentYtd.divide(budget, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal(100)).toInt()
        }

        // Counts
        val subCount = visible.count { it.kind == SubscriptionKind.REGULAR || it.kind == SubscriptionKind.STORED_VALUE }
        val purchaseCount = visible.count { it.kind == SubscriptionKind.LIFETIME }
        val activeCount = visible.count {
            it.status == SubscriptionStatus.ACTIVE &&
                (it.kind == SubscriptionKind.REGULAR || it.kind == SubscriptionKind.STORED_VALUE)
        }
        val firstCreated = visible.minByOrNull { it.createdAt }?.createdAt
        val addedDays = if (firstCreated != null) {
            ChronoUnit.DAYS.between(LocalDate.ofInstant(firstCreated, time.zone()), today)
        } else 0L

        // Annual expense = monthlyEquiv * 12 (CostCalculator approximation)
        val annualTotal = visible
            .filter { it.kind == SubscriptionKind.REGULAR || it.kind == SubscriptionKind.STORED_VALUE }
            .mapNotNull { io.github.submark.core.domain.CostCalculator.monthlyOf(it) }
            .fold(BigDecimal.ZERO, BigDecimal::add)
            .multiply(BigDecimal(12))

        val avgMonthly = visible
            .filter { it.status == SubscriptionStatus.ACTIVE && (it.kind == SubscriptionKind.REGULAR || it.kind == SubscriptionKind.STORED_VALUE) }
            .mapNotNull { sub -> io.github.submark.core.domain.CostCalculator.monthlyOf(sub)?.let { m -> converterSafe.convert(m, sub.currencyCode, defaultCode) } }
            .fold(BigDecimal.ZERO, BigDecimal::add)

        // Recent Activity for classic
        val recentActivity = (recentPayments.take(30) + comingUp.take(30))
            .distinctBy { it.subscription.id + it.date + it.payment?.id }
            .sortedBy { it.date }
            .map { ActivityItem.Row(it) }

        return OverviewUiState(
            loading = false,
            layout = s.overview.layout,
            period = s.overview.period,
            spendingMode = s.overview.mode,
            classicComponents = s.overview.classicComponents,
            modernComponents = s.overview.modernComponents,
            defaultCurrencyCode = defaultCode,
            currencySymbols = curList.associate { it.code to it.symbol },
            hideDecimalPlaces = s.money.hideDecimalPlaces,
            paidThisPeriod = paidThisPeriod,
            scheduledThisPeriod = scheduledThisPeriod,
            periodProjectedTotal = paidThisPeriod + scheduledThisPeriod,
            monthlyAvg = avgMonthly,
            annualTotal = annualTotal,
            lifetimeTotal = visible
                .filter { it.kind == SubscriptionKind.LIFETIME }
                .mapNotNull { converterSafe.convert(it.price, it.currencyCode, defaultCode) }
                .fold(BigDecimal.ZERO, BigDecimal::add),
            subscriptionCount = subCount,
            purchaseCount = purchaseCount,
            comingUp = comingUp,
            scheduleStrip = byDate.filterKeys { it >= today && it <= today.plusDays(6) },
            stripSelectedDate = env.stripSelected,
            stripAgenda = env.stripSelected?.let { byDate[it].orEmpty().filter { o -> !o.paid } }.orEmpty(),
            recentPayments = recentPayments,
            categoryBreakdown = categorySpend,
            monthlyTrend = trend,
            hasEnoughTrendData = trend.count { it.total.signum() > 0 } >= 2,
            monthlyTimeline = monthlyTimeline,
            wallets = walletBalances,
            wishlistPriceStatus = when {
                priceSummary == null || priceSummary.monitorCount == 0 -> WishlistPriceState.EMPTY
                else -> WishlistPriceState.Count(priceSummary.monitorCount)
            },
            markTarget = env.mark,
            subscriptionById = subById,
            addedDays = addedDays,
            activeCount = activeCount,
            annualBudget = annualBudget,
            annualSpentYtd = annualSpentYtd,
            budgetUsagePercent = budgetUsagePercent,
            recentActivity = recentActivity,
            rawByDate = byDate,
        )
    }

    fun setPeriod(period: SummaryPeriod) {
        viewModelScope.launch {
            settings.update { it.copy(overview = it.overview.copy(period = period)) }
        }
    }

    fun setLayout(layout: OverviewLayout) {
        viewModelScope.launch {
            settings.update { it.copy(overview = it.overview.copy(layout = layout)) }
        }
    }

    fun setSpendingMode(mode: SpendingMode) {
        viewModelScope.launch {
            settings.update { it.copy(overview = it.overview.copy(mode = mode)) }
        }
    }

    fun selectStripDate(date: LocalDate) {
        stripSelectedDateFlow.value = date
    }

    fun requestMarkPaid(occurrence: PaymentOccurrence) {
        val sub = occurrence.subscription
        markTargetFlow.value = MarkTarget(
            subscriptionId = sub.id,
            name = sub.name,
            amount = occurrence.amount ?: sub.price,
            currencyCode = sub.currencyCode,
            dueDate = occurrence.date,
        )
    }

    fun confirmMarkPaid(timing: io.github.submark.core.model.MarkTiming) {
        val target = markTargetFlow.value ?: return
        markTargetFlow.value = null
        viewModelScope.launch {
            val result = subscriptionService.markPaid(
                id = target.subscriptionId,
                newCycle = timing == io.github.submark.core.model.MarkTiming.EARLY_NEW_CYCLE ||
                    timing == io.github.submark.core.model.MarkTiming.OVERDUE_NEW_CYCLE,
            )
            when (result) {
                is io.github.submark.core.data.result.DataResult.Success -> snackbarChannel.send(
                    SnackbarMessage(io.github.submark.core.ui.format.UiText.res(R.string.overview_mark_paid_success, target.name)),
                )
                is io.github.submark.core.data.result.DataResult.Failure ->
                    snackbarChannel.send(SnackbarMessage(result.error.toOverviewUiText()))
            }
        }
    }

    fun dismissMarkPaid() {
        markTargetFlow.value = null
    }

    fun pauseSubscription(subscriptionId: String) {
        viewModelScope.launch {
            val result = subscriptionService.pause(subscriptionId)
            if (result is io.github.submark.core.data.result.DataResult.Success) {
                snackbarChannel.send(
                    SnackbarMessage(io.github.submark.core.ui.format.UiText.res(R.string.overview_paused_success)),
                )
            }
        }
    }
}

fun periodRange(period: SummaryPeriod, today: LocalDate): Pair<LocalDate, LocalDate> = when (period) {
    SummaryPeriod.MONTH -> YearMonth.from(today).let { it.atDay(1) to it.atEndOfMonth() }
    SummaryPeriod.QUARTER -> {
        val q = (today.monthValue - 1) / 3
        val start = LocalDate.of(today.year, q * 3 + 1, 1)
        start to start.plusMonths(3).minusDays(1)
    }
    SummaryPeriod.YEAR -> LocalDate.of(today.year, 1, 1) to LocalDate.of(today.year, 12, 31)
}

private fun lastSixMonths(today: LocalDate): List<YearMonth> =
    (5 downTo 0).map { YearMonth.from(today).minusMonths(it.toLong()) }

/** Pure preview math (for tests): added-days, active count. */

// Extension for OverviewError resolution (shared with search screen)
@get:androidx.annotation.StringRes
private val io.github.submark.core.data.result.InvalidReason.messageRes: Int
    get() = when (this) {
        io.github.submark.core.data.result.InvalidReason.NOT_MARKABLE -> R.string.overview_invalid_not_markable
        io.github.submark.core.data.result.InvalidReason.BLANK_NAME -> R.string.overview_invalid_blank_name
        else -> R.string.overview_invalid_generic
    }

fun io.github.submark.core.data.result.DataError.toOverviewUiText(): io.github.submark.core.ui.format.UiText = when (this) {
    io.github.submark.core.data.result.DataError.NotFound ->
        io.github.submark.core.ui.format.UiText.res(R.string.overview_error_not_found)
    is io.github.submark.core.data.result.DataError.Invalid ->
        io.github.submark.core.ui.format.UiText.res(reason.messageRes)
    is io.github.submark.core.data.result.DataError.InUse ->
        io.github.submark.core.ui.format.UiText.res(R.string.overview_error_in_use)
    is io.github.submark.core.data.result.DataError.DuplicateAppStoreId ->
        io.github.submark.core.ui.format.UiText.res(R.string.overview_error_duplicate_app)
    is io.github.submark.core.data.result.DataError.InsufficientFunds ->
        io.github.submark.core.ui.format.UiText.res(R.string.overview_error_insufficient_funds)
    is io.github.submark.core.data.result.DataError.RateUnavailable ->
        io.github.submark.core.ui.format.UiText.res(R.string.overview_error_rate_unavailable, currencyCode)
    is io.github.submark.core.data.result.DataError.UnsupportedCurrency ->
        io.github.submark.core.ui.format.UiText.res(R.string.overview_error_unsupported_currency, currencyCode)
    io.github.submark.core.data.result.DataError.Stale ->
        io.github.submark.core.ui.format.UiText.res(R.string.overview_error_stale)
    is io.github.submark.core.data.result.DataError.Network ->
        io.github.submark.core.ui.format.UiText.res(R.string.overview_error_network)
}
