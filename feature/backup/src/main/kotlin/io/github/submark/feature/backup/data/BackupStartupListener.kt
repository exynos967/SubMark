package io.github.submark.feature.backup.data

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.submark.core.data.change.AppStartListener
import io.github.submark.core.database.dao.BackupDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * At every app launch: (re)enqueue periodic work for all non-manual profiles and run any profile
 * whose next eligible time has passed ("Backup due" — spec §2.4 scheduling).
 */
@Singleton
class BackupStartupListener @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: BackupDao,
    private val scheduler: BackupScheduler,
    private val runner: BackupRunner,
) : AppStartListener {
    override suspend fun onAppStart() {
        val profiles = dao.getProfiles()
        scheduler.reconcile(context, profiles)
        profiles.filter { scheduler.isDue(it) }.forEach { profile ->
            runCatching { runner.resumeIfInterrupted(profile) ?: runner.runBackup(profile) }
        }
        // Interrupted staged jobs for any profile get a chance to resume.
        dao.getUnfinishedJobs().forEach { job ->
            profiles.firstOrNull { it.id == job.profileId }?.let { runCatching { runner.resumeIfInterrupted(it) } }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupHiltModule {
    @Binds @IntoSet abstract fun appStartListener(impl: BackupStartupListener): AppStartListener
}
