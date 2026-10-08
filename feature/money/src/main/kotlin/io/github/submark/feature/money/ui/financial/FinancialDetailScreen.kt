package io.github.submark.feature.money.ui.financial

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.data.settings.FinancialDetailFilter
import io.github.submark.core.data.settings.FinancialDetailMode
import io.github.submark.core.model.PaymentKind
import io.github.submark.core.model.PaymentStatus
import io.github.submark.core.ui.component.DatePickerField
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.MoneyText
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.SegmentedTabs
import io.github.submark.core.ui.component.StatusBadge
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.component.formatMoney
import io.github.submark.core.ui.format.BadgeTone
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.format.labelRes
import io.github.submark.feature.money.R
import io.github.submark.feature.money.ui.common.BadgeRow
import io.github.submark.feature.money.ui.common.StatCell
import io.github.submark.feature.money.ui.common.TipsCard
import io.github.submark.feature.money.ui.common.formatDate
import io.github.submark.feature.money.ui.common.paymentBadges
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoField

@Composable
fun FinancialDetailRoute(onBack: () -> Unit, viewModel: FinancialDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FinancialDetailScreen(
        state = state,
        onBack = onBack,
        onPeriod = viewModel::setPeriod,
        onCustomFrom = viewModel::setCustomFrom,
        onCustomTo = viewModel::setCustomTo,
        onFilter = viewModel::setFilter,
        onMode = viewModel::setMode,
    )
}

