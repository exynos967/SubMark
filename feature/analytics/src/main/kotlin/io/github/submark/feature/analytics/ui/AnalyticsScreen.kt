package io.github.submark.feature.analytics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.submark.core.data.settings.AnalyticsComponent
import io.github.submark.core.data.settings.SpendingMode
import io.github.submark.core.data.settings.SummaryPeriod
import io.github.submark.core.data.settings.TrendPeriod
import io.github.submark.core.ui.chart.ChartEntry
import io.github.submark.core.ui.chart.DonutChart
import io.github.submark.core.ui.chart.HeatmapDay
import io.github.submark.core.ui.chart.HeatmapGrid
import io.github.submark.core.ui.chart.LineBarChart
import io.github.submark.core.ui.chart.LineBarStyle
import io.github.submark.core.ui.chart.RadarChart
import io.github.submark.core.ui.chart.RadarSeries
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.SegmentedTabs
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.component.currentLocale
import io.github.submark.core.ui.component.formatMoney
import io.github.submark.core.ui.format.DateLabels
import io.github.submark.core.ui.format.MoneyFormatter
import io.github.submark.feature.analytics.R
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun AnalyticsRoute(
    onCustomize: () -> Unit,
    onFinancialDetail: () -> Unit,
    onStoredValueStats: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    AnalyticsScreen(
        uiState = uiState,
        onCustomize = onCustomize,
        onFinancialDetail = onFinancialDetail,
        onStoredValueStats = onStoredValueStats,
        onModeChange = viewModel::setMode,
        onPeriodChange = viewModel::setPeriod,
        onTrendPeriodChange = viewModel::setTrendPeriod,
        onCategoryExpandedChange = viewModel::setCategoryExpanded,
    )
}

