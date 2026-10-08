package io.github.submark.feature.calendar.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Save
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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import io.github.submark.core.ui.theme.SubMarkTheme
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.core.ui.util.thenIf
import io.github.submark.feature.calendar.R
import io.github.submark.feature.calendar.data.CalendarScenario
import io.github.submark.feature.calendar.data.Occurrence
import io.github.submark.feature.calendar.data.daysUntil
import io.github.submark.feature.calendar.data.startOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun CalendarRoute(
    onAddSubscription: () -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    SnackbarEffect(messages = viewModel.snackbars, hostState = snackbarHostState)

    CalendarScreen(
        nativeState = uiState,
        snackbarHostState = snackbarHostState,
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
                        CalendarMode.WEEK -> weekRangeLabel(scenario.anchorDate.startOfWeek())
                        CalendarMode.TIMELINE -> ""
                    },
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onToday = onToday,
                )
            }

            // Month grid and week strip keep their natural height; the selected day's agenda below takes the rest.
            when (scenario.mode) {
                CalendarMode.MONTH -> MonthGrid(
                    scenario = scenario,
                    byDate = nativeState.byDate,
                    onSelectDate = onSelectDate,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    modifier = Modifier.fillMaxWidth(),
                )
                CalendarMode.WEEK -> WeekStrip(
                    scenario = scenario,
                    byDate = nativeState.byDate,
                    onSelectDate = onSelectDate,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    today = scenario.today,
                )
            }

            // Agenda for the selected day (Month and Week modes)
            if (scenario.mode != CalendarMode.TIMELINE && nativeState.agenda.isNotEmpty()) {
                AgendaStrip(
                    agenda = nativeState.agenda,
                    selectedDate = scenario.selectedDate,
                    defaultCurrencyCode = nativeState.defaultCurrencyCode,
                    currencySymbols = nativeState.currencySymbols,
                    onMarkPaid = onMarkPaid,
                    today = scenario.today,
                    modifier = Modifier.padding(top = 8.dp).weight(1f),
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
            Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.calendar_previous))
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
            Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.calendar_next))
        }
    }
}