@Composable
fun FinancialDetailScreen(
    state: FinancialDetailUiState,
    onBack: () -> Unit,
    onPeriod: (FinPeriod) -> Unit,
    onCustomFrom: (java.time.LocalDate) -> Unit,
    onCustomTo: (java.time.LocalDate) -> Unit,
    onFilter: (FinancialDetailFilter) -> Unit,
    onMode: (FinancialDetailMode) -> Unit,
) {
    Scaffold(topBar = { SubMarkTopAppBar(title = stringResource(R.string.money_fin_title), onBack = onBack) }) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        val env = state.env
        val def = env.defaultCode
        val sym = env.symbol(def)
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "period") {
                SegmentedTabs(FinPeriod.entries, state.period, onPeriod) {
                    stringResource(
                        when (it) {
                            FinPeriod.MONTH -> R.string.money_fin_this_month
                            FinPeriod.QUARTER -> R.string.money_fin_this_quarter
                            FinPeriod.YEAR -> R.string.money_fin_this_year
                            FinPeriod.CUSTOM -> R.string.money_fin_custom
                        },
                    )
                }
            }
            if (state.period == FinPeriod.CUSTOM) {
                item(key = "custom") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DatePickerField(
                            label = stringResource(R.string.money_fin_from),
                            date = state.customFrom,
                            onDateChange = onCustomFrom,
                            today = java.time.LocalDate.now(),
                            modifier = Modifier.weight(1f),
                        )
                        DatePickerField(
                            label = stringResource(R.string.money_fin_to),
                            date = state.customTo,
                            onDateChange = onCustomTo,
                            today = java.time.LocalDate.now(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item(key = "controls") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FinancialDetailFilter.entries.forEach { f ->
                        FilterChip(selected = state.filter == f, onClick = { onFilter(f) }, label = { Text(stringResource(filterLabelRes(f))) })
                    }
                }
            }
            item(key = "mode") {
                SegmentedTabs(FinancialDetailMode.entries, state.mode, onMode) {
                    stringResource(if (it == FinancialDetailMode.SIMPLE) R.string.money_fin_mode_simple else R.string.money_fin_mode_detailed)
                }
            }
            item(key = "progress") {
                if (state.converting) {
                    Column {
                        LinearProgressIndicator(progress = { state.progressDone.toFloat() / state.progressTotal }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text(
                            pluralStringResource(R.plurals.money_fin_converting, state.progressTotal, state.progressDone, state.progressTotal),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            val result = state.result
            when {
                result == null && !state.converting -> item(key = "empty") {
                    EmptyState(title = stringResource(R.string.money_fin_empty), message = stringResource(R.string.money_fin_empty_message), icon = Icons.Rounded.Savings)
                }
                result != null && result.paymentCount == 0 -> item(key = "empty") {
                    EmptyState(title = stringResource(R.string.money_fin_empty), message = stringResource(R.string.money_fin_empty_message), icon = Icons.Rounded.Savings)
                }
                result != null -> {
                    item(key = "overview") {
                        SectionCard(title = stringResource(R.string.money_fin_overview)) {
                            Row {
                                StatCell(stringResource(R.string.money_fin_total), formatMoney(result.total, def, sym), Modifier.weight(1f))
                                StatCell(stringResource(R.string.money_fin_count), result.paymentCount.toString(), Modifier.weight(1f))
                            }
                            Row {
                                StatCell(stringResource(R.string.money_fin_average), formatMoney(result.average, def, sym), Modifier.weight(1f))
                                StatCell(
                                    stringResource(R.string.money_fin_expense_months),
                                    result.expenseMonths.toString(),
                                    Modifier.weight(1f),
                                )
                            }
                            state.theoreticalMonthly?.let {
                                Text(
                                    stringResource(R.string.money_fin_theoretical, formatMoney(it, def, sym)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (result.unconvertedCount > 0) {
                                Text(
                                    pluralStringResource(R.plurals.money_unconverted_note, result.unconvertedCount, result.unconvertedCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    if (result.byType.isNotEmpty()) {
                        item(key = "types") {
                            SectionCard(title = stringResource(R.string.money_fin_by_type)) {
                                result.byType.forEach { (t, total) ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text(stringResource(spendingTypeRes(t)), Modifier.weight(1f))
                                        Text(formatMoney(total, def, sym), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                    item(key = "notes") {
                        TipsCard(
                            title = stringResource(R.string.money_fin_notes_title),
                            lines = buildList {
                                add(stringResource(R.string.money_fin_note_actual))
                                if (state.period != FinPeriod.CUSTOM) add(stringResource(R.string.money_fin_note_period))
                                add(stringResource(R.string.money_fin_note_historical))
                                if (state.filter != FinancialDetailFilter.INCLUDE_LIFETIME) add(stringResource(R.string.money_fin_note_filter))
                                if (result.months.isNotEmpty()) add(stringResource(R.string.money_fin_note_subscriptions))
                            },
                        )
                    }
                    result.months.forEach { group ->
                        item(key = "m_${group.month}") {
                            MonthGroupCard(group, state.mode, def, sym)
                        }
                    }
                }
            }
        }
    }
}

private fun filterLabelRes(f: FinancialDetailFilter): Int = when (f) {
    FinancialDetailFilter.INCLUDE_LIFETIME -> R.string.money_fin_filter_include
    FinancialDetailFilter.SUBSCRIPTIONS_ONLY -> R.string.money_fin_filter_subs
    FinancialDetailFilter.LIFETIME_ONLY -> R.string.money_fin_filter_lifetime
}

private fun spendingTypeRes(t: SpendingType): Int = when (t) {
    SpendingType.MONTHLY -> R.string.money_fin_type_monthly
    SpendingType.QUARTERLY -> R.string.money_fin_type_quarterly
    SpendingType.YEARLY -> R.string.money_fin_type_yearly
    SpendingType.OTHER_CYCLE -> R.string.money_fin_type_other
    SpendingType.LIFETIME -> R.string.money_fin_type_lifetime
    SpendingType.SHARED -> R.string.money_fin_type_shared
    SpendingType.IAP -> R.string.money_fin_type_iap
    SpendingType.STORED_VALUE_DEPOSIT -> R.string.money_fin_type_stored
    SpendingType.BUNDLE_CHILD -> R.string.money_fin_type_bundle
}

@Composable
private fun MonthGroupCard(group: MonthGroup, mode: FinancialDetailMode, def: String, sym: String?) {
    SectionCard(title = formatMonth(group.month)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            StatCell(stringResource(R.string.money_fin_month_total), formatMoney(group.total, def, sym), Modifier.weight(1f))
            StatCell(stringResource(R.string.money_fin_month_count), group.payments.size.toString(), Modifier.weight(1f))
        }
        group.payments.forEachIndexed { i, p ->
            if (i > 0) Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(
                        p.subscription?.name ?: stringResource(R.string.money_fin_unknown),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        formatDate(p.record.paymentDate) + " · " + stringResource(p.record.status.labelRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (mode == FinancialDetailMode.DETAILED) {
                        BadgeRow(paymentBadges(p.record))
                    }
                }
                Column {
                    MoneyText(p.defaultAmount, def, symbol = sym)
                    Text(
                        formatMoney(p.record.amount, p.record.currencyCode, null),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatMonth(yearMonth: YearMonth): String {
    val formatter = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
    val calendar = java.util.GregorianCalendar(yearMonth.year, yearMonth.monthValue - 1, 1)
    return formatter.format(calendar.time).replaceFirstChar { it.uppercase() }
}
