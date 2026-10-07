package io.github.submark.core.database.dao

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Upsert

interface BaseDao<T> {
    @Upsert suspend fun upsert(item: T)
    @Upsert suspend fun upsertAll(items: List<T>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIgnoreAll(items: List<T>): List<Long>
    @Delete suspend fun delete(item: T)
}
