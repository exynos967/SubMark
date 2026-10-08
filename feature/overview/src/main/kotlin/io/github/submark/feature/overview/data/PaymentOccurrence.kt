package io.github.submark.feature.overview.data

import io.github.submark.core.domain.CurrencyConverter
import io.github.submark.core.model.Category
import io.github.submark.core.model.Money
import io.github.submark.core.model.PaymentRecord
import io.github.submark.core.model.PaymentStatus
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * One instance of a payment on a specific [date]: an actual payment record, a projected due
 * occurrence, or both merged (a scheduled occurrence that was actually paid).
 */
data class PaymentOccurrence(
    val subscription: Subscription,
    val date: LocalDate,
    /** Amount in the default currency; null when the rate is unavailable. */
    val amount: Money?,
    /** Actual payment record on [date] for this subscription (paid), or null when unpaid. */
    val payment: PaymentRecord? = null,
    /** A projected occurrence exists on [date] (unpaid when [payment] is null). */
    val scheduled: Boolean = false,
    /** Category name for the "by category" list (may be null for lifetime purchases). */
    val categoryName: String? = null,
) {
    val paid: Boolean get() = payment != null

    fun isOverdue(today: LocalDate): Boolean = scheduled && !paid && date < today
}

/** Pure computing for payment projections over a date range. */
object PaymentProjection {

    /** Not every subscription contributes to the payment calendar / overview math. */
    fun counts(sub: Subscription): Boolean =
        (sub.kind == SubscriptionKind.REGULAR || sub.kind == SubscriptionKind.STORED_VALUE) &&
            sub.status == SubscriptionStatus.ACTIVE

    data class Report(
        val byDate: Map<LocalDate, List<PaymentOccurrence>>,
        /** Σ paid in [from]..[to] in default currency. */
        val paid: BigDecimal = BigDecimal.ZERO,
        /** Σ scheduled (unpaid) in [from]..[to] in default currency. */
        val scheduled: BigDecimal = BigDecimal.ZERO,
    ) {
        val projectedTotal: BigDecimal get() = paid + scheduled

        /** Same occurrences, with [paid] / [scheduled] re-totalled over [from]..[to] only. */
        fun between(from: LocalDate, to: LocalDate): Report =
            byDate.filterKeys { it in from..to }.values.flatten().let { occ ->
                copy(paid = paidTotal(occ), scheduled = scheduledTotal(occ))
            }
    }

    private fun paidTotal(occ: List<PaymentOccurrence>): BigDecimal =
        occ.filter { it.paid }.mapNotNull { it.amount }.fold(BigDecimal.ZERO) { a, b -> a + b }

    private fun scheduledTotal(occ: List<PaymentOccurrence>): BigDecimal =
        occ.filter { it.scheduled && !it.paid }.mapNotNull { it.amount }.fold(BigDecimal.ZERO) { a, b -> a + b }

