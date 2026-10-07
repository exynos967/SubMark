package io.github.submark.core.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.submark.core.ui.theme.ChartOtherColor
import io.github.submark.core.ui.theme.SubMarkTheme
import java.util.Locale
import kotlin.math.sqrt

/**
 * Donut of [entries] (e.g. spending per category) with an optional legend.
 * Entries without a color take palette slots in order; slots past the palette use the neutral "other" color,
 * so callers should fold long tails into an "Other" entry.
 */
@Composable
fun DonutChart(
    entries: List<ChartEntry>,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onSelect: ((Int?) -> Unit)? = null,
    centerLabel: String? = null,
    centerValue: String? = null,
    valueLabel: (Double) -> String = { it.toString() },
    showLegend: Boolean = true,
    diameter: Dp = 180.dp,
    thickness: Dp = 26.dp,
) {
    val palette = SubMarkTheme.chartPalette
    val colors = entries.mapIndexed { i, e -> e.color ?: palette.getOrElse(i) { ChartOtherColor } }
    val total = entries.sumOf { it.value.coerceAtLeast(0.0) }
    val emptyColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val currentSelected = rememberUpdatedState(selectedIndex)
    val currentOnSelect = rememberUpdatedState(onSelect)
    val description = entries.joinToString("; ") { "${it.label}: ${valueLabel(it.value)} (${percent(it.value, total)})" }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .size(diameter)
                    .semantics { contentDescription = description }
                    .pointerInput(entries) {
                        detectTapGestures { pos ->
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val dx = pos.x - cx
                            val dy = pos.y - cy
                            val dist = sqrt(dx * dx + dy * dy)
                            val outer = size.width / 2f
                            val inner = outer - thickness.toPx() * 1.5f
                            val idx = if (dist in inner..outer) {
                                ChartMath.segmentAt(ChartMath.clockAngle(dx, dy), entries.map { it.value })
                            } else {
                                null
                            }
                            currentOnSelect.value?.invoke(if (idx == currentSelected.value) null else idx)
                        }
                    },
            ) {
                val stroke = thickness.toPx()
                val selectedStroke = stroke * 1.3f
                val inset = selectedStroke / 2
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                if (total <= 0.0) {
                    drawArc(emptyColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                    return@Canvas
                }
                // 2dp surface gap between segments, expressed as degrees on the mid radius.
                val gapDeg = if (entries.count { it.value > 0 } > 1) {
                    Math.toDegrees((2.dp.toPx() / (arcSize.width / 2)).toDouble()).toFloat()
                } else {
                    0f
                }
                var start = -90f
                entries.forEachIndexed { i, e ->
                    val sweep = (e.value.coerceAtLeast(0.0) / total * 360.0).toFloat()
                    if (sweep > 0f) {
                        val dim = selectedIndex != null && i != selectedIndex
                        drawArc(
                            color = if (dim) colors[i].copy(alpha = 0.35f) else colors[i],
                            startAngle = start + gapDeg / 2,
                            sweepAngle = (sweep - gapDeg).coerceAtLeast(0.5f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(if (i == selectedIndex) selectedStroke else stroke),
                        )
                    }
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(diameter - thickness * 2 - 8.dp)) {
                val sel = selectedIndex?.let { entries.getOrNull(it) }
                val label = sel?.label ?: centerLabel
                val value = sel?.let { valueLabel(it.value) } ?: centerValue
                if (label != null) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                if (value != null) {
                    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center)
                }
            }
        }
        if (showLegend && entries.size > 1) {
            Spacer(Modifier.size(12.dp))
            ChartLegend(
                items = entries.mapIndexed { i, e -> LegendItem(e.label, colors[i], "${valueLabel(e.value)} · ${percent(e.value, total)}") },
                selectedIndex = selectedIndex,
                onSelect = onSelect?.let { cb -> { i: Int -> cb(if (i == selectedIndex) null else i) } },
            )
        }
    }
}

internal fun percent(value: Double, total: Double): String =
    if (total <= 0.0) "0%" else String.format(Locale.getDefault(), "%.1f%%", value / total * 100)

data class LegendItem(val label: String, val color: Color, val trailing: String? = null)

/** Vertical legend: color dot, label, optional trailing value. Rows are selectable when [onSelect] is set. */
@Composable
fun ChartLegend(
    items: List<LegendItem>,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onSelect: ((Int) -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { i, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { selected = i == selectedIndex }
                    .then(if (onSelect != null) Modifier.clickable { onSelect(i) } else Modifier)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).background(item.color, CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(
                    item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (i == selectedIndex) FontWeight.SemiBold else null,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.trailing != null) {
                    Text(item.trailing, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DonutChartPreview() {
    SubMarkTheme {
        DonutChart(
            entries = listOf(ChartEntry("Video", 42.0), ChartEntry("Music", 18.0), ChartEntry("Cloud", 12.0), ChartEntry("Other", 6.0)),
            centerLabel = "Total",
            centerValue = "$78",
            valueLabel = { "$" + it.toInt() },
        )
    }
}