@Composable
fun AnalyticsScreen(
    uiState: AnalyticsUiState,
    onCustomize: () -> Unit,
    onFinancialDetail: () -> Unit,
    onStoredValueStats: () -> Unit,
    onModeChange: (SpendingMode) -> Unit,
    onPeriodChange: (SummaryPeriod) -> Unit,
    onTrendPeriodChange: (TrendPeriod) -> Unit,
    onCategoryExpandedChange: (Boolean) -> Unit,
) {
    val modeDesc = stringResource(if (uiState.mode == SpendingMode.SUBSCRIPTIONS) R.string.analytics_mode_subscriptions else R.string.analytics_mode_lifetime)
    Scaffold(
        topBar = {
            SubMarkTopAppBar(
                title = stringResource(R.string.analytics_title),
                subtitle = modeDesc,
                actions = {
                    val customizeDesc = stringResource(R.string.analytics_go_customize)
                    IconButton(onClick = onCustomize, modifier = Modifier.semantics { contentDescription = customizeDesc }) {
                        Icon(Icons.Rounded.Tune, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.loading -> LoadingState(
                modifier = Modifier.padding(padding),
                message = stringResource(R.string.analytics_loading),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SegmentedTabs(
                            items = listOf(SpendingMode.SUBSCRIPTIONS, SpendingMode.LIFETIME),
                            selected = uiState.mode,
                            onSelect = onModeChange,
                        ) { mode ->
                            stringResource(if (mode == SpendingMode.SUBSCRIPTIONS) R.string.analytics_mode_subscriptions else R.string.analytics_mode_lifetime)
                        }
                        uiState.rateProgress?.let { pct ->
                            Column {
                                Text(
                                    stringResource(R.string.analytics_loading_rates, pct),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }

                val visibleComponents = uiState.components.filter { it.visible || it.id == AnalyticsComponent.FINANCIAL_OVERVIEW }
                if (!uiState.hasAnyPayments) {
                    item {
                        EmptyState(
                            title = stringResource(R.string.analytics_empty_title),
                            message = stringResource(R.string.analytics_empty_message),
                            actionLabel = stringResource(R.string.analytics_go_customize),
                            onAction = onCustomize,
                        )
                    }
                } else if (visibleComponents.isEmpty()) {
                    item {
                        EmptyState(
                            title = stringResource(R.string.analytics_no_components_title),
                            message = stringResource(R.string.analytics_no_components_message),
                            actionLabel = stringResource(R.string.analytics_go_customize),
                            onAction = onCustomize,
                        )
                    }
                } else {
                    visibleComponents.forEach { component ->
                        item(key = component.id.name) {
                            when (component.id) {
                                AnalyticsComponent.FINANCIAL_OVERVIEW -> OverviewSection(
                                    uiState = uiState,
                                    onPeriodChange = onPeriodChange,
                                    onMore = onFinancialDetail,
                                )
                                AnalyticsComponent.TREND -> TrendSection(
                                    uiState = uiState,
                                    onTrendPeriodChange = onTrendPeriodChange,
                                )
                                AnalyticsComponent.HEATMAP -> HeatmapSection(uiState)
                                AnalyticsComponent.CATEGORY -> CategorySection(
                                    uiState = uiState,
                                    onExpandedChange = onCategoryExpandedChange,
                                )
                                AnalyticsComponent.MULTI_DIMENSION -> RadarSection(uiState)
                                AnalyticsComponent.STORED_VALUE -> StoredValueSection(onStoredValueStats)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Sections ----------------

@Composable
private fun OverviewSection(
    uiState: AnalyticsUiState,
    onPeriodChange: (SummaryPeriod) -> Unit,
    onMore: () -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.analytics_overview_title),
        actionLabel = stringResource(R.string.analytics_overview_more),
        onAction = onMore,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SegmentedTabs(
                items = listOf(SummaryPeriod.MONTH, SummaryPeriod.QUARTER, SummaryPeriod.YEAR),
                selected = uiState.period,
                onSelect = onPeriodChange,
            ) { p ->
                stringResource(
                    when (p) {
                        SummaryPeriod.MONTH -> R.string.analytics_period_month
                        SummaryPeriod.QUARTER -> R.string.analytics_period_quarter
                        SummaryPeriod.YEAR -> R.string.analytics_period_year
                    },
                )
            }
            Text(
                formatMoney(uiState.totalSpending, uiState.defaultCurrencyCode, uiState.currencySymbols[uiState.defaultCurrencyCode], hideDecimals = uiState.hideDecimalPlaces),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                stringResource(R.string.analytics_overview_total_spending),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatRow(
                label = stringResource(R.string.analytics_overview_active_count),
                value = uiState.activeCount.toString(),
            )
            StatRow(
                label = stringResource(R.string.analytics_overview_lifetime_count),
                value = uiState.lifetimeCount.toString(),
            )
            StatRow(
                label = stringResource(R.string.analytics_overview_daily_avg),
                value = formatMoney(uiState.dailyAverage, uiState.defaultCurrencyCode, uiState.currencySymbols[uiState.defaultCurrencyCode], hideDecimals = uiState.hideDecimalPlaces),
            )
            val budget = uiState.annualBudget
            if (budget != null && budget.signum() > 0) {
                val pct = uiState.spentYearToDate.divide(budget, 4, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
                Column {
                    StatRow(
                        label = stringResource(R.string.analytics_overview_budget_usage),
                        value = "${(pct * 100).toInt()}%",
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun TrendSection(
    uiState: AnalyticsUiState,
    onTrendPeriodChange: (TrendPeriod) -> Unit,
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    SectionCard(title = stringResource(R.string.analytics_trend_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SegmentedTabs(
                items = listOf(TrendPeriod.MONTHLY, TrendPeriod.YEARLY),
                selected = uiState.trendPeriod,
                onSelect = { onTrendPeriodChange(it); selectedIndex = null },
            ) { p ->
                stringResource(if (p == TrendPeriod.MONTHLY) R.string.analytics_trend_monthly else R.string.analytics_trend_yearly)
            }
            if (uiState.trendBuckets.count { it.total.signum() > 0 } < 2) {
                Text(
                    stringResource(R.string.analytics_trend_not_enough_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val symbol = uiState.currencySymbols[uiState.defaultCurrencyCode]
                val entries = uiState.trendBuckets.map { bucket ->
                    ChartEntry(
                        // Six full "2026年5月" labels don't fit the axis; the selected-point readout keeps the year.
                        label = bucket.label?.let { DateLabels.formatMonth(it.atDay(1)) }
                            ?: bucket.year?.toString().orEmpty(),
                        value = bucket.total.toDouble(),
                    )
                }
                val locale = currentLocale()
                LineBarChart(
                    entries = entries,
                    style = LineBarStyle.LINE,
                    selectedIndex = selectedIndex,
                    onSelect = { selectedIndex = it },
                    valueLabel = {
                        MoneyFormatter.format(
                            amount = BigDecimal(it),
                            currencyCode = uiState.defaultCurrencyCode,
                            symbol = symbol,
                            locale = locale,
                            hideDecimals = uiState.hideDecimalPlaces,
                        )
                    },
                )
                val selected = selectedIndex?.let { uiState.trendBuckets.getOrNull(it) }
                if (selected != null) {
                    val share = if (uiState.trendTotal.signum() > 0) {
                        selected.total.divide(uiState.trendTotal, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100))
                            .setScale(1, RoundingMode.HALF_UP)
                    } else BigDecimal.ZERO
                    val vsAvg = selected.total - uiState.trendAverage
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(
                                R.string.analytics_trend_details_title,
                                selected.label?.let { DateLabels.formatYearMonth(it.atDay(1)) } ?: selected.year?.toString().orEmpty(),
                            ),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        StatRow(stringResource(R.string.analytics_trend_amount), formatMoney(selected.total, uiState.defaultCurrencyCode, symbol, hideDecimals = uiState.hideDecimalPlaces))
                        StatRow(stringResource(R.string.analytics_trend_share), "$share%")
                        StatRow(
                            stringResource(R.string.analytics_trend_vs_average),
                            formatMoney(vsAvg, uiState.defaultCurrencyCode, symbol, showPlusSign = true, hideDecimals = uiState.hideDecimalPlaces),
                        )
                        TextButton(onClick = { selectedIndex = null }) { Text(stringResource(R.string.analytics_trend_clear_selection)) }
                    }
                }
                StatRow(stringResource(R.string.analytics_trend_total), formatMoney(uiState.trendTotal, uiState.defaultCurrencyCode, symbol, hideDecimals = uiState.hideDecimalPlaces))
                StatRow(stringResource(R.string.analytics_trend_average), formatMoney(uiState.trendAverage, uiState.defaultCurrencyCode, symbol, hideDecimals = uiState.hideDecimalPlaces))
                uiState.trendPeakIndex?.let { peak ->
                    val bucket = uiState.trendBuckets.getOrNull(peak)
                    if (bucket != null) {
                        StatRow(
                            stringResource(R.string.analytics_trend_peak),
                            (bucket.label?.let { DateLabels.formatYearMonth(it.atDay(1)) } ?: bucket.year?.toString().orEmpty()),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatmapSection(uiState: AnalyticsUiState) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val levelLabels = listOf(
        stringResource(R.string.analytics_heatmap_level_none),
        stringResource(R.string.analytics_heatmap_level_low),
        stringResource(R.string.analytics_heatmap_level_medium),
        stringResource(R.string.analytics_heatmap_level_high),
    )
    SectionCard(title = stringResource(R.string.analytics_heatmap_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HeatmapGrid(
                days = uiState.heatmapCells.map { HeatmapDay(it.date, it.count.toDouble()) },
                selectedIndex = selectedIndex,
                onSelect = { selectedIndex = it },
                dayDescription = { day ->
                    val cell = uiState.heatmapCells.firstOrNull { it.date == day.date }
                    "${DateLabels.formatDate(day.date)}: ${cell?.count ?: 0}, ${levelLabels[cell?.level?.level ?: 0]}"
                },
            )
            val selected = selectedIndex?.let { uiState.heatmapCells.getOrNull(it) }
            if (selected != null) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(DateLabels.formatDate(selected.date), style = MaterialTheme.typography.titleSmall)
                    StatRow(stringResource(R.string.analytics_heatmap_count_label), selected.count.toString())
                    StatRow(
                        stringResource(R.string.analytics_trend_amount),
                        formatMoney(selected.amount, uiState.defaultCurrencyCode, uiState.currencySymbols[uiState.defaultCurrencyCode], hideDecimals = uiState.hideDecimalPlaces),
                    )
                    StatRow(stringResource(R.string.analytics_heatmap_title), levelLabels[selected.level.level])
                }
            }
            StatRow(stringResource(R.string.analytics_heatmap_total), uiState.heatmapTotal.toString())
            StatRow(stringResource(R.string.analytics_heatmap_average), uiState.heatmapAverage.toPlainString())
            uiState.heatmapPeakIndex?.let { peak ->
                uiState.heatmapCells.getOrNull(peak)?.let { cell ->
                    StatRow(stringResource(R.string.analytics_heatmap_peak_day), DateLabels.formatMonthDay(cell.date))
                }
            }
        }
    }
}

@Composable
private fun CategorySection(
    uiState: AnalyticsUiState,
    onExpandedChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val symbol = uiState.currencySymbols[uiState.defaultCurrencyCode]
    SectionCard(title = stringResource(R.string.analytics_category_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val entries = uiState.categoryTotals.map { total ->
                ChartEntry(
                    label = uiState.categoryNames[total.categoryKey] ?: total.categoryKey,
                    value = total.amount.toDouble(),
                )
            }
            if (entries.isEmpty()) {
                Text(
                    stringResource(R.string.analytics_empty_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val donutLocale = currentLocale()
                DonutChart(
                    entries = entries,
                    valueLabel = {
                        MoneyFormatter.format(
                            amount = BigDecimal(it),
                            currencyCode = uiState.defaultCurrencyCode,
                            symbol = symbol,
                            locale = donutLocale,
                            hideDecimals = uiState.hideDecimalPlaces,
                        )
                    },
                    centerLabel = stringResource(R.string.analytics_trend_total),
                    centerValue = formatMoney(
                        entries.fold(BigDecimal.ZERO) { a, b -> a + BigDecimal(b.value) },
                        uiState.defaultCurrencyCode, symbol, hideDecimals = uiState.hideDecimalPlaces,
                    ),
                )
                Text(
                    pluralStringResource(
                        R.plurals.analytics_category_count,
                        uiState.categoryShownCount,
                        uiState.categoryShownCount,
                    ) + " · " + stringResource(R.string.analytics_category_shown_of, uiState.categoryTotals.size, uiState.categoryShownCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (uiState.categoryShownCount > 7) {
                    TextButton(onClick = { expanded = !expanded; onExpandedChange(expanded) }) {
                        Text(stringResource(if (expanded) R.string.analytics_category_collapse else R.string.analytics_category_expand))
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarSection(uiState: AnalyticsUiState) {
    val metrics = uiState.radar ?: return
    if (uiState.radarAxes.isEmpty()) return
    SectionCard(title = stringResource(R.string.analytics_radar_title)) {
        // A radar needs at least three axes (categories) to draw a shape.
        if (uiState.radarAxes.size < 3) {
            Text(
                stringResource(R.string.analytics_radar_not_enough_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        RadarChart(
            axes = uiState.radarAxes,
            series = listOf(
                RadarSeries(stringResource(R.string.analytics_radar_amount), metrics.amount),
                RadarSeries(stringResource(R.string.analytics_radar_count), metrics.count),
                RadarSeries(stringResource(R.string.analytics_radar_average), metrics.average),
            ),
        )
    }
}

@Composable
private fun StoredValueSection(onOpen: () -> Unit) {
    SectionCard(
        title = stringResource(R.string.analytics_stored_value_title),
        actionLabel = stringResource(R.string.analytics_stored_value_open),
        onAction = onOpen,
        onClick = onOpen,
    ) {
        Text(
            stringResource(R.string.analytics_stored_value_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
