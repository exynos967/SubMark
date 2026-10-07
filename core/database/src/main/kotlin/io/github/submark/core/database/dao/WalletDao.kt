package io.github.submark.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import io.github.submark.core.model.Wallet
import io.github.submark.core.model.WalletTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao : BaseDao<Wallet> {
    @Query("SELECT * FROM wallets WHERE deletedAt IS NULL ORDER BY isActive DESC, sortOrder")
    fun observeVisible(): Flow<List<Wallet>>

    @Query("SELECT * FROM wallets")
    fun observeAllIncludingDeleted(): Flow<List<Wallet>>

    @Query("SELECT * FROM wallets")
    suspend fun getAll(): List<Wallet>

    @Query("SELECT * FROM wallets WHERE id = :id")
    suspend fun get(id: String): Wallet?

    @Query("SELECT * FROM wallets WHERE id = :id")
    fun observe(id: String): Flow<Wallet?>

    @Query("SELECT * FROM wallet_transactions ORDER BY occurredAt DESC")
    fun observeAllTransactions(): Flow<List<WalletTransaction>>

    @Query("SELECT * FROM wallet_transactions")
    suspend fun getAllTransactions(): List<WalletTransaction>

    @Query("SELECT * FROM wallet_transactions WHERE walletId = :walletId ORDER BY occurredAt DESC")
    fun observeTransactions(walletId: String): Flow<List<WalletTransaction>>

    @Query("SELECT * FROM wallet_transactions WHERE id = :id")
    suspend fun getTransaction(id: String): WalletTransaction?

    @Query("SELECT COUNT(*) FROM wallet_transactions WHERE walletId = :walletId")
    suspend fun countTransactions(walletId: String): Int

    @Upsert suspend fun upsertTransaction(txn: WalletTransaction)
    @Upsert suspend fun upsertTransactions(items: List<WalletTransaction>)

    @Query("UPDATE subscriptions SET walletId = NULL WHERE walletId = :walletId")
    suspend fun unlinkSubscriptions(walletId: String)

    @Query("SELECT COUNT(*) FROM wallets WHERE currencyCode = :code")
    suspend fun countWithCurrency(code: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreTransactions(items: List<WalletTransaction>): List<Long>

    /** Keeps a deleted subscription's wallet history while dropping the dangling links. */
    @Query("UPDATE wallet_transactions SET subscriptionId = NULL, paymentRecordId = NULL, storedValueRecordId = NULL WHERE subscriptionId = :subscriptionId")
    suspend fun detachSubscription(subscriptionId: String)

    @Query("DELETE FROM wallet_transactions")
    suspend fun deleteAllTransactions()

    @Query("DELETE FROM wallets")
    suspend fun deleteAll()
}
