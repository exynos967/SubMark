package io.github.submark.feature.subscriptions.ui.list

import io.github.submark.core.data.repository.SubscriptionItem
import io.github.submark.core.data.repository.TagFolderWithTags
import io.github.submark.core.data.settings.ListSegment
import io.github.submark.core.data.settings.SortDirection
import io.github.submark.core.data.settings.SortField
import io.github.submark.core.data.settings.SubscriptionPrefs
import io.github.submark.core.domain.CostCalculator
import io.github.submark.core.domain.CurrencyConverter
import io.github.submark.core.model.BundleRole
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import java.math.BigDecimal
import java.math.MathContext

/** Search text, single category and tag filter (ANY of [tagIds]); all three are AND-ed. */
data class ListFilter(
    val query: String = "",
    val categoryId: String? = null,
    val tagIds: Set<String> = emptySet(),
) {
    val isActive: Boolean get() = query.isNotBlank() || categoryId != null || tagIds.isNotEmpty()
}

/**
 * Totals in [currencyCode]. [monthly]/[annual]/[average] cover recurring active items; [total] is the sum of
 * one-time (lifetime) or planned (wishlist) prices. Amounts whose currency has no rate are skipped and listed
 * in [missingRateCodes].
 */
data class ListSummary(
    val count: Int,
    val currencyCode: String,
    val monthly: BigDecimal = BigDecimal.ZERO,
    val annual: BigDecimal = BigDecimal.ZERO,
    val average: BigDecimal? = null,
    val total: BigDecimal = BigDecimal.ZERO,
    val missingRateCodes: Set<String> = emptySet(),
)

data class FolderEntry(val folder: TagFolderWithTags, val items: List<SubscriptionItem>, val summary: ListSummary)

data class FolderPartition(val folders: List<FolderEntry>, val main: List<SubscriptionItem>)

/** Pure list rules shared by the Subscriptions tab, the archive and tag folder screens. */
object SubscriptionListLogic {
    private val MC = MathContext.DECIMAL64

    fun segmentOf(kind: SubscriptionKind): ListSegment = when (kind) {
        SubscriptionKind.WISHLIST -> ListSegment.WISHLIST
        SubscriptionKind.LIFETIME -> ListSegment.LIFETIME
        SubscriptionKind.REGULAR, SubscriptionKind.STORED_VALUE -> ListSegment.SUBSCRIPTIONS
    }

    /** Archived = paused while archive mode is on. Wishlist items are always paused and never archived. */
    fun isArchived(item: SubscriptionItem, prefs: SubscriptionPrefs): Boolean =
        prefs.archiveMode && item.subscription.status == SubscriptionStatus.PAUSED && item.subscription.kind != SubscriptionKind.WISHLIST

    fun isChildHidden(item: SubscriptionItem, prefs: SubscriptionPrefs): Boolean =
        item.subscription.bundleRole == BundleRole.CHILD && !prefs.showChildSubscriptions

    /** Items that belong on the main list of [segment] before any filter. */
    fun segmentItems(items: List<SubscriptionItem>, segment: ListSegment, prefs: SubscriptionPrefs): List<SubscriptionItem> =
        items.filter { segmentOf(it.subscription.kind) == segment && !isArchived(it, prefs) && !isChildHidden(it, prefs) }

    fun matches(item: SubscriptionItem, filter: ListFilter): Boolean {
        val sub = item.subscription
        if (filter.categoryId != null && sub.categoryId != filter.categoryId) return false
        if (filter.tagIds.isNotEmpty() && item.tags.none { it.id in filter.tagIds }) return false
        val q = filter.query.trim()
        if (q.isEmpty()) return true
        return sub.name.contains(q, ignoreCase = true) ||
            sub.note?.contains(q, ignoreCase = true) == true ||
            item.tags.any { it.name.contains(q, ignoreCase = true) }
    }

    fun filter(items: List<SubscriptionItem>, filter: ListFilter): List<SubscriptionItem> = items.filter { matches(it, filter) }

