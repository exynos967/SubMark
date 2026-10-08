package io.github.submark.feature.share.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.currency.CurrencyRepository
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.service.PaymentService
import io.github.submark.core.data.settings.PosterDisplayMode
import io.github.submark.core.data.settings.PosterStyle
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.model.PaymentRecord
import io.github.submark.core.model.PaymentStatus
import io.github.submark.core.model.Subscription
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class ReportMonth(
    val yearMonth: YearMonth,
    val payments: List<PaymentRecord>,
    val subtotal: BigDecimal,
)

data class ReportUiState(
    val loading: Boolean = true,
    val style: PosterStyle = PosterStyle.MODERN,
    val displayMode: PosterDisplayMode = PosterDisplayMode.SIMPLE,
    val hideAmounts: Boolean = false,
    val hideNames: Boolean = false,
    val hideNotes: Boolean = true,
    val hidePaymentDetails: Boolean = false,
    val showLogo: Boolean = true,
    val showGeneratedDate: Boolean = true,
    val showStatistics: Boolean = true,
    val periodStart: LocalDate? = null,
    val periodEnd: LocalDate? = null,
    val periodValid: Boolean = true,
    val total: BigDecimal = BigDecimal.ZERO,
    val expenseMonths: Int = 0,
    val paymentCount: Int = 0,
    val averagePayment: BigDecimal = BigDecimal.ZERO,
    val months: List<ReportMonth> = emptyList(),
    val subscriptionNames: Map<String, String> = emptyMap(),
    val defaultCurrencyCode: String = "USD",
    val currencySymbols: Map<String, String> = emptyMap(),
    val generatedDate: LocalDate? = null,
)

enum class PeriodKind { MONTH, RANGE }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FinancialReportPosterViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val payments: PaymentService,
    private val subscriptions: SubscriptionRepository,
    private val currencies: CurrencyRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val today = time.today()
    private val periodFlow = MutableStateFlow<Pair<LocalDate, LocalDate>>(
        YearMonth.from(today).atDay(1) to YearMonth.from(today).atEndOfMonth(),
    )

    val uiState: StateFlow<ReportUiState> = combine(settings.settings, periodFlow) { s, period ->
        s to period
    }
        .flatMapLatest { (s, period) ->
            val (start, end) = period
            combine(
                payments.observeBetween(start, end),
                subscriptions.observeAll(),
                currencies.observeCurrencies(),
            ) { pays, subs, curs ->
                buildState(s, start, end, pays, subs.associateBy { it.id }, curs.associate { it.code to it.symbol })
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportUiState())

    private suspend fun buildState(
        s: io.github.submark.core.data.settings.AppSettings,
        start: LocalDate,
        end: LocalDate,
        allPays: List<PaymentRecord>,
        subsById: Map<String, Subscription>,
        symbols: Map<String, String>,
    ): ReportUiState {
        val relevant = allPays.filter { it.status == PaymentStatus.SUCCESS || it.status == PaymentStatus.PENDING }
        val names = subsById.values.associate { it.id to it.name }

        // Group by month, converted to default currency at payment-date historical rates handled
        // by caller display; sums stay in the payment's own currency unless convertible.
        val defaultCode = s.money.defaultCurrencyCode
        val byMonth = relevant.groupBy { YearMonth.from(it.paymentDate) }.toSortedMap()
        val months = byMonth.map { (ym, list) ->
            val subtotal = list.fold(BigDecimal.ZERO) { a, p -> a + convertSafe(p, defaultCode) }
            ReportMonth(ym, list.sortedBy { it.paymentDate }, subtotal)
        }
        val total = months.fold(BigDecimal.ZERO) { a, m -> a + m.subtotal }
        val count = relevant.size
        val average = if (count > 0) total.divide(BigDecimal(count), 2, RoundingMode.HALF_UP) else BigDecimal.ZERO
        val valid = !end.isBefore(start) && start != end

        return ReportUiState(
            loading = false,
            style = s.poster.style,
            displayMode = s.poster.displayMode,
            hideAmounts = s.poster.hideAmounts,
            hideNames = s.poster.hideNames,
            hideNotes = s.poster.hideNotes,
            hidePaymentDetails = s.poster.hidePaymentDetails,
            showLogo = s.poster.showLogo,
            showGeneratedDate = s.poster.showGeneratedDate,
            showStatistics = s.poster.showStatistics,
            periodStart = start,
            periodEnd = end,
            periodValid = valid,
            total = total,
            expenseMonths = months.count { it.subtotal.signum() > 0 },
            paymentCount = count,
            averagePayment = average,
            months = months,
            subscriptionNames = names,
            defaultCurrencyCode = defaultCode,
            currencySymbols = symbols,
            generatedDate = today,
        )
    }

    private suspend fun convertSafe(p: PaymentRecord, defaultCode: String): BigDecimal {
        if (p.currencyCode == defaultCode) return p.amount
        val converter = currencies.historicalConverter(p.paymentDate)
        return converter.convert(p.amount, p.currencyCode, defaultCode) ?: BigDecimal.ZERO
    }

    fun setMonth(month: YearMonth) {
        periodFlow.value = month.atDay(1) to month.atEndOfMonth()
    }

    fun setRange(start: LocalDate, end: LocalDate) {
        periodFlow.value = start to end
    }

    fun setStyle(style: PosterStyle) = updatePoster { it.copy(style = style) }
    fun setDisplayMode(mode: PosterDisplayMode) = updatePoster { it.copy(displayMode = mode) }
    fun setHideAmounts(v: Boolean) = updatePoster { it.copy(hideAmounts = v) }
    fun setHideNames(v: Boolean) = updatePoster { it.copy(hideNames = v) }
    fun setHideNotes(v: Boolean) = updatePoster { it.copy(hideNotes = v) }
    fun setHidePaymentDetails(v: Boolean) = updatePoster { it.copy(hidePaymentDetails = v) }
    fun setShowLogo(v: Boolean) = updatePoster { it.copy(showLogo = v) }
    fun setShowGeneratedDate(v: Boolean) = updatePoster { it.copy(showGeneratedDate = v) }
    fun setShowStatistics(v: Boolean) = updatePoster { it.copy(showStatistics = v) }

    private fun updatePoster(transform: (io.github.submark.core.data.settings.PosterSettings) -> io.github.submark.core.data.settings.PosterSettings) {
        viewModelScope.launch {
            settings.update { s -> s.copy(poster = transform(s.poster)) }
        }
    }
}
