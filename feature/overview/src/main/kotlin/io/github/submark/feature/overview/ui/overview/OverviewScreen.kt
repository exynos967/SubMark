package io.github.submark.feature.overview.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.submark.core.data.settings.ClassicOverviewComponent
import io.github.submark.core.data.settings.ComponentSetting
import io.github.submark.core.data.settings.ModernOverviewComponent
import io.github.submark.core.data.settings.OverviewLayout
import io.github.submark.core.data.settings.SummaryPeriod
import io.github.submark.core.data.settings.SpendingMode
import io.github.submark.core.model.MarkTiming
import io.github.submark.core.ui.component.MarkPaidDialog
import androidx.compose.material.icons.rounded.Settings
import io.github.submark.core.ui.component.SegmentedTabs
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.component.formatMoney
import io.github.submark.core.ui.format.DateLabels
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.icon.SubscriptionIcon
import io.github.submark.core.ui.theme.SubMarkTheme
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.feature.overview.R
import io.github.submark.feature.overview.data.PaymentOccurrence
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.FormatStyle

@Composable
fun OverviewRoute(
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onCustomize: () -> Unit,
    onAddSubscription: () -> Unit,
    onSubscriptions: () -> Unit,
    onAnalytics: () -> Unit,
    onWalletManagement: () -> Unit,
    onWalletTopUp: (String) -> Unit,
    onWalletExpense: (String) -> Unit,
    onFinancialDetail: () -> Unit,
    onFinancialReport: () -> Unit,
    onPriceMonitor: () -> Unit,
    viewModel: OverviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    SnackbarEffect(messages = viewModel.snackbars, hostState = snackbarHostState)

    OverviewScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onSearch = onSearch,
        onCustomize = onCustomize,
        onAddSubscription = onAddSubscription,
        onSubscriptions = onSubscriptions,
        onAnalytics = onAnalytics,
        onWalletManagement = onWalletManagement,
        onWalletTopUp = onWalletTopUp,
        onWalletExpense = onWalletExpense,
        onFinancialDetail = onFinancialDetail,
        onFinancialReport = onFinancialReport,
        onPriceMonitor = onPriceMonitor,
        onPeriodSelect = viewModel::setPeriod,
        onSpendingModeSelect = viewModel::setSpendingMode,
        onLayoutSelect = viewModel::setLayout,
        onSelectStripDate = viewModel::selectStripDate,
        onMarkPaid = viewModel::requestMarkPaid,
        onConfirmMarkPaid = viewModel::confirmMarkPaid,
        onDismissMarkPaid = viewModel::dismissMarkPaid,
        onPause = viewModel::pauseSubscription,
    )
}

@Composable
private fun OverviewScreen(
    uiState: OverviewUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onCustomize: () -> Unit,
    onAddSubscription: () -> Unit,
    onSubscriptions: () -> Unit,
    onAnalytics: () -> Unit,
    onWalletManagement: () -> Unit,
    onWalletTopUp: (String) -> Unit,
    onWalletExpense: (String) -> Unit,
    onFinancialDetail: () -> Unit,
    onFinancialReport: () -> Unit,
    onPriceMonitor: () -> Unit,
    onPeriodSelect: (SummaryPeriod) -> Unit,
    onSpendingModeSelect: (SpendingMode) -> Unit,
    onLayoutSelect: (OverviewLayout) -> Unit,
    onSelectStripDate: (LocalDate) -> Unit,
    onMarkPaid: (PaymentOccurrence) -> Unit,
    onConfirmMarkPaid: (MarkTiming) -> Unit,
    onDismissMarkPaid: () -> Unit,
    onPause: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            OverviewTopBar(
                onSearch = onSearch,
                onCustomize = onCustomize,
                onAdd = onAddSubscription,
                onPoster = onFinancialReport,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (uiState.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
                return@Column
            }

            // ── Inline layout switcher (Modern | Classic) ──────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SegmentedTabs(
                    items = OverviewLayout.entries.toList(),
                    selected = uiState.layout,
                    onSelect = onLayoutSelect,
                    modifier = Modifier.width(160.dp),
                    label = {
                        when (it) {
                            OverviewLayout.MODERN -> stringResource(R.string.overview_layout_modern)
                            OverviewLayout.CLASSIC -> stringResource(R.string.overview_layout_classic)
                        }
                    },
                )
            }

            // ── Body ──────────────────────────────────────────────────────
            when {
                uiState.layout == OverviewLayout.MODERN -> ModernBody(
                    uiState = uiState,
                    onPeriodSelect = onPeriodSelect,
                    onSpendingModeSelect = onSpendingModeSelect,
                    onMarkPaid = onMarkPaid,
                    onSelectStripDate = onSelectStripDate,
                    onPause = onPause,
                    onFinancialDetail = onFinancialDetail,
                    onWalletManagement = onWalletManagement,
                    onWalletTopUp = onWalletTopUp,
                    onWalletExpense = onWalletExpense,
                    onSubscriptions = onSubscriptions,
                    onAnalytics = onAnalytics,
                    onPriceMonitor = onPriceMonitor,
                    modifier = Modifier.weight(1f),
                )
                else -> ClassicBody(
                    uiState = uiState,
                    onPeriodSelect = onPeriodSelect,
                    onSpendingModeSelect = onSpendingModeSelect,
                    onMarkPaid = onMarkPaid,
                    onSelectStripDate = onSelectStripDate,
                    onPause = onPause,
                    onFinancialDetail = onFinancialDetail,
                    onWalletManagement = onWalletManagement,
                    onWalletTopUp = onWalletTopUp,
                    onWalletExpense = onWalletExpense,
                    onSubscriptions = onSubscriptions,
                    onAnalytics = onAnalytics,
                    onPriceMonitor = onPriceMonitor,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // ── Mark-paid dialog ────────────────────────────────────────────────────
    uiState.markTarget?.let { target ->
        MarkPaidDialog(
            name = target.name,
            amountText = formatMoney(target.amount, target.currencyCode, uiState.currencySymbols[target.currencyCode]),
            dueDate = target.dueDate,
            today = LocalDate.now(),
            onConfirm = onConfirmMarkPaid,
            onDismiss = onDismissMarkPaid,
        )
    }
}

@Composable
private fun OverviewTopBar(
    onSearch: () -> Unit,
    onCustomize: () -> Unit,
    onAdd: () -> Unit,
    onPoster: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    SubMarkTopAppBar(
        title = stringResource(R.string.overview_title),
        subtitle = stringResource(R.string.overview_subtitle),
        onBack = onBack,
        actions = {
            IconButton(onClick = onSearch) {
                Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.overview_action_search))
            }
            IconButton(onClick = onCustomize) {
                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.overview_action_customize))
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.overview_action_add))
            }
            IconButton(onClick = onPoster) {
                Icon(Icons.Rounded.Assessment, contentDescription = stringResource(R.string.overview_action_poster))
            }
        },
    )
}

