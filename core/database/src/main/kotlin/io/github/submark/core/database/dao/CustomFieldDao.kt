package io.github.submark.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import io.github.submark.core.model.CustomFieldDefinition
import io.github.submark.core.model.CustomFieldOption
import io.github.submark.core.model.CustomFieldValue
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomFieldDao : BaseDao<CustomFieldDefinition> {
    @Query("SELECT * FROM custom_field_definitions ORDER BY sortOrder")
    fun observeDefinitions(): Flow<List<CustomFieldDefinition>>

    @Query("SELECT * FROM custom_field_definitions")
    suspend fun getDefinitions(): List<CustomFieldDefinition>

    @Query("SELECT * FROM custom_field_options ORDER BY sortOrder")
    fun observeOptions(): Flow<List<CustomFieldOption>>

    @Query("SELECT * FROM custom_field_options")
    suspend fun getOptions(): List<CustomFieldOption>

    @Upsert suspend fun upsertOptions(items: List<CustomFieldOption>)

    @Query("DELETE FROM custom_field_options WHERE fieldId = :fieldId")
    suspend fun clearOptions(fieldId: String)

    @Query("SELECT * FROM custom_field_values WHERE subscriptionId = :subscriptionId")
    fun observeValues(subscriptionId: String): Flow<List<CustomFieldValue>>

    @Query("SELECT * FROM custom_field_values")
    suspend fun getAllValues(): List<CustomFieldValue>

    @Query("SELECT * FROM custom_field_values WHERE fieldId = :fieldId")
    suspend fun getValuesForField(fieldId: String): List<CustomFieldValue>

    @Upsert suspend fun upsertValues(items: List<CustomFieldValue>)

    @Query("DELETE FROM custom_field_values WHERE subscriptionId = :subscriptionId")
    suspend fun clearValues(subscriptionId: String)

    @Query("SELECT * FROM custom_field_values WHERE subscriptionId = :subscriptionId")
    suspend fun getValues(subscriptionId: String): List<CustomFieldValue>

    @Query("SELECT * FROM custom_field_values")
    fun observeAllValues(): Flow<List<CustomFieldValue>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreOptions(items: List<CustomFieldOption>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreValues(items: List<CustomFieldValue>): List<Long>

    @Query("DELETE FROM custom_field_definitions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM custom_field_definitions")
    suspend fun deleteAll()

    @Query("DELETE FROM custom_field_options")
    suspend fun deleteAllOptions()

    @Query("DELETE FROM custom_field_values")
    suspend fun deleteAllValues()
}
