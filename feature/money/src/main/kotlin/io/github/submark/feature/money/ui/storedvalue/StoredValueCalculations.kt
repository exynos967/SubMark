package io.github.submark.feature.money.ui.storedvalue

import io.github.submark.core.domain.CurrencyConverter
import io.github.submark.core.domain.StoredValueCalculator
import io.github.submark.core.domain.StoredValueStatus
import io.github.submark.core.model.StoredValueRecord
import io.github.submark.core.model.StoredValueRecordType
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** What a top-up of [amount] would leave on the subscription. */
data class TopUpPreview(
    /** Amount in the subscription currency; null when no exchange rate. */
    val converted: BigDecimal?,
    val balanceAfter: BigDecimal?,
    val payableCycles: Long?,
    val status: StoredValueStatus?,
)

object TopUpPreviewCalculator {
    fun preview(sub: Subscription, amount: BigDecimal?, currencyCode: String, converter: CurrencyConverter): TopUpPreview {
        if (amount == null || amount.signum() <= 0) return TopUpPreview(null, null, null, null)
        val converted = converter.convert(amount, currencyCode, sub.currencyCode)?.setScale(2, RoundingMode.HALF_UP)
            ?: return TopUpPreview(null, null, null, null)
        val after = sub.storedValueBalance + converted
        return TopUpPreview(converted, after, StoredValueCalculator.payableCycles(after, sub.price), StoredValueCalculator.status(after, sub.price))
    }

    /** Suggested amounts: 1, 3, 6 and 12 cycles of the fee (or round numbers when the fee is zero). */
    fun quickAmounts(fee: BigDecimal): List<BigDecimal> =
        if (fee.signum() <= 0) listOf(50, 100, 200, 500).map(::BigDecimal)
        else listOf(1, 3, 6, 12).map { fee.multiply(BigDecimal(it)).stripTrailingZeros() }
}

enum class StoredValuePeriod { ALL_TIME, THIS_MONTH, LAST_MONTH, THIS_YEAR, LAST_YEAR }

data class StoredValueSubSummary(val subscription: Subscription, val balanceInDefault: BigDecimal?, val status: StoredValueStatus, val payableCycles: Long?)

data class StoredValueStats(
    val totalBalance: BigDecimal,
    val subscriptionCount: Int,
    val thisMonthDeposits: BigDecimal,
    val thisYearDeposits: BigDecimal,
    val periodDeposits: BigDecimal,
    val periodDepositCount: Int,
    val averageDaily: BigDecimal,
    val averageMonthly: BigDecimal,
    /** Amounts left out because no exchange rate was available. */
    val unconvertedCount: Int,
    val subscriptions: List<StoredValueSubSummary>,
)

/** Aggregates stored-value balances and deposits in the default currency at current rates. */
object StoredValueStatsCalculator {
    private val MC = MathContext.DECIMAL64

    fun range(period: StoredValuePeriod, today: LocalDate, earliest: LocalDate?): Pair<LocalDate, LocalDate> = when (period) {
        StoredValuePeriod.ALL_TIME -> (earliest ?: today) to today
        StoredValuePeriod.THIS_MONTH -> today.withDayOfMonth(1) to today
        StoredValuePeriod.LAST_MONTH -> YearMonth.from(today).minusMonths(1).let { it.atDay(1) to it.atEndOfMonth() }
        StoredValuePeriod.THIS_YEAR -> today.withDayOfYear(1) to today
        StoredValuePeriod.LAST_YEAR -> today.minusYears(1).let { it.withDayOfYear(1) to it.withDayOfYear(it.lengthOfYear()) }
    }

    fun compute(
        subscriptions: List<Subscription>,
        records: List<StoredValueRecord>,
        period: StoredValuePeriod,
        today: LocalDate,
        zone: ZoneId,
        converter: CurrencyConverter,
        defaultCode: String,
    ): StoredValueStats {
        var unconverted = 0
        fun toDefault(amount: BigDecimal, code: String): BigDecimal? =
            converter.convert(amount, code, defaultCode).also { if (it == null) unconverted++ }

        val storedSubs = subscriptions.filter { it.kind == SubscriptionKind.STORED_VALUE }
        val summaries = storedSubs.map { s ->
            StoredValueSubSummary(
                s, toDefault(s.storedValueBalance, s.currencyCode),
                StoredValueCalculator.status(s.storedValueBalance, s.price), StoredValueCalculator.payableCycles(s.storedValueBalance, s.price),
            )
        }
        val deposits = records.filter { it.type == StoredValueRecordType.DEPOSIT }
            .map { it to it.occurredAt.atZone(zone).toLocalDate() }
        val earliest = deposits.minOfOrNull { it.second }
        fun sumBetween(from: LocalDate, to: LocalDate): Pair<BigDecimal, Int> {
            val inRange = deposits.filter { (_, d) -> d in from..to }
            val sum = inRange.mapNotNull { (r, _) -> toDefault(r.amount, r.currencyCode) }.fold(BigDecimal.ZERO, BigDecimal::add)
            return sum to inRange.size
        }
        val month = sumBetween(today.withDayOfMonth(1), today).first
        val year = sumBetween(today.withDayOfYear(1), today).first
        val (from, to) = range(period, today, earliest)
        unconverted = 0
        val (periodTotal, periodCount) = sumBetween(from, to)
        val periodUnconverted = unconverted
        val days = ChronoUnit.DAYS.between(from, to) + 1
        val months = ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to)) + 1
        val balanceTotal = summaries.mapNotNull { it.balanceInDefault }.fold(BigDecimal.ZERO, BigDecimal::add)
        return StoredValueStats(
            totalBalance = balanceTotal,
            subscriptionCount = storedSubs.size,
            thisMonthDeposits = month,
            thisYearDeposits = year,
            periodDeposits = periodTotal,
            periodDepositCount = periodCount,
            averageDaily = if (periodCount == 0) BigDecimal.ZERO else periodTotal.divide(BigDecimal(maxOf(1, days)), MC),
            averageMonthly = if (periodCount == 0) BigDecimal.ZERO else periodTotal.divide(BigDecimal(maxOf(1, months)), MC),
            unconvertedCount = periodUnconverted + summaries.count { it.balanceInDefault == null },
            subscriptions = summaries.sortedByDescending { it.balanceInDefault ?: BigDecimal.ZERO },
        )
    }
}
