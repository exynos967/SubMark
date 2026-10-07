package io.github.submark.core.database

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Money is stored as TEXT to keep exact decimals, so money aggregation happens in Kotlin, not SQL.
 * Dates are epoch days and instants epoch millis so range queries and sorting work in SQL.
 */
internal class Converters {
    @TypeConverter fun bigDecimalToString(value: BigDecimal?): String? = value?.toPlainString()
    @TypeConverter fun stringToBigDecimal(value: String?): BigDecimal? = value?.let(::BigDecimal)

    @TypeConverter fun localDateToLong(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun longToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter fun localTimeToInt(value: LocalTime?): Int? = value?.toSecondOfDay()
    @TypeConverter fun intToLocalTime(value: Int?): LocalTime? = value?.let { LocalTime.ofSecondOfDay(it.toLong()) }

    @TypeConverter fun stringListToJson(value: List<String>?): String? = value?.let { Json.encodeToString(stringList, it) }
    @TypeConverter fun jsonToStringList(value: String?): List<String>? = value?.let { Json.decodeFromString(stringList, it) }

    private companion object {
        val stringList = ListSerializer(String.serializer())
    }
}
