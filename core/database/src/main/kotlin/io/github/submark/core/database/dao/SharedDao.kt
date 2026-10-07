package io.github.submark.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import io.github.submark.core.model.SharedConfig
import io.github.submark.core.model.SharedMember
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedDao {
    @Query("SELECT * FROM shared_configs WHERE subscriptionId = :subscriptionId")
    fun observeConfig(subscriptionId: String): Flow<SharedConfig?>

    @Query("SELECT * FROM shared_configs")
    fun observeAllConfigs(): Flow<List<SharedConfig>>

    @Query("SELECT * FROM shared_configs")
    suspend fun getAllConfigs(): List<SharedConfig>

    @Upsert suspend fun upsertConfig(config: SharedConfig)
    @Upsert suspend fun upsertConfigs(items: List<SharedConfig>)

    @Query("DELETE FROM shared_configs WHERE subscriptionId = :subscriptionId")
    suspend fun deleteConfig(subscriptionId: String)

    @Query("SELECT * FROM shared_members WHERE subscriptionId = :subscriptionId ORDER BY isCreator DESC, sortOrder")
    fun observeMembers(subscriptionId: String): Flow<List<SharedMember>>

    @Query("SELECT * FROM shared_members")
    fun observeAllMembers(): Flow<List<SharedMember>>

    @Query("SELECT * FROM shared_members")
    suspend fun getAllMembers(): List<SharedMember>

    @Query("SELECT * FROM shared_members WHERE subscriptionId = :subscriptionId")
    suspend fun getMembers(subscriptionId: String): List<SharedMember>

    @Upsert suspend fun upsertMember(member: SharedMember)
    @Upsert suspend fun upsertMembers(items: List<SharedMember>)
    @Delete suspend fun deleteMember(member: SharedMember)

    @Query("DELETE FROM shared_members WHERE subscriptionId = :subscriptionId")
    suspend fun deleteMembers(subscriptionId: String)

    @Query("SELECT * FROM shared_configs WHERE subscriptionId = :subscriptionId")
    suspend fun getConfig(subscriptionId: String): SharedConfig?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreConfigs(items: List<SharedConfig>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreMembers(items: List<SharedMember>): List<Long>

    @Query("DELETE FROM shared_configs")
    suspend fun deleteAllConfigs()

    @Query("DELETE FROM shared_members")
    suspend fun deleteAllMembers()
}
