package io.github.submark.feature.calendar.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.currency.CurrencyRepository
import io.github.submark.core.data.repository.CategoryRepository
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.service.PaymentService
import io.github.submark.core.data.service.SharedService
import io.github.submark.core.data.service.SubscriptionService
import io.github.submark.core.data.settings.AppSettings
import io.github.submark.core.data.settings.CalendarMode
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.settings.TimelinePeriod
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.model.Currency
import io.github.submark.core.model.MarkTiming
import io.github.submark.core.model.PaymentRecord
import io.github.submark.core.model.Subscription
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.calendar.R
import io.github.submark.feature.calendar.data.CalendarScenario
import io.github.submark.feature.calendar.data.anchorFor
import io.github.submark.feature.calendar.data.Occurrence
import io.github.submark.feature.calendar.data.OccurrenceProjector
import io.github.submark.feature.calendar.data.TimelineBucket
import io.github.submark.feature.calendar.data.timelineBuckets
import io.github.submark.feature.calendar.ui.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

/** The "mark paid" dialog target: which occurrence is being marked. */
data class MarkTarget(
    val subscriptionId: String,
    val name: String,
    val amount: BigDecimal,
    val currencyCode: String,
    val dueDate: LocalDate,
)

data class CalendarUiState(
    val loading: Boolean = true,
    val scenario: CalendarScenario? = null,
    /** Occurrences inside [CalendarScenario.range]. */
    val byDate: Map<LocalDate, List<Occurrence>> = emptyMap(),
    /** Agenda rows of the selected day. */
    val agenda: List<Occurrence> = emptyList(),
    val timeline: List<TimelineBucket> = emptyList(),
    val defaultCurrencyCode: String = "USD",
    val currencySymbols: Map<String, String> = emptyMap(),
    val includeChildren: Boolean = false,
    val markTarget: MarkTarget? = null,
    val hasSubscriptions: Boolean = true,
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val subscriptions: SubscriptionRepository,
    private val categories: CategoryRepository,
    private val payments: PaymentService,
    private val shared: SharedService,
    private val subscriptionService: SubscriptionService,
    private val settings: SettingsRepository,
    private val currencies: CurrencyRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val scenarioFlow = MutableStateFlow<CalendarScenario?>(null)
    private val markTargetFlow = MutableStateFlow<MarkTarget?>(null)
    private val snackbarChannel = Channel<SnackbarMessage>(Channel.BUFFERED)
    val snackbars: Flow<SnackbarMessage> = snackbarChannel.receiveAsFlow()

    private data class Env(
        val settings: AppSettings,
        val scenario: CalendarScenario,
        val mark: MarkTarget?,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CalendarUiState> = combine(
        settings.settings,
        scenarioFlow,
        markTargetFlow,
    ) { s, scenario, mark -> Env(s, scenario ?: defaultScenario(s), mark) }
        .flatMapLatest { env ->
            val (from, to) = env.scenario.range()
            val today = time.today()
            combine(
                subscriptions.observeAll(),
                categories.observeAll(),
                payments.observeBetween(from, to),
                currencies.observeCurrencies(),
                currencies.observeConverter(),
            ) { subs, cats, payList, curList, converter ->
                buildState(env, today, subs, cats, payList, curList, converter)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    init {
        viewModelScope.launch {
            val first = settings.settings.first()
            if (scenarioFlow.value == null) scenarioFlow.value = defaultScenario(first)
        }
    }

    private fun defaultScenario(s: AppSettings): CalendarScenario {
        val today = time.today()
        return CalendarScenario(
            mode = s.calendar.defaultMode,
            timelinePeriod = s.calendar.timelinePeriod,
            anchorDate = s.calendar.defaultMode.anchorFor(today),
            selectedDate = today,
            today = today,
        )
    }

    private fun buildState(
        env: Env,
        today: LocalDate,
        subs: List<Subscription>,
        cats: List<io.github.submark.core.model.Category>,
        payList: List<PaymentRecord>,
        curList: List<Currency>,
        converter: io.github.submark.core.domain.CurrencyConverter,
    ): CalendarUiState {
        val s = env.settings
        val scenario = env.scenario
        val includeChildren = s.subscriptions.showChildSubscriptions
        val visible = subs.filter { includeChildren || it.parentId == null }
        val byDate = OccurrenceProjector.project(
            subs = visible,
            payments = payList.filter { p -> visible.any { v -> v.id == p.subscriptionId } },
            from = scenario.range().first,
            to = scenario.range().second,
            defaultCode = s.money.defaultCurrencyCode,
            converter = converter,
            categoriesById = cats.associateBy { it.id },
            includeChildren = includeChildren,
        )
        val agenda = byDate[scenario.selectedDate].orEmpty()
        val timeline = if (scenario.mode == CalendarMode.TIMELINE) {
            timelineBuckets(byDate, today, scenario.timelinePeriod)
        } else emptyList()
        return CalendarUiState(
            loading = false,
            scenario = scenario,
            byDate = byDate,
            agenda = agenda,
            timeline = timeline,
            defaultCurrencyCode = s.money.defaultCurrencyCode,
            currencySymbols = curList.associate { it.code to it.symbol },
            includeChildren = includeChildren,
            markTarget = env.mark,
            hasSubscriptions = subs.isNotEmpty(),
        )
    }

    fun setMode(mode: CalendarMode) {
        viewModelScope.launch {
            settings.update { it.copy(calendar = it.calendar.copy(defaultMode = mode)) }
            scenarioFlow.value?.let { sc -> scenarioFlow.value = sc.withMode(mode) }
        }
    }

    fun setTimelinePeriod(period: TimelinePeriod) {
        viewModelScope.launch {
            settings.update { it.copy(calendar = it.calendar.copy(timelinePeriod = period)) }
            scenarioFlow.value?.let { sc -> scenarioFlow.value = sc.withTimelinePeriod(period) }
        }
    }

    fun previous() {
        scenarioFlow.value = scenarioFlow.value?.previous()
    }

    fun next() {
        scenarioFlow.value = scenarioFlow.value?.next()
    }

    fun goToday() {
        scenarioFlow.value = scenarioFlow.value?.goToday()
    }

    fun select(date: LocalDate) {
        scenarioFlow.value = scenarioFlow.value?.select(date)
    }

    fun jumpTo(date: LocalDate) {
        scenarioFlow.value = scenarioFlow.value?.jumpTo(date)
    }

    /** Open the mark-paid dialog for [occurrence]. */
    fun requestMarkPaid(occurrence: Occurrence) {
        val sub = occurrence.subscription
        markTargetFlow.value = MarkTarget(
            subscriptionId = sub.id,
            name = sub.name,
            amount = occurrence.amount ?: sub.price,
            currencyCode = sub.currencyCode,
            dueDate = occurrence.date,
        )
    }

    fun confirmMarkPaid(timing: MarkTiming) {
        val target = markTargetFlow.value ?: return
        markTargetFlow.value = null
        viewModelScope.launch {
            val result: DataResult<io.github.submark.core.data.service.MarkPaidOutcome> = subscriptionService.markPaid(
                id = target.subscriptionId,
                newCycle = timing == MarkTiming.EARLY_NEW_CYCLE || timing == MarkTiming.OVERDUE_NEW_CYCLE,
            )
            when (result) {
                is DataResult.Success -> snackbarChannel.send(
                    SnackbarMessage(UiText.res(R.string.calendar_mark_paid_success, target.name)),
                )
                is DataResult.Failure -> snackbarChannel.send(SnackbarMessage(result.error.toUiText()))
            }
        }
    }

    fun dismissMarkPaid() {
        markTargetFlow.value = null
    }
}
