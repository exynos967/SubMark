package io.github.submark.feature.notifications.data

import io.github.submark.core.data.settings.NotificationSettings
import io.github.submark.core.domain.BillingCalculator
import io.github.submark.core.model.CustomReminder
import io.github.submark.core.model.RenewalType
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

enum class ReminderKind { ADVANCE, PAYDAY_FIRST, PAYDAY_SECOND, PAYDAY_THIRD, CUSTOM }

/** One concrete reminder moment. [requestCode] is deterministic so a rebuild is idempotent. */
data class ReminderTrigger(
    val subscriptionId: String,
    val subscriptionName: String,
    /** Due date of the occurrence this trigger reminds about. */
    val cycleDueDate: LocalDate,
    val triggerAt: LocalDateTime,
    val kind: ReminderKind,
    val daysBefore: Int,
    val amount: String,
    val currencyCode: String,
    val renewalType: RenewalType,
    val requestCode: Int,
)

object ReminderTriggers {

    const val WINDOW_DAYS = 60L

    /** Deterministic request code (notification id) for one trigger. Stable across rebuilds. */
    fun requestCode(subscriptionId: String, triggerMillis: Long, kind: ReminderKind): Int {
        val key = "$subscriptionId|${triggerMillis}|${kind.name}"
        return (key.hashCode() and 0x7FFFFFFF) % 1_000_000_000 + 1
    }

    /** A subscription gets payment reminders only when it is active and recurring with a due date. */
    fun isReminderEligible(sub: Subscription): Boolean =
        sub.status == SubscriptionStatus.ACTIVE &&
            (sub.kind == SubscriptionKind.REGULAR || sub.kind == SubscriptionKind.STORED_VALUE) &&
            sub.renewalType != RenewalType.TRIAL &&
            sub.nextPaymentDate != null

    /**
     * All trigger moments for [subs] per spec §2.1:
     * custom mode fires each (daysBefore, time) pair; default mode fires advance-day reminders in
     * each enabled slot plus payment-day reminders (AUTO = 1 at firstTime, MANUAL = 3).
     * Triggers already in the past are skipped; occurrences land within the rolling [WINDOW_DAYS] window.
     */
    fun compute(
        subs: List<Subscription>,
        remindersBySub: Map<String, List<CustomReminder>>,
        prefs: NotificationSettings,
        now: LocalDateTime,
        zone: ZoneId,
        formatAmount: (Subscription) -> String,
    ): List<ReminderTrigger> {
        val today = now.toLocalDate()
        val windowEnd = today.plusDays(WINDOW_DAYS)
        val out = ArrayList<ReminderTrigger>()
        for (sub in subs) {
            if (!isReminderEligible(sub)) continue
            val occurrences = occurrencesInWindow(sub, today, windowEnd)
            if (occurrences.isEmpty()) continue
            val custom = if (sub.customReminderEnabled) remindersBySub[sub.id].orEmpty() else emptyList()
            if (custom.isNotEmpty()) {
                for (reminder in custom) {
                    for (due in occurrences) {
                        addIfFuture(out, sub, due, due.minusDays(reminder.daysBefore.toLong()), reminder.time,
                            ReminderKind.CUSTOM, reminder.daysBefore, now, zone, formatAmount)
                    }
                }
            } else {
                val slots = listOf(
                    prefs.firstTime to prefs.firstSlotEnabled,
                    prefs.secondTime to prefs.secondSlotEnabled,
                    prefs.thirdTime to prefs.thirdSlotEnabled,
                )
                val advanceDays = prefs.advanceDays
                if (advanceDays != null && advanceDays > 0) {
                    val advanceDate = { due: LocalDate -> due.minusDays(advanceDays.toLong()) }
                    for (due in occurrences) {
                        for ((time, enabled) in slots) {
                            if (enabled) {
                                addIfFuture(out, sub, due, advanceDate(due), time,
                                    ReminderKind.ADVANCE, advanceDays, now, zone, formatAmount)
                            }
                        }
                    }
                }
                // Payment-day reminders are always on; slot switches never affect them.
                for (due in occurrences) {
                    when (sub.renewalType) {
                        RenewalType.AUTO ->
                            addIfFuture(out, sub, due, due, prefs.firstTime, ReminderKind.PAYDAY_FIRST, 0, now, zone, formatAmount)
                        RenewalType.MANUAL -> {
                            addIfFuture(out, sub, due, due, prefs.firstTime, ReminderKind.PAYDAY_FIRST, 0, now, zone, formatAmount)
                            addIfFuture(out, sub, due, due, prefs.secondTime, ReminderKind.PAYDAY_SECOND, 0, now, zone, formatAmount)
                            addIfFuture(out, sub, due, due, prefs.thirdTime, ReminderKind.PAYDAY_THIRD, 0, now, zone, formatAmount)
                        }
                        RenewalType.TRIAL -> Unit
                    }
                }
            }
        }
        return out.sortedBy { it.triggerAt }
    }

    /**
     * Due dates in [today]..[windowEnd]: the stored next date plus every later occurrence of the
     * schedule within the window. Capped by the end date.
     */
    fun occurrencesInWindow(sub: Subscription, today: LocalDate, windowEnd: LocalDate): List<LocalDate> {
        val next = sub.nextPaymentDate ?: return emptyList()
        val cycle = BillingCalculator.cycleLength(sub)
        if (cycle == null || sub.cycleAnchorDate == null) {
            return if (next <= windowEnd) listOf(next) else emptyList()
        }
        val anchor = requireNotNull(sub.cycleAnchorDate)
        val all = BillingCalculator.occurrencesBetween(anchor, cycle, today, windowEnd, sub.fixedPaymentDay)
        val folded = if (next in today..windowEnd && next !in all) listOf(next) else emptyList()
        return (folded + all).filter { sub.endDate == null || it <= sub.endDate }.distinct().sorted()
    }

    private fun addIfFuture(
        out: MutableList<ReminderTrigger>,
        sub: Subscription,
        due: LocalDate,
        date: LocalDate,
        time: LocalTime,
        kind: ReminderKind,
        daysBefore: Int,
        now: LocalDateTime,
        zone: ZoneId,
        formatAmount: (Subscription) -> String,
    ) {
        val at = LocalDateTime.of(date, time)
        if (at.isBefore(now)) return // missed = skipped, never sent late
        val millis = at.atZone(zone).toInstant().toEpochMilli()
        out += ReminderTrigger(
            subscriptionId = sub.id,
            subscriptionName = sub.name,
            cycleDueDate = due,
            triggerAt = at,
            kind = kind,
            daysBefore = daysBefore,
            amount = formatAmount(sub),
            currencyCode = sub.currencyCode,
            renewalType = sub.renewalType,
            requestCode = requestCode(sub.id, millis, kind),
        )
    }

    /** Millis since epoch of a trigger in [zone]. */
    fun triggerMillis(trigger: ReminderTrigger, zone: ZoneId): Long =
        trigger.triggerAt.atZone(zone).toInstant().toEpochMilli()

    fun triggerMillis(date: LocalDate, time: LocalTime, zone: ZoneId): Long =
        LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
}
