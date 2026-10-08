package io.github.submark.feature.analytics.data

import io.github.submark.core.model.PaymentStatus
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth

/** One payment pre-converted to the reporting currency. */
data class PaidAmount(val date: LocalDate, val amount: BigDecimal, val categoryKey: String? = null, val isLifetime: Boolean = false)

private val MC = MathContext.DECIMAL64

// ---------------- Trend ----------------

data class TrendBucket(
    val label: YearMonth?,           // null for yearly buckets
    val year: Year?,                 // non-null for yearly buckets
    val total: BigDecimal,
    val firstDay: LocalDate,
    val lastDay: LocalDate,
)

/** Last 6 calendar months including [today]'s, each bucket = sum of payments in that month. */
fun monthlyTrend(payments: List<PaidAmount>, today: LocalDate): List<TrendBucket> {
    val current = YearMonth.from(today)
    return (5 downTo 0).map { back ->
        val ym = current.minusMonths(back.toLong())
        val total = payments.filter { YearMonth.from(it.date) == ym }
            .fold(BigDecimal.ZERO) { a, b -> a + b.amount }
        TrendBucket(label = ym, year = null, total = total, firstDay = ym.atDay(1), lastDay = ym.atEndOfMonth())
    }
}

/** Last 3 calendar years including [today]'s. */
fun yearlyTrend(payments: List<PaidAmount>, today: LocalDate): List<TrendBucket> {
    val current = Year.from(today)
    return (2 downTo 0).map { back ->
        val y = current.minusYears(back.toLong())
        val total = payments.filter { Year.from(it.date) == y }
            .fold(BigDecimal.ZERO) { a, b -> a + b.amount }
        TrendBucket(label = null, year = y, total = total, firstDay = y.atDay(1), lastDay = y.atMonth(12).atEndOfMonth())
    }
}

data class TrendStats(val total: BigDecimal, val average: BigDecimal, val peakIndex: Int?)

fun trendStats(buckets: List<TrendBucket>): TrendStats {
    if (buckets.isEmpty()) return TrendStats(BigDecimal.ZERO, BigDecimal.ZERO, null)
    val total = buckets.fold(BigDecimal.ZERO) { a, b -> a + b.total }
    val avg = total.divide(BigDecimal(buckets.size), MC)
    val peak = buckets.indices.maxByOrNull { buckets[it].total }?.takeIf { buckets[it].total.signum() > 0 }
    return TrendStats(total, avg, peak)
}

// ---------------- Heatmap ----------------

enum class HeatLevel(val level: Int) { NONE(0), LOW(1), MEDIUM(2), HIGH(3) }

data class HeatmapCell(
    val date: LocalDate,
    val count: Int,
    val amount: BigDecimal,
    val level: HeatLevel,
)

/** 30 cells ending at [today]; value = number of payments that day. Levels relative to the max count. */
fun heatmap(payments: List<PaidAmount>, today: LocalDate, days: Int = 30): List<HeatmapCell> {
    val start = today.minusDays(days.toLong() - 1)
    val byDate = payments.filter { it.date in start..today }.groupBy { it.date }
    val cells = (0 until days).map { offset ->
        val date = start.plusDays(offset.toLong())
        val dayPayments = byDate[date].orEmpty()
        HeatmapCell(
            date = date,
            count = dayPayments.size,
            amount = dayPayments.fold(BigDecimal.ZERO) { a, b -> a + b.amount },
            level = HeatLevel.NONE,
        )
    }
    val max = cells.maxOfOrNull { it.count } ?: 0
    return cells.map { cell ->
        val level = when {
            cell.count <= 0 || max <= 0 -> HeatLevel.NONE
            cell.count.toDouble() / max <= 1.0 / 3 -> HeatLevel.LOW
            cell.count.toDouble() / max <= 2.0 / 3 -> HeatLevel.MEDIUM
            else -> HeatLevel.HIGH
        }
        cell.copy(level = level)
    }
}

data class HeatmapStats(val total: Int, val averagePerDay: BigDecimal, val peakIndex: Int?)

fun heatmapStats(cells: List<HeatmapCell>): HeatmapStats {
    val total = cells.sumOf { it.count }
    val avg = if (cells.isEmpty()) BigDecimal.ZERO
    else BigDecimal(total).divide(BigDecimal(cells.size), 2, RoundingMode.HALF_UP)
    val peak = cells.indices.maxByOrNull { cells[it].count }?.takeIf { cells[it].count > 0 }
    return HeatmapStats(total, avg, peak)
}

// ---------------- Category ----------------

data class CategoryTotal(
    val categoryKey: String,
    val amount: BigDecimal,
    val count: Int,
)

