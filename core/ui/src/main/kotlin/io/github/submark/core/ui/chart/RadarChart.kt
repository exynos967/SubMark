package io.github.submark.core.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.submark.core.ui.theme.ChartOtherColor
import io.github.submark.core.ui.theme.SubMarkTheme
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** One polygon on a [RadarChart]; [values] are normalized 0..1, one per axis. */
@Immutable
data class RadarSeries(val label: String, val values: List<Float>, val color: Color? = null)

/**
 * Radar chart over [axes] (e.g. categories) with one or more normalized [series]
 * (e.g. amount / count / average, each divided by its max). Tapping near an axis selects it.
 * A legend is shown for two or more series.
 */
@Composable
fun RadarChart(
    axes: List<String>,
    series: List<RadarSeries>,
    modifier: Modifier = Modifier,
    selectedAxis: Int? = null,
    onSelectAxis: ((Int?) -> Unit)? = null,
    gridLevels: Int = 4,
    height: Dp = 240.dp,
) {
    val palette = SubMarkTheme.chartPalette
    val colors = series.mapIndexed { i, s -> s.color ?: palette.getOrElse(i) { ChartOtherColor } }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val selectedLabelStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    val currentSelected = rememberUpdatedState(selectedAxis)
    val currentOnSelect = rememberUpdatedState(onSelectAxis)
    val description = series.joinToString("; ") { s ->
        s.label + ": " + axes.mapIndexed { i, a -> "$a ${((s.values.getOrElse(i) { 0f }) * 100).toInt()}%" }.joinToString(", ")
    }

    Column(modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = description }
                .pointerInput(axes.size) {
                    detectTapGestures { pos ->
                        val dx = pos.x - size.width / 2f
                        val dy = pos.y - size.height / 2f
                        val idx = ChartMath.nearestAxis(ChartMath.clockAngle(dx, dy), axes.size)
                        currentOnSelect.value?.invoke(if (idx == currentSelected.value) null else idx)
                    }
                },
        ) {
            val n = axes.size
            if (n < 3) return@Canvas
            val center = Offset(size.width / 2f, size.height / 2f)
            val labelRoom = 28.dp.toPx()
            val radius = min(size.width, size.height) / 2f - labelRoom
            fun point(i: Int, r: Float): Offset {
                val a = ChartMath.axisRadians(i, n)
                return Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
            }
            for (level in 1..gridLevels) {
                val r = radius * level / gridLevels
                val path = Path().apply {
                    for (i in 0 until n) point(i, r).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) }
                    close()
                }
                drawPath(path, gridColor, style = Stroke(1.dp.toPx()))
            }
            for (i in 0 until n) {
                drawLine(
                    if (i == selectedAxis) labelStyle.color else gridColor,
                    center,
                    point(i, radius),
                    strokeWidth = if (i == selectedAxis) 2.dp.toPx() else 1.dp.toPx(),
                )
                val style = if (i == selectedAxis) selectedLabelStyle else labelStyle
                val layout = measurer.measure(axes[i], style, maxLines = 1)
                val p = point(i, radius + labelRoom / 2)
                val x = (p.x - layout.size.width / 2f).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
                drawText(layout, topLeft = Offset(x, p.y - layout.size.height / 2f))
            }
            series.forEachIndexed { si, s ->
                val path = Path().apply {
                    for (i in 0 until n) {
                        point(i, radius * s.values.getOrElse(i) { 0f }.coerceIn(0f, 1f)).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) }
                    }
                    close()
                }
                drawPath(path, colors[si].copy(alpha = 0.18f))
                drawPath(path, colors[si], style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
                for (i in 0 until n) {
                    val p = point(i, radius * s.values.getOrElse(i) { 0f }.coerceIn(0f, 1f))
                    drawCircle(surface, 5.dp.toPx(), p)
                    drawCircle(colors[si], 3.5.dp.toPx(), p)
                }
            }
        }
        if (series.size > 1) {
            ChartLegend(series.mapIndexed { i, s -> LegendItem(s.label, colors[i]) })
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RadarPreview() {
    SubMarkTheme {
        RadarChart(
            axes = listOf("Video", "Music", "Cloud", "AI", "News"),
            series = listOf(
                RadarSeries("Amount", listOf(1f, 0.4f, 0.3f, 0.7f, 0.1f)),
                RadarSeries("Count", listOf(0.5f, 1f, 0.5f, 0.25f, 0.25f)),
            ),
            selectedAxis = 0,
        )
    }
}
