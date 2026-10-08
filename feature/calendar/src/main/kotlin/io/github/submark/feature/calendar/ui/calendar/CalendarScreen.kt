package io.github.submark.feature.calendar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Today
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
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.submark.core.data.settings.CalendarMode
import io.github.submark.core.data.settings.TimelinePeriod
import io.github.submark.core.ui.component.MarkPaidDialog
import io.github.submark.core.ui.component.SearchField
import io.github.submark.core.ui.component.SegmentedTabs
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.component.formatMoney
import io.github.submark.core.ui.format.DateLabels
import io.github.submark.core.ui.icon.SubscriptionIcon
import io.github.submark.core.ui.util.thenIf
import io.github.submark.core.ui.theme.SubMarkTheme
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.feature.calendar.R
import io.github.submark.feature.calendar.data.CalendarScenario
import io.github.submark.feature.calendar.data.Occurrence
import io.github.submark.feature.calendar.data.daysUntil
import io.github.submark.feature.calendar.data.startOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun CalendarRoute(
    onBack: () -> Unit,
    onAddSubscription: () -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    SnackbarEffect(messages = viewModel.snackbars, hostState = snackbarHostState)

    CalendarScreen(
        nativeState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onAddSubscription = onAddSubscription,
        onModeSelect = viewModel::setMode,
        onTimelinePeriodSelect = viewModel::setTimelinePeriod,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onToday = viewModel::goToday,
        onSelectDate = viewModel::select,
        onJumpTo = viewModel::jumpTo,
        onMarkPaid = viewModel::requestMarkPaid,
        onConfirmMarkPaid = viewModel::confirmMarkPaid,
        onDismissMarkPaid = viewModel::dismissMarkPaid,
    )
}

@Composable
private fun CalendarScreen(
    nativeState: CalendarUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAddSubscription: () -> Unit,
    onModeSelect: (CalendarMode) -> Unit,
    onTimelinePeriodSelect: (TimelinePeriod) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onJumpTo: (LocalDate) -> Unit,
    onMarkPaid: (Occurrence) -> Unit,
    onConfirmMarkPaid: (io.github.submark.core.model.MarkTiming) -> Unit,
    onDismissMarkPaid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scenario = nativeState.scenario
    var showJumpDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SubMarkTopAppBar(
                title = stringResource(R.string.calendar_title),
                subtitle = stringResource(R.string.calendar_subtitle),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showJumpDialog = true }) {
                        Icon(
                            Icons.Rounded.Today,
                            contentDescription = stringResource(R.string.calendar_jump_to_date),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (scenario == null) return@Column

            // Mode tabs
            SegmentedTabs(
                items = CalendarMode.entries.toList(),
                selected = scenario.mode,
                onSelect = onModeSelect,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                label = { mode ->
                    when (mode) {
                        CalendarMode.MONTH -> stringResource(R.string.calendar_mode_month)
                        CalendarMode.WEEK -> stringResource(R.string.calendar_mode_week)
                        CalendarMode.TIMELINE -> stringResource(R.string.calendar_mode_timeline)
                    }
                },
            )

            // Navigation row (prev/next/today) — shown for Month & Week
            if (scenario.mode != CalendarMode.TIMELINE) {
                PagerRow(
                    anchorText = when (scenario.mode) {
                        CalendarMode.MONTH -> DateLabels.formatYearMonth(scenario.anchorDate)
                        CalendarMode.WEEK -> weekRangeLabel(scenario.anchorDate)
                        CalendarMode.TIMELINE -> ""
                    },
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onToday = onToday,
                )
            }

            val contentModifier = Modifier.fillMaxWidth().weight(1f)
            when (scenario.mode) {
                CalendarMode.MONTH -> MonthGrid(
                    scenario = scenario,
                    byDate = nativeState.byDate,
                    includeChildren = nativeState.includeChildren,
                    defaultCurrencyCode = nativeState.defaultCurrencyCode,
                    currencySymbols = nativeState.currencySymbols,
                    onSelectDate = onSelectDate,
                    onMarkPaid = onMarkPaid,
                    modifier = contentModifier,
                    today = scenario.today,
                )
                CalendarMode.WEEK -> WeekStrip(
                    scenario = scenario,
                    byDate = nativeState.byDate,
                    onSelectDate = onSelectDate,
                    modifier = contentModifier,
                )
                CalendarMode.TIMELINE -> TimelineView(
                    scenario = scenario,
                    buckets = nativeState.timeline,
                    defaultCurrencyCode = nativeState.defaultCurrencyCode,
                    currencySymbols = nativeState.currencySymbols,
                    onPeriodSelect = onTimelinePeriodSelect,
                    onMarkPaid = onMarkPaid,
                    onAddSubscription = onAddSubscription,
                    hasSubscriptions = nativeState.hasSubscriptions,
                    modifier = contentModifier,
                    today = scenario.today,
                )
            }

            // Bottom agenda for Month mode
            if (scenario.mode == CalendarMode.MONTH && nativeState.agenda.isNotEmpty()) {
                AgendaStrip(
                    agenda = nativeState.agenda,
                    selectedDate = scenario.selectedDate,
                    defaultCurrencyCode = nativeState.defaultCurrencyCode,
                    currencySymbols = nativeState.currencySymbols,
                    onMarkPaid = onMarkPaid,
                    today = scenario.today,
                )
            }
        }
    }

    // Jump to date dialog
    if (showJumpDialog) {
        val dialogState = rememberDatePickerState(
            initialSelectedDateMillis = (scenario?.selectedDate ?: LocalDate.now())
                .toEpochDay().let { it * 86_400_000L },
        )
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text(stringResource(R.string.calendar_jump_to_date)) },
            text = {
                androidx.compose.material3.DatePicker(state = dialogState)
            },
            confirmButton = {
                TextButton(onClick = {
                    dialogState.selectedDateMillis?.let { millis ->
                        onJumpTo(LocalDate.ofEpochDay(millis / 86_400_000L))
                    }
                    showJumpDialog = false
                }) { Text(stringResource(io.github.submark.core.ui.R.string.ui_action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text(stringResource(io.github.submark.core.ui.R.string.ui_action_cancel))
                }
            },
        )
    }

    // Mark-paid dialog
    nativeState.markTarget?.let { target ->
        MarkPaidDialog(
            name = target.name,
            amountText = formatMoney(target.amount, target.currencyCode, nativeState.currencySymbols[target.currencyCode]),
            dueDate = target.dueDate,
            today = scenario?.today ?: LocalDate.now(),
            onConfirm = onConfirmMarkPaid,
            onDismiss = onDismissMarkPaid,
        )
    }
}

