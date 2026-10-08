package io.github.submark.feature.widget

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class WidgetDataTest {

    private val today: LocalDate = LocalDate.of(2026, 3, 10)

    private fun sub(
        id: String,
        name: String = "Sub $id",
        kind: SubscriptionKind = SubscriptionKind.REGULAR,
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        next: LocalDate? = today.plusDays(2),
        price: BigDecimal = BigDecimal("10"),
        cycle: BillingCycle? = BillingCycle.MONTHLY,
        anchor: LocalDate? = next,
    ) = Subscription(
        id = id,
        name = name,
        kind = kind,
        price = price,
        currencyCode = "USD",
        billingCycle = cycle,
        startDate = next ?: today,
        cycleAnchorDate = anchor,
        nextPaymentDate = next,
        status = status,
        categoryId = "cat_other",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private val identity: (BigDecimal, String) -> BigDecimal? = { amount, code -> if (code == "USD") amount else null }

    @Test
    fun `dueLabel maps relative offsets`() {
        assertThat(WidgetData.dueLabel(today, today)).isEqualTo(DueLabel.Today)
        assertThat(WidgetData.dueLabel(today.plusDays(1), today)).isEqualTo(DueLabel.Tomorrow)
        assertThat(WidgetData.dueLabel(today.plusDays(4), today)).isEqualTo(DueLabel.InDays(4))
        assertThat(WidgetData.dueLabel(today.minusDays(3), today)).isEqualTo(DueLabel.Overdue(3))
        assertThat(WidgetData.dueLabel(null, today)).isEqualTo(DueLabel.Unknown)
    }

    @Test
    fun `fallbackLetter uppercases the first letter`() {
        assertThat(WidgetData.fallbackLetterOf("netflix")).isEqualTo("N")
        assertThat(WidgetData.fallbackLetterOf(" Spotify ")).isEqualTo("S")
        assertThat(WidgetData.fallbackLetterOf("")).isEqualTo("?")
    }

    @Test
    fun `upcoming keeps only active recurring and sorts overdue first`() {
        val overdue = sub("o", next = today.minusDays(2))
        val soon = sub("s", next = today.plusDays(1))
        val later = sub("l", next = today.plusDays(9))
        val paused = sub("p", status = SubscriptionStatus.PAUSED)
        val wishlist = sub("w", kind = SubscriptionKind.WISHLIST)
        val state = WidgetData.upcoming(listOf(paused, wishlist, later, soon, overdue), today, 10, identity)
        assertThat(state.rows.map { it.id }).containsExactly("o", "s", "l").inOrder()
        assertThat(state.rows[0].overdue).isTrue()
        assertThat(state.rows[1].dueLabel).isEqualTo(DueLabel.Tomorrow)
        assertThat(state.subscriptionCount).isEqualTo(3)
    }

    @Test
    fun `upcoming limits rows but counts every eligible subscription in the total`() {
        val subs = (1..8).map { sub("s$it", next = today.plusDays(it.toLong())) }
        val state = WidgetData.upcoming(subs, today, 3, identity)
        assertThat(state.rows).hasSize(3)
        assertThat(state.subscriptionCount).isEqualTo(8)
        assertThat(state.total).isEqualTo(BigDecimal("80.00"))
    }

    @Test
    fun `upcoming total skips subscriptions whose currency cannot convert`() {
        val usd = sub("a", price = BigDecimal("10"))
        val eur = sub("b", price = BigDecimal("20")).copy(currencyCode = "EUR")
        val state = WidgetData.upcoming(listOf(usd, eur), today, 10, identity)
        assertThat(state.total).isEqualTo(BigDecimal("10.00"))
    }

    @Test
    fun `spendingSummary sums remaining occurrences of the current month`() {
        // Weekly subscription due today and again in 7, 14, 21 days (all inside March 2026).
        val weekly = sub(
            "w",
            next = today,
            cycle = BillingCycle.WEEKLY,
            anchor = today,
            price = BigDecimal("5"),
        )
        val monthly = sub("m", next = today.plusDays(15), price = BigDecimal("10")) // Mar 25, same month
        val state = WidgetData.spendingSummary(listOf(weekly, monthly), today, null, identity)
        // today, +7, +14, +21 = 4 weekly occurrences, +28 lands in April; monthly adds one.
        assertThat(state.projectedThisMonth).isEqualTo(BigDecimal("30.00"))
        assertThat(state.activeCount).isEqualTo(2)
        assertThat(state.budgetUsage).isNull()
        assertThat(state.overBudget).isFalse()
    }

    @Test
    fun `spendingSummary ignores next month and includes overdue of this month`() {
        val nextMonth = sub("n", next = today.plusDays(30), price = BigDecimal("99")) // April 9
        val overdue = sub("o", next = today.minusDays(1), price = BigDecimal("7"))
        val state = WidgetData.spendingSummary(listOf(nextMonth, overdue), today, null, identity)
        assertThat(state.projectedThisMonth).isEqualTo(BigDecimal("7.00"))
    }

    @Test
    fun `spendingSummary computes budget usage against a twelfth of the annual budget`() {
        val sub = sub("a", price = BigDecimal("60"))
        val budget = BigDecimal("1200") // 100/month
        val state = WidgetData.spendingSummary(listOf(sub), today, budget, identity)
        assertThat(state.budgetUsage).isEqualTo(BigDecimal("0.6000"))
        assertThat(state.overBudget).isFalse()
    }

    @Test
    fun `spendingSummary flags over budget`() {
        val sub = sub("a", price = BigDecimal("150"))
        val budget = BigDecimal("1200") // 100/month
        val state = WidgetData.spendingSummary(listOf(sub), today, budget, identity)
        assertThat(state.overBudget).isTrue()
    }

    @Test
    fun `spendingSummary returns null projection when everything is paused or skipped`() {
        val paused = sub("p", status = SubscriptionStatus.PAUSED)
        val state = WidgetData.spendingSummary(listOf(paused), today, null, identity)
        assertThat(state.projectedThisMonth).isNull()
        assertThat(state.activeCount).isEqualTo(0)
    }
}
