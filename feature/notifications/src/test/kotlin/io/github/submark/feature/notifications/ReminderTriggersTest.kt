package io.github.submark.feature.notifications

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.data.settings.NotificationSettings
import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.CustomReminder
import io.github.submark.core.model.RenewalType
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import io.github.submark.feature.notifications.data.ReminderKind
import io.github.submark.feature.notifications.data.ReminderTriggers
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderTriggersTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 3, 10)
    // 08:00 so 09:00 triggers are in the future.
    private val now: LocalDateTime = LocalDateTime.of(today, LocalTime.of(8, 0))

    private fun prefs(
        enabled: Boolean = true,
        advance: Int? = 3,
        first: LocalTime = LocalTime.of(9, 0),
        second: LocalTime = LocalTime.of(14, 0),
        third: LocalTime = LocalTime.of(20, 0),
        firstOn: Boolean = true,
        secondOn: Boolean = true,
        thirdOn: Boolean = true,
    ) = NotificationSettings(
        enabled = enabled,
        advanceDays = advance,
        firstTime = first,
        secondTime = second,
        thirdTime = third,
        firstSlotEnabled = firstOn,
        secondSlotEnabled = secondOn,
        thirdSlotEnabled = thirdOn,
    )

    private fun sub(
        id: String = "s1",
        kind: SubscriptionKind = SubscriptionKind.REGULAR,
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        renewal: RenewalType = RenewalType.MANUAL,
        next: LocalDate? = today.plusDays(5),
        anchor: LocalDate? = next,
        customEnabled: Boolean = false,
    ) = Subscription(
        id = id,
        name = "Test $id",
        kind = kind,
        price = BigDecimal("9.99"),
        currencyCode = "USD",
        billingCycle = BillingCycle.MONTHLY,
        renewalType = renewal,
        startDate = next ?: today,
        cycleAnchorDate = anchor,
        nextPaymentDate = next,
        status = status,
        categoryId = "cat_other",
        customReminderEnabled = customEnabled,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun compute(
        subs: List<Subscription>,
        reminders: Map<String, List<CustomReminder>> = emptyMap(),
        settings: NotificationSettings = prefs(),
        at: LocalDateTime = now,
    ) = ReminderTriggers.compute(subs, reminders, settings, at, zone) { "9.99 USD" }

    /** A narrowed sub whose cycle is outside the 60-day window, so only one occurrence fires. */
    private fun singleOccurrenceSub(
        renewal: RenewalType = RenewalType.MANUAL,
        next: LocalDate? = today.plusDays(5),
        customEnabled: Boolean = false,
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    ) = sub(
        renewal = renewal,
        next = next,
        anchor = next,
        customEnabled = customEnabled,
        status = status,
    ).copy(endDate = next) // endDate caps the window to a single occurrence

    @Test
    fun `manual renewal schedules advance slots plus three payment-day reminders`() {
        val triggers = compute(listOf(singleOccurrenceSub()))
        val kinds = triggers.map { it.kind }
        assertThat(kinds).containsExactly(
            ReminderKind.ADVANCE, ReminderKind.ADVANCE, ReminderKind.ADVANCE,
            ReminderKind.PAYDAY_FIRST, ReminderKind.PAYDAY_SECOND, ReminderKind.PAYDAY_THIRD,
        )
        val firstAdvance = triggers.first { it.kind == ReminderKind.ADVANCE }
        assertThat(firstAdvance.triggerAt.toLocalDate()).isEqualTo(today.plusDays(2)) // 5 - 3
    }

    @Test
    fun `auto renewal gets exactly one payment-day reminder`() {
        val triggers = compute(listOf(singleOccurrenceSub(renewal = RenewalType.AUTO)))
        val payday = triggers.filter { it.kind != ReminderKind.ADVANCE }
        assertThat(payday).hasSize(1)
        assertThat(payday.single().kind).isEqualTo(ReminderKind.PAYDAY_FIRST)
        assertThat(payday.single().triggerAt.toLocalTime()).isEqualTo(LocalTime.of(9, 0))
    }

    @Test
    fun `slot switches control advance reminders only`() {
        val settings = prefs(firstOn = false, secondOn = false, thirdOn = true)
        val triggers = compute(listOf(singleOccurrenceSub()), settings = settings)
        val advance = triggers.filter { it.kind == ReminderKind.ADVANCE }
        assertThat(advance).hasSize(1)
        assertThat(advance.single().triggerAt.toLocalTime()).isEqualTo(LocalTime.of(20, 0))
        // Payment-day reminders are unaffected.
        assertThat(triggers.count { it.kind != ReminderKind.ADVANCE }).isEqualTo(3)
    }

    @Test
    fun `no advance reminder when advanceDays is null`() {
        val triggers = compute(listOf(singleOccurrenceSub()), settings = prefs(advance = null))
        assertThat(triggers.map { it.kind }.distinct()).containsNoneOf(ReminderKind.ADVANCE, ReminderKind.CUSTOM)
        assertThat(triggers).hasSize(3)
    }

    @Test
    fun `advanceDays zero produces no advance reminders (payment day covers it)`() {
        val triggers = compute(listOf(sub()), settings = prefs(advance = 0))
        assertThat(triggers.filter { it.kind == ReminderKind.ADVANCE }).isEmpty()
    }

    @Test
    fun `custom reminders replace defaults`() {
        val subscription = singleOccurrenceSub(customEnabled = true)
        val custom = listOf(
            CustomReminder(id = "r1", subscriptionId = subscription.id, daysBefore = 2, time = LocalTime.of(10, 30)),
            CustomReminder(id = "r2", subscriptionId = subscription.id, daysBefore = 0, time = LocalTime.of(18, 0)),
        )
        val triggers = compute(listOf(subscription), mapOf(subscription.id to custom))
        assertThat(triggers.map { it.kind }.distinct()).containsExactly(ReminderKind.CUSTOM)
        assertThat(triggers).hasSize(2)
        val advance = triggers.first { it.daysBefore == 2 }
        assertThat(advance.triggerAt).isEqualTo(LocalDateTime.of(today.plusDays(3), LocalTime.of(10, 30)))
    }

    @Test
    fun `custom flag with empty list falls back to defaults`() {
        val subscription = sub(customEnabled = true)
        val triggers = compute(listOf(subscription))
        assertThat(triggers.map { it.kind }).contains(ReminderKind.ADVANCE)
    }

    @Test
    fun `past trigger times are skipped`() {
        // now is 08:00, due today with first slot at 07:00 => skipped; window shows the later slots only.
        val triggers = compute(
            listOf(singleOccurrenceSub(next = today)),
            settings = prefs(advance = null, first = LocalTime.of(7, 0)),
        )
        assertThat(triggers.map { it.kind }).containsExactly(ReminderKind.PAYDAY_SECOND, ReminderKind.PAYDAY_THIRD)
    }

    @Test
    fun `when all payment-day slots are past, only the future occurrences remain`() {
        val late = LocalDateTime.of(today, LocalTime.of(23, 30))
        val triggers = compute(listOf(sub(next = today)), at = late)
        // Monthly schedule: occurrences in window start one month later, all in future.
        assertThat(triggers.none { it.cycleDueDate == today }).isTrue()
        assertThat(triggers).isNotEmpty()
    }

    @Test
    fun `paused wishlist and lifetime subscriptions produce nothing`() {
        val paused = sub(id = "paused", status = SubscriptionStatus.PAUSED)
        val wishlist = sub(id = "wish", kind = SubscriptionKind.WISHLIST)
        val trial = sub(id = "trial", renewal = RenewalType.TRIAL)
        assertThat(compute(listOf(paused, wishlist, trial))).isEmpty()
    }

    @Test
    fun `rolling window limits occurrences to sixty days`() {
        // Weekly schedule starting today: occurrences at 0,7,...,56 within 60 days.
        val weekly = Subscription(
            id = "w",
            name = "Weekly",
            kind = SubscriptionKind.REGULAR,
            price = BigDecimal.ONE,
            currencyCode = "USD",
            billingCycle = BillingCycle.WEEKLY,
            renewalType = RenewalType.MANUAL,
            startDate = today,
            cycleAnchorDate = today,
            nextPaymentDate = today,
            categoryId = "cat_other",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val dueDates = compute(listOf(weekly), settings = prefs(advance = null)).map { it.cycleDueDate }.distinct()
        assertThat(dueDates).isEqualTo(
            (0L..60L step 7).map { today.plusDays(it) },
        )
    }

    @Test
    fun `request codes are deterministic`() {
        val a = ReminderTriggers.requestCode("s1", 1_700_000_000_000L, ReminderKind.ADVANCE)
        val b = ReminderTriggers.requestCode("s1", 1_700_000_000_000L, ReminderKind.ADVANCE)
        val c = ReminderTriggers.requestCode("s1", 1_700_000_000_001L, ReminderKind.ADVANCE)
        val d = ReminderTriggers.requestCode("s2", 1_700_000_000_000L, ReminderKind.ADVANCE)
        assertThat(a).isEqualTo(b)
        assertThat(a).isNotEqualTo(c)
        assertThat(a).isNotEqualTo(d)
        assertThat(a).isGreaterThan(0)
    }

    @Test
    fun `occurrences respect the end date`() {
        val ending = sub(next = today.plusDays(7)).copy(endDate = today.plusDays(20))
        val dueDates = compute(listOf(ending), settings = prefs(advance = null)).map { it.cycleDueDate }.distinct()
        assertThat(dueDates.all { it <= today.plusDays(20) }).isTrue()
    }
}
