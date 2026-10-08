package io.github.submark.feature.backup.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.submark.core.database.dao.BackupDao

/**
 * Runs one profile's backup. Periodic executions skip the run when the profile is not due
 * (timing is best-effort); "Back up now" one-shot work forces the run.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: BackupDao,
    private val runner: BackupRunner,
    private val scheduler: BackupScheduler,
    private val notifier: BackupNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val profileId = inputData.getString(KEY_PROFILE_ID) ?: return Result.failure()
        val profile = dao.getProfile(profileId) ?: return Result.failure()

        // Periodic work is only a wake-up hint; run only when actually due or on a manual "run now".
        val forced = inputData.getBoolean(KEY_FORCE, false)
        if (!forced && !scheduler.isDue(profile)) return Result.success()

        val outcome = runner.resumeIfInterrupted(profile) ?: runner.runBackup(profile)
        return when (outcome) {
            is BackupRunner.Outcome.Success -> Result.success()
            is BackupRunner.Outcome.Failed -> {
                notifier.notifyFailure(profile, outcome.error.toString())
                if (runAttemptCount < MAX_RETRIES && isTransient(outcome.error)) Result.retry() else Result.success()
            }
            BackupRunner.Outcome.Busy -> Result.retry()
            BackupRunner.Outcome.Canceled -> Result.success()
        }
    }

    private fun isTransient(error: BackupRunner.BackupFailure): Boolean = when (error) {
        is BackupRunner.BackupFailure.Dav -> when (error.error) {
            is WebDavError.Io, WebDavError.Canceled -> true
            is WebDavError.Http -> error.error.code >= 500
            else -> false
        }
        is BackupRunner.BackupFailure.Io -> true
        else -> false
    }

    companion object {
        const val KEY_PROFILE_ID = "profile_id"
        const val KEY_FORCE = "force"
        private const val MAX_RETRIES = 3
    }
}
