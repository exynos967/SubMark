package io.github.submark.core.data.repository

import io.github.submark.core.data.change.ChangeNotifier
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.result.InvalidReason
import io.github.submark.core.data.result.TransactionRunner
import io.github.submark.core.data.result.abort
import io.github.submark.core.data.seed.SystemCategories
import io.github.submark.core.database.dao.CategoryDao
import io.github.submark.core.database.dao.SubscriptionDao
import io.github.submark.core.model.Category
import io.github.submark.core.model.IconType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Categories. Presets are hidden/restored, never deleted; "Other" can be neither.
 * Subscriptions of a hidden or deleted category move to "Other" and do not move back on restore.
 */
@Singleton
class CategoryRepository @Inject internal constructor(
    private val dao: CategoryDao,
    private val subscriptionDao: SubscriptionDao,
    private val tx: TransactionRunner,
    private val notifier: ChangeNotifier,
) {
    fun observeAll(): Flow<List<Category>> = dao.observeAll()

    fun observeVisible(): Flow<List<Category>> = dao.observeAll().map { list -> list.filter { !it.isHidden } }

    fun observeHidden(): Flow<List<Category>> = dao.observeAll().map { list -> list.filter { it.isHidden } }

    suspend fun get(id: String): Category? = dao.get(id)

    suspend fun create(name: String, iconType: IconType, iconValue: String, colorHex: String): DataResult<Category> = tx.run {
        if (name.isBlank()) abort(InvalidReason.BLANK_NAME)
        val order = (dao.getAll().maxOfOrNull { it.sortOrder } ?: 0) + 1
        Category(name = name.trim(), iconType = iconType, iconValue = iconValue, colorHex = colorHex, sortOrder = order)
            .also { dao.upsert(it) }
    }

    /** Saves name/icon/color. A preset may get a custom name; null name restores the localized preset name. */
    suspend fun update(category: Category): DataResult<Unit> = tx.run {
        val current = dao.get(category.id) ?: abort(DataError.NotFound)
        if (!current.isSystem && category.name.isNullOrBlank()) abort(InvalidReason.BLANK_NAME)
        dao.upsert(current.copy(name = category.name?.trim(), iconType = category.iconType, iconValue = category.iconValue, colorHex = category.colorHex))
    }

    /** Restores a preset's icon and color (name too). */
    suspend fun resetPresetStyle(id: String): DataResult<Unit> = tx.run {
        val current = dao.get(id) ?: abort(DataError.NotFound)
        val key = current.systemKey ?: abort(InvalidReason.NOT_SYSTEM)
        val preset = SystemCategories.default(key)
        dao.upsert(current.copy(name = null, iconType = preset.iconType, iconValue = preset.iconValue, colorHex = preset.colorHex))
    }

    suspend fun hide(id: String): DataResult<Unit> = moveAwayAnd(id) { current ->
        if (!current.isSystem) abort(InvalidReason.NOT_SYSTEM)
        dao.upsert(current.copy(isHidden = true))
    }

    suspend fun restore(id: String): DataResult<Unit> = tx.run {
        val current = dao.get(id) ?: abort(DataError.NotFound)
        dao.upsert(current.copy(isHidden = false))
    }

    /** Deletes a custom category; its subscriptions move to "Other". */
    suspend fun delete(id: String): DataResult<Unit> = moveAwayAnd(id) { current ->
        if (current.isSystem) abort(InvalidReason.SYSTEM_ITEM)
        dao.delete(current)
    }

    /** Persists the order of [orderedIds]; ids not listed keep their relative order after them. */
    suspend fun reorder(orderedIds: List<String>): DataResult<Unit> = tx.run {
        val all = dao.getAll()
        val rank = orderedIds.withIndex().associate { it.value to it.index }
        val sorted = all.sortedWith(compareBy<Category> { rank[it.id] ?: Int.MAX_VALUE }.thenBy { it.sortOrder })
        dao.upsertAll(sorted.mapIndexed { index, c -> c.copy(sortOrder = index) })
    }

    /** Factory reset: presets visible with preset style/order, custom categories deleted (subscriptions → Other). */
    suspend fun resetToDefault(): DataResult<Unit> {
        val result = tx.run {
            val custom = dao.getAll().filter { !it.isSystem }
            custom.forEach { subscriptionDao.moveCategory(it.id, SystemCategories.OTHER_ID) }
            dao.deleteCustom()
            dao.upsertAll(SystemCategories.defaults)
        }
        if (result.isSuccess) notifier.notifyChanged(null)
        return result
    }

    private suspend fun moveAwayAnd(id: String, action: suspend (Category) -> Unit): DataResult<Unit> {
        if (id == SystemCategories.OTHER_ID) return DataResult.Failure(DataError.Invalid(InvalidReason.OTHER_CATEGORY_PROTECTED))
        val result = tx.run {
            val current = dao.get(id) ?: abort(DataError.NotFound)
            action(current)
            subscriptionDao.moveCategory(id, SystemCategories.OTHER_ID)
        }
        if (result.isSuccess) notifier.notifyChanged(null)
        return result
    }
}
