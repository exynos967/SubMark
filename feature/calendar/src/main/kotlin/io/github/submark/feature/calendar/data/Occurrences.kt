package io.github.submark.feature.calendar.data

import io.github.submark.core.domain.BillingCalculator
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

/** One cell/row of a billing calendar: an actual payment, a projected due date, or both. */
data class Occurrence(
    val subscription: Subscription,
    val category: Category?,
    val date: LocalDate,
    /** Amount in the default currency; null when a rate is unavailable. */
    val amount: Money?,
    /** Success payment record on [date] for this subscription. */
    val payment: PaymentRecord? = null,
    /** A projected occurrence exists on [date] (unpaid when [payment] is null). */
    val scheduled: Boolean = false,
) {
    val paid: Boolean get() = payment != null

    fun isOverdue(today: LocalDate): Boolean = scheduled && !paid && date < today
}

/** Pure payment-occurrence projection, shared by the calendar screens. */
object OccurrenceProjector {

    /** Recurring subscriptions that produce due occurrences; paused and wishlist are excluded. */
    fun isProjectable(sub: Subscription): Boolean =
        sub.status == SubscriptionStatus.ACTIVE &&
            (sub.kind == SubscriptionKind.REGULAR || sub.kind == SubscriptionKind.STORED_VALUE) &&
            BillingCalculator.cycleLength(sub) != null

    /**
     * Projected due dates of [sub] within [from]..[to] inclusive.
     *
     * Single-cycle AUTO subscriptions hold their sole due date in
     * [Subscription.nextPaymentDate]. Recurring subscriptions are projected from
     * [Subscription.cycleAnchorDate]; the projection is anchored at the stored next date when it
     * lies inside the range so UI edits that moved the next date are honoured.
     */
    fun dueDates(sub: Subscription, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (!isProjectable(sub)) return emptyList()
        if (sub.isSingleCycle) {
            val due = sub.nextPaymentDate ?: return emptyList()
            return if (due in from..to) listOf(due) else emptyList()
        }
        val anchor = sub.cycleAnchorDate ?: BillingCalculator.firstBillingDate(sub)
        val cycle = BillingCalculator.cycleLength(sub) ?: return emptyList()
        val next = sub.nextPaymentDate
        val end = sub.endDate
        if (end != null && from > end) return emptyList()
        val upper = if (end != null) minOf(to, end) else to
        // Outstanding dues only: occurrences at and after the stored next date (earlier
        // occurrences were either paid — payment records carry them — or auto-marked).
        val lower = if (next != null && next > from) next else from
        if (lower > upper) return emptyList()
        val nextMatchesAnchor = next != null &&
            BillingCalculator.occurrencesBetween(anchor, cycle, next, next, sub.fixedPaymentDay).isNotEmpty()
        val scheduleAnchor = if (next != null && next <= upper && !nextMatchesAnchor) next else anchor
        return BillingCalculator.occurrencesBetween(scheduleAnchor, cycle, lower, upper, sub.fixedPaymentDay)
    }