@Composable
private fun MonthGrid(
    scenario: CalendarScenario,
    byDate: Map<LocalDate, List<Occurrence>>,
    onSelectDate: (LocalDate) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        WeekdayHeader()
        SwipePager(
            page = YearMonth.from(scenario.anchorDate),
            onPrevious = onPrevious,
            onNext = onNext,
        ) { month ->
            val weeks = generateSequence(month.atDay(1).startOfWeek()) { it.plusWeeks(1) }
                .takeWhile { it <= month.atEndOfMonth() }
                .map { start -> (0L..6L).map(start::plusDays) }
                .toList()
            Column {
                weeks.forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            DayCell(
                                date = date,
                                occurrences = byDate[date].orEmpty(),
                                isSelected = date == scenario.selectedDate,
                                isToday = date == scenario.today,
                                isOutside = YearMonth.from(date) != month,
                                onSelect = { onSelectDate(date) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(
    scenario: CalendarScenario,
    byDate: Map<LocalDate, List<Occurrence>>,
    onSelectDate: (LocalDate) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        WeekdayHeader()
        SwipePager(
            page = scenario.anchorDate.startOfWeek(),
            onPrevious = onPrevious,
            onNext = onNext,
        ) { weekStart ->
            Row(Modifier.fillMaxWidth()) {
                (0L..6L).map(weekStart::plusDays).forEach { date ->
                    DayCell(
                        date = date,
                        occurrences = byDate[date].orEmpty(),
                        isSelected = date == scenario.selectedDate,
                        isToday = date == scenario.today,
                        isOutside = false,
                        onSelect = { onSelectDate(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
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
}

/**
 * Hosts one month / week page: a horizontal swipe pages back or forward, and a page change slides in
 * from the side it came from (shared axis X, same rhythm as screen navigation).
 */
@Composable
private fun <T : Comparable<T>> SwipePager(
    page: T,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    content: @Composable (T) -> Unit,
) {
    val currentOnPrevious by rememberUpdatedState(onPrevious)
    val currentOnNext by rememberUpdatedState(onNext)
    val thresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
    val slidePx = with(LocalDensity.current) { 30.dp.roundToPx() }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val sign = if (targetState > initialState) 1 else -1
            val enter = slideInHorizontally(tween(PAGE_DURATION, easing = EmphasizedDecelerate)) { sign * slidePx } +
                fadeIn(tween(PAGE_DURATION - PAGE_FADE_OUT, delayMillis = PAGE_FADE_OUT))
            val exit = slideOutHorizontally(tween(PAGE_DURATION, easing = EmphasizedDecelerate)) { -sign * slidePx } +
                fadeOut(tween(PAGE_FADE_OUT))
            enter togetherWith exit
        },
        label = "calendarPage",
        modifier = Modifier.pointerInput(Unit) {
            var dragged = 0f
            detectHorizontalDragGestures(
                onDragStart = { dragged = 0f },
                onDragEnd = {
                    when {
                        dragged > thresholdPx -> currentOnPrevious()
                        dragged < -thresholdPx -> currentOnNext()
                    }
                },
            ) { _, delta -> dragged += delta }
        },
    ) { target -> content(target) }
}

/**
 * One day in the month grid or week strip: the date sits in a circle (filled when selected, outlined
 * for today) and up to three dots below mark that day's payments. The whole cell is tappable while the
 * ripple stays inside the circle.
 */
@Composable
private fun DayCell(
    date: LocalDate,
    occurrences: List<Occurrence>,
    isSelected: Boolean,
    isToday: Boolean,
    isOutside: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        if (isSelected) colors.primary else colors.primary.copy(alpha = 0f),
        animationSpec = tween(SELECTION_DURATION),
        label = "dayContainer",
    )
    val contentColor by animateColorAsState(
        when {
            isSelected -> colors.onPrimary
            isToday -> colors.primary
            isOutside -> colors.onSurface.copy(alpha = 0.38f)
            else -> colors.onSurface
        },
        animationSpec = tween(SELECTION_DURATION),
        label = "dayContent",
    )
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .height(DAY_CELL_HEIGHT)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onSelect),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(DAY_CIRCLE_SIZE)
                .clip(CircleShape)
                .background(containerColor)
                .thenIf(isToday && !isSelected) { Modifier.border(1.dp, colors.primary, CircleShape) }
                .indication(interactionSource, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isSelected) FontWeight.SemiBold else null,
                color = contentColor,
            )
        }
        if (occurrences.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 4.dp).alpha(if (isOutside) 0.38f else 1f),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                occurrences.take(MAX_DOTS).forEach { occ ->
                    val color = when {
                        occ.paid -> SubMarkTheme.extendedColors.success
                        occ.scheduled -> colors.primary
                        else -> colors.onSurfaceVariant
                    }
                    Box(Modifier.size(5.dp).clip(CircleShape).background(color))
                }
                if (occurrences.size > MAX_DOTS) {
                    Text(
                        text = "+${occurrences.size - MAX_DOTS}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val DAY_CELL_HEIGHT = 54.dp
private val DAY_CIRCLE_SIZE = 38.dp
private const val MAX_DOTS = 3
private const val SELECTION_DURATION = 150
private const val PAGE_DURATION = 300
private const val PAGE_FADE_OUT = 90

/** M3 "emphasized decelerate" easing. */
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

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
        daysUntilValue > 0 -> pluralStringResource(R.plurals.calendar_days_until, daysUntilValue.toInt(), daysUntilValue.toInt())
        else -> pluralStringResource(R.plurals.calendar_days_overdue, (-daysUntilValue).toInt(), (-daysUntilValue).toInt())
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
                text = DateTimeFormatter.ofPattern("MMM", Locale.getDefault()).format(occ.date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Icon
        SubscriptionIcon(
            type = occ.subscription.iconType,
            value = occ.subscription.iconValue,
            fallbackName = occ.subscription.name,
            size = 36.dp,
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

private fun weekRangeLabel(weekStart: LocalDate): String =
    "${DateLabels.formatMonthDay(weekStart)} – ${DateLabels.formatMonthDay(weekStart.plusDays(6))}"

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
