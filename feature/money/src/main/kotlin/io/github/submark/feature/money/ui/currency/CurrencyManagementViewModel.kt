package io.github.submark.feature.money.ui.currency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.currency.CurrencyRepository
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.domain.CurrencyConverter
import io.github.submark.core.model.Currency
import io.github.submark.core.model.RateMode
import io.github.submark.core.model.RateProvider
import io.github.submark.core.ui.format.MoneyInput
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.money.R
import io.github.submark.feature.money.data.MoneyEnv
import io.github.submark.feature.money.data.MoneyEnvSource
import io.github.submark.feature.money.ui.common.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Add/edit form. Rates are entered as "1 <default> = [rateText] <code>". */
data class CurrencyFormState(
    /** Null = adding a custom currency. */
    val editingCode: String? = null,
    val isCustom: Boolean = true,
    val isDefault: Boolean = false,
    val code: String = "",
    val name: String = "",
    val symbol: String = "",
    val rateText: String = "",
    val initialRateText: String = "",
    val autoUpdate: Boolean = false,
    val fetching: Boolean = false,
    val suggested: BigDecimal? = null,
    val suggestedProvider: RateProvider? = null,
    val suggestedDate: LocalDate? = null,
    val saving: Boolean = false,
    val error: UiText? = null,
)

data class CurrencyManagementUiState(
    val loading: Boolean = true,
    val query: String = "",
    val enabled: List<Currency> = emptyList(),
    val available: List<Currency> = emptyList(),
    val refreshing: Boolean = false,
    val lastUpdated: Instant? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
    val form: CurrencyFormState? = null,
    val env: MoneyEnv = MoneyEnv.EMPTY,
)

