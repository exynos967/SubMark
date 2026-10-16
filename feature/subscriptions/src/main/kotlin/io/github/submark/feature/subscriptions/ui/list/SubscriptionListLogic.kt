package io.github.submark.feature.subscriptions.ui.list

import io.github.submark.core.data.repository.SubscriptionItem
import io.github.submark.core.data.repository.TagFolderWithTags
import io.github.submark.core.data.settings.ListSegment
import io.github.submark.core.data.settings.SortDirection
import io.github.submark.core.data.settings.SortField
import io.github.submark.core.data.settings.SubscriptionPrefs
import io.github.submark.core.domain.CurrencyConverter
import io.github.submark.core.domain.PaymentProjection
import io.github.submark.core.model.BundleRole
import io.github.submark.core.model.PaymentRecord
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.YearMonth

/** Search text, single category and tag filter (ANY of [tagIds]); all three are AND-ed. */
data class ListFilter(
    val query: String = "",
    val categoryId: String? = null,
    val tagIds: Set<String> = emptySet(),
) {
    val isActive: Boolean get() = query.isNotBlank() || categoryId != null || tagIds.isNotEmpty()
}

/**
 * Totals in [currencyCode]. [thisMonth]/[thisYear] are the recurring payments of the calendar month/year, paid
 * plus still due (only the user's share of shared subscriptions), the same figure as the overview's projected total; [total] is the sum of one-time (lifetime)
 * or planned (wishlist) prices. Amounts whose currency has no rate are skipped and listed in [missingRateCodes].
 */
data class ListSummary(
    val count: Int,
    val currencyCode: String,
    val thisMonth: BigDecimal = BigDecimal.ZERO,
    val thisYear: BigDecimal = BigDecimal.ZERO,
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
        SubscriptionKind.REGULAR -> ListSegment.SUBSCRIPTIONS
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
     * NAME by name; PRICE by the user's per-cycle cost converted to [currencyCode] (unconvertible last);
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
            SortField.PRICE -> keyed { converter.convert(it.ownPrice, it.subscription.currencyCode, currencyCode) }
            else -> when (segment) {
                ListSegment.SUBSCRIPTIONS -> keyed { it.subscription.nextPaymentDate }
                ListSegment.LIFETIME -> keyed { it.subscription.startDate }
                ListSegment.WISHLIST -> keyed { it.subscription.createdAt }
            }
        }
    }

    /** [payments] must cover the calendar year of [today]; records of other subscriptions are ignored. */
    fun summary(
        items: List<SubscriptionItem>,
        payments: List<PaymentRecord>,
        today: LocalDate,
        converter: CurrencyConverter,
        currencyCode: String,
    ): ListSummary {
        val recurring = items.map { it.subscription }.filter { it.kind == SubscriptionKind.REGULAR }
        val ids = recurring.map { it.id }.toSet()
        val ownPayments = payments.filter { it.subscriptionId in ids }
        val year = PaymentProjection.project(
            subs = recurring,
            payments = ownPayments,
            from = today.withDayOfYear(1),
            to = today.withDayOfYear(today.lengthOfYear()),
            today = today,
            defaultCode = currencyCode,
            converter = converter,
            userShares = items.mapNotNull { item -> item.userShare?.let { item.id to it } }.toMap(),
            includeChildren = true,
        )
        val month = YearMonth.from(today)
        var total = BigDecimal.ZERO
        val missing = mutableSetOf<String>()
        recurring.filter { PaymentProjection.counts(it) }.map { it.currencyCode }
            .plus(ownPayments.map { it.currencyCode })
            .filterTo(missing) { converter.convert(BigDecimal.ONE, it, currencyCode) == null }
        for (item in items.filter { it.subscription.kind != SubscriptionKind.REGULAR }) {
            val sub = item.subscription
            val converted = converter.convert(item.ownPrice, sub.currencyCode, currencyCode)
            if (converted == null) missing += sub.currencyCode else total = total.add(converted, MC)
        }
        return ListSummary(
            count = items.size,
            currencyCode = currencyCode,
            thisMonth = year.between(month.atDay(1), month.atEndOfMonth()).projectedTotal,
            thisYear = year.projectedTotal,
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
        payments: List<PaymentRecord>,
        today: LocalDate,
        converter: CurrencyConverter,
        currencyCode: String,
    ): FolderPartition {
        val entries = folders.filter { it.folder.isEnabled }
            .sortedBy { it.folder.sortOrder }
            .mapNotNull { folder ->
                val matched = items.filter { item -> folder.matches(item.tags.map { it.id }.toSet()) }
                if (matched.isEmpty()) null else FolderEntry(folder, matched, summary(matched, payments, today, converter, currencyCode))
            }
        val hidden = entries.filter { !it.folder.folder.showInMainList }.flatMap { e -> e.items.map { it.id } }.toSet()
        return FolderPartition(entries, items.filter { it.id !in hidden })
    }
}
