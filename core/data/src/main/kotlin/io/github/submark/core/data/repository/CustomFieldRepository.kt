package io.github.submark.core.data.repository

import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.result.InvalidReason
import io.github.submark.core.data.result.TransactionRunner
import io.github.submark.core.data.result.abort
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.database.dao.CustomFieldDao
import io.github.submark.core.model.CustomFieldDefinition
import io.github.submark.core.model.CustomFieldOption
import io.github.submark.core.model.CustomFieldType
import io.github.submark.core.model.CustomFieldValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

data class CustomFieldWithOptions(val definition: CustomFieldDefinition, val options: List<CustomFieldOption>)

data class CustomFieldStats(val fieldId: String, val subscriptionsUsing: Int, val filledValues: Int)

/** Custom field definitions, dropdown options and per-subscription values. */
@Singleton
class CustomFieldRepository @Inject internal constructor(
    private val dao: CustomFieldDao,
    private val tx: TransactionRunner,
    private val time: TimeProvider,
) {
    fun observeFields(): Flow<List<CustomFieldWithOptions>> = combine(dao.observeDefinitions(), dao.observeOptions()) { defs, options ->
        val byField = options.groupBy { it.fieldId }
        defs.map { CustomFieldWithOptions(it, byField[it.id].orEmpty()) }
    }

    fun observeValues(subscriptionId: String): Flow<List<CustomFieldValue>> = dao.observeValues(subscriptionId)

    fun observeStats(): Flow<List<CustomFieldStats>> = combine(dao.observeDefinitions(), dao.observeAllValues()) { defs, values ->
        val byField = values.groupBy { it.fieldId }
        defs.map { def ->
            val list = byField[def.id].orEmpty()
            CustomFieldStats(def.id, list.map { it.subscriptionId }.distinct().size, list.count { it.value.isNotBlank() })
        }
    }

    /** Active fields shown for a subscription in [categoryId]: global ones plus that category's, in order. */
    suspend fun fieldsFor(categoryId: String?): List<CustomFieldDefinition> =
        dao.getDefinitions().filter { it.isActive && (it.categoryId == null || it.categoryId == categoryId) }.sortedBy { it.sortOrder }

    /** Creates or updates a field. DROPDOWN needs at least one option; options are replaced. */
    suspend fun saveField(definition: CustomFieldDefinition, options: List<CustomFieldOption> = emptyList()): DataResult<CustomFieldDefinition> = tx.run {
        if (definition.name.isBlank()) abort(InvalidReason.BLANK_NAME)
        if (definition.type == CustomFieldType.DROPDOWN && options.none { it.label.isNotBlank() }) abort(InvalidReason.DROPDOWN_NEEDS_OPTION)
        val existing = dao.getDefinitions()
        val saved = if (existing.any { it.id == definition.id }) {
            definition.copy(name = definition.name.trim())
        } else {
            definition.copy(name = definition.name.trim(), sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1, createdAt = time.now())
        }
        dao.upsert(saved)
        dao.clearOptions(saved.id)
        if (saved.type == CustomFieldType.DROPDOWN) {
            dao.upsertOptions(options.filter { it.label.isNotBlank() }.mapIndexed { i, o -> o.copy(fieldId = saved.id, label = o.label.trim(), sortOrder = i) })
        }
        saved
    }

    /** Deletes the definition; its options and values cascade. */
    suspend fun deleteField(id: String): DataResult<Unit> = tx.run { dao.deleteById(id) }

    suspend fun setActive(id: String, active: Boolean): DataResult<Unit> = tx.run {
        val def = dao.getDefinitions().firstOrNull { it.id == id } ?: abort(DataError.NotFound)
        dao.upsert(def.copy(isActive = active))
    }

    suspend fun reorder(orderedIds: List<String>): DataResult<Unit> = tx.run {
        val rank = orderedIds.withIndex().associate { it.value to it.index }
        val sorted = dao.getDefinitions().sortedWith(compareBy<CustomFieldDefinition> { rank[it.id] ?: Int.MAX_VALUE }.thenBy { it.sortOrder })
        sorted.forEachIndexed { index, def -> dao.upsert(def.copy(sortOrder = index)) }
    }

    /**
     * Validates and stores the values of one subscription (fieldId -> encoded value; blank or null clears).
     * Values of fields not in [values] are kept, so values of other categories' fields survive a category change.
     */
    suspend fun setValues(subscriptionId: String, categoryId: String?, values: Map<String, String?>): DataResult<Unit> = tx.run {
        writeValues(subscriptionId, categoryId, values)
    }

    /** Transaction-internal variant used by SubscriptionService. */
    internal suspend fun writeValues(subscriptionId: String, categoryId: String?, values: Map<String, String?>) {
        val defs = dao.getDefinitions().associateBy { it.id }
        val options = dao.getOptions().groupBy({ it.fieldId }, { it.id })
        for (def in defs.values) {
            val applies = def.isActive && (def.categoryId == null || def.categoryId == categoryId)
            if (applies && def.isRequired && values[def.id].isNullOrBlank()) {
                abort(InvalidReason.REQUIRED_FIELD_MISSING, def.id)
            }
        }
        val current = dao.getValues(subscriptionId).associateBy { it.fieldId }.toMutableMap()
        for ((fieldId, raw) in values) {
            val def = defs[fieldId] ?: continue
            val value = raw?.trim()
            if (value.isNullOrEmpty()) {
                current.remove(fieldId)
            } else {
                if (!isValid(def.type, value, options[fieldId].orEmpty())) abort(InvalidReason.INVALID_FIELD_VALUE, fieldId)
                current[fieldId] = CustomFieldValue(subscriptionId, fieldId, value)
            }
        }
        dao.clearValues(subscriptionId)
        dao.upsertValues(current.values.toList())
    }

    companion object {
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        private val PHONE = Regex("^\\+?[0-9 ()\\-]{3,20}$")
        private val URL = Regex("^(https?://)?[^\\s/$.?#].[^\\s]*$", RegexOption.IGNORE_CASE)

        /** Format check of an encoded value (see `CustomFieldValue` for the encoding per type). */
        fun isValid(type: CustomFieldType, value: String, optionIds: List<String> = emptyList()): Boolean = when (type) {
            CustomFieldType.TEXT, CustomFieldType.MULTILINE -> true
            CustomFieldType.NUMBER -> value.toLongOrNull() != null
            CustomFieldType.DECIMAL -> value.toBigDecimalOrNull() != null
            CustomFieldType.DATE -> parses { LocalDate.parse(value) }
            CustomFieldType.DATETIME -> parses { Instant.parse(value) }
            CustomFieldType.BOOLEAN -> value == "true" || value == "false"
            CustomFieldType.DROPDOWN -> value in optionIds
            CustomFieldType.EMAIL -> EMAIL.matches(value)
            CustomFieldType.PHONE -> PHONE.matches(value)
            CustomFieldType.URL -> URL.matches(value)
            CustomFieldType.RATING -> value.toIntOrNull() in 1..5
        }

        private inline fun parses(block: () -> Unit): Boolean = try {
            block()
            true
        } catch (e: DateTimeParseException) {
            false
        }
    }
}
