package io.github.submark.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import io.github.submark.core.model.PaymentMethod
import io.github.submark.core.model.PaymentRecord
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface PaymentDao : BaseDao<PaymentRecord> {
    @Query("SELECT * FROM payment_records ORDER BY paymentDate DESC, createdAt DESC")
    fun observeAll(): Flow<List<PaymentRecord>>

    @Query("SELECT * FROM payment_records")
    suspend fun getAll(): List<PaymentRecord>

    @Query("SELECT * FROM payment_records WHERE subscriptionId = :subscriptionId ORDER BY paymentDate DESC, createdAt DESC")
    fun observeForSubscription(subscriptionId: String): Flow<List<PaymentRecord>>

    @Query("SELECT * FROM payment_records WHERE subscriptionId = :subscriptionId ORDER BY paymentDate DESC, createdAt DESC")
    suspend fun getForSubscription(subscriptionId: String): List<PaymentRecord>

    @Query("SELECT * FROM payment_records WHERE paymentDate BETWEEN :from AND :to ORDER BY paymentDate DESC")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<PaymentRecord>>

    @Query("SELECT * FROM payment_records WHERE id = :id")
    suspend fun get(id: String): PaymentRecord?

    @Query("SELECT * FROM payment_records WHERE id = :id")
    fun observe(id: String): Flow<PaymentRecord?>

    @Query("SELECT COUNT(*) FROM payment_records WHERE subscriptionId = :subscriptionId")
    suspend fun countForSubscription(subscriptionId: String): Int

    @Query("SELECT COUNT(*) FROM payment_records WHERE currencyCode = :code")
    suspend fun countWithCurrency(code: String): Int

    @Query("DELETE FROM payment_records WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM payment_records")
    suspend fun deleteAll()
}

@Dao
interface PaymentMethodDao : BaseDao<PaymentMethod> {
    @Query("SELECT * FROM payment_methods ORDER BY isSystem DESC, sortOrder")
    fun observeAll(): Flow<List<PaymentMethod>>

    @Query("SELECT * FROM payment_methods")
    suspend fun getAll(): List<PaymentMethod>

    @Query("SELECT COUNT(*) FROM payment_methods")
    suspend fun count(): Int

    @Query("DELETE FROM payment_methods WHERE isSystem = 0")
    suspend fun deleteCustom()

    @Query("SELECT * FROM payment_methods WHERE id = :id")
    suspend fun get(id: String): PaymentMethod?

    @Query("DELETE FROM payment_methods WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM payment_methods")
    suspend fun deleteAll()
}
