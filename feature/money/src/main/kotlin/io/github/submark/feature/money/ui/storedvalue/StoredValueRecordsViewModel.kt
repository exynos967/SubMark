package io.github.submark.feature.money.ui.storedvalue

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.currency.CurrencyRepository
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.service.StoredValueService
import io.github.submark.core.data.service.WalletService
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.domain.StoredValueCalculator
import io.github.submark.core.domain.StoredValueStatus
import io.github.submark.core.model.Currency
import io.github.submark.core.model.StoredValueRecord
import io.github.submark.core.model.StoredValueRecordType
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.ui.format.MoneyInput
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.navigation.StoredValueRecordsRoute
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.money.R
import io.github.submark.feature.money.data.MoneyEnv
import io.github.submark.feature.money.data.MoneyEnvSource
import io.github.submark.feature.money.ui.common.WalletChoice
import io.github.submark.feature.money.ui.common.toUiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.ZoneId
import javax.inject.Inject

enum class RecordFilter { ALL, DEPOSIT, DEDUCTION }

data class TopUpForm(
    val open: Boolean = false,
    val amountText: String = "",
    val currencyCode: String = "",
    val note: String = "",
    val useWallet: Boolean = false,
    val walletId: String? = null,
    val saving: Boolean = false,
    val error: UiText? = null,
) {
    val amount: BigDecimal? get() = MoneyInput.parse(amountText)
}

data class StoredValueRecordsUiState(
    val loading: Boolean = true,
    val subscription: Subscription? = null,
    val isStoredValue: Boolean = false,
    val status: StoredValueStatus? = null,
    val payableCycles: Long? = null,
    val debtCycles: Long = 0,
    val filter: RecordFilter = RecordFilter.ALL,
    val query: String = "",
    val records: List<StoredValueRecord> = emptyList(),
    val totalCount: Int = 0,
    val topUp: TopUpForm = TopUpForm(),
    val topUpPreview: TopUpPreview? = null,
    val quickAmounts: List<BigDecimal> = emptyList(),
    val walletChoices: List<WalletChoice> = emptyList(),
    val currencies: List<Currency> = emptyList(),
    val zone: ZoneId = ZoneId.systemDefault(),
    val env: MoneyEnv = MoneyEnv.EMPTY,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StoredValueRecordsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    subscriptions: SubscriptionRepository,
    private val storedValue: StoredValueService,
    private val wallets: WalletService,
    currencies: CurrencyRepository,
    envSource: MoneyEnvSource,
    private val time: TimeProvider,
) : ViewModel() {
    val subscriptionId = savedStateHandle.toRoute<StoredValueRecordsRoute>().subscriptionId
    private val filter = MutableStateFlow(RecordFilter.ALL)
    private val query = MutableStateFlow("")
    private val topUp = MutableStateFlow(TopUpForm())
    private val messages = Channel<SnackbarMessage>(Channel.BUFFERED)
    val snackbar: Flow<SnackbarMessage> = messages.receiveAsFlow()

    private val subscription = subscriptions.observe(subscriptionId)

    private val walletChoices: Flow<List<WalletChoice>> = combine(
        wallets.observeWallets(),
        topUp.map { it.amount to it.currencyCode }.distinctUntilChanged(),
    ) { list, key -> list.filter { it.isActive } to key }
        .mapLatest { (list, key) ->
            val (amount, code) = key
            list.map { w ->
                WalletChoice(w, if (amount != null && amount.signum() > 0 && code.isNotEmpty()) wallets.chargeProblem(w.id, amount, code) else null)
            }
        }

    val uiState: StateFlow<StoredValueRecordsUiState> = combine(
        combine(subscription, storedValue.observeRecords(subscriptionId), ::Pair),
        combine(filter, query, ::Pair),
        combine(topUp, walletChoices, ::Pair),
        currencies.observeEnabledSorted(),
        envSource.observe(),
    ) { (sub, records), (f, q), (form, choices), enabled, env ->
        val sorted = records.sortedByDescending { it.occurredAt }
        val visible = sorted.filter { r ->
            (f == RecordFilter.ALL || (f == RecordFilter.DEPOSIT) == (r.type == StoredValueRecordType.DEPOSIT)) && matches(r, q)
        }
        StoredValueRecordsUiState(
            loading = false,
            subscription = sub,
            isStoredValue = sub?.kind == SubscriptionKind.STORED_VALUE,
            status = sub?.let { StoredValueCalculator.status(it.storedValueBalance, it.price) },
            payableCycles = sub?.let { StoredValueCalculator.payableCycles(it.storedValueBalance, it.price) },
            debtCycles = sub?.let { StoredValueCalculator.debtCycles(it.storedValueBalance, it.price) } ?: 0,
            filter = f,
            query = q,
            records = visible,
            totalCount = sorted.size,
            topUp = form,
            topUpPreview = sub?.let { TopUpPreviewCalculator.preview(it, form.amount, form.currencyCode, env.converter) },
            quickAmounts = sub?.let { TopUpPreviewCalculator.quickAmounts(it.price) }.orEmpty(),
            walletChoices = choices,
            currencies = enabled,
            zone = time.zone(),
            env = env,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoredValueRecordsUiState())

    fun setFilter(value: RecordFilter) { filter.value = value }

    fun setQuery(value: String) { query.value = value }

    fun openTopUp() {
        val sub = uiState.value.subscription ?: return
        topUp.value = TopUpForm(open = true, currencyCode = sub.currencyCode, useWallet = sub.walletId != null, walletId = sub.walletId)
    }

    fun closeTopUp() = topUp.update { TopUpForm() }

    fun updateTopUp(transform: (TopUpForm) -> TopUpForm) = topUp.update(transform)

    fun submitTopUp() {
        val form = topUp.value
        val amount = form.amount
        if (form.saving) return
        if (amount == null || amount.signum() <= 0) {
            topUp.update { it.copy(error = UiText.res(R.string.money_invalid_non_positive_amount)) }
            return
        }
        if (form.useWallet && form.walletId == null) {
            topUp.update { it.copy(error = UiText.res(R.string.money_add_error_wallet_required)) }
            return
        }
        topUp.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val result = storedValue.topUp(
                subscriptionId, amount, form.currencyCode,
                description = form.note.trim().takeIf { it.isNotEmpty() },
                walletId = form.walletId.takeIf { form.useWallet },
            )
            when (result) {
                is DataResult.Success -> {
                    topUp.value = TopUpForm()
                    messages.send(SnackbarMessage(UiText.res(R.string.money_sv_topup_done)))
                }
                is DataResult.Failure -> topUp.update { it.copy(saving = false, error = result.error.toUiText()) }
            }
        }
    }

    fun delete(record: StoredValueRecord) {
        viewModelScope.launch {
            when (val result = storedValue.deleteRecord(record.id)) {
                is DataResult.Success -> messages.send(SnackbarMessage(UiText.res(R.string.money_sv_deleted)))
                is DataResult.Failure -> messages.send(SnackbarMessage(result.error.toUiText()))
            }
        }
    }

    private fun matches(record: StoredValueRecord, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return record.description?.contains(q, ignoreCase = true) == true ||
            record.amount.toPlainString().contains(q.replace(',', '.')) ||
            record.occurredAt.atZone(time.zone()).toLocalDate().toString().contains(q)
    }
}
