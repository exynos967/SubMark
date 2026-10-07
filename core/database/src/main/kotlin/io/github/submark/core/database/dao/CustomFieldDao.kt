package io.github.submark.core.database.dao

import androidx.room.Dao
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
}
