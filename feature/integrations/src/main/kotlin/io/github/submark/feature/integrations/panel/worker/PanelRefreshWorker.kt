package io.github.submark.feature.integrations.panel.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.database.dao.ApiBudgetDao
import io.github.submark.core.database.dao.ServiceConnectionDao
import io.github.submark.feature.integrations.R
import io.github.submark.feature.integrations.panel.data.BudgetComputation
import io.github.submark.feature.integrations.panel.data.BudgetStatus
import io.github.submark.feature.integrations.panel.data.PanelFetchException
import io.github.submark.feature.integrations.panel.data.PanelErrorReason
import io.github.submark.feature.integrations.panel.data.PanelRepository
import io.github.submark.feature.integrations.panel.data.SnapshotCodec
import java.util.concurrent.TimeUnit

/**
 * Periodic refresh for the service panel and API budget cards.
 *
 * Runs every 30 minutes (WorkManager's floor is 15); each item additionally gates on its own
 * `refreshIntervalMinutes` / enabled flag, so effectively an item refreshes at its configured
 * interval (rounded up to the 30-minute tick). Budget alerts are posted once per threshold
 * crossing per calendar month.
 */
@HiltWorker
class PanelRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val apiBudgetDao: ApiBudgetDao,
    private val serviceDao: ServiceConnectionDao,
    private val repository: PanelRepository,
    private val time: TimeProvider,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = time.now()
        refreshServices(now.epochSecond)
        refreshBudgets(now.epochSecond)
        return Result.success()
    }

    private suspend fun refreshServices(nowEpoch: Long) {
        serviceDao.getAll()
            .filter { it.enabled && it.autoRefresh && it.isDue(nowEpoch, it.lastRefreshAt?.epochSecond, it.refreshIntervalMinutes) }
            .forEach { repository.refreshService(it) }
    }

    private suspend fun refreshBudgets(nowEpoch: Long) {
        apiBudgetDao.getAll()
            .filter { it.enabled }
            .forEach { config ->
                val result = repository.refreshBudget(config)
                result.onSuccess { snapshot -> maybeNotify(config.id, config.name, config.alertThreshold, snapshot, config.monthlyBudget, config.dailyBudget) }
            }
    }
    /** Posts the alert only on a fresh crossing (normal → warning/over) within the current month. */
    private fun maybeNotify(
        id: String,
        name: String,
        thresholdPercent: Int,
        snapshot: io.github.submark.feature.integrations.panel.data.BudgetSnapshot,
        monthlyBudget: java.math.BigDecimal?,
        dailyBudget: java.math.BigDecimal?,
    ) {
        val computation = BudgetComputation.compute(snapshot, monthlyBudget, dailyBudget, thresholdPercent)
        if (computation.status == BudgetStatus.NORMAL) return
        val monthKey = time.today().toString().take(7) // yyyy-MM
        val markerKey = "budget_alert:${id}:$monthKey"
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastStatus = prefs.getString(markerKey, null)
        if (lastStatus == computation.status.name || (computation.status == BudgetStatus.WARNING && lastStatus == BudgetStatus.OVER.name)) return
        prefs.edit().putString(markerKey, computation.status.name).apply()

        ensureChannel()
        if (Build.VERSION.SDK_INT >= 33 &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val title = applicationContext.getString(R.string.panel_alert_title, name)
        val text = if (computation.status == BudgetStatus.OVER) {
            applicationContext.getString(R.string.panel_alert_over)
        } else {
            applicationContext.getString(R.string.panel_alert_warning, thresholdPercent)
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(id.hashCode(), notification)
    }

    private fun ensureChannel() {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.panel_alert_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    companion object {
        private const val WORK_NAME = "panel_refresh"
        private const val CHANNEL_ID = "api_budget"
        private const val PREFS_NAME = "panel_alerts"
        private const val PERIODIC_INTERVAL_MINUTES = 30L

        /** Idempotent; call once at app start. Requires HiltWorkerFactory in the app. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PanelRefreshWorker>(PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

private fun io.github.submark.core.model.ServiceConnection.isDue(nowEpoch: Long, lastEpoch: Long?, intervalMinutes: Int): Boolean {
    val intervalSeconds = intervalMinutes.coerceAtLeast(15) * 60L
    return lastEpoch == null || nowEpoch - lastEpoch >= intervalSeconds
}
