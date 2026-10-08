package io.github.submark.feature.backup.data

import java.time.Instant

/** Which remote backups to delete given a profile's keepCount/keepDays retention. Pure and unit-tested. */
object PrunePolicy {

    /**
     * Returns payload file names to delete: every backup older than `keepDays` OR whose rank
     * (newest first) exceeds `keepCount` — except the single newest backup, which is never pruned.
     * Non-positive limits are treated as "no limit".
     * [backups] = (payloadFileName, createdAt), in any order.
     */
    fun selectForDeletion(
        backups: List<Pair<String, Instant>>,
        keepCount: Int,
        keepDays: Int,
        now: Instant,
    ): List<String> {
        if (backups.size <= 1) return emptyList()
        val sorted = backups.sortedByDescending { it.second }
        val candidates = sorted.drop(1) // newest is protected
        val cutoff = if (keepDays > 0) now.minusSeconds(keepDays.toLong() * 24 * 3600) else null
        return candidates.mapIndexedNotNull { index, (name, created) ->
            // Rank including the protected newest: index 0 here is overall #2.
            val rank = index + 2
            val beyondCount = keepCount > 0 && rank > keepCount
            val tooOld = cutoff != null && created.isBefore(cutoff)
            if (beyondCount || tooOld) name else null
        }
    }
}
