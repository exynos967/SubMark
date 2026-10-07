package io.github.submark.core.data.repository

import io.github.submark.core.data.change.ChangeNotifier
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.result.TransactionRunner
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.database.dao.SubscriptionExtrasDao
import io.github.submark.core.model.CustomReminder
import io.github.submark.core.model.SubscriptionPhoto
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** Photo references and custom reminders of a subscription. Photo files themselves are managed by the caller. */
@Singleton
class SubscriptionExtrasRepository @Inject internal constructor(
    private val dao: SubscriptionExtrasDao,
    private val tx: TransactionRunner,
    private val notifier: ChangeNotifier,
    private val time: TimeProvider,
) {
    fun observePhotos(subscriptionId: String): Flow<List<SubscriptionPhoto>> = dao.observePhotos(subscriptionId)

    /** Appends a photo whose file was already saved in the app's photo directory. */
    suspend fun addPhoto(subscriptionId: String, fileName: String): DataResult<SubscriptionPhoto> = tx.run {
        val order = dao.getPhotos(listOf(subscriptionId)).maxOfOrNull { it.sortOrder + 1 } ?: 0
        SubscriptionPhoto(subscriptionId = subscriptionId, fileName = fileName, sortOrder = order, createdAt = time.now())
            .also { dao.upsertPhotos(listOf(it)) }
    }

    suspend fun deletePhoto(photo: SubscriptionPhoto): DataResult<Unit> = tx.run { dao.deletePhoto(photo) }

    suspend fun reorderPhotos(subscriptionId: String, orderedIds: List<String>): DataResult<Unit> = tx.run {
        val rank = orderedIds.withIndex().associate { it.value to it.index }
        val sorted = dao.getPhotos(listOf(subscriptionId)).sortedWith(compareBy<SubscriptionPhoto> { rank[it.id] ?: Int.MAX_VALUE }.thenBy { it.sortOrder })
        dao.upsertPhotos(sorted.mapIndexed { index, p -> p.copy(sortOrder = index) })
    }

    fun observeReminders(subscriptionId: String): Flow<List<CustomReminder>> = dao.observeReminders(subscriptionId)

    fun observeAllReminders(): Flow<List<CustomReminder>> = dao.observeAllReminders()

    /** Replaces the custom reminders of a subscription ((daysBefore, time) pairs, duplicates dropped). */
    suspend fun setReminders(subscriptionId: String, reminders: List<Pair<Int, LocalTime>>): DataResult<Unit> {
        val result = tx.run {
            dao.clearReminders(subscriptionId)
            dao.upsertReminders(
                reminders.filter { it.first >= 0 }.distinct().map { (days, at) -> CustomReminder(subscriptionId = subscriptionId, daysBefore = days, time = at) },
            )
        }
        if (result.isSuccess) notifier.notifyChanged(subscriptionId)
        return result
    }
}
