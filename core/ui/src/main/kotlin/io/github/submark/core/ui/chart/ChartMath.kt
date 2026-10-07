package io.github.submark.core.ui.chart

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** One labelled value. [color] null = chart default (primary, or palette slot by index for donuts). */
@Immutable
data class ChartEntry(val label: String, val value: Double, val color: Color? = null)

/** Pure geometry/scale helpers shared by the Canvas charts. */
object ChartMath {

    /** Rounds [max] up to 1/2/2.5/5 x 10^n so grid lines land on readable values; non-positive input returns 1. */
    fun niceMax(max: Double): Double {
        if (max <= 0.0 || max.isNaN()) return 1.0
        val exp = floor(log10(max))
        val base = 10.0.pow(exp)
        val f = max / base
        val nice = when {
            f <= 1.0 -> 1.0
            f <= 2.0 -> 2.0
            f <= 2.5 -> 2.5
            f <= 5.0 -> 5.0
            else -> 10.0
        }
        return nice * base
    }

    /** Index of the equal-width slot containing [x], or null outside [0, width). */
    fun slotIndex(x: Float, width: Float, count: Int): Int? {
        if (count <= 0 || width <= 0f || x < 0f || x >= width) return null
        return (x / (width / count)).toInt().coerceIn(0, count - 1)
    }

    /** Show every n-th x label so roughly [maxLabels] fit. */
    fun labelStep(count: Int, maxLabels: Int): Int = if (count <= maxLabels || maxLabels <= 0) 1 else ceil(count / maxLabels.toDouble()).toInt()

    /**
     * Heat level 0..3 relative to the maximum: 0 = none, <= 1/3 low, <= 2/3 medium, else high.
     */
    fun heatLevel(value: Double, max: Double): Int = when {
        value <= 0.0 || max <= 0.0 -> 0
        value / max <= 1.0 / 3 -> 1
        value / max <= 2.0 / 3 -> 2
        else -> 3
    }

    /** Clockwise angle in degrees from 12 o'clock of the point (dx, dy) relative to the center (y grows downward). */
    fun clockAngle(dx: Float, dy: Float): Double {
        val deg = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble()))
        return (deg + 360.0) % 360.0
    }

    /** Index of the donut segment covering [angle] (degrees from 12 o'clock), given segment values. */
    fun segmentAt(angle: Double, values: List<Double>): Int? {
        val total = values.sumOf { it.coerceAtLeast(0.0) }
        if (total <= 0.0) return null
        var acc = 0.0
        values.forEachIndexed { i, v ->
            acc += v.coerceAtLeast(0.0) / total * 360.0
            if (angle < acc) return i
        }
        return values.lastIndex
    }

    /** Radar axis nearest to [angle] (degrees from 12 o'clock) for [count] evenly spaced axes. */
    fun nearestAxis(angle: Double, count: Int): Int? {
        if (count <= 0) return null
        val step = 360.0 / count
        return (Math.round(angle / step).toInt()) % count
    }

    /** Radians for axis [i] of [count], starting at 12 o'clock, clockwise (for Canvas cos/sin). */
    fun axisRadians(i: Int, count: Int): Double = -PI / 2 + 2 * PI * i / count
}
