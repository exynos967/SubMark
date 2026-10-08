package io.github.submark.feature.subscriptions.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.currency.CurrencyRepository
import io.github.submark.core.data.repository.CategoryRepository
import io.github.submark.core.data.repository.SubscriptionItem
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.repository.TagRepository
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.service.SubscriptionService
import io.github.submark.core.data.settings.AppSettings
import io.github.submark.core.data.settings.DefaultListStyle
import io.github.submark.core.data.settings.ListSegment
import io.github.submark.core.data.settings.ListSettings
import io.github.submark.core.data.settings.ListStyle
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.settings.SortDirection
import io.github.submark.core.data.settings.SortField
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.domain.CurrencyConverter
import io.github.submark.core.model.BundleRole
import io.github.submark.core.model.Category
import io.github.submark.core.model.MarkTiming
import io.github.submark.core.model.Tag
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.subscriptions.R
import io.github.submark.feature.subscriptions.data.PhotoStorage
import io.github.submark.feature.subscriptions.ui.common.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Dialogs opened from a card's context menu. */
sealed interface ListDialog {
    val item: SubscriptionItem

    data class MarkPaid(override val item: SubscriptionItem) : ListDialog
    data class Pause(override val item: SubscriptionItem) : ListDialog
    data class Activate(override val item: SubscriptionItem) : ListDialog
    data class Delete(override val item: SubscriptionItem, val hasWalletCharges: Boolean) : ListDialog
}

data class SubscriptionListUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.MIN,
    val segments: List<ListSegment> = listOf(ListSegment.SUBSCRIPTIONS, ListSegment.LIFETIME),
    val segment: ListSegment = ListSegment.SUBSCRIPTIONS,
    val filter: ListFilter = ListFilter(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val sortField: SortField = SortField.DATE,
    val sortDirection: SortDirection = SortDirection.ASC,
    val style: ListStyle = ListStyle.LIST,
    val folders: List<FolderEntry> = emptyList(),
    val items: List<SubscriptionItem> = emptyList(),
    val summary: ListSummary = ListSummary(0, "USD"),
    val listSettings: ListSettings = ListSettings(),
    val archiveMode: Boolean = true,
    /** Segment has items before filtering (distinguishes "empty" from "no matches"). */
    val segmentHasItems: Boolean = false,
    val symbols: Map<String, String> = emptyMap(),
    val dialog: ListDialog? = null,
)

