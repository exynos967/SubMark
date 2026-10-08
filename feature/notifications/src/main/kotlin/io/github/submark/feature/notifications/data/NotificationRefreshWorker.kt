package io.github.submark.feature.notifications.data

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.submark.core.data.service.ProcessDueSummary
import io.github.submark.core.data.service.SubscriptionService
import io.github.submark.feature.notifications.R
import java.util.concurrent.TimeUnit

/**
 * Daily refresh: rebuild the reminder schedule, run SubscriptionService.processDue(), and post
 * alerts from its summary (expired / trial ended / wallet auto-pay blocked) on the alerts channel.
 */
@HiltWorker
class NotificationRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val rebuildService: ReminderRebuildService,
    private val subscriptionService: SubscriptionService,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        rebuildService.rebuildAll()
        val summary = subscriptionService.processDue()
        // processDue may pause expired subscriptions / advance dates — schedule again.
        rebuildService.rebuildAll()
        postAlerts(summary)
        return Result.success()
    }

    private fun postAlerts(summary: ProcessDueSummary) {
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return
        NotificationChannels.create(applicationContext)
        if (summary.expiredIds.isNotEmpty()) {
            val body = if (summary.expiredNames.size == 1) {
                applicationContext.getString(R.string.notifications_expired_single, summary.expiredNames.first())
            } else {
                val names = summary.expiredNames.take(5).joinToString("、")
                applicationContext.resources.getQuantityString(
                    R.plurals.notifications_expired_multiple, summary.expiredIds.size, summary.expiredIds.size, names,
                )
            }
            post(manager, ALERT_EXPIRED, applicationContext.getString(R.string.notifications_expired_title), body)
        }
        if (summary.trialEndedIds.isNotEmpty()) {
            val body = applicationContext.resources.getQuantityString(
                R.plurals.notifications_trial_ended, summary.trialEndedIds.size, summary.trialEndedIds.size,
            )
            post(manager, ALERT_TRIAL, applicationContext.getString(R.string.notifications_trial_title), body)
        }
        summary.walletFailures.forEachIndexed { index, failure ->
            val body = applicationContext.getString(R.string.notifications_wallet_body, failure.subscriptionName)
            post(manager, ALERT_WALLET_BASE + index, applicationContext.getString(R.string.notifications_wallet_title), body)
        }
    }

    private fun post(manager: NotificationManagerCompat, id: Int, title: String, body: String) {
        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }

    companion object {
        const val WORK_NAME = "notification_refresh"
        private const val ALERT_EXPIRED = 900_000_100
        private const val ALERT_TRIAL = 900_000_101
        private const val ALERT_WALLET_BASE = 900_000_200

        /** Ensures the daily refresh is scheduled. Safe to call on every app start. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NotificationRefreshWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.NONE)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