@HiltViewModel
class CurrencyManagementViewModel @Inject constructor(
    private val currencies: CurrencyRepository,
    envSource: MoneyEnvSource,
    private val time: TimeProvider,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val refreshing = MutableStateFlow(false)
    private val form = MutableStateFlow<CurrencyFormState?>(null)
    private val messages = Channel<SnackbarMessage>(Channel.BUFFERED)
    val snackbar: Flow<SnackbarMessage> = messages.receiveAsFlow()

    val uiState: StateFlow<CurrencyManagementUiState> = combine(
        currencies.observeEnabledSorted(),
        envSource.observe(),
        query,
        refreshing,
        form,
    ) { enabled, env, q, r, f ->
        val all = env.currencies.values
        CurrencyManagementUiState(
            loading = false,
            query = q,
            enabled = enabled.filter { matches(it, q) },
            available = all.filter { !it.isEnabled && matches(it, q) }.sortedWith(compareBy({ !it.isCustom }, { it.code })),
            refreshing = r,
            lastUpdated = all.filter { it.rateMode == RateMode.AUTO && it.rateProvider != null }.mapNotNull { it.rateUpdatedAt }.maxOrNull(),
            zone = time.zone(),
            form = f,
            env = env,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CurrencyManagementUiState())

    fun setQuery(value: String) { query.value = value }

    fun enable(code: String) = mutate { currencies.enable(code) }

    fun disable(code: String) = mutate { currencies.disable(code) }

    fun setDefault(code: String) = mutate(R.string.money_currency_default_set) { currencies.setDefault(code) }

    fun delete(code: String) = mutate(R.string.money_currency_deleted) { currencies.delete(code) }

    fun refreshRates() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            when (val result = currencies.refreshRates()) {
                is DataResult.Success -> messages.send(SnackbarMessage(UiText.plural(R.plurals.money_currency_refreshed, result.value, result.value)))
                is DataResult.Failure -> messages.send(SnackbarMessage(result.error.toUiText()))
            }
            refreshing.value = false
        }
    }

    fun openAdd() {
        form.value = CurrencyFormState()
    }

    fun openEdit(currency: Currency) {
        val env = uiState.value.env
        val rate = CurrencyRates.displayRate(env.converter, env.defaultCode, currency.code)
        form.value = CurrencyFormState(
            editingCode = currency.code,
            isCustom = currency.isCustom,
            isDefault = currency.code == env.defaultCode,
            code = currency.code,
            name = currency.name,
            symbol = currency.symbol,
            rateText = rate.orEmpty(),
            initialRateText = rate.orEmpty(),
            autoUpdate = currency.rateMode == RateMode.AUTO,
        )
    }

    fun closeForm() { form.value = null }

    fun updateForm(transform: (CurrencyFormState) -> CurrencyFormState) = form.update { it?.let(transform)?.copy(error = null) }

    fun fetchSuggested() {
        val f = form.value ?: return
        val code = f.code.trim().uppercase()
        if (code.isEmpty() || f.fetching) return
        form.update { it?.copy(fetching = true, error = null, suggested = null) }
        viewModelScope.launch {
            val result = currencies.fetchSuggestedRate(code)
            val env = uiState.value.env
            form.update { current ->
                current ?: return@update null
                when (result) {
                    is DataResult.Success -> {
                        val cross = CurrencyRates.crossFromUsd(result.value.usdRate, env.converter.rate(env.defaultCode))
                        if (cross == null) {
                            current.copy(fetching = false, error = DataError.RateUnavailable(env.defaultCode).toUiText())
                        } else {
                            current.copy(fetching = false, suggested = cross, suggestedProvider = result.value.provider, suggestedDate = result.value.date)
                        }
                    }
                    is DataResult.Failure -> current.copy(fetching = false, error = result.error.toUiText())
                }
            }
        }
    }

    fun useSuggested() = form.update { f ->
        f?.suggested?.let { f.copy(rateText = CurrencyRates.plain(it), autoUpdate = true) } ?: f
    }

    fun save() {
        val f = form.value ?: return
        if (f.saving) return
        val env = uiState.value.env
        val rateChanged = f.rateText != f.initialRateText
        val usdRate: BigDecimal? = if (f.isDefault || (!rateChanged && f.editingCode != null)) {
            null
        } else {
            val value = MoneyInput.parse(f.rateText)
            if (value == null || value.signum() <= 0) return formError(UiText.res(R.string.money_invalid_rate_range))
            val defaultUsd = env.converter.rate(env.defaultCode)
                ?: return formError(DataError.RateUnavailable(env.defaultCode).toUiText())
            CurrencyConverter.usdRateFromDefault(value, defaultUsd)
        }
        if (f.isCustom && (f.name.isBlank() || f.symbol.isBlank())) return formError(UiText.res(R.string.money_invalid_blank_name))
        val mode = if (f.autoUpdate) RateMode.AUTO else RateMode.MANUAL
        form.update { it?.copy(saving = true) }
        viewModelScope.launch {
            val result = if (f.editingCode == null) {
                currencies.addCustom(f.code, f.name, f.symbol, usdRate ?: BigDecimal.ONE, mode)
            } else {
                currencies.update(
                    code = f.editingCode,
                    name = f.name.takeIf { f.isCustom },
                    symbol = f.symbol.takeIf { f.isCustom },
                    usdRate = usdRate,
                    rateMode = mode,
                )
            }
            when (result) {
                is DataResult.Success -> {
                    form.value = null
                    messages.send(SnackbarMessage(UiText.res(R.string.money_currency_saved)))
                }
                is DataResult.Failure -> form.update { it?.copy(saving = false, error = result.error.toUiText()) }
            }
        }
    }

    private fun formError(text: UiText) = form.update { it?.copy(error = text) }

    private fun mutate(success: Int? = null, block: suspend () -> DataResult<*>) {
        viewModelScope.launch {
            when (val result = block()) {
                is DataResult.Success -> success?.let { messages.send(SnackbarMessage(UiText.res(it))) }
                is DataResult.Failure -> messages.send(SnackbarMessage(result.error.toUiText()))
            }
        }
    }

    private fun matches(c: Currency, q: String): Boolean {
        val t = q.trim()
        return t.isEmpty() || c.code.contains(t, true) || c.name.contains(t, true) || c.symbol.contains(t, true)
    }
}

/** Display helpers for "1 <default> = x <code>" rates. */
object CurrencyRates {
    private val MC = MathContext.DECIMAL64

    fun displayRate(converter: CurrencyConverter, defaultCode: String, code: String): String? =
        converter.crossRate(defaultCode, code)?.let(::plain)

    /** "1 default = x code" from a per-USD rate of code. */
    fun crossFromUsd(usdRate: BigDecimal, defaultUsdRate: BigDecimal?): BigDecimal? =
        defaultUsdRate?.takeIf { it.signum() > 0 }?.let { usdRate.divide(it, MC) }

    fun plain(value: BigDecimal): String = value.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    /** Calculator: amount of default currency vs amount of target -> rate with 4 decimals. */
    fun fromAmounts(defaultAmount: BigDecimal?, targetAmount: BigDecimal?): BigDecimal? {
        if (defaultAmount == null || targetAmount == null || defaultAmount.signum() <= 0 || targetAmount.signum() <= 0) return null
        return targetAmount.divide(defaultAmount, 4, RoundingMode.HALF_UP)
    }
}
