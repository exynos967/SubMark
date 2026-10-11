package io.github.submark.core.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.submark.core.ui.R
import io.github.submark.core.ui.component.currentLocale
import io.github.submark.core.ui.theme.SubMarkTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Immutable
data class HeatmapDay(val date: LocalDate, val value: Double)

/** Column/row of [date] in a Monday-first week grid starting at the week containing [first]. */
internal fun heatmapCell(first: LocalDate, date: LocalDate): Pair<Int, Int> {
    val start = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val offset = ChronoUnit.DAYS.between(start, date).toInt()
    return offset / 7 to offset % 7
}

/**
 * GitHub-style activity grid: one column per week, rows Monday..Sunday, 4 intensity levels ([ChartMath.heatLevel])
 * and month labels on top. [days] should be a contiguous, date-sorted range (e.g. the last year).
 * Cells grow to fill the width but never shrink below [minCellSize]; a longer range scrolls horizontally and
 * opens on the latest week. Tapping a cell calls [onSelect] with its index in [days] (null when tapping the
 * selected cell again).
 */
@Composable
fun HeatmapGrid(
    days: List<HeatmapDay>,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onSelect: ((Int?) -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    dayDescription: (HeatmapDay) -> String = { "${it.date}: ${it.value}" },
    showScale: Boolean = true,
    minCellSize: Dp = 16.dp,
) {
    val locale = currentLocale()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val emptyColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val outline = MaterialTheme.colorScheme.onSurface
    val levelColors = listOf(emptyColor, color.copy(alpha = 0.35f), color.copy(alpha = 0.65f), color)
    val max = days.maxOfOrNull { it.value } ?: 0.0
    val first = days.firstOrNull()?.date
    val columns = if (first == null) 0 else heatmapCell(first, days.last().date).first + 1
    val currentSelected = rememberUpdatedState(selectedIndex)
    val currentOnSelect = rememberUpdatedState(onSelect)
    val description = days.joinToString("; ") { dayDescription(it) }
    val weekdayLabels = DayOfWeek.entries.map { it.getDisplayName(TextStyle.NARROW, locale) }
    val gutter = with(density) { (weekdayLabels.maxOfOrNull { measurer.measure(it, labelStyle).size.width } ?: 0).toDp() } + 6.dp
    val monthRow = with(density) { measurer.measure("0", labelStyle).size.height.toDp() } + 4.dp
    val monthLabels = remember(first, columns, locale) { first?.let { monthLabelColumns(it, columns, locale) }.orEmpty() }
    val scroll = rememberScrollState()
    // Open on the latest week; maxValue is only known after the first layout.
    LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }

    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell = if (columns == 0) minCellSize else maxOf(minCellSize, (maxWidth - gutter) / columns)
            val height = monthRow + cell * 7
            Row {
                Canvas(Modifier.width(gutter).height(height)) {
                    val cellPx = cell.toPx()
                    // Weekday labels on Mon / Wed / Fri / Sun rows keep the gutter readable.
                    listOf(0, 2, 4, 6).forEach { row ->
                        val layout = measurer.measure(weekdayLabels[row], labelStyle)
                        drawText(layout, topLeft = Offset(0f, monthRow.toPx() + row * cellPx + (cellPx - layout.size.height) / 2))
                    }
                }
                Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                    Canvas(
                        modifier = Modifier
                            .width(cell * columns)
                            .height(height)
                            .semantics { contentDescription = description }
                            .pointerInput(days, cell) {
                                detectTapGestures { pos ->
                                    if (first == null) return@detectTapGestures
                                    val col = (pos.x / cell.toPx()).toInt()
                                    val row = ((pos.y - monthRow.toPx()) / cell.toPx()).toInt()
                                    if (pos.y < monthRow.toPx() || col !in 0 until columns || row !in 0..6) return@detectTapGestures
                                    val idx = days.indexOfFirst { heatmapCell(first, it.date) == (col to row) }.takeIf { it >= 0 }
                                    currentOnSelect.value?.invoke(if (idx == currentSelected.value) null else idx)
                                }
                            },
                    ) {
                        if (first == null) return@Canvas
                        val cellPx = cell.toPx()
                        val top = monthRow.toPx()
                        val gap = 3.dp.toPx()
                        val radius = CornerRadius(3.dp.toPx())
                        monthLabels.forEach { (col, label) ->
                            drawText(measurer.measure(label, labelStyle), topLeft = Offset(col * cellPx + gap / 2, 0f))
                        }
                        days.forEachIndexed { i, day ->
                            val (col, row) = heatmapCell(first, day.date)
                            val topLeft = Offset(col * cellPx + gap / 2, top + row * cellPx + gap / 2)
                            val s = Size(cellPx - gap, cellPx - gap)
                            drawRoundRect(levelColors[ChartMath.heatLevel(day.value, max)], topLeft, s, radius)
                            if (i == selectedIndex) {
                                drawRoundRect(outline, topLeft, s, radius, style = Stroke(2.dp.toPx()))
                            }
                        }
                    }
                }
            }
        }
        if (showScale) {
            Row(
                modifier = Modifier.align(Alignment.End).padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(stringResource(R.string.ui_chart_less), style = MaterialTheme.typography.labelSmall)
                levelColors.forEach { Box(Modifier.size(10.dp).background(it, RoundedCornerShape(2.dp))) }
                Text(stringResource(R.string.ui_chart_more), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * Columns where a month label goes: each week column whose Monday starts a new month. The first column is
 * labelled too unless the next label would crowd it (labels need about [MONTH_LABEL_COLUMNS] columns).
 */
internal fun monthLabelColumns(first: LocalDate, columns: Int, locale: Locale): List<Pair<Int, String>> {
    val start = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    fun label(col: Int) = start.plusWeeks(col.toLong()).month.getDisplayName(TextStyle.SHORT, locale)
    val changes = (1 until columns).filter { start.plusWeeks(it.toLong()).month != start.plusWeeks(it - 1L).month }
    val firstLabel = if ((changes.firstOrNull() ?: columns) >= MONTH_LABEL_COLUMNS) listOf(0) else emptyList()
    return (firstLabel + changes).map { it to label(it) }
}

private const val MONTH_LABEL_COLUMNS = 3

@Preview(showBackground = true)
@Composable
private fun HeatmapPreview() {
    val today = LocalDate.of(2026, 3, 10)
    val days = (364 downTo 0).map { HeatmapDay(today.minusDays(it.toLong()), listOf(0.0, 1.0, 0.0, 2.0, 3.0, 0.0, 1.0)[it % 7]) }
    SubMarkTheme { HeatmapGrid(days, selectedIndex = 5) }
}
