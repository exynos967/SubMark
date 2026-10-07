package io.github.submark.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import io.github.submark.core.model.ApiBudgetConfig
import io.github.submark.core.model.IconRepository
import io.github.submark.core.model.PopularRepository
import io.github.submark.core.model.PriceMonitor
import io.github.submark.core.model.PriceRecord
import io.github.submark.core.model.ServiceConnection
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceMonitorDao : BaseDao<PriceMonitor> {
    @Query("SELECT * FROM price_monitors")
    fun observeAll(): Flow<List<PriceMonitor>>

    @Query("SELECT * FROM price_monitors")
    suspend fun getAll(): List<PriceMonitor>

    @Query("SELECT * FROM price_monitors WHERE subscriptionId = :subscriptionId")
    fun observe(subscriptionId: String): Flow<PriceMonitor?>

    @Query("SELECT * FROM price_records WHERE subscriptionId = :subscriptionId ORDER BY checkedAt DESC")
    fun observeRecords(subscriptionId: String): Flow<List<PriceRecord>>

    @Query("SELECT * FROM price_records")
    suspend fun getAllRecords(): List<PriceRecord>

    @Query("SELECT * FROM price_records WHERE subscriptionId = :subscriptionId AND region = :region ORDER BY checkedAt DESC LIMIT 1")
    suspend fun latestRecord(subscriptionId: String, region: String): PriceRecord?

    @Upsert suspend fun upsertRecords(items: List<PriceRecord>)

    @Query("SELECT COUNT(*) FROM price_records")
    fun observeRecordCount(): Flow<Int>

    @Query("DELETE FROM price_records")
    suspend fun clearRecords()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreRecords(items: List<PriceRecord>): List<Long>

    @Query("DELETE FROM price_monitors")
    suspend fun deleteAll()
}

@Dao
interface PopularRepositoryDao : BaseDao<PopularRepository> {
    @Query("SELECT * FROM popular_repositories ORDER BY sortOrder")
    fun observeAll(): Flow<List<PopularRepository>>

    @Query("SELECT * FROM popular_repositories")
    suspend fun getAll(): List<PopularRepository>

    @Query("DELETE FROM popular_repositories")
    suspend fun deleteAll()
}

@Dao
interface IconRepositoryDao : BaseDao<IconRepository> {
    @Query("SELECT * FROM icon_repositories ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<IconRepository>>

    @Query("SELECT * FROM icon_repositories")
    suspend fun getAll(): List<IconRepository>

    @Query("DELETE FROM icon_repositories")
    suspend fun deleteAll()
}

@Dao
interface ApiBudgetDao : BaseDao<ApiBudgetConfig> {
    @Query("SELECT * FROM api_budget_configs ORDER BY createdAt")
    fun observeAll(): Flow<List<ApiBudgetConfig>>

    @Query("SELECT * FROM api_budget_configs")
    suspend fun getAll(): List<ApiBudgetConfig>

    @Query("SELECT * FROM api_budget_configs WHERE id = :id")
    suspend fun get(id: String): ApiBudgetConfig?

    @Query("DELETE FROM api_budget_configs")
    suspend fun deleteAll()
}

@Dao
interface ServiceConnectionDao : BaseDao<ServiceConnection> {
    @Query("SELECT * FROM service_connections ORDER BY sortOrder, createdAt")
    fun observeAll(): Flow<List<ServiceConnection>>

    @Query("SELECT * FROM service_connections")
    suspend fun getAll(): List<ServiceConnection>

    @Query("SELECT * FROM service_connections WHERE id = :id")
    suspend fun get(id: String): ServiceConnection?

    @Query("DELETE FROM service_connections")
    suspend fun deleteAll()
}
