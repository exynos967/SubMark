package io.github.submark.feature.notifications.data

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.submark.core.data.service.SubscriptionService
import io.github.submark.core.model.PaymentSource
import io.github.submark.feature.notifications.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

/**
 * Posts reminder notifications scheduled by [ReminderAlarmScheduler], marks a subscription paid
 * from the notification action, and serves as the boot/time-change reschedule entry.
 */
@AndroidEntryPoint
class PaymentReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var subscriptionService: SubscriptionService
    @Inject lateinit var rebuildService: ReminderRebuildService

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SHOW -> postReminder(context, intent)
            ACTION_MARK_PAID -> markPaid(context, intent)
            ACTION_TEST -> postTest(context)
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> goAsync { rebuildService.rebuildAll() }
        }
    }

    private fun goAsync(block: suspend () -> Unit) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                block()
            } finally {
                pending.finish()
            }
        }
    }

    private fun launcherIntent(context: Context, subscriptionId: String): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent().setPackage(context.packageName)
        launch.action = Intent.ACTION_VIEW
        launch.putExtra(EXTRA_DEEP_LINK_SUBSCRIPTION_ID, subscriptionId)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            subscriptionId.hashCode(),
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun postReminder(context: Context, intent: Intent) {
        NotificationChannels.create(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val subscriptionId = intent.getStringExtra(EXTRA_SUBSCRIPTION_ID).orEmpty()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val kind = intent.getStringExtra(EXTRA_KIND)?.let { runCatching { ReminderKind.valueOf(it) }.getOrNull() }
            ?: ReminderKind.ADVANCE
        val days = intent.getIntExtra(EXTRA_DAYS, 0)
        val amount = intent.getStringExtra(EXTRA_AMOUNT).orEmpty()
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, subscriptionId.hashCode())

        val (titleRes, body) = titleAndBody(context, kind, name, amount, days)
        val markPaid = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, PaymentReminderReceiver::class.java).apply {
                action = ACTION_MARK_PAID
                putExtra(EXTRA_SUBSCRIPTION_ID, subscriptionId)
                putExtra(EXTRA_NAME, name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.PAYMENT_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(titleRes))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(launcherIntent(context, subscriptionId))
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.notifications_action_mark_paid), markPaid)
            .build()
        manager.notify(requestCode, notification)
    }

    private fun titleAndBody(context: Context, kind: ReminderKind, name: String, amount: String, days: Int): Pair<Int, String> =
        when (kind) {
            ReminderKind.ADVANCE, ReminderKind.CUSTOM -> R.string.notifications_reminder_title to when {
                days <= 0 -> context.getString(R.string.notifications_due_today, name, amount)
                days == 1 -> context.getString(R.string.notifications_due_tomorrow, name, amount)
                else -> context.resources.getQuantityString(R.plurals.notifications_due_in_days, days, name, days, amount)
            }
            ReminderKind.PAYDAY_FIRST -> R.string.notifications_payday_first_title to
                context.getString(R.string.notifications_payday_first_body, name, amount)
            ReminderKind.PAYDAY_SECOND -> R.string.notifications_payday_second_title to
                context.getString(R.string.notifications_payday_second_body, name, amount)
            ReminderKind.PAYDAY_THIRD -> R.string.notifications_payday_third_title to
                context.getString(R.string.notifications_payday_third_body, name, amount)
        }

    private fun markPaid(context: Context, intent: Intent) {
        val subscriptionId = intent.getStringExtra(EXTRA_SUBSCRIPTION_ID).orEmpty()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        goAsync {
            val result = subscriptionService.markPaid(subscriptionId, newCycle = false, source = PaymentSource.USER_MANUAL)
            val manager = NotificationManagerCompat.from(context)
            if (manager.areNotificationsEnabled()) {
                NotificationChannels.create(context)
                val text = if (result.isSuccess) {
                    context.getString(R.string.notifications_mark_paid_done, name)
                } else {
                    context.getString(R.string.notifications_mark_paid_failed, name)
                }
                val notification: Notification = NotificationCompat.Builder(context, NotificationChannels.PAYMENT_REMINDERS)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(context.getString(R.string.notifications_reminder_title))
                    .setContentText(text)
                    .setAutoCancel(true)
                    .build()
                manager.notify((subscriptionId + "|paid").hashCode() and 0x7FFFFFFF, notification)
            }
            // The change listener rebuilds reminders; markPaid notifies via the change bus.
        }
    }

    private fun postTest(context: Context) {
        NotificationChannels.create(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val time = LocalTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM))
        val notification = NotificationCompat.Builder(context, NotificationChannels.PAYMENT_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.notifications_test_title))
            .setContentText(context.getString(R.string.notifications_test_body, time))
            .setAutoCancel(true)
            .build()
        manager.notify(ReminderAlarmScheduler.TEST_REQUEST_CODE, notification)
    }

    companion object {
        const val ACTION_SHOW = "io.github.submark.notifications.SHOW_REMINDER"
        const val ACTION_MARK_PAID = "io.github.submark.notifications.MARK_PAID"
        const val ACTION_TEST = "io.github.submark.notifications.TEST"

        const val EXTRA_SUBSCRIPTION_ID = "submark.notification.subscriptionId"
        const val EXTRA_NAME = "submark.notification.name"
        const val EXTRA_KIND = "submark.notification.kind"
        const val EXTRA_DAYS = "submark.notification.daysBefore"
        const val EXTRA_AMOUNT = "submark.notification.amount"
        const val EXTRA_REQUEST_CODE = "submark.notification.requestCode"

        /** Extra the launcher activity reads to route to a subscription detail. */
        const val EXTRA_DEEP_LINK_SUBSCRIPTION_ID = "submark.subscriptionId"
    }
}