// ── Reusable small components ─────────────────────────────────────────────────

@Composable
internal fun PeriodSelector(
    selected: SummaryPeriod,
    onSelect: (SummaryPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedTabs(
        items = SummaryPeriod.entries.toList(),
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        label = {
            when (it) {
                SummaryPeriod.MONTH -> stringResource(R.string.overview_period_month)
                SummaryPeriod.QUARTER -> stringResource(R.string.overview_period_quarter)
                SummaryPeriod.YEAR -> stringResource(R.string.overview_period_year)
            }
        },
    )
}

@Composable
internal fun SpendingModeSelector(
    selected: SpendingMode,
    onSelect: (SpendingMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedTabs(
        items = SpendingMode.entries.toList(),
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        label = {
            when (it) {
                SpendingMode.SUBSCRIPTIONS -> stringResource(R.string.overview_mode_subscriptions)
                SpendingMode.LIFETIME -> stringResource(R.string.overview_mode_lifetime)
            }
        },
    )
}

@Composable
internal fun AmountRow(
    label: String,
    amount: BigDecimal,
    currencyCode: String,
    symbols: Map<String, String>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatMoney(amount, currencyCode, symbols[currencyCode], hideDecimals = false),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
internal fun OccurrenceRow(
    occ: PaymentOccurrence,
    today: LocalDate,
    defaultCurrencyCode: String,
    symbols: Map<String, String>,
    onMarkPaid: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = onMarkPaid != null) { onMarkPaid?.invoke() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SubscriptionIcon(
            type = occ.subscription.iconType,
            value = occ.subscription.iconValue,
            fallbackName = occ.subscription.name,
            size = 36.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = occ.subscription.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (occ.paid) {
                    Spacer(Modifier.width(4.dp))
                    Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                        Text(stringResource(R.string.overview_badge_paid), style = MaterialTheme.typography.labelSmall)
                    }
                } else if (occ.isOverdue(today)) {
                    Spacer(Modifier.width(4.dp))
                    Badge(containerColor = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            stringResource(R.string.overview_overdue_by, java.time.temporal.ChronoUnit.DAYS.between(occ.date, today).toInt()),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                } else {
                    Spacer(Modifier.width(4.dp))
                    val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, occ.date)
                    val countdownText = when {
                        daysUntil == 0L -> stringResource(R.string.overview_badge_today)
                        daysUntil == 1L -> stringResource(R.string.overview_badge_tomorrow)
                        else -> stringResource(R.plurals.overview_days_until, daysUntil.toInt(), daysUntil.toInt())
                    }
                    Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(countdownText, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (!occ.subscription.note.isNullOrBlank()) {
                Text(
                    text = occ.subscription.note.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            occ.amount?.let { amount ->
                Text(
                    text = formatMoney(amount, defaultCurrencyCode, symbols[defaultCurrencyCode], hideDecimals = false),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                text = DateLabels.formatDate(occ.date, FormatStyle.MEDIUM),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!occ.paid && onMarkPaid != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = stringResource(io.github.submark.core.ui.R.string.ui_mark_paid_confirm),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

// ── Modern body ───────────────────────────────────────────────────────────────

@Composable
private fun ModernBody(
    uiState: OverviewUiState,
    onPeriodSelect: (SummaryPeriod) -> Unit,
    onSpendingModeSelect: (SpendingMode) -> Unit,
    onMarkPaid: (PaymentOccurrence) -> Unit,
    onSelectStripDate: (LocalDate) -> Unit,
    onPause: (String) -> Unit,
    onFinancialDetail: () -> Unit,
    onWalletManagement: () -> Unit,
    onWalletTopUp: (String) -> Unit,
    onWalletExpense: (String) -> Unit,
    onSubscriptions: () -> Unit,
    onAnalytics: () -> Unit,
    onPriceMonitor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mode = uiState.spendingMode
    val components = uiState.modernComponents.filter { it.visible }.sortedBy { uiState.modernComponents.indexOf(it) }

    LazyColumn(modifier = modifier) {
        // Welcome / empty
        if (uiState.subscriptionCount == 0 && mode == SpendingMode.SUBSCRIPTIONS) {
            item {
                EmptyHero(onAdd = onSubscriptions)
            }
        }

        // Spending hero
        if (components.any { it.id == ModernOverviewComponent.SPENDING_HERO }) {
            item { HeroSection(uiState, onPeriodSelect, onFinancialDetail) }
        }

        // Coming up
        if (components.any { it.id == ModernOverviewComponent.COMING_UP }) {
            item { ComingUpSection(uiState, onMarkPaid, onPause) }
        }

        // Payment schedule strip
        if (components.any { it.id == ModernOverviewComponent.PAYMENT_SCHEDULE }) {
            item { PaymentScheduleSection(uiState, onSelectStripDate) }
        }

        // Recent payments
        if (components.any { it.id == ModernOverviewComponent.RECENT_PAYMENTS }) {
            item { RecentPaymentsSection(uiState) }
        }

        // Wallet balances
        if (components.any { it.id == ModernOverviewComponent.WALLET_BALANCES }) {
            item { WalletSection(uiState, onWalletManagement, onWalletTopUp, onWalletExpense) }
        }

        // Wishlist prices
        if (components.any { it.id == ModernOverviewComponent.WISHLIST_PRICES }) {
            item { WishlistPricesSection(uiState, onPriceMonitor) }
        }

        // My subscriptions & purchases
        if (components.any { it.id == ModernOverviewComponent.MY_SUBSCRIPTIONS }) {
            item {
                MySubscriptionsSection(uiState, mode, onSubscriptions)
            }
        }

        // Spending insights
        if (components.any { it.id == ModernOverviewComponent.SPENDING_INSIGHTS }) {
            item { SpendingInsightsSection(onAnalytics) }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ── Classic body ───────────────────────────────────────────────────────────────

@Composable
private fun ClassicBody(
    uiState: OverviewUiState,
    onPeriodSelect: (SummaryPeriod) -> Unit,
    onSpendingModeSelect: (SpendingMode) -> Unit,
    onMarkPaid: (PaymentOccurrence) -> Unit,
    onSelectStripDate: (LocalDate) -> Unit,
    onPause: (String) -> Unit,
    onFinancialDetail: () -> Unit,
    onWalletManagement: () -> Unit,
    onWalletTopUp: (String) -> Unit,
    onWalletExpense: (String) -> Unit,
    onSubscriptions: () -> Unit,
    onAnalytics: () -> Unit,
    onPriceMonitor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mode = uiState.spendingMode
    val comps = uiState.classicComponents.filter { it.visible }

    LazyColumn(modifier = modifier) {
        // Subscription vs Lifetime segmented switch
        item {
            Spacer(Modifier.height(4.dp))
            SpendingModeSelector(selected = mode, onSelect = onSpendingModeSelect, modifier = Modifier.padding(horizontal = 16.dp))
        }

        comps.forEach { comp ->
            when (comp.id) {
                ClassicOverviewComponent.EXPENSE_OVERVIEW ->
                    item { ExpenseOverviewCard(uiState, onPeriodSelect, onFinancialDetail) }
                ClassicOverviewComponent.UPCOMING_PAYMENTS ->
                    item { ComingUpSection(uiState, onMarkPaid, onPause) }
                ClassicOverviewComponent.RECENT_PAID ->
                    item { RecentlyPaidSection(uiState) }
                ClassicOverviewComponent.MONTHLY_TIMELINE ->
                    item { MonthlyTimelineCard(uiState) }
                ClassicOverviewComponent.RECENT_PAYMENT_TIMELINE ->
                    item { RecentPaymentTimelineCard(uiState, onMarkPaid) }
                ClassicOverviewComponent.CATEGORY_BREAKDOWN ->
                    item { CategoryBreakdownCard(uiState, onPeriodSelect) }
                ClassicOverviewComponent.TREND ->
                    item { TrendCard(uiState) }
                ClassicOverviewComponent.GLOBAL_WALLET ->
                    item { WalletSection(uiState, onWalletManagement, onWalletTopUp, onWalletExpense) }
                ClassicOverviewComponent.PRICE_MONITORING ->
                    item { WishlistPricesSection(uiState, onPriceMonitor) }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