    /**
     * Merges projected due dates with success payment records over [from]..[to].
     * [converter] converts every amount to [defaultCode]; [userShares] = sub id -> user share
     * price (subscriptions where payments count only the user's part).
     */
    fun project(
        subs: List<Subscription>,
        payments: List<PaymentRecord>,
        from: LocalDate,
        to: LocalDate,
        defaultCode: String,
        converter: CurrencyConverter,
        userShares: Map<String, BigDecimal?> = emptyMap(),
        categoriesById: Map<String, Category> = emptyMap(),
        includeChildren: Boolean = false,
    ): Map<LocalDate, List<Occurrence>> {
        val result = sortedMapOf<LocalDate, MutableList<Occurrence>>()
        val countable = payments.filter {
            it.status == PaymentStatus.SUCCESS && it.paymentDate in from..to
        }
        val subsById = subs.associateBy { it.id }

        countable.forEach { record ->
            val sub = subsById[record.subscriptionId]
            if (sub != null && (sub.kind == SubscriptionKind.WISHLIST || sub.status == SubscriptionStatus.PAUSED)) {
                // Wishlist purchases and payments of paused items still count as spending records.
            }
            val resolved = sub ?: orphanSubscription(record)
            val amount = converter.convert(record.amount, record.currencyCode, defaultCode)
            result.getOrPut(record.paymentDate) { mutableListOf() }.add(
                Occurrence(
                    subscription = resolved,
                    category = sub?.let { categoriesById[it.categoryId] },
                    date = record.paymentDate,
                    amount = amount,
                    payment = record,
                ),
            )
        }

        subs.filter { isProjectable(it) }
            .filter { it.parentId == null || includeChildren }
            .forEach { sub ->
                val basePrice = userShares[sub.id] ?: sub.price
                dueDates(sub, from, to).forEach { date ->
                    val list = result.getOrPut(date) { mutableListOf() }
                    val existing = list.firstOrNull { it.subscription.id == sub.id }
                    if (existing != null) {
                        val index = list.indexOf(existing)
                        list[index] = existing.copy(
                            amount = existing.amount ?: converter.convert(basePrice, sub.currencyCode, defaultCode),
                        )
                    } else {
                        list.add(
                            Occurrence(
                                subscription = sub,
                                category = categoriesById[sub.categoryId],
                                date = date,
                                amount = converter.convert(basePrice, sub.currencyCode, defaultCode),
                                scheduled = true,
                            ),
                        )
                    }
                }
            }
        result.values.forEach { list -> list.sortBy { it.subscription.name.lowercase() } }
        return result
    }

    private fun orphanSubscription(record: PaymentRecord): Subscription = Subscription(
        id = record.subscriptionId,
        name = record.subscriptionId,
        price = record.amount,
        currencyCode = record.currencyCode,
        kind = SubscriptionKind.REGULAR,
        categoryId = "",
        startDate = record.paymentDate,
        createdAt = record.createdAt,
        updatedAt = record.updatedAt,
    )

    /** Unpaid occurrences overdue at [today] or due within the next [windowDays]. */
    fun comingUp(
        byDate: Map<LocalDate, List<Occurrence>>,
        today: LocalDate,
        windowDays: Int = 7,
    ): List<Occurrence> {
        val end = today.plusDays(windowDays.toLong())
        return byDate.toSortedMap()
            .filterKeys { it <= end }
            .flatMap { (_, list) -> list }
            .filter { it.scheduled && !it.paid }
            .sortedWith(compareBy<Occurrence> { it.date }.thenBy { it.subscription.name.lowercase() })
    }
}

/** Calendar-aligned period used by overview/month splits. */
enum class CalendarPeriod { MONTH, QUARTER, YEAR }

/** Start (inclusive) and end (inclusive) of the calendar month/quarter/year containing [day]. */
fun periodRange(period: CalendarPeriod, day: LocalDate): Pair<LocalDate, LocalDate> = when (period) {
    CalendarPeriod.MONTH -> YearMonth.from(day).let { it.atDay(1) to it.atEndOfMonth() }
    CalendarPeriod.QUARTER -> {
        val q = (day.monthValue - 1) / 3
        val start = LocalDate.of(day.year, q * 3 + 1, 1)
        start to start.plusMonths(3).minusDays(1)
    }
    CalendarPeriod.YEAR -> LocalDate.of(day.year, 1, 1) to LocalDate.of(day.year, 12, 31)
}

/** Paid vs scheduled totals of [byDate] for the calendar period containing [today]. */
data class PeriodSplit(
    val paid: BigDecimal,
    val scheduled: BigDecimal,
) {
    val projected: BigDecimal get() = paid + scheduled
}

fun periodSplit(byDate: Map<LocalDate, List<Occurrence>>, period: CalendarPeriod, today: LocalDate): PeriodSplit {
    val (start, end) = periodRange(period, today)
    var paid = BigDecimal.ZERO
    var scheduled = BigDecimal.ZERO
    byDate.forEach { (date, list) ->
        if (date < start || date > end) return@forEach
        list.forEach { occ ->
            val amount = occ.amount ?: return@forEach
            when {
                occ.paid -> paid += amount
                occ.scheduled -> scheduled += amount
            }
        }
    }
    return PeriodSplit(paid, scheduled)
}
