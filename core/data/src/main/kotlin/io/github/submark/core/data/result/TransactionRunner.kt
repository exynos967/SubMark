package io.github.submark.core.data.result

import androidx.room.withTransaction
import io.github.submark.core.database.SubMarkDatabase
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown by [abort] to roll back the surrounding transaction with an expected error. */
internal class TransactionAbort(val error: DataError) : RuntimeException(null, null, false, false)

/** Rolls back the current [TransactionRunner.run] block and turns it into [DataResult.Failure]. */
internal fun abort(error: DataError): Nothing = throw TransactionAbort(error)

internal fun abort(reason: InvalidReason, detail: String? = null): Nothing = abort(DataError.Invalid(reason, detail))

/** Runs a block in one Room transaction; [abort] inside it rolls everything back. */
@Singleton
internal class TransactionRunner @Inject constructor(private val database: SubMarkDatabase) {
    suspend fun <T> run(block: suspend () -> T): DataResult<T> = try {
        DataResult.Success(database.withTransaction { block() })
    } catch (e: TransactionAbort) {
        DataResult.Failure(e.error)
    }
}
