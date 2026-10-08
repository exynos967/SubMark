package io.github.submark.feature.overview.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.repository.CategoryRepository
import io.github.submark.core.data.repository.PaymentMethodRepository
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.service.PaymentService
import io.github.submark.core.data.service.SharedService
import io.github.submark.core.data.settings.SearchSettings
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.model.IconType
import io.github.submark.core.model.PaymentKind
import io.github.submark.core.model.PaymentRecord
import io.github.submark.core.model.PaymentStatus
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.overview.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchScope(val labelRes: Int) {
    ALL(R.string.search_scope_all),
    SUBSCRIPTIONS(R.string.search_scope_subscriptions),
    PAYMENTS(R.string.search_scope_payments),
    CATEGORIES(R.string.search_scope_categories),
}

sealed interface SearchResult {
    val id: String
    val title: String
    val subtitle: String?
    val iconType: IconType?
    val iconValue: String?

    data class SubscriptionRow(
        val subscription: Subscription,
        override val title: String,
        override val subtitle: String?,
        override val iconType: IconType?,
        override val iconValue: String?,
    ) : SearchResult {
        override val id: String get() = subscription.id
    }

    data class PaymentRow(
        val record: PaymentRecord,
        val subscription: Subscription?,
        override val title: String,
        override val subtitle: String?,
        override val iconType: IconType?,
        override val iconValue: String?,
    ) : SearchResult {
        override val id: String get() = record.id
    }

    data class CategoryRow(
        val categoryId: String,
        override val title: String,
        override val subtitle: String?,
        override val iconType: IconType?,
        override val iconValue: String?,
    ) : SearchResult {
        override val id: String get() = categoryId
    }
}

data class GlobalSearchUiState(
    val query: String = "",
    val scope: SearchScope = SearchScope.ALL,
    val isSearching: Boolean = false,
    val recentSearches: List<String> = emptyList(),
    val results: List<SearchResult> = emptyList(),
    val hasQuery: Boolean = false,
    val showTips: Boolean = true,
)

sealed interface SearchNavTarget {
    data class OpenSubscription(val subscriptionId: String) : SearchNavTarget
    data class OpenPayment(val paymentId: String) : SearchNavTarget
    data class OpenCategory(val categoryId: String) : SearchNavTarget
}

