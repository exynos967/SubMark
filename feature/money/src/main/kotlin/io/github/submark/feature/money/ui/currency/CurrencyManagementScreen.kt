package io.github.submark.feature.money.ui.currency

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.model.Currency
import io.github.submark.core.model.RateMode
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.MoneyInputField
import io.github.submark.core.ui.component.SearchField
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.SectionHeader
import io.github.submark.core.ui.component.SettingsSwitchRow
import io.github.submark.core.ui.component.StatusBadge
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.component.currentLocale
import io.github.submark.core.ui.format.BadgeTone
import io.github.submark.core.ui.format.MoneyInput
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.feature.money.R
import io.github.submark.feature.money.data.MoneyEnv
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import io.github.submark.core.ui.R as CoreR

@Composable
fun CurrencyManagementRoute(
    onBack: () -> Unit,
    onOpenHistoricalRates: () -> Unit,
    viewModel: CurrencyManagementViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    SnackbarEffect(viewModel.snackbar, host)
    CurrencyManagementScreen(
        state = state,
        snackbarHost = host,
        onBack = onBack,
        onOpenHistoricalRates = onOpenHistoricalRates,
        onQuery = viewModel::setQuery,
        onRefresh = viewModel::refreshRates,
        onEnable = viewModel::enable,
        onDisable = viewModel::disable,
        onSetDefault = viewModel::setDefault,
        onDelete = viewModel::delete,
        onAdd = viewModel::openAdd,
        onEdit = viewModel::openEdit,
        formActions = CurrencyFormActions(
            update = viewModel::updateForm,
            fetch = viewModel::fetchSuggested,
            useSuggested = viewModel::useSuggested,
            save = viewModel::save,
            close = viewModel::closeForm,
        ),
    )
}

class CurrencyFormActions(
    val update: ((CurrencyFormState) -> CurrencyFormState) -> Unit,
    val fetch: () -> Unit,
    val useSuggested: () -> Unit,
    val save: () -> Unit,
    val close: () -> Unit,
)

