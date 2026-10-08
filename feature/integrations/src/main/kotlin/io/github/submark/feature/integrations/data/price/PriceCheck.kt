package io.github.submark.feature.integrations.data.price

/** Pure helpers for per-region price history statistics and drop detection. */
object PriceCheck {

    data class RegionStats(
        val region: String,
        val current: java.math.BigDecimal,
        val lowest: java.math.BigDecimal,
        val highest: java.math.BigDecimal,
        val average: java.math.BigDecimal,
        val currencyCode: String,
        val recordCount: Int,
    )

    /** Statistics over one region's history; [records] must be newest-first, all of the same region. */
    fun stats(region: String, records: List<io.github.submark.core.model.PriceRecord>): RegionStats? {
        if (records.isEmpty()) return null
        val prices = records.map { it.price }
        val avg = prices.fold(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
            .divide(java.math.BigDecimal(prices.size), 2, java.math.RoundingMode.HALF_UP)
        return RegionStats(
            region = region,
            current = prices.first(),
            lowest = prices.min(),
            highest = prices.max(),
            average = avg,
            currencyCode = records.first().currencyCode,
            recordCount = records.size,
        )
    }

    /**
     * Percent by which [current] is below [previous] (0..100); null when not a drop or previous is zero.
     * Drop notifications fire when this is >= the user threshold.
     */
    fun dropPercent(previous: java.math.BigDecimal, current: java.math.BigDecimal): Double? {
        if (previous.signum() <= 0) return null
        if (current >= previous) return null
        val drop = previous.subtract(current)
            .multiply(java.math.BigDecimal(100))
            .divide(previous, 2, java.math.RoundingMode.HALF_UP)
        return drop.toDouble()
    }
}