@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    private val subscriptions: SubscriptionRepository,
    private val categories: CategoryRepository,
    private val payments: PaymentService,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val scopeFlow = MutableStateFlow(SearchScope.ALL)
    private val resultFlow = MutableStateFlow<List<SearchResult>>(emptyList())

    private val snackbarChannel = Channel<SnackbarMessage>(Channel.BUFFERED)
    val snackbars: Flow<SnackbarMessage> = snackbarChannel.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<GlobalSearchUiState> = combine(
        queryFlow,
        scopeFlow,
        settings.settings.map { it.search.recentSearches },
        resultFlow,
    ) { query, scope, recent, results ->
        GlobalSearchUiState(
            query = query,
            scope = scope,
            isSearching = false,
            recentSearches = recent,
            results = results,
            hasQuery = query.isNotBlank(),
            showTips = query.isBlank(),
        )
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), GlobalSearchUiState())

    init {
        viewModelScope.launch {
            queryFlow
                .debounce(250)
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        resultFlow.value = emptyList()
                        return@flatMapLatest kotlinx.coroutines.flow.flowOf(Unit)
                    }
                    runSearch(query, scopeFlow.value).map { resultFlow.value = it }
                }
                .collect { }
        }
    }

    private fun runSearch(query: String, scope: SearchScope): Flow<List<SearchResult>> {
        val q = query.trim().lowercase()
        return when (scope) {
            SearchScope.SUBSCRIPTIONS -> subscriptions.observeAll().map { toSubRows(q, it) }
            SearchScope.PAYMENTS -> combine(
                subscriptions.observeAll(),
                payments.observeAll(),
            ) { subs, pays -> toPaymentRows(q, subs, pays) }
            SearchScope.CATEGORIES -> categories.observeAll().map { toCategoryRows(q, it) }
            SearchScope.ALL -> combine(
                subscriptions.observeAll(),
                payments.observeAll(),
                categories.observeAll(),
            ) { subs, pays, cats ->
                toSubRows(q, subs) + toPaymentRows(q, subs, pays) + toCategoryRows(q, cats)
            }
        }
    }

    private fun toSubRows(query: String, subs: List<Subscription>): List<SearchResult.SubscriptionRow> =
        subs
            .filter { sub ->
                sub.kind != SubscriptionKind.WISHLIST &&
                    (sub.name.lowercase().contains(query) ||
                        sub.note?.lowercase()?.contains(query) == true ||
                        sub.website?.lowercase()?.contains(query) == true ||
                        sub.price.toString().contains(query) ||
                        sub.currencyCode.lowercase() == query)
            }
            .sortedWith(
                compareByDescending<Subscription> { it.name.lowercase().startsWith(query) }
                    .thenBy { it.name.lowercase() }
            )
            .map { sub ->
                SearchResult.SubscriptionRow(
                    subscription = sub,
                    title = sub.name,
                    subtitle = "${sub.currencyCode} ${sub.price}",
                    iconType = sub.iconType,
                    iconValue = sub.iconValue,
                )
            }

    private fun toPaymentRows(query: String, subs: List<Subscription>, pays: List<PaymentRecord>): List<SearchResult.PaymentRow> {
        val subById = subs.associateBy { it.id }
        return pays
            .filter { record ->
                if (record.status != PaymentStatus.SUCCESS) return@filter false
                record.amount.toString().contains(query) ||
                    record.note?.lowercase()?.contains(query) == true ||
                    record.paymentDate.toString().contains(query) ||
                    (subById[record.subscriptionId]?.name?.lowercase()?.contains(query) == true)
            }
            .sortedBy { it.paymentDate }
            .reversed()
            .take(30)
            .map { record ->
                val sub = subById[record.subscriptionId]
                SearchResult.PaymentRow(
                    record = record,
                    subscription = sub,
                    title = sub?.name ?: "Unknown subscription",
                    subtitle = "${record.paymentDate} · ${record.currencyCode} ${record.amount}",
                    iconType = sub?.iconType,
                    iconValue = sub?.iconValue,
                )
            }
    }

    private fun toCategoryRows(query: String, cats: List<io.github.submark.core.model.Category>): List<SearchResult.CategoryRow> =
        cats
            .filter { cat ->
                (cat.name?.lowercase()?.contains(query) == true) ||
                    (cat.systemKey?.name?.lowercase()?.contains(query) == true)
            }
            .map { cat ->
                SearchResult.CategoryRow(
                    categoryId = cat.id,
                    title = cat.name ?: (cat.systemKey?.name ?: "Category"),
                    subtitle = cat.systemKey?.name?.lowercase() ?: "Custom",
                    iconType = IconType.SYMBOL,
                    iconValue = cat.iconValue,
                )
            }

    fun onQueryChange(q: String) {
        queryFlow.value = q
    }

    fun onScopeChange(scope: SearchScope) {
        scopeFlow.value = scope
    }

    fun onRecentClick(query: String) {
        queryFlow.value = query
    }

    fun onClearRecent() {
        viewModelScope.launch {
            settings.update { s -> s.copy(search = s.search.copy(recentSearches = emptyList())) }
        }
    }

    fun onResultClick(result: SearchResult, onNavigate: (SearchNavTarget) -> Unit) {
        addRecent(queryFlow.value)
        when (result) {
            is SearchResult.SubscriptionRow -> onNavigate(SearchNavTarget.OpenSubscription(result.subscription.id))
            is SearchResult.PaymentRow -> onNavigate(SearchNavTarget.OpenPayment(result.record.id))
            is SearchResult.CategoryRow -> onNavigate(SearchNavTarget.OpenCategory(result.categoryId))
        }
    }

    private fun addRecent(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            settings.update { s ->
                val existing = s.search.recentSearches.toMutableList()
                existing.remove(query.trim())
                existing.add(0, query.trim())
                s.copy(search = s.search.copy(recentSearches = existing.take(SearchSettings.MAX_RECENT)))
            }
        }
    }
}
