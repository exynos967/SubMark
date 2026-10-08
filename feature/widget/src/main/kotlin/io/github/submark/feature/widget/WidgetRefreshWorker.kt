package io.github.submark.feature.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.submark.feature.widget.spending.SpendingWidget
import io.github.submark.feature.widget.upcoming.UpcomingWidget
import java.util.concurrent.TimeUnit

/** Re-renders every placed widget instance (upcoming + spending). */
object WidgetRefresher {
    suspend fun updateAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        val upcoming = UpcomingWidget()
        manager.getGlanceIds(UpcomingWidget::class.java).forEach { upcoming.update(context, it) }
        val spending = SpendingWidget()
        manager.getGlanceIds(SpendingWidget::class.java).forEach { spending.update(context, it) }
    }
}

/** Periodic refresh (every 6 h) so "Today/Tomorrow" labels roll over with the date. */
@androidx.hilt.work.HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        WidgetRefresher.updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "widget_refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.NONE)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
