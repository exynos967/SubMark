package io.github.submark.feature.money.ui.financial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.currency.CurrencyRepository
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.service.PaymentService
import io.github.submark.core.data.settings.FinancialDetailFilter
import io.github.submark.core.data.settings.FinancialDetailMode
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.domain.CostCalculator
import io.github.submark.core.domain.CurrencyConverter
import io.github.submark.core.model.PaymentStatus
import io.github.submark.core.ui.format.UiText
import io.github.submark.feature.money.data.MoneyEnv
import io.github.submark.feature.money.data.MoneyEnvSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class FinPeriod { MONTH, QUARTER, YEAR, CUSTOM }

data class FinRange(val from: LocalDate, val to: LocalDate)

data class FinancialDetailUiState(
    val loading: Boolean = true,
    val period: FinPeriod = FinPeriod.MONTH,
    val filter: FinancialDetailFilter = FinancialDetailFilter.INCLUDE_LIFETIME,
    val mode: FinancialDetailMode = FinancialDetailMode.SIMPLE,
    val customFrom: LocalDate = LocalDate.MIN,
    val customTo: LocalDate = LocalDate.MIN,
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val result: FinancialResult? = null,
    val range: FinRange? = null,
    val theoreticalMonthly: BigDecimal? = null,
    val error: UiText? = null,
    val env: MoneyEnv = MoneyEnv.EMPTY,
) {
    val converting: Boolean get() = progressTotal > 0 && progressDone < progressTotal
}

@HiltViewModel
class FinancialDetailViewModel @Inject constructor(
    private val payments: PaymentService,
    private val subscriptions: SubscriptionRepository,
    private val currencies: CurrencyRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
    envSource: MoneyEnvSource,
) : ViewModel() {
    private val period = MutableStateFlow(FinPeriod.MONTH)
    private val customFrom = MutableStateFlow(time.today())
    private val customTo = MutableStateFlow(time.today())
    private val progress = MutableStateFlow(0 to 0)
    private val result = MutableStateFlow<FinancialResult?>(null)
    private val range = MutableStateFlow<FinRange?>(null)
    private val theoretical = MutableStateFlow<BigDecimal?>(null)
    private val error = MutableStateFlow<UiText?>(null)
    private var job: Job? = null

    val uiState: StateFlow<FinancialDetailUiState> = combine(
        combine(period, customFrom, customTo, ::Triple),
        combine(progress, result, range, theoretical, error) { p, r, rng, th, err -> SnapshotInputs(p, r, rng, th, err) },
        settings.settings,
        envSource.observe(),
    ) { (p, cf, ct), inputs, s, env ->
        FinancialDetailUiState(
            loading = false,
            period = p,
            filter = s.money.financialDetailFilter,
            mode = s.money.financialDetailMode,
            customFrom = cf,
            customTo = ct,
            progressDone = inputs.progress.first,
            progressTotal = inputs.progress.second,
            result = inputs.result,
            range = inputs.range,
            theoreticalMonthly = inputs.theoretical,
            error = inputs.error,
            env = env,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinancialDetailUiState())

    private data class SnapshotInputs(
        val progress: Pair<Int, Int>,
        val result: FinancialResult?,
        val range: FinRange?,
        val theoretical: BigDecimal?,
        val error: UiText?,
    )

    init {
        recompute()
    }

    fun setPeriod(p: FinPeriod) {
        if (period.value != p) {
            period.value = p
            recompute()
        }
    }

    fun setCustomFrom(d: LocalDate) {
        customFrom.value = d
        if (d > customTo.value) customTo.value = d
        recompute()
    }

    fun setCustomTo(d: LocalDate) {
        customTo.value = d
        if (d < customFrom.value) customFrom.value = d
        recompute()
    }

    fun setFilter(filter: FinancialDetailFilter) {
        viewModelScope.launch {
            settings.update { it.copy(money = it.money.copy(financialDetailFilter = filter)) }
            recompute()
        }
    }

    fun setMode(mode: FinancialDetailMode) {
        viewModelScope.launch {
            settings.update { it.copy(money = it.money.copy(financialDetailMode = mode)) }
        }
    }

    fun recompute() {
        job?.cancel()
        job = viewModelScope.launch { doRecompute() }
    }

    private suspend fun doRecompute() {
        val defaultCode = settings.settings.first().money.defaultCurrencyCode
        val filter = settings.settings.first().money.financialDetailFilter
        val (from, to) = rangeFor(period.value, customFrom.value, customTo.value)
        range.value = FinRange(from, to)
        result.value = null
        error.value = null
        progress.value = 0 to 0

        val subs = subscriptions.getAll().associateBy { it.id }
        val records = payments.observeBetween(from, to).first().filter { it.status == PaymentStatus.SUCCESS && it.paymentDate in from..to }

        val convertedConverters: MutableMap<LocalDate, CurrencyConverter> = LinkedHashMap()
        val dates = records.map { it.paymentDate }.distinct().sorted()
        progress.value = 0 to dates.size
        dates.forEachIndexed { i, d ->
            convertedConverters[d] = currencies.historicalConverter(d)
            progress.value = (i + 1) to dates.size
        }
        result.value = FinancialAggregator.aggregate(records, subs, filter, defaultCode, convertedConverters)
        theoretical.value = computeTheoretical(subscriptions.getAll(), defaultCode)
        progress.value = 0 to 0
    }

    private suspend fun computeTheoretical(items: List<io.github.submark.core.model.Subscription>, defaultCode: String): BigDecimal? {
        val converter = currencies.converter()
        var acc: BigDecimal? = null
        items.forEach { sub ->
            val monthly = CostCalculator.monthlyOf(sub) ?: return@forEach
            val converted = converter.convert(monthly, sub.currencyCode, defaultCode) ?: return@forEach
            acc = (acc ?: BigDecimal.ZERO) + converted
        }
        return acc
    }

    private fun rangeFor(p: FinPeriod, cf: LocalDate, ct: LocalDate): Pair<LocalDate, LocalDate> = when (p) {
        FinPeriod.MONTH -> {
            val m = YearMonth.from(time.today())
            m.atDay(1) to m.atEndOfMonth()
        }
        FinPeriod.QUARTER -> {
            val t = time.today()
            val q = (t.monthValue - 1) / 3 * 3 + 1
            val from = LocalDate.of(t.year, q, 1)
            from to YearMonth.of(t.year, q + 2).atEndOfMonth()
        }
        FinPeriod.YEAR -> LocalDate.of(time.today().year, 1, 1) to LocalDate.of(time.today().year, 12, 31)
        FinPeriod.CUSTOM -> cf to ct
    }
}
