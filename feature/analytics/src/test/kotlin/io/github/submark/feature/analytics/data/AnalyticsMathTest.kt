package io.github.submark.feature.analytics.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth

class AnalyticsMathTest {

    private val today = LocalDate.of(2026, 10, 15)

    private fun pay(date: LocalDate, amount: String, key: String? = "catA") =
        PaidAmount(date, BigDecimal(amount), key)

    // ---------------- Trend ----------------

    @Test
    fun `monthly trend builds 6 buckets ending at current month`() {
        val payments = listOf(
            pay(LocalDate.of(2026, 10, 1), "10"),
            pay(LocalDate.of(2026, 8, 15), "20"),
            pay(LocalDate.of(2026, 5, 1), "99"), // outside window
        )
        val buckets = monthlyTrend(payments, today)
        assertThat(buckets).hasSize(6)
        assertThat(buckets.first().label).isEqualTo(YearMonth.of(2026, 5))
        assertThat(buckets.last().label).isEqualTo(YearMonth.of(2026, 10))
        assertThat(buckets.last().total.compareTo(BigDecimal("10"))).isEqualTo(0)
        assertThat(buckets[3].total.compareTo(BigDecimal("20"))).isEqualTo(0) // August
        assertThat(buckets[0].total.compareTo(BigDecimal("99"))).isEqualTo(0) // May included (5 back)
    }

    @Test
    fun `yearly trend builds 3 buckets`() {
        val payments = listOf(
            pay(LocalDate.of(2026, 1, 5), "100"),
            pay(LocalDate.of(2024, 12, 31), "50"),
        )
        val buckets = yearlyTrend(payments, today)
        assertThat(buckets).hasSize(3)
        assertThat(buckets.map { it.year }).containsExactly(Year.of(2024), Year.of(2025), Year.of(2026)).inOrder()
        assertThat(buckets[0].total.compareTo(BigDecimal("50"))).isEqualTo(0)
        assertThat(buckets[2].total.compareTo(BigDecimal("100"))).isEqualTo(0)
    }

    @Test
    fun `trend stats compute total average and peak`() {
        val buckets = monthlyTrend(
            listOf(
                pay(LocalDate.of(2026, 10, 1), "30"),
                pay(LocalDate.of(2026, 9, 1), "60"),
            ),
            today,
        )
        val stats = trendStats(buckets)
        assertThat(stats.total.compareTo(BigDecimal("90"))).isEqualTo(0)
        assertThat(stats.average.compareTo(BigDecimal("15"))).isEqualTo(0)
        assertThat(stats.peakIndex).isEqualTo(4) // September (index 4 of 6)
    }

    @Test
    fun `trend stats peak is null when all buckets are zero`() {
        val stats = trendStats(monthlyTrend(emptyList(), today))
        assertThat(stats.peakIndex).isNull()
        assertThat(stats.total.compareTo(BigDecimal("0"))).isEqualTo(0)
    }

    // ---------------- Heatmap ----------------

    @Test
    fun `heatmap produces 30 contiguous days with counts and levels`() {
        val payments = buildList {
            repeat(6) { add(pay(today, "1")) }
            repeat(2) { add(pay(today.minusDays(5), "1")) }
            add(pay(today.minusDays(10), "1"))
            add(pay(today.minusDays(40), "1")) // outside window
        }
        val cells = heatmap(payments, today)
        assertThat(cells).hasSize(30)
        assertThat(cells.first().date).isEqualTo(today.minusDays(29))
        assertThat(cells.last().date).isEqualTo(today)

        val todayCell = cells.last()
        assertThat(todayCell.count).isEqualTo(6)
        assertThat(todayCell.level).isEqualTo(HeatLevel.HIGH)

        val midCell = cells[cells.indexOfFirst { it.date == today.minusDays(5) }]
        assertThat(midCell.count).isEqualTo(2)
        assertThat(midCell.level).isEqualTo(HeatLevel.LOW) // 2/6 = 1/3 -> LOW boundary

        val lowCell = cells[cells.indexOfFirst { it.date == today.minusDays(10) }]
        assertThat(lowCell.level).isEqualTo(HeatLevel.LOW)

        val emptyCell = cells.first()
        assertThat(emptyCell.level).isEqualTo(HeatLevel.NONE)
    }

    @Test
    fun `heatmap stats total average and peak`() {
        val payments = buildList {
            repeat(4) { add(pay(today, "2")) }
            repeat(2) { add(pay(today.minusDays(1), "3")) }
        }
        val cells = heatmap(payments, today)
        val stats = heatmapStats(cells)
        assertThat(stats.total).isEqualTo(6)
        assertThat(stats.averagePerDay.compareTo(BigDecimal("0.2"))).isEqualTo(0)
        assertThat(stats.peakIndex).isEqualTo(29)
    }

