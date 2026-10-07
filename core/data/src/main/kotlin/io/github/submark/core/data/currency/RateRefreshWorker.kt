package io.github.submark.core.data.currency

import android.content.Context
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
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.DataResult
import java.util.concurrent.TimeUnit

/** Daily refresh of AUTO-mode exchange rates plus historical cache cleanup. */
@HiltWorker
class RateRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val currencies: CurrencyRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        currencies.cleanupHistorical()
        val result = currencies.refreshRates()
        return if (result is DataResult.Failure && result.error is DataError.Network) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "exchange_rate_refresh"

        /** Idempotent; call once at app start. Requires the app to use HiltWorkerFactory. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RateRefreshWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