@Composable
fun CurrencyManagementScreen(
    state: CurrencyManagementUiState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onOpenHistoricalRates: () -> Unit,
    onQuery: (String) -> Unit,
    onRefresh: () -> Unit,
    onEnable: (String) -> Unit,
    onDisable: (String) -> Unit,
    onSetDefault: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Currency) -> Unit,
    formActions: CurrencyFormActions,
) {
    var pendingDelete by remember { mutableStateOf<Currency?>(null) }
    Scaffold(
        topBar = {
            SubMarkTopAppBar(
                title = stringResource(R.string.money_currency_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = onOpenHistoricalRates) {
                        Icon(Icons.Rounded.History, contentDescription = stringResource(R.string.money_historical_title))
                    }
                    if (state.refreshing) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.money_currency_refresh))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.money_currency_add_custom))
            }
        },
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        val env = state.env
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "refresh") {
                SectionCard {
                    Text(stringResource(R.string.money_currency_rates_info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    val locale = currentLocale()
                    val updated = state.lastUpdated?.let {
                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).format(it.atZone(state.zone))
                    }
                    Text(
                        if (updated != null) stringResource(R.string.money_currency_last_updated, updated) else stringResource(R.string.money_currency_never_updated),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = onRefresh, enabled = !state.refreshing) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.money_currency_refresh))
                    }
                }
            }
            item(key = "search") { SearchField(state.query, onQuery, placeholder = stringResource(R.string.money_currency_search)) }
            item(key = "enabled_header") {
                Column {
                    SectionHeader(stringResource(R.string.money_currency_enabled_section))
                    Text(stringResource(R.string.money_currency_enabled_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            items(state.enabled, key = { "e_${it.code}" }) { c ->
                EnabledRow(
                    currency = c,
                    env = env,
                    onEdit = { onEdit(c) },
                    onSetDefault = { onSetDefault(c.code) },
                    onDisable = { onDisable(c.code) },
                    onDelete = { pendingDelete = c },
                )
            }
            item(key = "available_header") {
                Column {
                    SectionHeader(stringResource(R.string.money_currency_available_section))
                    Text(stringResource(R.string.money_currency_available_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            if (state.available.isEmpty()) {
                item(key = "available_empty") { EmptyState(title = stringResource(CoreR.string.ui_search_no_results), icon = null) }
            }
            items(state.available, key = { "a_${it.code}" }) { c ->
                SectionCard(onClick = { onEnable(c.code) }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SymbolCircle(c.symbol)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.code, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(c.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.AddCircleOutline, contentDescription = stringResource(R.string.money_currency_enable_cd, c.code), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    pendingDelete?.let { c ->
        ConfirmDialog(
            title = stringResource(R.string.money_currency_delete_title, c.code),
            message = stringResource(R.string.money_currency_delete_message),
            onConfirm = { pendingDelete = null; onDelete(c.code) },
            onDismiss = { pendingDelete = null },
            confirmLabel = stringResource(CoreR.string.ui_action_delete),
            destructive = true,
        )
    }
    state.form?.let { CurrencyFormSheet(it, state.env, formActions) }
}

@Composable
private fun SymbolCircle(symbol: String) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Text(symbol, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

@Composable
private fun EnabledRow(
    currency: Currency,
    env: MoneyEnv,
    onEdit: () -> Unit,
    onSetDefault: () -> Unit,
    onDisable: () -> Unit,
    onDelete: () -> Unit,
) {
    val isDefault = currency.code == env.defaultCode
    var menu by remember { mutableStateOf(false) }
    SectionCard(onClick = onEdit, contentPadding = PaddingValues(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SymbolCircle(currency.symbol)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(currency.code, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    if (isDefault) StatusBadge(stringResource(R.string.money_currency_tag_default), tone = BadgeTone.PRIMARY)
                    if (currency.isCustom) StatusBadge(stringResource(R.string.money_currency_tag_custom), tone = BadgeTone.TERTIARY)
                }
                Text(currency.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!isDefault) {
                    val rate = CurrencyRates.displayRate(env.converter, env.defaultCode, currency.code)
                    Text(
                        if (rate != null) stringResource(R.string.money_currency_rate_line, env.defaultCode, rate, currency.code)
                        else stringResource(R.string.money_rate_unavailable_short),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (rate != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                    Text(
                        stringResource(if (currency.rateMode == RateMode.AUTO) R.string.money_currency_mode_auto else R.string.money_currency_mode_manual),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.money_more_actions_for, currency.code))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!isDefault) DropdownMenuItem(text = { Text(stringResource(R.string.money_currency_set_default)) }, onClick = { menu = false; onSetDefault() })
                    DropdownMenuItem(text = { Text(stringResource(CoreR.string.ui_action_edit)) }, onClick = { menu = false; onEdit() })
                    if (!isDefault) DropdownMenuItem(text = { Text(stringResource(R.string.money_currency_disable)) }, onClick = { menu = false; onDisable() })
                    if (!isDefault && currency.isCustom) {
                        DropdownMenuItem(
                            text = { Text(stringResource(CoreR.string.ui_action_delete), color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyFormSheet(form: CurrencyFormState, env: MoneyEnv, actions: CurrencyFormActions) {
    var calculator by rememberSaveable { mutableStateOf(false) }
    val code = form.code.trim().uppercase().ifEmpty { "?" }
    ModalBottomSheet(onDismissRequest = actions.close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).imePadding().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(if (form.editingCode == null) R.string.money_currency_add_custom else R.string.money_currency_edit_title),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = form.code,
                onValueChange = { v -> actions.update { it.copy(code = v.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(10)) } },
                label = { Text(stringResource(R.string.money_currency_code)) },
                enabled = form.editingCode == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                supportingText = { Text(stringResource(R.string.money_currency_code_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> actions.update { it.copy(name = v) } },
                label = { Text(stringResource(R.string.money_currency_name)) },
                enabled = form.isCustom,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.symbol,
                onValueChange = { v -> actions.update { it.copy(symbol = v.take(6)) } },
                label = { Text(stringResource(R.string.money_currency_symbol)) },
                enabled = form.isCustom,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (form.isDefault) {
                Text(stringResource(R.string.money_currency_default_rate_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                MoneyInputField(
                    value = form.rateText,
                    onValueChange = { v -> actions.update { it.copy(rateText = v) } },
                    label = stringResource(R.string.money_currency_rate_label, env.defaultCode, code),
                    maxFractionDigits = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = actions.fetch, enabled = !form.fetching && form.code.isNotBlank()) {
                        if (form.fetching) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.money_currency_fetch))
                    }
                    OutlinedButton(onClick = { calculator = true }) {
                        Icon(Icons.Rounded.Calculate, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.money_currency_calculator))
                    }
                }
                form.suggested?.let { s ->
                    SectionCard {
                        Text(
                            stringResource(R.string.money_currency_suggested, env.defaultCode, CurrencyRates.plain(s), code),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        form.suggestedDate?.let {
                            Text(stringResource(R.string.money_currency_suggested_date, it.toString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = actions.useSuggested) { Text(stringResource(R.string.money_currency_use_suggested)) }
                    }
                }
            }
            SettingsSwitchRow(
                title = stringResource(R.string.money_currency_auto_update),
                subtitle = stringResource(if (form.autoUpdate) R.string.money_currency_auto_update_on else R.string.money_currency_auto_update_off),
                checked = form.autoUpdate,
                onCheckedChange = { c -> actions.update { it.copy(autoUpdate = c) } },
            )
            form.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
            Button(onClick = actions.save, enabled = !form.saving && form.code.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(CoreR.string.ui_action_save))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (calculator) {
        RateCalculatorDialog(
            defaultCode = env.defaultCode,
            code = code,
            onUse = { r -> actions.update { it.copy(rateText = r.toPlainString()) }; calculator = false },
            onDismiss = { calculator = false },
        )
    }
}

@Composable
private fun RateCalculatorDialog(defaultCode: String, code: String, onUse: (java.math.BigDecimal) -> Unit, onDismiss: () -> Unit) {
    var a by rememberSaveable { mutableStateOf("") }
    var b by rememberSaveable { mutableStateOf("") }
    val rate = CurrencyRates.fromAmounts(MoneyInput.parse(a), MoneyInput.parse(b))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.money_currency_calculator)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.money_currency_calc_hint), style = MaterialTheme.typography.bodySmall)
                MoneyInputField(a, { a = it }, label = stringResource(R.string.money_currency_calc_amount, defaultCode), maxFractionDigits = 6)
                MoneyInputField(b, { b = it }, label = stringResource(R.string.money_currency_calc_amount, code), maxFractionDigits = 6)
                Text(
                    rate?.let { stringResource(R.string.money_currency_rate_line, defaultCode, it.toPlainString(), code) } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clickable(enabled = rate != null) { rate?.let(onUse) },
                )
            }
        },
        confirmButton = { TextButton(onClick = { rate?.let(onUse) }, enabled = rate != null) { Text(stringResource(R.string.money_currency_calc_use)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.ui_action_cancel)) } },
    )
}
