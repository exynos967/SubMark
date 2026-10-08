package io.github.submark.feature.integrations.data.price

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.database.dao.PriceMonitorDao
import io.github.submark.core.database.dao.SubscriptionDao
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.model.PriceCheckResult
import io.github.submark.core.model.PriceMonitor
import io.github.submark.core.model.PriceRecord
import io.github.submark.feature.integrations.R
import io.github.submark.feature.integrations.data.itunes.ItunesService
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

const val PRICE_DROP_CHANNEL_ID = "price_drop"

/** Periodic App Store price check for all enabled monitors; posts drop notifications. */
@HiltWorker
class PriceCheckWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val monitorDao: PriceMonitorDao,
    private val subscriptionDao: SubscriptionDao,
    private val itunes: ItunesService,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        ensureChannel(context)
        val current = settings.settings.first()
        if (!current.integrations.priceMonitorEnabled) return Result.success()
        val threshold = current.integrations.priceDropThresholdPercent
        val monitors = monitorDao.getAll().filter { it.enabled }
        for (monitor in monitors) {
            checkOne(monitor, threshold)
        }
        return Result.success()
    }

    /** Checks one monitor across all its regions; records only complete price data. */
    private suspend fun checkOne(monitor: PriceMonitor, thresholdPercent: Int) {
        val name = subscriptionDao.get(monitor.subscriptionId)?.name ?: monitor.appStoreId
        var succeeded = 0
        var failed = 0
        for (regionRaw in monitor.regions) {
            val region = regionRaw.uppercase()
            itunes.lookupPrice(monitor.appStoreId, region).fold(
                onSuccess = { price ->
                    succeeded++
                    val previous = monitorDao.latestRecord(monitor.subscriptionId, region)
                    monitorDao.upsertRecords(
                        listOf(
                            PriceRecord(
                                subscriptionId = monitor.subscriptionId,
                                region = region,
                                price = price.price,
                                currencyCode = price.currency,
                                formattedPrice = price.formattedPrice,
                                checkedAt = time.now(),
                            ),
                        ),
                    )
                    val drop = previous?.let { PriceCheck.dropPercent(it.price, price.price) }
                    if (drop != null && drop >= thresholdPercent && previous.price != price.price) {
                        notifyDrop(context, name, price.region, drop, price.price, price.currency, previous.price)
                    }
                },
                onFailure = { failed++ },
            )
        }
        monitorDao.upsert(
            monitor.copy(
                lastCheckAt = time.now(),
                lastCheckResult = when {
                    succeeded == 0 -> PriceCheckResult.FAILED
                    failed > 0 -> PriceCheckResult.PARTIAL
                    else -> PriceCheckResult.SUCCESS
                },
            ),
        )
    }

    companion object {
        const val WORK_NAME = "price_monitor_check"

        /** (Re)schedules the periodic check; cancels it when [enabled] is false. Idempotent. */
        fun schedule(context: Context, enabled: Boolean, intervalHours: Int) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<PriceCheckWorker>(intervalHours.coerceAtLeast(1).toLong(), TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(PRICE_DROP_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        PRICE_DROP_CHANNEL_ID,
                        context.getString(R.string.integrations_price_drop_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply { description = context.getString(R.string.integrations_price_drop_channel_desc) },
                )
            }
        }

        fun hasNotificationPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        internal fun notifyDrop(
            context: Context,
            appName: String,
            region: String,
            dropPercent: Double,
            price: BigDecimal,
            currency: String,
            was: BigDecimal,
        ) {
            if (!hasNotificationPermission(context)) return
            ensureChannel(context)
            val text = context.getString(
                R.string.integrations_price_drop_body,
                appName,
                region,
                dropPercent.toInt(),
                "$currency $price",
                "$currency $was",
            )
            val notification = NotificationCompat.Builder(context, PRICE_DROP_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(context.getString(R.string.integrations_price_drop_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build()
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(appName.hashCode() + region.hashCode(), notification)
        }
    }
}
