package io.github.submark.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import io.github.submark.core.model.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao : BaseDao<Category> {
    @Query("SELECT * FROM categories ORDER BY sortOrder")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY sortOrder")
    suspend fun getAll(): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: String): Category?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("DELETE FROM categories WHERE systemKey IS NULL")
    suspend fun deleteCustom()

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}