/**
 * Totals per category, descending. Keys not tied to a named category should be grouped under
 * [otherKey] by the caller beforehand if desired; `expandTop` folds the tail beyond [top] into
 * a synthetic entry keyed [otherKey].
 */
fun categoryTotals(payments: List<PaidAmount>): List<CategoryTotal> =
    payments.groupBy { it.categoryKey ?: "other" }
        .map { (key, list) ->
            CategoryTotal(
                categoryKey = key,
                amount = list.fold(BigDecimal.ZERO) { a, b -> a + b.amount },
                count = list.size,
            )
        }
        .sortedByDescending { it.amount }

/** Top [top] entries plus an "Other" entry folding the remainder (count summed too). */
fun foldTail(totals: List<CategoryTotal>, top: Int, otherKey: String = "@other"): List<CategoryTotal> {
    if (totals.size <= top) return totals
    val head = totals.take(top)
    val tail = totals.drop(top)
    val other = CategoryTotal(
        categoryKey = otherKey,
        amount = tail.fold(BigDecimal.ZERO) { a, b -> a + b.amount },
        count = tail.sumOf { it.count },
    )
    return head + other
}

// ---------------- Radar ----------------

data class RadarInput(val categories: List<String>, val perCategory: List<CategoryTotal>)

data class RadarMetrics(
    /** Normalized 0..1 series for amount, count, average (amount/count). */
    val amount: List<Float>,
    val count: List<Float>,
    val average: List<Float>,
)

/** Normalizes each metric by its own maximum; empty/zero max -> zeros. */
fun radarMetrics(perCategory: List<CategoryTotal>): RadarMetrics {
    val amounts = perCategory.map { it.amount.toDouble() }
    val counts = perCategory.map { it.count.toDouble() }
    val averages = perCategory.map {
        if (it.count > 0) it.amount.divide(BigDecimal(it.count), MC).toDouble() else 0.0
    }
    fun normalize(values: List<Double>): List<Float> {
        val max = values.maxOrNull() ?: 0.0
        return values.map { v -> (if (max <= 0.0) 0.0 else v / max).toFloat() }
    }
    return RadarMetrics(normalize(amounts), normalize(counts), normalize(averages))
}

// ---------------- Per-subscription lifecycle ----------------

data class PaymentPoint(val date: LocalDate, val amount: BigDecimal)

data class LifecycleNumbers(
    val subscribedDays: Long,
    val completedCycles: Int,
    val daysToNext: Long?,
    val theoryTotal: BigDecimal,
    val actualTotal: BigDecimal,
    val actualDaily: BigDecimal,
    val actualMonthly: BigDecimal,
    val highest: BigDecimal?,
    val lowest: BigDecimal?,
    val averagePayment: BigDecimal?,
    /** last - previous payment amount, null when fewer than two payments. */
    val lastChange: BigDecimal?,
)

/**
 * Pure per-subscription aggregation. [payments] = the subscription's SUCCESS payments
 * (in its own currency), [startDate] when billing started, [nextPaymentDate] the scheduled
 * next billing date (null if none), [price] current cycle price.
 */
fun lifecycleNumbers(
    payments: List<PaymentPoint>,
    startDate: LocalDate,
    today: LocalDate,
    price: BigDecimal,
    nextPaymentDate: LocalDate?,
): LifecycleNumbers {
    val sorted = payments.sortedBy { it.date }
    val days = (today.toEpochDay() - startDate.toEpochDay()).coerceAtLeast(0)
    val completedCycles = sorted.size
    val daysToNext = nextPaymentDate?.let { (it.toEpochDay() - today.toEpochDay()) }
    val theoryTotal = price.multiply(BigDecimal(completedCycles))
    val actualTotal = sorted.fold(BigDecimal.ZERO) { a, b -> a + b.amount }
    val actualDaily = if (days > 0) actualTotal.divide(BigDecimal(days), MC) else actualTotal
    val actualMonthly = actualDaily.multiply(BigDecimal("30.436875"), MC)
    val highest = sorted.maxByOrNull { it.amount }?.amount
    val lowest = sorted.minByOrNull { it.amount }?.amount
    val avg = if (sorted.isNotEmpty()) actualTotal.divide(BigDecimal(sorted.size), MC) else null
    val lastChange = if (sorted.size >= 2) sorted.last().amount - sorted[sorted.size - 2].amount else null
    return LifecycleNumbers(
        subscribedDays = days,
        completedCycles = completedCycles,
        daysToNext = daysToNext,
        theoryTotal = theoryTotal,
        actualTotal = actualTotal,
        actualDaily = actualDaily,
        actualMonthly = actualMonthly,
        highest = highest,
        lowest = lowest,
        averagePayment = avg,
        lastChange = lastChange,
    )
}