    // ---------------- Category ----------------

    @Test
    fun `category totals aggregate and sort descending`() {
        val totals = categoryTotals(
            listOf(
                pay(today, "10", "A"),
                pay(today, "5", "A"),
                pay(today, "20", "B"),
                pay(today, "7", null),
            ),
        )
        assertThat(totals.map { it.categoryKey }).containsExactly("B", "A", "other").inOrder()
        assertThat(totals.first { it.categoryKey == "A" }.amount.compareTo(BigDecimal("15"))).isEqualTo(0)
        assertThat(totals.first { it.categoryKey == "A" }.count).isEqualTo(2)
    }

    @Test
    fun `fold tails beyond top into other entry`() {
        val totals = (1..10).map { CategoryTotal("cat$it", BigDecimal(11 - it), 1) }
        val folded = foldTail(totals, 7)
        assertThat(folded).hasSize(8)
        assertThat(folded.last().categoryKey).isEqualTo("@other")
        assertThat(folded.last().count).isEqualTo(3)
        assertThat(folded.last().amount.compareTo(BigDecimal("6"))).isEqualTo(0) // 3+2+1
    }

    @Test
    fun `fold is identity when within top`() {
        val totals = (1..5).map { CategoryTotal("cat$it", BigDecimal(it), 1) }
        assertThat(foldTail(totals, 7)).hasSize(5)
    }

    // ---------------- Radar ----------------

    @Test
    fun `radar normalization divides by per-metric max`() {
        val totals = listOf(
            CategoryTotal("A", BigDecimal("40"), 2),
            CategoryTotal("B", BigDecimal("20"), 4),
        )
        val m = radarMetrics(totals)
        assertThat(m.amount).containsExactly(1.0f, 0.5f).inOrder()
        assertThat(m.count).containsExactly(0.5f, 1.0f).inOrder()
        assertThat(m.average).containsExactly(1.0f, 0.25f).inOrder() // avg A=20, B=5
    }

    @Test
    fun `radar handles empty and zero input`() {
        assertThat(radarMetrics(emptyList()).amount).isEmpty()
        val m = radarMetrics(listOf(CategoryTotal("A", BigDecimal.ZERO, 0)))
        assertThat(m.amount).containsExactly(0.0f)
    }

    // ---------------- Lifecycle ----------------

    @Test
    fun `lifecycle numbers aggregate payments`() {
        val payments = listOf(
            PaymentPoint(LocalDate.of(2026, 1, 15), BigDecimal("10")),
            PaymentPoint(LocalDate.of(2026, 2, 15), BigDecimal("12")),
            PaymentPoint(LocalDate.of(2026, 3, 15), BigDecimal("8")),
        )
        val life = lifecycleNumbers(
            payments = payments,
            startDate = LocalDate.of(2026, 1, 15),
            today = LocalDate.of(2026, 3, 20),
            price = BigDecimal("10"),
            nextPaymentDate = LocalDate.of(2026, 4, 15),
        )
        assertThat(life.completedCycles).isEqualTo(3)
        assertThat(life.daysToNext).isEqualTo(26)
        assertThat(life.theoryTotal.compareTo(BigDecimal("30"))).isEqualTo(0)
        assertThat(life.actualTotal.compareTo(BigDecimal("30"))).isEqualTo(0)
        assertThat(life.highest!!.compareTo(BigDecimal("12"))).isEqualTo(0)
        assertThat(life.lowest!!.compareTo(BigDecimal("8"))).isEqualTo(0)
        assertThat(life.averagePayment!!.compareTo(BigDecimal("10"))).isEqualTo(0)
        assertThat(life.lastChange!!.compareTo(BigDecimal("-4"))).isEqualTo(0)
        assertThat(life.subscribedDays).isEqualTo(64)
        assertThat(life.actualDaily).isGreaterThan(BigDecimal.ZERO)
        // monthly ~= daily * 30.44
        assertThat(life.actualMonthly).isGreaterThan(life.actualDaily)
    }

    @Test
    fun `lifecycle with zero payments`() {
        val life = lifecycleNumbers(
            payments = emptyList(),
            startDate = today,
            today = today,
            price = BigDecimal("10"),
            nextPaymentDate = null,
        )
        assertThat(life.completedCycles).isEqualTo(0)
        assertThat(life.actualTotal.compareTo(BigDecimal("0"))).isEqualTo(0)
        assertThat(life.highest).isNull()
        assertThat(life.lastChange).isNull()
        assertThat(life.daysToNext).isNull()
    }
}
