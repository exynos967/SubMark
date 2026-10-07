package io.github.submark.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.submark.core.model.BackupJob
import io.github.submark.core.model.BackupProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupDao : BaseDao<BackupProfile> {
    @Query("SELECT * FROM backup_profiles ORDER BY createdAt")
    fun observeProfiles(): Flow<List<BackupProfile>>

    @Query("SELECT * FROM backup_profiles")
    suspend fun getProfiles(): List<BackupProfile>

    @Query("SELECT * FROM backup_profiles WHERE id = :id")
    suspend fun getProfile(id: String): BackupProfile?

    @Query("SELECT * FROM backup_jobs WHERE profileId = :profileId ORDER BY startedAt DESC LIMIT :limit")
    fun observeJobs(profileId: String, limit: Int = 20): Flow<List<BackupJob>>

    @Query("SELECT * FROM backup_jobs WHERE phase NOT IN ('COMPLETED','FAILED','CANCELED')")
    suspend fun getUnfinishedJobs(): List<BackupJob>

    @Upsert suspend fun upsertJob(job: BackupJob)
}
