package io.github.submark.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.submark.core.model.Currency
import io.github.submark.core.model.HistoricalRate
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface CurrencyDao : BaseDao<Currency> {
    @Query("SELECT * FROM currencies ORDER BY isEnabled DESC, sortOrder, code")
    fun observeAll(): Flow<List<Currency>>

    @Query("SELECT * FROM currencies")
    suspend fun getAll(): List<Currency>

    @Query("SELECT * FROM currencies WHERE code = :code")
    suspend fun get(code: String): Currency?

    @Query("SELECT COUNT(*) FROM currencies")
    suspend fun count(): Int

    @Query("DELETE FROM currencies WHERE code = :code")
    suspend fun deleteByCode(code: String)

    @Query("SELECT * FROM historical_rates WHERE date = :date AND code = :code")
    suspend fun getHistorical(date: LocalDate, code: String): HistoricalRate?

    @Query("SELECT * FROM historical_rates WHERE date BETWEEN :from AND :to")
    suspend fun getHistoricalRange(from: LocalDate, to: LocalDate): List<HistoricalRate>

    @Upsert suspend fun upsertHistorical(items: List<HistoricalRate>)

    @Query("SELECT COUNT(*) FROM historical_rates")
    fun observeHistoricalCount(): Flow<Int>

    @Query("SELECT MIN(date) FROM historical_rates")
    suspend fun oldestHistoricalDate(): LocalDate?

    @Query("SELECT MAX(date) FROM historical_rates")
    suspend fun newestHistoricalDate(): LocalDate?

    @Query("DELETE FROM historical_rates WHERE fetchedAt < :before")
    suspend fun deleteHistoricalFetchedBefore(before: Instant)

    @Query("DELETE FROM historical_rates")
    suspend fun clearHistorical()

    @Query("DELETE FROM currencies")
    suspend fun deleteAll()
}
