package io.github.submark.feature.widget

import io.github.submark.core.domain.BillingCalculator
import io.github.submark.core.domain.CostCalculator
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Relative due label — Today / Tomorrow / In N days / Overdue by N days / Unknown. */
sealed interface DueLabel {
    data object Unknown : DueLabel
    data object Today : DueLabel
    data object Tomorrow : DueLabel
    data class InDays(val days: Int) : DueLabel
    data class Overdue(val days: Int) : DueLabel
}

/** One row of the upcoming widget. */
data class UpcomingRow(
    val id: String,
    val name: String,
    /** First letter fallback when no icon is available. */
    val fallbackLetter: String,
    val amount: BigDecimal,
    val currencyCode: String,
    val dueLabel: DueLabel,
    val overdue: Boolean,
)

data class UpcomingWidgetState(
    val rows: List<UpcomingRow>,
    val subscriptionCount: Int,
    /** Total of the upcoming recurring subscriptions converted to the default currency. */
    val total: BigDecimal?,
    /** Currency the [total] is expressed in. */
    val defaultCurrencyCode: String,
)

data class SpendingSummaryState(
    val projectedThisMonth: BigDecimal?,
    val activeCount: Int,
    /** Fraction of the monthly budget used (null = no budget set). */
    val budgetUsage: BigDecimal?,
    val overBudget: Boolean,
)

/** Data shaping for the Glance widgets; pure so it is unit-testable. */
object WidgetData {

    /** Monogram fallback for a row, matching the in-app icon fallback. */
    fun fallbackLetterOf(name: String): String = name.trim().firstOrNull()?.uppercase() ?: "?"

    fun dueLabel(dueDate: LocalDate?, today: LocalDate): DueLabel {
        if (dueDate == null) return DueLabel.Unknown
        val days = ChronoUnit.DAYS.between(today, dueDate).toInt()
        return when {
            days < 0 -> DueLabel.Overdue(-days)
            days == 0 -> DueLabel.Today
            days == 1 -> DueLabel.Tomorrow
            else -> DueLabel.InDays(days)
        }
    }

    /** Eligible rows: active recurring subscriptions with a due date, overdue first then by date. */
    fun upcoming(
        subs: List<Subscription>,
        today: LocalDate,
        limit: Int,
        convert: (BigDecimal, String) -> BigDecimal?,
        defaultCurrencyCode: String = "USD",
    ): UpcomingWidgetState {
        val eligible = subs
            .filter {
                it.status == SubscriptionStatus.ACTIVE &&
                    (it.kind == SubscriptionKind.REGULAR || it.kind == SubscriptionKind.STORED_VALUE) &&
                    it.nextPaymentDate != null
            }
            .sortedWith(
                compareBy<Subscription> { BillingCalculator.isOverdue(it, today) }.reversed()
                    .thenBy { it.nextPaymentDate },
            )
        val rows = eligible.take(limit).map { sub ->
            UpcomingRow(
                id = sub.id,
                name = sub.name,
                fallbackLetter = fallbackLetterOf(sub.name),
                amount = sub.price,
                currencyCode = sub.currencyCode,
                dueLabel = dueLabel(sub.nextPaymentDate, today),
                overdue = BillingCalculator.isOverdue(sub, today),
            )
        }
        val total = eligible
            .mapNotNull { convert(it.price, it.currencyCode) }
            .fold(null as BigDecimal?) { acc, v -> (acc ?: BigDecimal.ZERO) + v }
            ?.setScale(2, RoundingMode.HALF_UP)
        return UpcomingWidgetState(rows = rows, subscriptionCount = eligible.size, total = total, defaultCurrencyCode = defaultCurrencyCode)
    }

    /**
     * Projected spending for the current month: remaining due dates of every active recurring
     * subscription (including overdue) inside the current calendar month, converted.
     */
    fun spendingSummary(
        subs: List<Subscription>,
        today: LocalDate,
        annualBudget: BigDecimal?,
        convert: (BigDecimal, String) -> BigDecimal?,
    ): SpendingSummaryState {
        val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
        val active = subs.filter { sub ->
            sub.status == SubscriptionStatus.ACTIVE &&
                (sub.kind == SubscriptionKind.REGULAR || sub.kind == SubscriptionKind.STORED_VALUE)
        }
        var projected: BigDecimal? = null
        for (sub in active) {
            val occurrenceCount = occurrencesLeftInMonth(sub, today, monthEnd).size
            if (occurrenceCount == 0) continue
            val converted = convert(sub.price, sub.currencyCode) ?: continue
            projected = (projected ?: BigDecimal.ZERO) + converted.multiply(BigDecimal(occurrenceCount))
        }
        projected = projected?.setScale(2, RoundingMode.HALF_UP)
        val monthlyBudget = annualBudget?.divide(BigDecimal(12), 2, RoundingMode.HALF_UP)
        val usage = if (monthlyBudget != null && monthlyBudget.signum() > 0) {
            (projected ?: BigDecimal.ZERO).divide(monthlyBudget, 4, RoundingMode.HALF_UP)
        } else {
            null
        }
        return SpendingSummaryState(
            projectedThisMonth = projected,
            activeCount = active.size,
            budgetUsage = usage,
            overBudget = usage != null && usage > BigDecimal.ONE,
        )
    }

    private fun occurrencesLeftInMonth(sub: Subscription, today: LocalDate, monthEnd: LocalDate): List<LocalDate> {
        val next = sub.nextPaymentDate ?: return emptyList()
        if (next > monthEnd) return emptyList()
        val cycle = BillingCalculator.cycleLength(sub)
        val anchor = sub.cycleAnchorDate
        val from = if (next < today) today else next
        if (cycle == null || anchor == null) return listOf(next)
        val list = mutableListOf<LocalDate>()
        if (next < today) list += next // overdue still falls inside this month
        list += BillingCalculator.occurrencesBetween(anchor, cycle, from, monthEnd, sub.fixedPaymentDay)
        return list.filter { sub.endDate == null || it <= sub.endDate }.distinct().sorted()
    }
}
