package io.github.submark.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import io.github.submark.core.model.StoredValueRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface StoredValueDao : BaseDao<StoredValueRecord> {
    @Query("SELECT * FROM stored_value_records ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<StoredValueRecord>>

    @Query("SELECT * FROM stored_value_records")
    suspend fun getAll(): List<StoredValueRecord>

    @Query("SELECT * FROM stored_value_records WHERE subscriptionId = :subscriptionId ORDER BY occurredAt DESC")
    fun observeForSubscription(subscriptionId: String): Flow<List<StoredValueRecord>>

    @Query("SELECT * FROM stored_value_records WHERE id = :id")
    suspend fun get(id: String): StoredValueRecord?
}
