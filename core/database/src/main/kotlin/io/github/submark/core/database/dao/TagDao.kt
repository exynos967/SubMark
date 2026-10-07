package io.github.submark.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.submark.core.model.SubscriptionTag
import io.github.submark.core.model.Tag
import io.github.submark.core.model.TagFolder
import io.github.submark.core.model.TagFolderTag
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao : BaseDao<Tag> {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Tag>>

    @Query("SELECT * FROM tags")
    suspend fun getAll(): List<Tag>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Tag?

    @Query("SELECT * FROM subscription_tags")
    fun observeSubscriptionTags(): Flow<List<SubscriptionTag>>

    @Query("SELECT * FROM subscription_tags")
    suspend fun getSubscriptionTags(): List<SubscriptionTag>

    @Query("SELECT tagId FROM subscription_tags WHERE subscriptionId = :subscriptionId")
    suspend fun getTagIds(subscriptionId: String): List<String>

    @Upsert suspend fun upsertSubscriptionTags(items: List<SubscriptionTag>)

    @Query("DELETE FROM subscription_tags WHERE subscriptionId = :subscriptionId")
    suspend fun clearSubscriptionTags(subscriptionId: String)

    @Query("SELECT * FROM tag_folders ORDER BY sortOrder")
    fun observeFolders(): Flow<List<TagFolder>>

    @Query("SELECT * FROM tag_folders")
    suspend fun getFolders(): List<TagFolder>

    @Upsert suspend fun upsertFolder(folder: TagFolder)
    @Upsert suspend fun upsertFolders(folders: List<TagFolder>)

    @Query("DELETE FROM tag_folders WHERE id = :id")
    suspend fun deleteFolder(id: String)

    @Query("SELECT * FROM tag_folder_tags")
    fun observeFolderTags(): Flow<List<TagFolderTag>>

    @Query("SELECT * FROM tag_folder_tags")
    suspend fun getFolderTags(): List<TagFolderTag>

    @Upsert suspend fun upsertFolderTags(items: List<TagFolderTag>)

    @Query("DELETE FROM tag_folder_tags WHERE folderId = :folderId")
    suspend fun clearFolderTags(folderId: String)
}
