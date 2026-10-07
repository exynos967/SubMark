package io.github.submark.core.ui.chart

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class ChartMathTest {
    @Test fun `nice max`() {
        assertThat(ChartMath.niceMax(0.0)).isEqualTo(1.0)
        assertThat(ChartMath.niceMax(87.0)).isEqualTo(100.0)
        assertThat(ChartMath.niceMax(143.0)).isEqualTo(200.0)
        assertThat(ChartMath.niceMax(2.2)).isEqualTo(2.5)
        assertThat(ChartMath.niceMax(4000.0)).isEqualTo(5000.0)
    }

    @Test fun `slot index`() {
        assertThat(ChartMath.slotIndex(0f, 600f, 6)).isEqualTo(0)
        assertThat(ChartMath.slotIndex(599f, 600f, 6)).isEqualTo(5)
        assertThat(ChartMath.slotIndex(250f, 600f, 6)).isEqualTo(2)
        assertThat(ChartMath.slotIndex(-1f, 600f, 6)).isNull()
        assertThat(ChartMath.slotIndex(10f, 600f, 0)).isNull()
    }

    @Test fun `label step`() {
        assertThat(ChartMath.labelStep(6, 8)).isEqualTo(1)
        assertThat(ChartMath.labelStep(30, 8)).isEqualTo(4)
    }

    @Test fun `heat levels`() {
        assertThat(ChartMath.heatLevel(0.0, 9.0)).isEqualTo(0)
        assertThat(ChartMath.heatLevel(3.0, 9.0)).isEqualTo(1)
        assertThat(ChartMath.heatLevel(6.0, 9.0)).isEqualTo(2)
        assertThat(ChartMath.heatLevel(9.0, 9.0)).isEqualTo(3)
        assertThat(ChartMath.heatLevel(1.0, 0.0)).isEqualTo(0)
    }

    @Test fun `angles and segments`() {
        assertThat(ChartMath.clockAngle(0f, -1f)).isWithin(1e-6).of(0.0)
        assertThat(ChartMath.clockAngle(1f, 0f)).isWithin(1e-6).of(90.0)
        assertThat(ChartMath.clockAngle(0f, 1f)).isWithin(1e-6).of(180.0)
        assertThat(ChartMath.clockAngle(-1f, 0f)).isWithin(1e-6).of(270.0)
        val values = listOf(1.0, 1.0, 2.0)
        assertThat(ChartMath.segmentAt(10.0, values)).isEqualTo(0)
        assertThat(ChartMath.segmentAt(100.0, values)).isEqualTo(1)
        assertThat(ChartMath.segmentAt(359.0, values)).isEqualTo(2)
        assertThat(ChartMath.segmentAt(10.0, listOf(0.0))).isNull()
        assertThat(ChartMath.nearestAxis(350.0, 5)).isEqualTo(0)
        assertThat(ChartMath.nearestAxis(75.0, 5)).isEqualTo(1)
    }

    @Test fun `heatmap cells are monday first`() {
        val tuesday = LocalDate.of(2026, 3, 10)
        assertThat(heatmapCell(tuesday, tuesday)).isEqualTo(0 to 1)
        assertThat(heatmapCell(tuesday, tuesday.plusDays(5))).isEqualTo(0 to 6)
        assertThat(heatmapCell(tuesday, tuesday.plusDays(6))).isEqualTo(1 to 0)
    }
}
