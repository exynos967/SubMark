package io.github.submark.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import io.github.submark.core.model.CustomReminder
import io.github.submark.core.model.SubscriptionPhoto
import kotlinx.coroutines.flow.Flow

/** Photos and custom reminders hanging off a subscription. */
@Dao
interface SubscriptionExtrasDao {
    @Query("SELECT * FROM subscription_photos WHERE subscriptionId = :subscriptionId ORDER BY sortOrder")
    fun observePhotos(subscriptionId: String): Flow<List<SubscriptionPhoto>>

    @Query("SELECT * FROM subscription_photos")
    suspend fun getAllPhotos(): List<SubscriptionPhoto>

    @Upsert suspend fun upsertPhotos(items: List<SubscriptionPhoto>)
    @Delete suspend fun deletePhoto(item: SubscriptionPhoto)

    @Query("SELECT * FROM custom_reminders WHERE subscriptionId = :subscriptionId ORDER BY daysBefore DESC, time")
    fun observeReminders(subscriptionId: String): Flow<List<CustomReminder>>

    @Query("SELECT * FROM custom_reminders")
    fun observeAllReminders(): Flow<List<CustomReminder>>

    @Query("SELECT * FROM custom_reminders")
    suspend fun getAllReminders(): List<CustomReminder>

    @Upsert suspend fun upsertReminders(items: List<CustomReminder>)

    @Query("DELETE FROM custom_reminders WHERE subscriptionId = :subscriptionId")
    suspend fun clearReminders(subscriptionId: String)
}
