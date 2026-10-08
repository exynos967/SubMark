package io.github.submark.feature.integrations.panel

import com.google.common.truth.Truth.assertThat
import io.github.submark.feature.integrations.panel.data.BudgetComputation
import io.github.submark.feature.integrations.panel.data.BudgetSnapshot
import io.github.submark.feature.integrations.panel.data.BudgetStatus
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class BudgetStatusTest {

    private val now: Instant = Instant.ofEpochSecond(1_700_000_000)

    private fun snapshot(used: String?, dailySpent: String? = null) = BudgetSnapshot(
        serviceType = "TEST",
        usedAmount = used,
        dailySpentAmount = dailySpent,
        fetchedAt = now,
        rawJson = "{}",
    )

    @Test
    fun normalBelowThreshold() {
        val result = BudgetComputation.compute(snapshot("70"), BigDecimal("100"), null, 90)
        assertThat(result.status).isEqualTo(BudgetStatus.NORMAL)
        assertThat(result.usagePercent).isEqualTo(70)
        assertThat(result.monthlyRemaining).isEqualByComparingTo(BigDecimal("30"))
    }

    @Test
    fun warningAtExactThreshold() {
        val result = BudgetComputation.compute(snapshot("90"), BigDecimal("100"), null, 90)
        assertThat(result.status).isEqualTo(BudgetStatus.WARNING)
        assertThat(result.usagePercent).isEqualTo(90)
    }

    @Test
    fun overWhenRemainingNegative() {
        val result = BudgetComputation.compute(snapshot("120"), BigDecimal("100"), null, 95)
        assertThat(result.status).isEqualTo(BudgetStatus.OVER)
        assertThat(result.monthlyRemaining!!.signum()).isEqualTo(-1)
    }

    @Test
    fun overExactlyAtZeroRemaining() {
        val result = BudgetComputation.compute(snapshot("100"), BigDecimal("100"), null, 95)
        assertThat(result.status).isEqualTo(BudgetStatus.OVER)
    }

    @Test
    fun dailyBudgetOverTriggersOverStatus() {
        val result = BudgetComputation.compute(snapshot("10", dailySpent = "6"), BigDecimal("100"), BigDecimal("5"), 90)
        assertThat(result.status).isEqualTo(BudgetStatus.OVER)
        assertThat(result.dailyRemaining!!.signum()).isEqualTo(-1)
    }

    @Test
    fun noBudgetNoSnapshot_noPercentNormalStatus() {
        val result = BudgetComputation.compute(null, null, null, 90)
        assertThat(result.status).isEqualTo(BudgetStatus.NORMAL)
        assertThat(result.usagePercent).isNull()
        assertThat(result.monthlyRemaining).isNull()
        assertThat(result.dailyRemaining).isNull()
    }

    @Test
    fun percentageRoundsHalfUp() {
        val result = BudgetComputation.compute(snapshot("1"), BigDecimal("3"), null, 50)
        // 1/3*100 = 33.33 -> 33
        assertThat(result.usagePercent).isEqualTo(33)
    }

    @Test
    fun percentRoundingBoundary() {
        // 89.5% at threshold 90 should round to 90 -> warning
        val result = BudgetComputation.compute(snapshot("179"), BigDecimal("200"), null, 90)
        assertThat(result.usagePercent).isEqualTo(90)
        assertThat(result.status).isEqualTo(BudgetStatus.WARNING)
    }
}
