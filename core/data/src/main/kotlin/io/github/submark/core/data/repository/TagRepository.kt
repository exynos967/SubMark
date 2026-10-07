package io.github.submark.core.data.repository

import io.github.submark.core.data.change.ChangeNotifier
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.result.InvalidReason
import io.github.submark.core.data.result.TransactionRunner
import io.github.submark.core.data.result.abort
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.database.dao.TagDao
import io.github.submark.core.model.IconType
import io.github.submark.core.model.SubscriptionTag
import io.github.submark.core.model.Tag
import io.github.submark.core.model.TagColor
import io.github.submark.core.model.TagFolder
import io.github.submark.core.model.TagFolderTag
import io.github.submark.core.model.TagMatchMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

data class TagWithUsage(val tag: Tag, val subscriptionCount: Int)

data class TagFolderWithTags(val folder: TagFolder, val tagIds: Set<String>) {
    /** ANY: shares at least one tag; ALL: has every folder tag. Disabled or empty folders match nothing. */
    fun matches(subscriptionTagIds: Set<String>): Boolean = folder.isEnabled && TagFolderMatcher.matches(folder.matchMode, tagIds, subscriptionTagIds)
}

object TagFolderMatcher {
    fun matches(mode: TagMatchMode, folderTagIds: Set<String>, subscriptionTagIds: Set<String>): Boolean {
        if (folderTagIds.isEmpty()) return false
        return when (mode) {
            TagMatchMode.ANY -> folderTagIds.any { it in subscriptionTagIds }
            TagMatchMode.ALL -> subscriptionTagIds.containsAll(folderTagIds)
        }
    }
}

/** Tags (unique, case-insensitive names), subscription-tag links and tag folders. */
@Singleton
class TagRepository @Inject internal constructor(
    private val dao: TagDao,
    private val tx: TransactionRunner,
    private val notifier: ChangeNotifier,
    private val time: TimeProvider,
) {
    fun observeTags(): Flow<List<Tag>> = dao.observeAll()

    fun observeSubscriptionTags(): Flow<List<SubscriptionTag>> = dao.observeSubscriptionTags()

    fun observeTagsWithUsage(): Flow<List<TagWithUsage>> = combine(dao.observeAll(), dao.observeSubscriptionTags()) { tags, links ->
        val counts = links.groupingBy { it.tagId }.eachCount()
        tags.map { TagWithUsage(it, counts[it.id] ?: 0) }
    }

    suspend fun getTagIds(subscriptionId: String): List<String> = dao.getTagIds(subscriptionId)

    suspend fun create(name: String, color: TagColor = TagColor.BLUE, iconType: IconType? = null, iconValue: String? = null): DataResult<Tag> = tx.run {
        val trimmed = validName(name, excludeId = null)
        Tag(name = trimmed, color = color, iconType = iconType, iconValue = iconValue, createdAt = time.now()).also { dao.upsert(it) }
    }

    /** Existing tag with this name (case-insensitive) or a new one; used by "Create tag 'x'" in pickers. */
    suspend fun findOrCreate(name: String): DataResult<Tag> {
        if (name.isBlank()) return DataResult.Failure(DataError.Invalid(InvalidReason.BLANK_NAME))
        dao.findByName(name.trim())?.let { return DataResult.Success(it) }
        return create(name)
    }

    suspend fun update(tag: Tag): DataResult<Unit> = tx.run {
        val current = dao.getAll().firstOrNull { it.id == tag.id } ?: abort(DataError.NotFound)
        val trimmed = validName(tag.name, excludeId = tag.id)
        dao.upsert(current.copy(name = trimmed, color = tag.color, iconType = tag.iconType, iconValue = tag.iconValue))
    }

    /** Deletes the tag; its subscription and folder links cascade. */
    suspend fun delete(id: String): DataResult<Unit> {
        val affected = dao.getSubscriptionTags().filter { it.tagId == id }.map { it.subscriptionId }.toSet()
        val result = tx.run { dao.deleteById(id) }
        if (result.isSuccess) notifier.notifyChanged(affected)
        return result
    }

    /** Replaces the tags of one subscription. */
    suspend fun setSubscriptionTags(subscriptionId: String, tagIds: Collection<String>): DataResult<Unit> {
        val result = tx.run { replaceLinks(subscriptionId, tagIds) }
        if (result.isSuccess) notifier.notifyChanged(subscriptionId)
        return result
    }

    internal suspend fun replaceLinks(subscriptionId: String, tagIds: Collection<String>) {
        dao.clearSubscriptionTags(subscriptionId)
        dao.upsertSubscriptionTags(tagIds.distinct().map { SubscriptionTag(subscriptionId, it) })
    }

    // ---- folders ----

    fun observeFolders(): Flow<List<TagFolderWithTags>> = combine(dao.observeFolders(), dao.observeFolderTags()) { folders, links ->
        val byFolder = links.groupBy({ it.folderId }, { it.tagId })
        folders.map { TagFolderWithTags(it, byFolder[it.id].orEmpty().toSet()) }
    }

    /** Creates or updates a folder with its tags (at least one). New folders go last. */
    suspend fun saveFolder(folder: TagFolder, tagIds: Set<String>): DataResult<TagFolder> = tx.run {
        if (folder.name.isBlank()) abort(InvalidReason.BLANK_NAME)
        if (tagIds.isEmpty()) abort(InvalidReason.FOLDER_NEEDS_TAG)
        val existing = dao.getFolders()
        val saved = if (existing.any { it.id == folder.id }) {
            folder.copy(name = folder.name.trim())
        } else {
            folder.copy(name = folder.name.trim(), sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1)
        }
        dao.upsertFolder(saved)
        dao.clearFolderTags(saved.id)
        dao.upsertFolderTags(tagIds.map { TagFolderTag(saved.id, it) })
        saved
    }

    /** Deletes only the folder; tags and subscriptions stay. */
    suspend fun deleteFolder(id: String): DataResult<Unit> = tx.run { dao.deleteFolder(id) }

    suspend fun reorderFolders(orderedIds: List<String>): DataResult<Unit> = tx.run {
        val rank = orderedIds.withIndex().associate { it.value to it.index }
        val sorted = dao.getFolders().sortedWith(compareBy<TagFolder> { rank[it.id] ?: Int.MAX_VALUE }.thenBy { it.sortOrder })
        dao.upsertFolders(sorted.mapIndexed { index, f -> f.copy(sortOrder = index) })
    }

    private suspend fun validName(name: String, excludeId: String?): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) abort(InvalidReason.BLANK_NAME)
        val clash = dao.findByName(trimmed)
        if (clash != null && clash.id != excludeId) abort(InvalidReason.NAME_EXISTS)
        return trimmed
    }
}
