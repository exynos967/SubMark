package io.github.submark.feature.backup.data

import io.github.submark.core.data.change.ChangeNotifier
import io.github.submark.core.database.dao.SharedDao
import io.github.submark.core.database.dao.SubscriptionDao
import io.github.submark.core.model.SharedMember
import javax.inject.Inject
import javax.inject.Singleton

/** Pure selection logic for the shared-member cleanup tools; unit-tested. */
object MemberCleanup {

    /** Members whose subscriptionId has no matching subscription row. */
    fun orphans(members: List<SharedMember>, subscriptionIds: Set<String>): List<SharedMember> =
        members.filter { it.subscriptionId !in subscriptionIds }

    data class DuplicateGroups(
        /** name → members beyond the first (oldest) one per subscription. */
        val victims: List<SharedMember>,
        /** total members that share (subscriptionId, name) with at least one sibling. */
        val groupCount: Int,
    )

    /**
     * Groups by (subscriptionId, normalized name); keeps the earliest-created member of each group,
     * marks the rest for deletion. Creators are never marked.
     */
    fun duplicates(members: List<SharedMember>): DuplicateGroups {
        val groups = members.groupBy { it.subscriptionId to it.name.trim().lowercase() }
        val victims = mutableListOf<SharedMember>()
        var groupsWithDupes = 0
        groups.values.forEach { group ->
            if (group.size > 1) {
                val keep = group.filter { it.isCreator }.ifEmpty { listOf(group.minBy { it.createdAt }) }.first()
                val extra = group.filter { it.id != keep.id }
                if (extra.isNotEmpty()) {
                    groupsWithDupes++
                    victims += extra
                }
            }
        }
        return DuplicateGroups(victims, groupsWithDupes)
    }
}

/**
 * Cleanup tools from Data Management: orphaned shared members (no subscription) and duplicate
 * shared members grouped by subscriptionId + name. Each returns a report for the UI.
 */
@Singleton
class MemberCleanupService @Inject constructor(
    private val sharedDao: SharedDao,
    private val subscriptionDao: SubscriptionDao,
    private val notifier: ChangeNotifier,
) {
    data class OrphanReport(val checked: Int, val orphaned: Int, val kept: Int)
    data class DuplicateReport(
        val checked: Int,
        val deleted: Int,
        val remaining: Int,
        val duplicateGroups: Int,
        val affectedSubscriptions: Int,
    )

    suspend fun cleanOrphans(): OrphanReport {
        val members = sharedDao.getAllMembers()
        val ids = subscriptionDao.getAll().mapTo(HashSet()) { it.id }
        val victims = MemberCleanup.orphans(members, ids)
        victims.forEach { sharedDao.deleteMember(it) }
        if (victims.isNotEmpty()) notifier.notifyChanged(null)
        return OrphanReport(checked = members.size, orphaned = victims.size, kept = members.size - victims.size)
    }

    suspend fun cleanDuplicates(): DuplicateReport {
        val members = sharedDao.getAllMembers()
        val dupes = MemberCleanup.duplicates(members)
        dupes.victims.forEach { sharedDao.deleteMember(it) }
        if (dupes.victims.isNotEmpty()) notifier.notifyChanged(null)
        return DuplicateReport(
            checked = members.size,
            deleted = dupes.victims.size,
            remaining = members.size - dupes.victims.size,
            duplicateGroups = dupes.groupCount,
            affectedSubscriptions = dupes.victims.mapTo(HashSet()) { it.subscriptionId }.size,
        )
    }
}
