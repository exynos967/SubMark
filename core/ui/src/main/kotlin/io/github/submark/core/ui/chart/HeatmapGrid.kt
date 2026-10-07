package io.github.submark.core.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.submark.core.ui.R
import io.github.submark.core.ui.component.currentLocale
import io.github.submark.core.ui.theme.SubMarkTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

@Immutable
data class HeatmapDay(val date: LocalDate, val value: Double)

/** Column/row of [date] in a Monday-first week grid starting at the week containing [first]. */
internal fun heatmapCell(first: LocalDate, date: LocalDate): Pair<Int, Int> {
    val start = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val offset = ChronoUnit.DAYS.between(start, date).toInt()
    return offset / 7 to offset % 7
}

/**
 * GitHub-style activity grid: one column per week, rows Monday..Sunday, 4 intensity levels
 * ([ChartMath.heatLevel]). [days] should be a contiguous, date-sorted range (e.g. the last 30 days).
 * Tapping a cell calls [onSelect] with its index in [days] (null when tapping the selected cell again).
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
) {
    val locale = currentLocale()
    val measurer = rememberTextMeasurer()
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
    val labelWidthPx = weekdayLabels.maxOfOrNull { measurer.measure(it, labelStyle).size.width } ?: 0

    Column(modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp * 7)
                .semantics { contentDescription = description }
                .pointerInput(days) {
                    detectTapGestures { pos ->
                        if (first == null || columns == 0) return@detectTapGestures
                        val gutter = labelWidthPx + 6.dp.toPx()
                        val cell = minOf((size.width - gutter) / columns, size.height / 7f)
                        val col = ((pos.x - gutter) / cell).toInt()
                        val row = (pos.y / cell).toInt()
                        if (pos.x < gutter || col !in 0 until columns || row !in 0..6) return@detectTapGestures
                        val idx = days.indexOfFirst { heatmapCell(first, it.date) == (col to row) }.takeIf { it >= 0 }
                        currentOnSelect.value?.invoke(if (idx == currentSelected.value) null else idx)
                    }
                },
        ) {
            if (first == null) return@Canvas
            val gutter = labelWidthPx + 6.dp.toPx()
            val cell = minOf((size.width - gutter) / columns, size.height / 7f)
            val gap = 3.dp.toPx()
            val radius = CornerRadius(3.dp.toPx())
            // Weekday labels on Mon / Wed / Fri / Sun rows keep the gutter readable.
            listOf(0, 2, 4, 6).forEach { row ->
                val layout = measurer.measure(weekdayLabels[row], labelStyle)
                drawText(layout, topLeft = Offset(0f, row * cell + (cell - layout.size.height) / 2))
            }
            days.forEachIndexed { i, day ->
                val (col, row) = heatmapCell(first, day.date)
                val topLeft = Offset(gutter + col * cell + gap / 2, row * cell + gap / 2)
                val s = Size(cell - gap, cell - gap)
                drawRoundRect(levelColors[ChartMath.heatLevel(day.value, max)], topLeft, s, radius)
                if (i == selectedIndex) {
                    drawRoundRect(outline, topLeft, s, radius, style = Stroke(2.dp.toPx()))
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

@Preview(showBackground = true)
@Composable
private fun HeatmapPreview() {
    val today = LocalDate.of(2026, 3, 10)
    val days = (29 downTo 0).map { HeatmapDay(today.minusDays(it.toLong()), listOf(0.0, 1.0, 0.0, 2.0, 3.0, 0.0, 1.0)[it % 7]) }
    SubMarkTheme { HeatmapGrid(days, selectedIndex = 5) }
}
