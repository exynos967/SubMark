package io.github.submark.core.data.repository

import io.github.submark.core.data.change.ChangeNotifier
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.result.InvalidReason
import io.github.submark.core.data.result.TransactionRunner
import io.github.submark.core.data.result.abort
import io.github.submark.core.data.seed.SystemPaymentMethods
import io.github.submark.core.database.dao.PaymentMethodDao
import io.github.submark.core.database.dao.SubscriptionDao
import io.github.submark.core.model.PaymentMethod
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Payment methods: system presets plus user-defined ones. */
@Singleton
class PaymentMethodRepository @Inject internal constructor(
    private val dao: PaymentMethodDao,
    private val subscriptionDao: SubscriptionDao,
    private val tx: TransactionRunner,
    private val notifier: ChangeNotifier,
) {
    fun observeAll(): Flow<List<PaymentMethod>> = dao.observeAll()

    suspend fun get(id: String): PaymentMethod? = dao.get(id)

    suspend fun add(name: String, iconValue: String): DataResult<PaymentMethod> = tx.run {
        if (name.isBlank()) abort(InvalidReason.BLANK_NAME)
        val order = (dao.getAll().maxOfOrNull { it.sortOrder } ?: 0) + 1
        PaymentMethod(name = name.trim(), iconValue = iconValue, sortOrder = order).also { dao.upsert(it) }
    }

    /** Custom methods only. */
    suspend fun update(method: PaymentMethod): DataResult<Unit> = tx.run {
        val current = dao.get(method.id) ?: abort(DataError.NotFound)
        if (current.isSystem) abort(InvalidReason.SYSTEM_ITEM)
        if (method.name.isBlank()) abort(InvalidReason.BLANK_NAME)
        dao.upsert(current.copy(name = method.name.trim(), iconValue = method.iconValue))
    }

    /** Deletes a custom method; subscriptions using it fall back to no method. */
    suspend fun delete(id: String): DataResult<Unit> {
        val result = tx.run {
            val current = dao.get(id) ?: abort(DataError.NotFound)
            if (current.isSystem) abort(InvalidReason.SYSTEM_ITEM)
            subscriptionDao.clearPaymentMethod(id)
            dao.deleteById(id)
        }
        if (result.isSuccess) notifier.notifyChanged(null)
        return result
    }

    /** Deletes all custom methods and restores the presets. */
    suspend fun resetToDefault(): DataResult<Unit> {
        val result = tx.run {
            subscriptionDao.clearCustomPaymentMethods()
            dao.deleteCustom()
            dao.upsertAll(SystemPaymentMethods.defaults)
        }
        if (result.isSuccess) notifier.notifyChanged(null)
        return result
    }
}