    /**
     * Build [PaymentOccurrence]s over [from]..[to] from actual payments + projected due dates.
     * [converter] converts every amount to [defaultCode].
     * [includeChildren] controls whether bundle children (parentId != null) appear.
     */
    fun project(
        subs: List<Subscription>,
        payments: List<PaymentRecord>,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate,
        defaultCode: String,
        converter: CurrencyConverter,
        userShares: Map<String, BigDecimal?> = emptyMap(),
        includeChildren: Boolean = false,
    ): Report {
        val byDate = sortedMapOf<LocalDate, MutableList<PaymentOccurrence>>()
        val subscriptionById = subs.associateBy { it.id }

        // ---- Actual success payment records ----
        payments
            .filter { it.status == PaymentStatus.SUCCESS && it.paymentDate in from..to }
            .filter { record -> subs.any { it.id == record.subscriptionId } }
            .forEach { record ->
                val sub = subscriptionById[record.subscriptionId] ?: return@forEach
                if (sub.kind == SubscriptionKind.WISHLIST) return@forEach
                val defaultAmount = converter.convert(record.amount, record.currencyCode, defaultCode)
                byDate.getOrPut(record.paymentDate) { mutableListOf() }.add(
                    PaymentOccurrence(
                        subscription = sub,
                        date = record.paymentDate,
                        amount = defaultAmount,
                        payment = record,
                        categoryName = null, // resolved lazily in UI
                    ),
                )
            }

        // ---- Projected occurrences ----
        subs
            .filter { counts(it) }
            .filter { includeChildren || it.parentId == null }
            .forEach { sub ->
                val userShare = userShares[sub.id]
                val basePrice = userShare ?: sub.price
                val cycle = io.github.submark.core.domain.BillingCalculator.cycleLength(sub) ?: return@forEach
                val anchor = when {
                    sub.isSingleCycle -> sub.nextPaymentDate
                    else -> sub.cycleAnchorDate ?: io.github.submark.core.domain.BillingCalculator.firstBillingDate(sub)
                } ?: return@forEach

                val end = sub.endDate
                if (end != null && end < from) return@forEach
                val rangeEnd = if (end != null) minOf(end, to) else to
                val next = sub.nextPaymentDate
                // Outstanding dues only: occurrences at/after the stored next date.
                val lower = if (next != null && next > from) next else from
                if (lower > rangeEnd) return@forEach
                val nextMatchesAnchor = next != null &&
                    io.github.submark.core.domain.BillingCalculator.occurrencesBetween(
                        anchor, cycle, next, next, sub.fixedPaymentDay,
                    ).isNotEmpty()
                val scheduleAnchor = if (next != null && next <= rangeEnd && !nextMatchesAnchor) next else anchor
                io.github.submark.core.domain.BillingCalculator.occurrencesBetween(
                    anchor = scheduleAnchor, cycle = cycle, from = lower, to = rangeEnd, fixedDay = sub.fixedPaymentDay,
                ).forEach { date ->
                    val list = byDate.getOrPut(date) { mutableListOf() }
                    val existing = list.firstOrNull { it.subscription.id == sub.id }
                    if (existing == null) {
                        list.add(
                            PaymentOccurrence(
                                subscription = sub,
                                date = date,
                                amount = converter.convert(basePrice, sub.currencyCode, defaultCode),
                                scheduled = true,
                            ),
                        )
                    }
                }
            }

        // ---- Merge paid records into the scheduled slot when (sub, date) match ----
        byDate.forEach { (_, list) ->
            for (i in list.indices) {
                val item = list[i]
                if (!item.scheduled || item.paid) continue
                val matchingPaid = list.firstOrNull { it.paid && it.subscription.id == item.subscription.id }
                if (matchingPaid != null) {
                    list[i] = item.copy(payment = matchingPaid.payment, amount = matchingPaid.amount ?: item.amount)
                }
            }
        }

        byDate.values.forEach { list ->
            list.sortWith(compareBy<PaymentOccurrence> { it.subscription.name.lowercase() })
        }

        val all = byDate.values.flatten()
        return Report(byDate = byDate, paid = paidTotal(all), scheduled = scheduledTotal(all))
    }

    /** All unpaid occurrences from today (inclusive) within the next [windowDays]. */
    fun comingUp(report: Report, today: LocalDate, windowDays: Int = 7): List<PaymentOccurrence> {
        val end = today.plusDays(windowDays.toLong())
        return report.byDate.toSortedMap()
            .filterKeys { it >= today && it <= end }
            .flatMap { (_, list) -> list }
            .filter { it.scheduled && !it.paid }
            .sortedWith(compareBy<PaymentOccurrence> { it.date }.thenBy { it.subscription.name.lowercase() })
    }

    /** Payments recorded in [today-7, today]. */
    fun recentPaid(report: Report, today: LocalDate, windowDays: Int = 7): List<PaymentOccurrence> {
        val start = today.minusDays(windowDays.toLong())
        return report.byDate.toSortedMap()
            .filterKeys { it >= start && it <= today }
            .flatMap { (_, list) -> list }
            .filter { it.paid }
            .sortedBy { it.date }
    }

    /** Unpaid occurrences due on [date] for the mini calendar-strip agenda. */
    fun agendaForDate(report: Report, date: LocalDate): List<PaymentOccurrence> =
        report.byDate[date].orEmpty().filter { !it.paid }

    /** Monthly paid totals for the trend chart. */
    fun monthlyPoints(report: Report): List<MonthlyPoint> {
        val grouped = mutableMapOf<YearMonth, MutableList<PaymentOccurrence>>()
        report.byDate.forEach { (date, list) ->
            grouped.getOrPut(YearMonth.from(date)) { mutableListOf() }.addAll(list)
        }
        return grouped.map { (ym, list) ->
            val total = list.filter { it.paid }
                .mapNotNull { it.amount }
                .fold(BigDecimal.ZERO) { a, b -> a + b }
            MonthlyPoint(ym.atDay(1), total)
        }.sortedBy { it.month }
    }
}

data class MonthlyPoint(val month: LocalDate, val total: BigDecimal)
