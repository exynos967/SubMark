package io.github.submark.feature.notifications.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Schedules/cancels the exact alarms that fire [PaymentReminderReceiver]. */
@Singleton
class ReminderAlarmScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val alarmManager: AlarmManager? get() = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean = alarmManager?.canScheduleExactAlarms() ?: false

    /** Exact alarm when allowed, otherwise a 10-minute wakeup window. */
    fun schedule(trigger: ReminderTrigger, triggerAtMillis: Long) {
        val manager = alarmManager ?: return
        val pending = pendingIntent(trigger, showReminder = true)
        if (manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        } else {
            manager.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, WINDOW_MILLIS, pending)
        }
    }

    fun cancel(requestCode: Int) {
        alarmManager?.cancel(createPendingIntent(requestCode))
    }

    fun cancelAll(codes: Collection<Int>) = codes.forEach(::cancel)

    /** One-shot test notification [delayMillis] from now — same receiver, no persisted state. */
    fun scheduleTest(subscriptionId: String, name: String, delayMillis: Long) {
        val trigger = ReminderTrigger(
            subscriptionId = subscriptionId,
            subscriptionName = name,
            cycleDueDate = java.time.LocalDate.ofEpochDay(0),
            triggerAt = java.time.LocalDateTime.now(),
            kind = ReminderKind.CUSTOM,
            daysBefore = 0,
            amount = "",
            currencyCode = "",
            renewalType = io.github.submark.core.model.RenewalType.MANUAL,
            requestCode = TEST_REQUEST_CODE,
        )
        val manager = alarmManager ?: return
        val at = System.currentTimeMillis() + delayMillis
        val pending = pendingIntent(trigger, showReminder = false)
        if (manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            manager.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MILLIS, pending)
        }
    }

    private fun createPendingIntent(requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, PaymentReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun pendingIntent(trigger: ReminderTrigger, showReminder: Boolean): PendingIntent {
        val intent = Intent(context, PaymentReminderReceiver::class.java).apply {
            if (showReminder) {
                action = PaymentReminderReceiver.ACTION_SHOW
                putExtra(PaymentReminderReceiver.EXTRA_SUBSCRIPTION_ID, trigger.subscriptionId)
                putExtra(PaymentReminderReceiver.EXTRA_NAME, trigger.subscriptionName)
                putExtra(PaymentReminderReceiver.EXTRA_KIND, trigger.kind.name)
                putExtra(PaymentReminderReceiver.EXTRA_DAYS, trigger.daysBefore)
                putExtra(PaymentReminderReceiver.EXTRA_AMOUNT, trigger.amount)
                putExtra(PaymentReminderReceiver.EXTRA_REQUEST_CODE, trigger.requestCode)
            } else {
                action = PaymentReminderReceiver.ACTION_TEST
            }
        }
        return PendingIntent.getBroadcast(
            context,
            trigger.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val TEST_REQUEST_CODE = 999_000_001
        const val WINDOW_MILLIS = 10 * 60 * 1000L
        const val TEST_DELAY_MILLIS = 5_000L
    }
}