    /**
     * NAME by name; PRICE by the per-cycle price converted to [currencyCode] (unconvertible last);
     * DATE by next payment (subscriptions), purchase date (lifetime) or creation time (wishlist). Missing dates go last.
     */
    fun sort(
        items: List<SubscriptionItem>,
        field: SortField,
        direction: SortDirection,
        segment: ListSegment,
        converter: CurrencyConverter,
        currencyCode: String,
    ): List<SubscriptionItem> {
        val byName = compareBy<SubscriptionItem> { it.subscription.name.lowercase() }.thenBy { it.id }
        if (field == SortField.NAME) {
            return if (direction == SortDirection.ASC) items.sortedWith(byName) else items.sortedWith(byName.reversed())
        }
        fun <K : Comparable<K>> keyed(key: (SubscriptionItem) -> K?): List<SubscriptionItem> {
            val (withKey, without) = items.partition { key(it) != null }
            val cmp = compareBy<SubscriptionItem> { key(it)!! }
            val sorted = withKey.sortedWith(if (direction == SortDirection.ASC) cmp.then(byName) else cmp.reversed().then(byName))
            return sorted + without.sortedWith(byName)
        }
        return when (field) {
            SortField.PRICE -> keyed { converter.convert(it.subscription.price, it.subscription.currencyCode, currencyCode) }
            else -> when (segment) {
                ListSegment.SUBSCRIPTIONS -> keyed { it.subscription.nextPaymentDate }
                ListSegment.LIFETIME -> keyed { it.subscription.startDate }
                ListSegment.WISHLIST -> keyed { it.subscription.createdAt }
            }
        }
    }

    fun summary(items: List<SubscriptionItem>, converter: CurrencyConverter, currencyCode: String): ListSummary {
        var monthly = BigDecimal.ZERO
        var total = BigDecimal.ZERO
        var recurringCount = 0
        val missing = mutableSetOf<String>()
        for (item in items) {
            val sub = item.subscription
            when (sub.kind) {
                SubscriptionKind.REGULAR, SubscriptionKind.STORED_VALUE -> {
                    val m = CostCalculator.monthlyOf(sub) ?: continue
                    val converted = converter.convert(m, sub.currencyCode, currencyCode)
                    if (converted == null) {
                        missing += sub.currencyCode
                    } else {
                        monthly = monthly.add(converted, MC)
                        recurringCount++
                    }
                }
                SubscriptionKind.LIFETIME, SubscriptionKind.WISHLIST -> {
                    val converted = converter.convert(sub.price, sub.currencyCode, currencyCode)
                    if (converted == null) missing += sub.currencyCode else total = total.add(converted, MC)
                }
            }
        }
        return ListSummary(
            count = items.size,
            currencyCode = currencyCode,
            monthly = monthly,
            annual = monthly.multiply(BigDecimal(12), MC),
            average = if (recurringCount > 0) monthly.divide(BigDecimal(recurringCount), MC) else null,
            total = total,
            missingRateCodes = missing,
        )
    }

    /**
     * Enabled folders (in sort order) with at least one match among [items], and the main list without the
     * matches of folders that are not shown in the main list.
     */
    fun partitionFolders(
        items: List<SubscriptionItem>,
        folders: List<TagFolderWithTags>,
        converter: CurrencyConverter,
        currencyCode: String,
    ): FolderPartition {
        val entries = folders.filter { it.folder.isEnabled }
            .sortedBy { it.folder.sortOrder }
            .mapNotNull { folder ->
                val matched = items.filter { item -> folder.matches(item.tags.map { it.id }.toSet()) }
                if (matched.isEmpty()) null else FolderEntry(folder, matched, summary(matched, converter, currencyCode))
            }
        val hidden = entries.filter { !it.folder.folder.showInMainList }.flatMap { e -> e.items.map { it.id } }.toSet()
        return FolderPartition(entries, items.filter { it.id !in hidden })
    }
}
