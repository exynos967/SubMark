package io.github.submark.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import io.github.submark.core.model.Subscription
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface SubscriptionDao : BaseDao<Subscription> {
    @Query("SELECT * FROM subscriptions ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Subscription>>

    @Query("SELECT * FROM subscriptions")
    suspend fun getAll(): List<Subscription>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    fun observe(id: String): Flow<Subscription?>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun get(id: String): Subscription?

    @Query("SELECT * FROM subscriptions WHERE parentId = :parentId ORDER BY name COLLATE NOCASE")
    fun observeChildren(parentId: String): Flow<List<Subscription>>

    @Query("SELECT * FROM subscriptions WHERE parentId = :parentId")
    suspend fun getChildren(parentId: String): List<Subscription>

    /** Active recurring subscriptions with a due date on or before [date]; drives auto-mark and expiry. */
    @Query(
        """SELECT * FROM subscriptions WHERE status = 'ACTIVE' AND kind IN ('REGULAR','STORED_VALUE')
           AND nextPaymentDate IS NOT NULL AND nextPaymentDate <= :date""",
    )
    suspend fun getActiveDueOnOrBefore(date: LocalDate): List<Subscription>

    @Query("SELECT * FROM subscriptions WHERE status = 'ACTIVE' AND endDate IS NOT NULL AND endDate < :today")
    suspend fun getActiveExpired(today: LocalDate): List<Subscription>

    @Query("SELECT * FROM subscriptions WHERE appStoreId = :appStoreId AND id != :excludeId LIMIT 1")
    suspend fun findByAppStoreId(appStoreId: String, excludeId: String): Subscription?

    @Query("UPDATE subscriptions SET categoryId = :toId WHERE categoryId = :fromId")
    suspend fun moveCategory(fromId: String, toId: String)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM subscriptions")
    suspend fun deleteAll()
}