@HiltViewModel
class SubscriptionListViewModel @Inject constructor(
    subscriptionRepository: SubscriptionRepository,
    categoryRepository: CategoryRepository,
    tagRepository: TagRepository,
    currencyRepository: CurrencyRepository,
    private val settingsRepository: SettingsRepository,
    private val subscriptionService: SubscriptionService,
    private val photoStorage: PhotoStorage,
    private val time: TimeProvider,
) : ViewModel() {

    private val filter = MutableStateFlow(ListFilter())
    private val styleOverride = MutableStateFlow<ListStyle?>(null)
    private val dialog = MutableStateFlow<ListDialog?>(null)
    private val snackbar = Channel<SnackbarMessage>(Channel.BUFFERED)
    val messages: Flow<SnackbarMessage> = snackbar.receiveAsFlow()

    private data class Sources(
        val items: List<SubscriptionItem>,
        val categories: List<Category>,
        val tags: List<Tag>,
        val folders: List<io.github.submark.core.data.repository.TagFolderWithTags>,
        val settings: AppSettings,
    )

    private val sources = combine(
        subscriptionRepository.observeItems(),
        categoryRepository.observeVisible(),
        tagRepository.observeTags(),
        tagRepository.observeFolders(),
        settingsRepository.settings,
    ) { items, categories, tags, folders, settings -> Sources(items, categories, tags, folders, settings) }

    private val money = combine(currencyRepository.observeConverter(), currencyRepository.observeCurrencies()) { converter, currencies ->
        converter to currencies.associate { it.code to it.symbol }
    }

    private val local = combine(filter, styleOverride, dialog) { f, s, d -> Triple(f, s, d) }

    val uiState: StateFlow<SubscriptionListUiState> = combine(sources, money, local) { src, (converter, symbols), (f, override, d) ->
        build(src, converter, symbols, f, override, d)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionListUiState())

    private fun build(
        src: Sources,
        converter: CurrencyConverter,
        symbols: Map<String, String>,
        f: ListFilter,
        override: ListStyle?,
        d: ListDialog?,
    ): SubscriptionListUiState {
        val prefs = src.settings.subscriptions
        val segments = buildList {
            add(ListSegment.SUBSCRIPTIONS)
            add(ListSegment.LIFETIME)
            if (prefs.wishlistEnabled) add(ListSegment.WISHLIST)
        }
        val segment = prefs.lastSegment.takeIf { it in segments } ?: ListSegment.SUBSCRIPTIONS
        val currency = src.settings.money.defaultCurrencyCode
        val inSegment = SubscriptionListLogic.segmentItems(src.items, segment, prefs)
        // A filter on a category/tag that no longer exists is dropped silently.
        val effective = f.copy(
            categoryId = f.categoryId?.takeIf { id -> src.categories.any { it.id == id } },
            tagIds = f.tagIds.filter { id -> src.tags.any { it.id == id } }.toSet(),
        )
        val filtered = SubscriptionListLogic.filter(inSegment, effective)
        val sorted = SubscriptionListLogic.sort(filtered, prefs.sortField, prefs.sortDirection, segment, converter, currency)
        val partition = SubscriptionListLogic.partitionFolders(sorted, src.folders, converter, currency)
        val summary = SubscriptionListLogic.summary(summaryItems(src.items, filtered, prefs.showChildSubscriptions), converter, currency)
        val style = override ?: when (src.settings.list.defaultStyle) {
            DefaultListStyle.LIST -> ListStyle.LIST
            DefaultListStyle.GRID -> ListStyle.GRID
            DefaultListStyle.LAST_USED -> src.settings.list.lastStyle
        }
        return SubscriptionListUiState(
            loading = false,
            today = time.today(),
            segments = segments,
            segment = segment,
            filter = effective,
            categories = src.categories,
            tags = src.tags,
            sortField = prefs.sortField,
            sortDirection = prefs.sortDirection,
            style = style,
            folders = partition.folders,
            items = partition.main,
            summary = summary.copy(count = filtered.size),
            listSettings = src.settings.list,
            archiveMode = prefs.archiveMode,
            segmentHasItems = inSegment.isNotEmpty(),
            symbols = symbols,
            dialog = d,
        )
    }

    /** Hidden bundle children still cost money: include the children of listed MAIN items in the totals. */
    private fun summaryItems(all: List<SubscriptionItem>, shown: List<SubscriptionItem>, childrenShown: Boolean): List<SubscriptionItem> {
        if (childrenShown) return shown
        val mains = shown.filter { it.subscription.bundleRole == BundleRole.MAIN }.map { it.id }.toSet()
        if (mains.isEmpty()) return shown
        return shown + all.filter { it.subscription.parentId in mains && it.subscription.status == io.github.submark.core.model.SubscriptionStatus.ACTIVE }
    }

    fun selectSegment(segment: ListSegment) {
        viewModelScope.launch { settingsRepository.update { it.copy(subscriptions = it.subscriptions.copy(lastSegment = segment)) } }
    }

    fun setQuery(query: String) = filter.update { it.copy(query = query) }

    fun setCategory(categoryId: String?) = filter.update { it.copy(categoryId = categoryId) }

    fun setTags(tagIds: Set<String>) = filter.update { it.copy(tagIds = tagIds) }

    fun clearFilters() = filter.update { ListFilter() }

    fun setSort(field: SortField, direction: SortDirection) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(subscriptions = it.subscriptions.copy(sortField = field, sortDirection = direction)) }
        }
    }

    fun toggleStyle() {
        val next = if (uiState.value.style == ListStyle.LIST) ListStyle.GRID else ListStyle.LIST
        styleOverride.value = next
        viewModelScope.launch { settingsRepository.update { it.copy(list = it.list.copy(lastStyle = next)) } }
    }

    fun requestMarkPaid(item: SubscriptionItem) { dialog.value = ListDialog.MarkPaid(item) }

    fun requestPause(item: SubscriptionItem) { dialog.value = ListDialog.Pause(item) }

    fun requestActivate(item: SubscriptionItem) { dialog.value = ListDialog.Activate(item) }

    fun requestDelete(item: SubscriptionItem) {
        viewModelScope.launch { dialog.value = ListDialog.Delete(item, subscriptionService.hasWalletCharges(item.id)) }
    }

    fun dismissDialog() { dialog.value = null }

    fun markPaid(item: SubscriptionItem, timing: MarkTiming) {
        dialog.value = null
        val newCycle = timing == MarkTiming.EARLY_NEW_CYCLE || timing == MarkTiming.OVERDUE_NEW_CYCLE
        viewModelScope.launch {
            report(subscriptionService.markPaid(item.id, newCycle), UiText.res(R.string.subscriptions_list_marked_paid, item.subscription.name))
        }
    }

    fun pause(item: SubscriptionItem) {
        dialog.value = null
        viewModelScope.launch { report(subscriptionService.pause(item.id), UiText.res(R.string.subscriptions_list_paused, item.subscription.name)) }
    }

    fun activate(item: SubscriptionItem) {
        dialog.value = null
        viewModelScope.launch { report(subscriptionService.activate(item.id), UiText.res(R.string.subscriptions_list_activated, item.subscription.name)) }
    }

    fun delete(item: SubscriptionItem, reverseWalletCharges: Boolean) {
        dialog.value = null
        viewModelScope.launch {
            val result = subscriptionService.delete(item.id, reverseWalletCharges)
            result.getOrNull()?.let { photoStorage.delete(it.photoFileNames) }
            report(result, UiText.res(R.string.subscriptions_list_deleted, item.subscription.name))
        }
    }

    fun noteCopied() {
        snackbar.trySend(SnackbarMessage(UiText.res(R.string.subscriptions_list_note_copied)))
    }

    private fun report(result: DataResult<*>, success: UiText) {
        val error: DataError? = result.errorOrNull()
        snackbar.trySend(SnackbarMessage(error?.toUiText() ?: success))
    }
}
