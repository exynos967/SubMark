package io.github.submark.feature.money.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.ui.component.MoneyInputField
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.format.MoneyInput
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.money.R
import io.github.submark.feature.money.data.MoneyEnv
import io.github.submark.feature.money.data.MoneyEnvSource
import io.github.submark.feature.money.ui.common.InfoRow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import javax.inject.Inject

data class BudgetSettingsUiState(
    val loading: Boolean = true,
    val amountText: String = "",
    val initialText: String = "",
    val saving: Boolean = false,
    val error: UiText? = null,
    val monthly: BigDecimal? = null,
    val quarterly: BigDecimal? = null,
    val env: MoneyEnv = MoneyEnv.EMPTY,
) {
    val dirty: Boolean get() = amountText != initialText
}

@HiltViewModel
class BudgetSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    envSource: MoneyEnvSource,
) : ViewModel() {
    private data class Local(val text: String?, val saving: Boolean = false, val error: UiText? = null)

    private val local = MutableStateFlow<Local?>(null)
    private val messages = Channel<SnackbarMessage>(Channel.BUFFERED)
    val snackbar: Flow<SnackbarMessage> = messages.receiveAsFlow()

    val uiState: StateFlow<BudgetSettingsUiState> = combine(
        settings.settings, envSource.observe(), local,
    ) { s, env, l ->
        val saved = s.money.annualBudget
        val savedText = saved?.stripTrailingZeros()?.toPlainString().orEmpty()
        val text = l?.text ?: savedText
        val amount = MoneyInput.parse(text)
        val valid = text.isNotBlank() && amount != null && amount >= MIN_BUDGET
        BudgetSettingsUiState(
            loading = false,
            amountText = text,
            initialText = savedText,
            saving = l?.saving == true,
            error = when {
                l?.error != null -> l.error
                text.isBlank() -> null
                amount == null -> UiText.res(R.string.money_budget_invalid)
                amount < MIN_BUDGET -> UiText.res(R.string.money_budget_too_low, MIN_BUDGET.toPlainString())
                else -> null
            },
            monthly = amount?.takeIf { valid }?.divide(TWELVE, MC),
            quarterly = amount?.takeIf { valid }?.divide(FOUR, MC),
            env = env,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetSettingsUiState())

    fun onTextChange(value: String) {
        local.value = (local.value ?: Local(null)).copy(text = value, error = null)
    }

    fun clear() {
        local.value = Local("")
    }

    fun save() {
        val state = uiState.value
        if (state.saving || state.error != null) return
        val value = if (state.amountText.isBlank()) null else MoneyInput.parse(state.amountText)
        local.value = (local.value ?: Local(null)).copy(saving = true)
        viewModelScope.launch {
            settings.update { it.copy(money = it.money.copy(annualBudget = value)) }
            local.value = Local(null)
            messages.send(SnackbarMessage(UiText.res(R.string.money_budget_saved)))
        }
    }

    companion object {
        val MIN_BUDGET = BigDecimal(1000)
        private val TWELVE = BigDecimal(12)
        private val FOUR = BigDecimal(4)
        private val MC = MathContext.DECIMAL64
    }
}

@Composable
fun BudgetSettingsRoute(onBack: () -> Unit, viewModel: BudgetSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    SnackbarEffect(viewModel.snackbar, host)
    Scaffold(
        topBar = { SubMarkTopAppBar(title = stringResource(R.string.money_budget_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(host) },
    ) { padding ->
        if (state.loading) {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {}
            return@Scaffold
        }
        val env = state.env
        val def = env.defaultCode
        val sym = env.symbol(def)
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "budget") {
                SectionCard(title = stringResource(R.string.money_budget_annual)) {
                    Text(stringResource(R.string.money_budget_description, def), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                    MoneyInputField(
                        value = state.amountText,
                        onValueChange = viewModel::onTextChange,
                        label = stringResource(R.string.money_budget_amount, def),
                        currencySymbol = sym,
                        showErrorWhenEmpty = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.error?.let {
                        Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.money_budget_min_hint, BudgetSettingsViewModel.MIN_BUDGET.toPlainString(), def),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "derived") {
                SectionCard(title = stringResource(R.string.money_budget_derived)) {
                    if (state.monthly == null && state.quarterly == null) {
                        Text(stringResource(R.string.money_budget_enter_for_derived), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.monthly?.let { InfoRow(stringResource(R.string.money_budget_monthly), formatDerived(it, def, sym)) }
                        state.quarterly?.let { InfoRow(stringResource(R.string.money_budget_quarterly), formatDerived(it, def, sym)) }
                    }
                }
            }
            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::save, enabled = state.error == null && state.dirty && !state.saving, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(io.github.submark.core.ui.R.string.ui_action_save))
                    }
                    OutlinedButton(
                        onClick = viewModel::clear,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.money_budget_clear)) }
                }
            }
        }
    }
}

@Composable
private fun formatDerived(value: BigDecimal, code: String, symbol: String?): String =
    io.github.submark.core.ui.component.formatMoney(value, code, symbol)
