package io.github.submark.feature.money.ui.financial

import io.github.submark.core.data.settings.FinancialDetailFilter
import io.github.submark.core.domain.CurrencyConverter
import io.github.submark.core.domain.StoredValueCalculator
import io.github.submark.core.domain.StoredValueStatus
import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.PaymentKind
import io.github.submark.core.model.PaymentRecord
import io.github.submark.core.model.PaymentStatus
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/** Cycle the payment belongs to based on its subscription. */
enum class SpendingType { MONTHLY, QUARTERLY, YEARLY, OTHER_CYCLE, LIFETIME, SHARED, IAP, STORED_VALUE_DEPOSIT, BUNDLE_CHILD }

data class ConvertedPayment(
    val record: PaymentRecord,
    val subscription: Subscription?,
    val defaultAmount: BigDecimal,
    /** Only one of these labels survives so every payment is counted once in the type breakdown. */
    val type: SpendingType,
)

data class MonthGroup(val month: YearMonth, val total: BigDecimal, val payments: List<ConvertedPayment>)

data class FinancialResult(
    val total: BigDecimal,
    val paymentCount: Int,
    /** total / count. */
    val average: BigDecimal,
    /** Months in the range that contain at least one payment. */
    val expenseMonths: Int,
    /** Monthly/quarterly/yearly/lifetime/shared/IAP/stored-value/other breakdown, in display order. */
    val byType: List<Pair<SpendingType, BigDecimal>>,
    val months: List<MonthGroup>,
    /** Payments that could not be converted (skipped). */
    val unconvertedCount: Int,
)

/** Builds the historical-converter map and converts SUCCESS payments into default currency. */
object FinancialAggregator {
    private val MC = MathContext.DECIMAL64

    /** Ordered snapshot of per-day converters: cached day -> earliest first. */
    suspend fun converters(
        dates: List<LocalDate>,
        load: suspend (LocalDate) -> CurrencyConverter,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Map<LocalDate, CurrencyConverter>? {
        val distinct = dates.distinct().sorted()
        val out = LinkedHashMap<LocalDate, CurrencyConverter>(distinct.size)
        distinct.forEachIndexed { i, d ->
            out[d] = load(d)
            onProgress(i + 1, distinct.size)
        }
        return out.takeIf { out.isNotEmpty() || dates.isEmpty() }
    }

    /** Type with exclusivity: IAP > shared > lifetime > stored-value > bundle child > cycle length. */
    fun typeOf(record: PaymentRecord, subscription: Subscription?): SpendingType = when {
        record.kind == PaymentKind.IN_APP_PURCHASE -> SpendingType.IAP
        subscription?.isShared == true -> SpendingType.SHARED
        record.kind == PaymentKind.LIFETIME_PURCHASE || subscription?.kind == SubscriptionKind.LIFETIME -> SpendingType.LIFETIME
        record.kind == PaymentKind.STORED_VALUE_DEPOSIT || subscription?.kind == SubscriptionKind.STORED_VALUE -> SpendingType.STORED_VALUE_DEPOSIT
        subscription?.parentId != null -> SpendingType.BUNDLE_CHILD
        subscription?.billingCycle == BillingCycle.MONTHLY -> SpendingType.MONTHLY
        subscription?.billingCycle == BillingCycle.QUARTERLY -> SpendingType.QUARTERLY
        subscription?.billingCycle == BillingCycle.ANNUALLY -> SpendingType.YEARLY
        else -> SpendingType.OTHER_CYCLE
    }

    /** Per-payment converter lookup: exact date, else latest known before it, exact or current. */
    fun converterFor(converters: Map<LocalDate, CurrencyConverter>, date: LocalDate): CurrencyConverter? =
        converters[date] ?: converters.entries.filter { it.key <= date }.maxByOrNull { it.key }?.value

    /** Includes a payment based on the lifetime filter. */
    fun filterMode(record: PaymentRecord, subscription: Subscription?, filter: FinancialDetailFilter): Boolean {
        val lifetime = subscription?.kind == SubscriptionKind.LIFETIME || record.kind == PaymentKind.LIFETIME_PURCHASE
        return when (filter) {
            FinancialDetailFilter.INCLUDE_LIFETIME -> true
            FinancialDetailFilter.SUBSCRIPTIONS_ONLY -> !lifetime
            FinancialDetailFilter.LIFETIME_ONLY -> lifetime
        }
    }

    fun aggregate(
        records: List<PaymentRecord>,
        subscriptions: Map<String, Subscription>,
        filter: FinancialDetailFilter,
        defaultCode: String,
        converters: Map<LocalDate, CurrencyConverter>,
        onUnavailable: (PaymentRecord) -> Unit = {},
    ): FinancialResult {
        val converted = mutableListOf<ConvertedPayment>()
        var unconverted = 0
        records.filter { it.status == PaymentStatus.SUCCESS }.forEach { r ->
            val sub = subscriptions[r.subscriptionId]
            if (!filterMode(r, sub, filter)) return@forEach
                        val converter = converters.entries.filter { it.key <= r.paymentDate }.maxByOrNull { it.key }?.value
            val amount = if (r.currencyCode == defaultCode) r.amount else converter?.convert(r.amount, r.currencyCode, defaultCode)
            if (amount == null) {
                unconverted++
                onUnavailable(r)
                return@forEach
            }
            converted += ConvertedPayment(r, sub, amount, typeOf(r, sub))
        }
        val total = converted.fold(BigDecimal.ZERO) { acc, p -> acc + p.defaultAmount }
        val months = converted.groupBy { YearMonth.from(it.record.paymentDate) }
            .toSortedMap()
            .map { (m, list) -> MonthGroup(m, list.fold(BigDecimal.ZERO) { a, p -> a + p.defaultAmount }, list.sortedByDescending { it.record.paymentDate }) }
        val order = listOf(
            SpendingType.MONTHLY, SpendingType.QUARTERLY, SpendingType.YEARLY, SpendingType.OTHER_CYCLE,
            SpendingType.LIFETIME, SpendingType.SHARED, SpendingType.IAP, SpendingType.STORED_VALUE_DEPOSIT, SpendingType.BUNDLE_CHILD,
        )
        val byType = converted.groupBy { it.type }
            .mapValues { (_, list) -> list.fold(BigDecimal.ZERO) { a, p -> a + p.defaultAmount } }
            .let { map -> order.mapNotNull { t -> map[t]?.let { t to it } } }
        return FinancialResult(
            total = total,
            paymentCount = converted.size,
            average = if (converted.isEmpty()) BigDecimal.ZERO else total.divide(BigDecimal(converted.size), MC).setScale(2, RoundingMode.HALF_UP),
            expenseMonths = months.size,
            byType = byType,
            months = months,
            unconvertedCount = unconverted,
        )
    }
}