@Composable
private fun PagerRow(
    anchorText: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(io.github.submark.core.ui.R.string.ui_action_back))
        }
        Text(
            text = anchorText,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onToday, modifier = Modifier.height(32.dp)) {
            Text(stringResource(R.string.calendar_today), style = MaterialTheme.typography.labelLarge)
        }
        IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(io.github.submark.core.ui.R.string.ui_action_back))
        }
    }
}

@Composable
private fun MonthGrid(
    scenario: CalendarScenario,
    byDate: Map<LocalDate, List<Occurrence>>,
    includeChildren: Boolean,
    defaultCurrencyCode: String,
    currencySymbols: Map<String, String>,
    onSelectDate: (LocalDate) -> Unit,
    onMarkPaid: (Occurrence) -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val monthStart = YearMonth.from(scenario.anchorDate).atDay(1)
    val monthEnd = YearMonth.from(scenario.anchorDate).atEndOfMonth()
    val weeks = mutableListOf<List<LocalDate>>()
    var weekStart = monthStart
    while (weekStart <= monthEnd) {
        val week = (0..6).map { weekStart.plusDays(it.toLong()) }
        weeks.add(week)
        weekStart = weekStart.plusWeeks(1)
    }

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        // Weekday header
        Row(Modifier.fillMaxWidth()) {
            listOf(
                R.string.calendar_weekday_mon, R.string.calendar_weekday_tue,
                R.string.calendar_weekday_wed, R.string.calendar_weekday_thu,
                R.string.calendar_weekday_fri, R.string.calendar_weekday_sat,
                R.string.calendar_weekday_sun,
            ).forEach { stringRes ->
                Text(
                    text = stringResource(stringRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                week.forEach { date ->
                    val dayOccurrences = byDate[date].orEmpty()
                    val isSelected = date == scenario.selectedDate
                    val isToday = date == today
                    val isCurrentMonth = YearMonth.from(date) == YearMonth.from(scenario.anchorDate)

                    CalendarDayCell(
                        date = date,
                        dayOccurrences = dayOccurrences,
                        isSelected = isSelected,
                        isToday = isToday,
                        isCurrentMonth = isCurrentMonth,
                        includeChildren = includeChildren,
                        defaultCurrencyCode = defaultCurrencyCode,
                        onSelect = { onSelectDate(date) },
                        onMarkPaid = onMarkPaid,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    dayOccurrences: List<Occurrence>,
    isSelected: Boolean,
    isToday: Boolean,
    isCurrentMonth: Boolean,
    includeChildren: Boolean,
    defaultCurrencyCode: String,
    onSelect: () -> Unit,
    onMarkPaid: (Occurrence) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        isToday -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.tertiary
        else -> Color.Transparent
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .thenIf(isSelected || isToday) {
                Modifier.border(
                    width = 1.5.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(6.dp),
                )
            }
            .clickable(onClick = onSelect)
            .padding(2.dp),
    ) {
        // Day number
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = when {
                !isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
                isToday -> MaterialTheme.colorScheme.tertiary
                isSelected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        )

        // Icons or dots
        if (dayOccurrences.isNotEmpty()) {
            val showIcons = dayOccurrences.size <= 4
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (showIcons) {
                    dayOccurrences.take(4).forEach { occ ->
                        SubscriptionIcon(
                            type = occ.subscription.iconType,
                            value = occ.subscription.iconValue,
                            fallbackName = "payments",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    // Just show markers
                    dayOccurrences.take(3).forEach { occ ->
                        val color = when {
                            occ.paid -> SubMarkTheme.extendedColors.success
                            occ.scheduled -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(color),
                        )
                    }
                    Text(
                        text = "+${dayOccurrences.size - 3}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Amount if small
        if (dayOccurrences.isNotEmpty() && dayOccurrences.size <= 2) {
            val totalAmount = dayOccurrences.mapNotNull { it.amount }.fold(java.math.BigDecimal.ZERO) { a, b -> a + b }
            if (totalAmount.signum() > 0) {
                Text(
                    text = formatMoney(totalAmount, defaultCurrencyCode, hideDecimals = true, compact = true),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun WeekStrip(
    scenario: CalendarScenario,
    byDate: Map<LocalDate, List<Occurrence>>,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weekStart = scenario.anchorDate.startOfWeek()
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        // Weekday labels
        Row(Modifier.fillMaxWidth()) {
            listOf(
                R.string.calendar_weekday_mon, R.string.calendar_weekday_tue,
                R.string.calendar_weekday_wed, R.string.calendar_weekday_thu,
                R.string.calendar_weekday_fri, R.string.calendar_weekday_sat,
                R.string.calendar_weekday_sun,
            ).forEach { stringRes ->
                Text(
                    text = stringResource(stringRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        // Day cells
        Row(Modifier.fillMaxWidth()) {
            (0..6).forEach { offset ->
                val date = weekStart.plusDays(offset.toLong())
                val dayOccurrences = byDate[date].orEmpty()
                val isSelected = date == scenario.selectedDate
                val isToday = date == scenario.today

                WeekDayCell(
                    date = date,
                    count = dayOccurrences.size,
                    isSelected = isSelected,
                    isToday = isToday,
                    onSelect = { onSelectDate(date) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WeekDayCell(
    date: LocalDate,
    count: Int,
    isSelected: Boolean,
    isToday: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    isToday -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onSelect)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = when {
                isToday -> MaterialTheme.colorScheme.tertiary
                isSelected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        if (count > 0) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TimelineView(
    scenario: CalendarScenario,
    buckets: List<io.github.submark.feature.calendar.data.TimelineBucket>,
    defaultCurrencyCode: String,
    currencySymbols: Map<String, String>,
    onPeriodSelect: (TimelinePeriod) -> Unit,
    onMarkPaid: (Occurrence) -> Unit,
    onAddSubscription: () -> Unit,
    hasSubscriptions: Boolean,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Period tabs
        SegmentedTabs(
            items = TimelinePeriod.entries.toList(),
            selected = scenario.timelinePeriod,
            onSelect = onPeriodSelect,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            label = { period ->
                when (period) {
                    TimelinePeriod.ONE_MONTH -> stringResource(R.string.calendar_timeline_period_1m)
                    TimelinePeriod.THREE_MONTHS -> stringResource(R.string.calendar_timeline_period_3m)
                    TimelinePeriod.SIX_MONTHS -> stringResource(R.string.calendar_timeline_period_6m)
                    TimelinePeriod.ONE_YEAR -> stringResource(R.string.calendar_timeline_period_1y)
                }
            },
        )

        if (buckets.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Rounded.CalendarToday,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (!hasSubscriptions) {
                        stringResource(R.string.calendar_no_subscriptions)
                    } else {
                        stringResource(
                            R.string.calendar_timeline_empty,
                            when (scenario.timelinePeriod) {
                                TimelinePeriod.ONE_MONTH -> stringResource(R.string.calendar_timeline_period_1m)
                                TimelinePeriod.THREE_MONTHS -> stringResource(R.string.calendar_timeline_period_3m)
                                TimelinePeriod.SIX_MONTHS -> stringResource(R.string.calendar_timeline_period_6m)
                                TimelinePeriod.ONE_YEAR -> stringResource(R.string.calendar_timeline_period_1y)
                            },
                        )
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onAddSubscription) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.calendar_add_subscription))
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        text = stringResource(R.string.calendar_timeline_upcoming),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                buckets.forEach { bucket ->
                    item { TimelineMonthHeader(bucket) }
                    items(bucket.occurrences, key = { "${it.subscription.id}_${it.date}_${it.payment?.id ?: "sch"}" }) { occ ->
                        TimelineRow(
                            occ = occ,
                            today = today,
                            defaultCurrencyCode = defaultCurrencyCode,
                            currencySymbols = currencySymbols,
                            onMarkPaid = onMarkPaid,
                        )
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun TimelineMonthHeader(bucket: io.github.submark.feature.calendar.data.TimelineBucket) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            text = DateLabels.formatYearMonth(bucket.month.atDay(1)),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimelineRow(
    occ: Occurrence,
    today: LocalDate,
    defaultCurrencyCode: String,
    currencySymbols: Map<String, String>,
    onMarkPaid: (Occurrence) -> Unit,
) {
    val daysUntilValue = daysUntil(occ.date, today)
    val countdownText = when {
        daysUntilValue == 0L -> stringResource(R.string.calendar_badge_today)
        daysUntilValue == 1L -> stringResource(R.string.calendar_badge_tomorrow)
        daysUntilValue > 0 -> stringResource(R.plurals.calendar_days_until, daysUntilValue.toInt(), daysUntilValue.toInt())
        else -> stringResource(R.plurals.calendar_days_overdue, (-daysUntilValue).toInt(), (-daysUntilValue).toInt())
    }
    val countdownColor = when {
        occ.paid -> SubMarkTheme.extendedColors.success
        daysUntilValue < 0 -> MaterialTheme.colorScheme.error
        daysUntilValue == 0L -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!occ.paid) onMarkPaid(occ) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Date column
        Column(
            modifier = Modifier.width(44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = occ.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = if (occ.date == today) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${occ.date.month.name.take(3)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Icon
        SubscriptionIcon(
            type = occ.subscription.iconType,
            value = occ.subscription.iconValue,
            fallbackName = "payments",
            modifier = Modifier.size(36.dp),
        )

        Spacer(Modifier.width(10.dp))

        // Name + note
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = occ.subscription.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (occ.subscription.parentId != null) {
                    Spacer(Modifier.width(4.dp))
                    Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            stringResource(R.string.calendar_badge_child),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (occ.paid) {
                    Spacer(Modifier.width(4.dp))
                    Badge(containerColor = SubMarkTheme.extendedColors.successContainer) {
                        Text(
                            stringResource(R.string.calendar_badge_paid),
                            style = MaterialTheme.typography.labelSmall,
                        )
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

        // Countdown + amount
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = countdownText,
                style = MaterialTheme.typography.labelMedium,
                color = countdownColor,
            )
            occ.amount?.let { amount ->
                Text(
                    text = formatMoney(amount, defaultCurrencyCode, currencySymbols[defaultCurrencyCode]),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Mark-paid indicator
        if (!occ.paid) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .semantics { contentDescription = "Mark as paid" },
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun AgendaStrip(
    agenda: List<Occurrence>,
    selectedDate: LocalDate,
    defaultCurrencyCode: String,
    currencySymbols: Map<String, String>,
    onMarkPaid: (Occurrence) -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        HorizontalDivider()
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                Text(
                    text = DateLabels.formatDate(selectedDate),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(agenda, key = { "${it.subscription.id}_${it.date}_${it.payment?.id ?: "sch"}" }) { occ ->
                TimelineRow(
                    occ = occ,
                    today = today,
                    defaultCurrencyCode = defaultCurrencyCode,
                    currencySymbols = currencySymbols,
                    onMarkPaid = onMarkPaid,
                )
            }
        }
    }
}

private fun weekRangeLabel(anchor: LocalDate): String {
    val end = anchor.plusDays(6)
    return if (end.monthValue == anchor.monthValue && end.year == anchor.year) {
        "${anchor.month.name.take(3)} ${anchor.dayOfMonth} - ${end.dayOfMonth}"
    } else {
        "${anchor.month.name.take(3)} ${anchor.dayOfMonth} - ${end.month.name.take(3)} ${end.dayOfMonth}"
    }
}

@Preview(showBackground = true)
@Composable
private fun CalendarScreenPreview() {
    SubMarkTheme {
        CalendarScreen(
            nativeState = CalendarUiState(
                loading = false,
                scenario = null,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onAddSubscription = {},
            onModeSelect = {},
            onTimelinePeriodSelect = {},
            onPrevious = {},
            onNext = {},
            onToday = {},
            onSelectDate = {},
            onJumpTo = {},
            onMarkPaid = {},
            onConfirmMarkPaid = {},
            onDismissMarkPaid = {},
        )
    }
}
