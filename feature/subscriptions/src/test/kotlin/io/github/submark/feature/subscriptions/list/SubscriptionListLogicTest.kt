package io.github.submark.feature.subscriptions.list

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.data.repository.SubscriptionItem
import io.github.submark.core.data.repository.TagFolderWithTags
import io.github.submark.core.data.settings.ListSegment
import io.github.submark.core.data.settings.ListSettings
import io.github.submark.core.data.settings.SortDirection
import io.github.submark.core.data.settings.SortField
import io.github.submark.core.data.settings.SubscriptionPrefs
import io.github.submark.core.domain.CurrencyConverter
import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.BundleRole
import io.github.submark.core.model.RenewalType
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import io.github.submark.core.model.Tag
import io.github.submark.core.model.TagFolder
import io.github.submark.core.model.TagMatchMode
import io.github.submark.core.ui.component.SubscriptionBadge
import io.github.submark.feature.subscriptions.ui.archive.ArchiveLogic
import io.github.submark.feature.subscriptions.ui.common.CardDate
import io.github.submark.feature.subscriptions.ui.common.CardDateKind
import io.github.submark.feature.subscriptions.ui.common.SubscriptionCardModel
import io.github.submark.feature.subscriptions.ui.folder.TagFolderLogic
import io.github.submark.feature.subscriptions.ui.list.ListFilter
import io.github.submark.feature.subscriptions.ui.list.SubscriptionListLogic
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class SubscriptionListLogicTest {
    private val today = LocalDate.of(2026, 3, 10)
    private val now = Instant.parse("2026-03-10T10:00:00Z")
    // 1 USD = 7 CNY = 0.5 EUR (test values).
    private val converter = CurrencyConverter(mapOf("CNY" to BigDecimal("7"), "EUR" to BigDecimal("0.5")))
    private val prefs = SubscriptionPrefs()

    private val work = Tag(id = "t_work", name = "Work", createdAt = now)
    private val family = Tag(id = "t_family", name = "Family", createdAt = now)

    private fun item(
        id: String,
        name: String = id,
        price: String = "10",
        currency: String = "USD",
        kind: SubscriptionKind = SubscriptionKind.REGULAR,
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        cycle: BillingCycle? = BillingCycle.MONTHLY,
        next: LocalDate? = today.plusDays(5),
        start: LocalDate = today.minusMonths(1),
        category: String = "cat_video",
        tags: List<Tag> = emptyList(),
        note: String? = null,
        role: BundleRole = BundleRole.NONE,
        parentId: String? = null,
        createdAt: Instant = now,
        pausedAt: Instant? = null,
        endDate: LocalDate? = null,
    ) = SubscriptionItem(
        subscription = Subscription(
            id = id, name = name, kind = kind, price = BigDecimal(price), currencyCode = currency, billingCycle = cycle,
            startDate = start, nextPaymentDate = next, status = status, categoryId = category, note = note,
            bundleRole = role, parentId = parentId, createdAt = createdAt, updatedAt = createdAt, pausedAt = pausedAt, endDate = endDate,
        ),
        category = null,
        tags = tags,
        children = emptyList(),
    )

    // ---- segments / archive ----

    @Test
    fun segmentItems_splitsByKind_andHidesArchivedAndChildren() {
        val items = listOf(
            item("a"),
            item("paused", status = SubscriptionStatus.PAUSED),
            item("life", kind = SubscriptionKind.LIFETIME, cycle = null, next = null),
            item("wish", kind = SubscriptionKind.WISHLIST, status = SubscriptionStatus.PAUSED),
            item("child", role = BundleRole.CHILD, parentId = "a"),
        )
        assertThat(ids(SubscriptionListLogic.segmentItems(items, ListSegment.SUBSCRIPTIONS, prefs))).containsExactly("a")
        assertThat(ids(SubscriptionListLogic.segmentItems(items, ListSegment.LIFETIME, prefs))).containsExactly("life")
        // Wishlist items are paused by design but never archived.
        assertThat(ids(SubscriptionListLogic.segmentItems(items, ListSegment.WISHLIST, prefs))).containsExactly("wish")

        val noArchive = prefs.copy(archiveMode = false, showChildSubscriptions = true)
        assertThat(ids(SubscriptionListLogic.segmentItems(items, ListSegment.SUBSCRIPTIONS, noArchive)))
            .containsExactly("a", "paused", "child")
    }

    // ---- filtering ----

    @Test
    fun filter_searchesNameNoteAndTags_caseInsensitive() {
        val items = listOf(
            item("1", name = "Stream Max"),
            item("2", name = "Cloud", note = "shared with MAX"),
            item("3", name = "Music", tags = listOf(work)),
            item("4", name = "Other"),
        )
        assertThat(ids(SubscriptionListLogic.filter(items, ListFilter(query = "max")))).containsExactly("1", "2")
        assertThat(ids(SubscriptionListLogic.filter(items, ListFilter(query = " WORK ")))).containsExactly("3")
        assertThat(ids(SubscriptionListLogic.filter(items, ListFilter()))).hasSize(4)
    }

    @Test
    fun filter_categoryAndTags_areAnded_tagsMatchAny() {
        val items = listOf(
            item("1", category = "c1", tags = listOf(work)),
            item("2", category = "c1", tags = listOf(family)),
            item("3", category = "c2", tags = listOf(work, family)),
            item("4", category = "c1"),
        )
        assertThat(ids(SubscriptionListLogic.filter(items, ListFilter(categoryId = "c1")))).containsExactly("1", "2", "4")
        assertThat(ids(SubscriptionListLogic.filter(items, ListFilter(tagIds = setOf(work.id, family.id))))).containsExactly("1", "2", "3")
        assertThat(ids(SubscriptionListLogic.filter(items, ListFilter(categoryId = "c1", tagIds = setOf(work.id))))).containsExactly("1")
        assertThat(ListFilter().isActive).isFalse()
        assertThat(ListFilter(tagIds = setOf("x")).isActive).isTrue()
    }

    // ---- sorting ----

    @Test
    fun sort_byName_bothDirections() {
        val items = listOf(item("b", name = "beta"), item("a", name = "Alpha"), item("c", name = "gamma"))
        assertThat(ids(sort(items, SortField.NAME, SortDirection.ASC))).containsExactly("a", "b", "c").inOrder()
        assertThat(ids(sort(items, SortField.NAME, SortDirection.DESC))).containsExactly("c", "b", "a").inOrder()
    }

    @Test
    fun sort_byPrice_convertsToDefaultCurrency_unknownLast() {
        val items = listOf(
            item("usd", price = "10"), // 10 USD
            item("cny", price = "35", currency = "CNY"), // 5 USD
            item("eur", price = "10", currency = "EUR"), // 20 USD
            item("xyz", price = "1", currency = "XYZ"), // no rate
        )
        assertThat(ids(sort(items, SortField.PRICE, SortDirection.ASC))).containsExactly("cny", "usd", "eur", "xyz").inOrder()
        assertThat(ids(sort(items, SortField.PRICE, SortDirection.DESC))).containsExactly("eur", "usd", "cny", "xyz").inOrder()
    }

    @Test
    fun sort_byDate_dependsOnSegment_missingDatesLast() {
        val subs = listOf(item("late", next = today.plusDays(20)), item("none", next = null), item("soon", next = today.plusDays(1)))
        assertThat(ids(sort(subs, SortField.DATE, SortDirection.ASC))).containsExactly("soon", "late", "none").inOrder()
        assertThat(ids(sort(subs, SortField.DATE, SortDirection.DESC))).containsExactly("late", "soon", "none").inOrder()

        val life = listOf(
            item("old", kind = SubscriptionKind.LIFETIME, start = today.minusYears(2)),
            item("new", kind = SubscriptionKind.LIFETIME, start = today.minusDays(3)),
        )
        assertThat(ids(sort(life, SortField.DATE, SortDirection.ASC, ListSegment.LIFETIME))).containsExactly("old", "new").inOrder()

        val wish = listOf(
            item("second", kind = SubscriptionKind.WISHLIST, createdAt = now.plusSeconds(60)),
            item("first", kind = SubscriptionKind.WISHLIST, createdAt = now),
        )
        assertThat(ids(sort(wish, SortField.DATE, SortDirection.ASC, ListSegment.WISHLIST))).containsExactly("first", "second").inOrder()
    }

    // ---- summary ----

    @Test
    fun summary_convertsAndExcludesPausedAndNonRecurring() {
        val items = listOf(
            item("m", price = "10"), // 10/mo
            item("y", price = "840", currency = "CNY", cycle = BillingCycle.ANNUALLY), // 120 USD/yr = 10/mo
            item("paused", price = "100", status = SubscriptionStatus.PAUSED),
        )
        val s = SubscriptionListLogic.summary(items, converter, "USD")
        assertThat(s.count).isEqualTo(3)
        assertThat(s.monthly.compareTo(BigDecimal("20"))).isEqualTo(0)
        assertThat(s.annual.compareTo(BigDecimal("240"))).isEqualTo(0)
        assertThat(s.average!!.compareTo(BigDecimal("10"))).isEqualTo(0)
        assertThat(s.missingRateCodes).isEmpty()
    }

    @Test
    fun summary_lifetimeTotal_andMissingRates() {
        val items = listOf(
            item("l1", kind = SubscriptionKind.LIFETIME, price = "50", cycle = null),
            item("l2", kind = SubscriptionKind.LIFETIME, price = "10", currency = "EUR", cycle = null), // 20 USD
            item("l3", kind = SubscriptionKind.LIFETIME, price = "5", currency = "XYZ", cycle = null),
            item("r", price = "3", currency = "XYZ"),
        )
        val s = SubscriptionListLogic.summary(items, converter, "USD")
        assertThat(s.total.compareTo(BigDecimal("70"))).isEqualTo(0)
        assertThat(s.monthly.signum()).isEqualTo(0)
        assertThat(s.average).isNull()
        assertThat(s.missingRateCodes).containsExactly("XYZ")
    }

    @Test
    fun summary_inOtherCurrency() {
        val s = SubscriptionListLogic.summary(listOf(item("m", price = "2")), converter, "CNY")
        assertThat(s.monthly.compareTo(BigDecimal("14"))).isEqualTo(0)
        assertThat(s.currencyCode).isEqualTo("CNY")
    }

    // ---- folders ----

    private fun folder(id: String, tags: Set<String>, show: Boolean = true, enabled: Boolean = true, order: Int = 0, mode: TagMatchMode = TagMatchMode.ANY) =
        TagFolderWithTags(TagFolder(id = id, name = id, showInMainList = show, isEnabled = enabled, sortOrder = order, matchMode = mode, createdAt = now), tags)

    @Test
    fun partitionFolders_hidesMatchesOnlyForHiddenFolders() {
        val items = listOf(item("w", tags = listOf(work)), item("f", tags = listOf(family)), item("both", tags = listOf(work, family)), item("none"))
        val folders = listOf(
            folder("workFolder", setOf(work.id), show = false, order = 1),
            folder("familyFolder", setOf(family.id), show = true, order = 0),
            folder("disabled", setOf(work.id), show = false, enabled = false),
            folder("empty", setOf("t_unused")),
        )
        val p = SubscriptionListLogic.partitionFolders(items, folders, converter, "USD")
        assertThat(p.folders.map { it.folder.folder.id }).containsExactly("familyFolder", "workFolder").inOrder()
        assertThat(ids(p.folders.first { it.folder.folder.id == "workFolder" }.items)).containsExactly("w", "both")
        assertThat(ids(p.main)).containsExactly("f", "none")
        assertThat(p.folders.first().summary.monthly.compareTo(BigDecimal("20"))).isEqualTo(0)
    }

    @Test
    fun partitionFolders_matchAll() {
        val items = listOf(item("w", tags = listOf(work)), item("both", tags = listOf(work, family)))
        val p = SubscriptionListLogic.partitionFolders(items, listOf(folder("all", setOf(work.id, family.id), mode = TagMatchMode.ALL)), converter, "USD")
        assertThat(ids(p.folders.single().items)).containsExactly("both")
    }

    @Test
    fun tagFolderMembers_excludeWishlistArchivedAndHiddenChildren() {
        val items = listOf(
            item("a", tags = listOf(work)),
            item("wish", kind = SubscriptionKind.WISHLIST, tags = listOf(work)),
            item("paused", status = SubscriptionStatus.PAUSED, tags = listOf(work)),
            item("child", role = BundleRole.CHILD, tags = listOf(work)),
        )
        assertThat(ids(TagFolderLogic.members(items, folder("f", setOf(work.id)), prefs))).containsExactly("a")
    }

    // ---- archive ----

    @Test
    fun archive_listsPausedOfVariant_newestFirst() {
        val items = listOf(
            item("old", status = SubscriptionStatus.PAUSED, pausedAt = now.minusSeconds(100)),
            item("new", status = SubscriptionStatus.PAUSED, pausedAt = now),
            item("active"),
            item("wish", kind = SubscriptionKind.WISHLIST, status = SubscriptionStatus.PAUSED),
            item("life", kind = SubscriptionKind.LIFETIME, status = SubscriptionStatus.PAUSED, cycle = null),
        )
        assertThat(ids(ArchiveLogic.archived(items, lifetime = false, query = ""))).containsExactly("new", "old").inOrder()
        assertThat(ids(ArchiveLogic.archived(items, lifetime = true, query = ""))).containsExactly("life")
        assertThat(ids(ArchiveLogic.archived(items, lifetime = false, query = "OLD"))).containsExactly("old")
    }

    // ---- card ----

    @Test
    fun cardModel_dateAmountBadgesAndMarkable() {
        val list = ListSettings()
        val regular = item("r", next = today.plusDays(3), endDate = today.plusMonths(6)).subscription
        assertThat(SubscriptionCardModel.date(regular, list)).isEqualTo(CardDate(CardDateKind.NEXT, today.plusDays(3)))
        assertThat(SubscriptionCardModel.date(regular, list.copy(showEndDateForFixedCycle = true)))
            .isEqualTo(CardDate(CardDateKind.END, today.plusMonths(6)))
        val life = item("l", kind = SubscriptionKind.LIFETIME, cycle = null, next = null, start = today.minusDays(9)).subscription
        assertThat(SubscriptionCardModel.date(life, list)).isEqualTo(CardDate(CardDateKind.BOUGHT, today.minusDays(9)))
        assertThat(SubscriptionCardModel.badges(life, today, list)).contains(SubscriptionBadge.LIFETIME)
        assertThat(SubscriptionCardModel.badges(life, today, list.copy(showLifetimeLabel = false))).doesNotContain(SubscriptionBadge.LIFETIME)

        val stored = regular.copy(kind = SubscriptionKind.STORED_VALUE, storedValueBalance = BigDecimal("42"))
        assertThat(SubscriptionCardModel.amount(stored, list)).isEqualTo(BigDecimal("10"))
        assertThat(SubscriptionCardModel.amount(stored, list.copy(storedValueBalanceMode = true))).isEqualTo(BigDecimal("42"))

        assertThat(SubscriptionCardModel.isMarkable(regular)).isTrue()
        assertThat(SubscriptionCardModel.isMarkable(regular.copy(renewalType = RenewalType.TRIAL))).isFalse()
        assertThat(SubscriptionCardModel.isMarkable(regular.copy(status = SubscriptionStatus.PAUSED))).isFalse()
        assertThat(SubscriptionCardModel.isMarkable(life)).isFalse()
        assertThat(SubscriptionCardModel.dueDate(regular.copy(status = SubscriptionStatus.PAUSED))).isNull()
    }

    private fun sort(items: List<SubscriptionItem>, field: SortField, dir: SortDirection, segment: ListSegment = ListSegment.SUBSCRIPTIONS) =
        SubscriptionListLogic.sort(items, field, dir, segment, converter, "USD")

    private fun ids(items: List<SubscriptionItem>) = items.map { it.id }
}
