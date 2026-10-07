package io.github.submark.core.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.submark.core.ui.theme.SubMarkTheme

enum class LineBarStyle { BAR, LINE }

/**
 * Single-series bar or line chart over labelled buckets (e.g. monthly spending).
 * Tapping a bucket calls [onSelect] with its index; tapping the selected one again passes null.
 * The selected value is drawn above its mark using [valueLabel].
 */
@Composable
fun LineBarChart(
    entries: List<ChartEntry>,
    modifier: Modifier = Modifier,
    style: LineBarStyle = LineBarStyle.BAR,
    selectedIndex: Int? = null,
    onSelect: ((Int?) -> Unit)? = null,
    valueLabel: (Double) -> String = { it.toString() },
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 180.dp,
    maxXLabels: Int = 8,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val surface = MaterialTheme.colorScheme.surface
    val currentSelected = rememberUpdatedState(selectedIndex)
    val currentOnSelect = rememberUpdatedState(onSelect)
    val description = entries.joinToString("; ") { "${it.label}: ${valueLabel(it.value)}" }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .pointerInput(entries.size) {
                detectTapGestures { pos ->
                    val idx = ChartMath.slotIndex(pos.x, size.width.toFloat(), entries.size)
                    currentOnSelect.value?.invoke(if (idx == currentSelected.value) null else idx)
                }
            },
    ) {
        if (entries.isEmpty()) return@Canvas
        val labelHeight = measurer.measure("0", labelStyle).size.height + 6.dp.toPx()
        val topPad = measurer.measure("0", valueStyle).size.height + 6.dp.toPx()
        val plotHeight = size.height - labelHeight - topPad
        val maxValue = ChartMath.niceMax(entries.maxOf { it.value })
        val slot = size.width / entries.size
        fun yOf(v: Double) = topPad + plotHeight * (1f - (v.coerceAtLeast(0.0) / maxValue).toFloat())

        // Recessive grid: baseline + 2 lines.
        for (i in 0..2) {
            val y = topPad + plotHeight * i / 2f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }

        val dimmed = selectedIndex != null
        when (style) {
            LineBarStyle.BAR -> {
                val barWidth = (slot * 0.6f).coerceAtMost(28.dp.toPx())
                val radius = 4.dp.toPx()
                entries.forEachIndexed { i, e ->
                    val c = e.color ?: color
                    val top = yOf(e.value)
                    val left = slot * i + (slot - barWidth) / 2
                    val h = (topPad + plotHeight - top).coerceAtLeast(0f)
                    if (h > 0f) {
                        // Rounded data end, square at the baseline.
                        drawRoundRect(
                            color = if (dimmed && i != selectedIndex) c.copy(alpha = 0.4f) else c,
                            topLeft = Offset(left, top),
                            size = Size(barWidth, h),
                            cornerRadius = CornerRadius(radius, radius),
                        )
                        if (h > radius) {
                            drawRect(
                                color = if (dimmed && i != selectedIndex) c.copy(alpha = 0.4f) else c,
                                topLeft = Offset(left, top + h - radius),
                                size = Size(barWidth, radius),
                            )
                        }
                    }
                }
            }
            LineBarStyle.LINE -> {
                val points = entries.mapIndexed { i, e -> Offset(slot * i + slot / 2, yOf(e.value)) }
                val path = Path().apply {
                    points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
                }
                drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                points.forEachIndexed { i, p ->
                    val r = if (i == selectedIndex) 6.dp.toPx() else 4.dp.toPx()
                    drawCircle(surface, radius = r + 2.dp.toPx(), center = p)
                    drawCircle(entries[i].color ?: color, radius = r, center = p)
                }
                if (selectedIndex != null && selectedIndex in points.indices) {
                    val x = points[selectedIndex].x
                    drawLine(gridColor, Offset(x, topPad), Offset(x, topPad + plotHeight), strokeWidth = 1.dp.toPx())
                }
            }
        }

        val step = ChartMath.labelStep(entries.size, maxXLabels)
        entries.forEachIndexed { i, e ->
            if (i % step == 0 || i == selectedIndex) {
                drawCentered(measurer, e.label, labelStyle, slot * i + slot / 2, size.height - labelHeight + 4.dp.toPx())
            }
        }
        if (selectedIndex != null && selectedIndex in entries.indices) {
            val e = entries[selectedIndex]
            val y = (yOf(e.value) - topPad).coerceAtLeast(0f)
            drawCentered(measurer, valueLabel(e.value), valueStyle, slot * selectedIndex + slot / 2, y)
        }
    }
}

/** Draws [text] horizontally centered on [cx], clamped inside the canvas. */
internal fun DrawScope.drawCentered(measurer: TextMeasurer, text: String, style: TextStyle, cx: Float, top: Float) {
    val layout = measurer.measure(text, style)
    val x = (cx - layout.size.width / 2f).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
    drawText(layout, topLeft = Offset(x, top))
}

@Preview(showBackground = true)
@Composable
private fun LineBarChartPreview() {
    val data = listOf("Oct", "Nov", "Dec", "Jan", "Feb", "Mar").zip(listOf(120.0, 98.0, 143.0, 110.0, 87.0, 131.0)) { l, v -> ChartEntry(l, v) }
    SubMarkTheme {
        androidx.compose.foundation.layout.Column {
            LineBarChart(data, selectedIndex = 2, valueLabel = { "$" + it.toInt() })
            LineBarChart(data, style = LineBarStyle.LINE, selectedIndex = 4, valueLabel = { "$" + it.toInt() })
        }
    }
}
