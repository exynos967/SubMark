package io.github.submark.core.data.repository

import io.github.submark.core.database.dao.CategoryDao
import io.github.submark.core.database.dao.SubscriptionDao
import io.github.submark.core.database.dao.TagDao
import io.github.submark.core.model.Category
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** A subscription joined with what list and detail screens show next to it. */
data class SubscriptionItem(
    val subscription: Subscription,
    val category: Category?,
    val tags: List<Tag>,
    /** Bundle children (for a MAIN). */
    val children: List<Subscription>,
) {
    val id: String get() = subscription.id
}

/** Read side for subscriptions. Mutations go through `SubscriptionService`. */
@Singleton
class SubscriptionRepository @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val categoryDao: CategoryDao,
    private val tagDao: TagDao,
) {
    fun observeAll(): Flow<List<Subscription>> = subscriptionDao.observeAll()

    fun observe(id: String): Flow<Subscription?> = subscriptionDao.observe(id)

    fun observeChildren(parentId: String): Flow<List<Subscription>> = subscriptionDao.observeChildren(parentId)

    suspend fun get(id: String): Subscription? = subscriptionDao.get(id)

    suspend fun getAll(): List<Subscription> = subscriptionDao.getAll()

    /** Every subscription with category, tags and children, ordered by name. */
    fun observeItems(): Flow<List<SubscriptionItem>> = combine(
        subscriptionDao.observeAll(),
        categoryDao.observeAll(),
        tagDao.observeAll(),
        tagDao.observeSubscriptionTags(),
    ) { subs, categories, tags, links ->
        val categoryById = categories.associateBy { it.id }
        val tagById = tags.associateBy { it.id }
        val tagsBySub = links.groupBy({ it.subscriptionId }, { it.tagId })
        val childrenByParent = subs.filter { it.parentId != null }.groupBy { it.parentId!! }
        subs.map { sub ->
            SubscriptionItem(
                subscription = sub,
                category = categoryById[sub.categoryId],
                tags = tagsBySub[sub.id].orEmpty().mapNotNull(tagById::get).sortedBy { it.name.lowercase() },
                children = childrenByParent[sub.id].orEmpty(),
            )
        }
    }

    fun observeItem(id: String): Flow<SubscriptionItem?> = observeItems().map { items -> items.firstOrNull { it.id == id } }

    /** Another subscription with the same App Store id, for the duplicate warning. */
    suspend fun findByAppStoreId(appStoreId: String, excludeId: String = ""): Subscription? =
        subscriptionDao.findByAppStoreId(appStoreId, excludeId)
}
