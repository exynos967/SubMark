package io.github.submark.feature.backup.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.model.BackupFrequency
import io.github.submark.core.model.BackupProfile
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkManager scheduling for backup profiles. One unique periodic work per profile id
 * ([periodicName]) plus one-shot expedited runs ([runNowName]).
 *
 * Schedule semantics: the periodic worker re-checks eligibility on every run — the wall-clock
 * period is a wake-up hint, while [BackupProfile.lastSuccessAt] + [BackupFrequency] decides
 * whether the backup actually executes ("best effort, not exact").
 */
@Singleton
class BackupScheduler @Inject constructor(private val time: TimeProvider) {

    fun intervalFor(profile: BackupProfile): Duration = when (profile.frequency) {
        BackupFrequency.MANUAL -> Duration.ZERO
        BackupFrequency.DAILY -> Duration.ofDays(1)
        BackupFrequency.WEEKLY -> Duration.ofDays(7)
        BackupFrequency.EVERY_30_DAYS -> Duration.ofDays(30)
    }

    /** nextEligibleAt = lastSuccessAt + frequency interval (null when never backed up). */
    fun nextEligibleAt(profile: BackupProfile): Instant? {
        val interval = intervalFor(profile)
        if (interval.isZero) return null
        return (profile.lastSuccessAt ?: profile.createdAt).plus(interval)
    }

    /** True when a non-manual, enabled profile's interval has elapsed. */
    fun isDue(profile: BackupProfile, now: Instant = time.now()): Boolean {
        if (!profile.enabled || profile.frequency == BackupFrequency.MANUAL) return false
        val next = nextEligibleAt(profile) ?: return false
        return !now.isBefore(next)
    }

    /** Enqueue (or refresh) unique periodic work for every non-manual enabled profile; cancel the rest. */
    fun reconcile(context: Context, profiles: List<BackupProfile>) {
        val wm = WorkManager.getInstance(context)
        profiles.forEach { profile ->
            if (profile.enabled && profile.frequency != BackupFrequency.MANUAL) {
                enqueuePeriodic(context, profile)
            } else {
                wm.cancelUniqueWork(periodicName(profile.id))
            }
        }
    }

    fun enqueuePeriodic(context: Context, profile: BackupProfile) {
        val interval = intervalFor(profile)
        if (interval.isZero) return
        val request = PeriodicWorkRequestBuilder<BackupWorker>(interval.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(constraints(profile))
            .setInputData(workDataOf(BackupWorker.KEY_PROFILE_ID to profile.id))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(periodicName(profile.id), ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Expedited one-time run ("Back up now"). */
    fun enqueueRunNow(context: Context, profileId: String) {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(BackupWorker.KEY_PROFILE_ID to profileId, BackupWorker.KEY_FORCE to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(runNowName(profileId), ExistingWorkPolicy.KEEP, request)
    }

    private fun constraints(profile: BackupProfile): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(if (profile.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

    companion object {
        fun periodicName(profileId: String) = "backup_periodic:$profileId"
        fun runNowName(profileId: String) = "backup_now:$profileId"
    }
}
