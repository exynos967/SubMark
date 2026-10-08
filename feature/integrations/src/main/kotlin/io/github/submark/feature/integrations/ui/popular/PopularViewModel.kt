package io.github.submark.feature.integrations.ui.popular

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.settings.PopularRegionScope
import io.github.submark.core.data.settings.PopularSort
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.model.SubscriptionPrefill
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.integrations.R
import io.github.submark.feature.integrations.data.popular.BundlePrefillMode
import io.github.submark.feature.integrations.data.popular.CatalogEntry
import io.github.submark.feature.integrations.data.popular.CatalogParser
import io.github.submark.feature.integrations.data.popular.PopularService
import io.github.submark.feature.integrations.data.popular.PricingOption
import io.github.submark.feature.integrations.data.popular.RepoCategory
import io.github.submark.feature.integrations.ui.common.uiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

data class PopularUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val entries: List<CatalogEntry> = emptyList(),
    val query: String = "",
    val category: RepoCategory? = null,
    val scope: PopularRegionScope = PopularRegionScope.LOCAL,
    val sort: PopularSort = PopularSort.NAME,
    /** Device/store region used by scope LOCAL (uppercase). */
    val storeRegion: String = "US",
    val noRepositories: Boolean = false,
    /** Message when the whole load failed / nothing to show for data reasons. */
    val loadError: UiText? = null,
    val detail: CatalogEntry? = null,
) {
    val filtered: List<CatalogEntry>
        get() {
            val q = query.trim().lowercase()
            return entries
                .asSequence()
                .filter { category == null || it.category == category }
                .filter {
                    q.isEmpty() || it.name.lowercase().contains(q) || it.tags.any { t -> t.lowercase().contains(q) }
                }
                .filter { entry ->
                    when (scope) {
                        PopularRegionScope.ALL -> true
                        PopularRegionScope.LOCAL ->
                            entry.region.isEmpty() ||
                                entry.region.any { it.equals("Global", true) || it.equals(storeRegion, true) }
                    }
                }
                .sortedWith(
                    when (sort) {
                        PopularSort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                        PopularSort.CATEGORY -> compareBy<CatalogEntry> { it.category }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    },
                )
                .toList()
        }
}

@HiltViewModel
class PopularViewModel @Inject constructor(
    private val service: PopularService,
    private val settings: SettingsRepository,
    private val json: Json,
) : ViewModel() {

    private val local = MutableStateFlow(PopularUiState())

    val state: StateFlow<PopularUiState> =
        combine(local, settings.settings) { l, s ->
            l.copy(scope = s.integrations.popularRegionScope, sort = s.integrations.popularSort)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PopularUiState())

    private val messagesCh = Channel<SnackbarMessage>(Channel.BUFFERED)
    val messages = messagesCh.receiveAsFlow()

    private val prefillCh = Channel<String>(Channel.BUFFERED)
    /** JSON of [SubscriptionPrefill]; the route navigates to the edit form. */
    val prefill = prefillCh.receiveAsFlow()

    init {
        refresh(force = false)
    }

    fun refresh(force: Boolean) {
        viewModelScope.launch {
            local.update { it.copy(loading = !force && it.entries.isEmpty(), refreshing = force, loadError = null) }
            val region = settings.settings.first().integrations.storeRegion?.uppercase(Locale.US)
                ?: Locale.getDefault().country.uppercase(Locale.US).ifBlank { "US" }
            try {
                val loaded = service.loadCatalog(forceRefresh = force)
                local.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        entries = loaded.entries,
                        noRepositories = loaded.noRepositories,
                        storeRegion = region,
                        loadError = when {
                            loaded.noRepositories -> null // dedicated empty state
                            loaded.entries.isEmpty() && loaded.failures > 0 ->
                                UiText.res(R.string.integrations_popular_load_failed)
                            else -> null
                        },
                    )
                }
                if (loaded.failures > 0 && loaded.entries.isNotEmpty()) {
                    messagesCh.send(
                        SnackbarMessage(UiText.plural(R.plurals.integrations_popular_failed_repos, loaded.failures)),
                    )
                }
            } catch (e: Exception) {
                local.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        storeRegion = region,
                        loadError = e.uiMessage(UiText.res(R.string.integrations_popular_load_failed)),
                    )
                }
            }
        }
    }

    fun setQuery(query: String) = local.update { it.copy(query = query) }

    fun setCategory(category: RepoCategory?) = local.update { it.copy(category = category) }

    fun setScope(scope: PopularRegionScope) {
        viewModelScope.launch { settings.update { it.copy(integrations = it.integrations.copy(popularRegionScope = scope)) } }
    }

    fun setSort(sort: PopularSort) {
        viewModelScope.launch { settings.update { it.copy(integrations = it.integrations.copy(popularSort = sort)) } }
    }

    fun openDetail(entry: CatalogEntry?) = local.update { it.copy(detail = entry) }

    /** Price option chosen on a non-bundle entry (or bundle in [mode]). */
    fun chooseOption(entry: CatalogEntry, option: PricingOption, mode: BundlePrefillMode) {
        local.update { it.copy(detail = null) }
        viewModelScope.launch {
            prefillCh.send(json.encodeToString(SubscriptionPrefill.serializer(), CatalogParser.toPrefill(entry, option, mode)))
        }
    }
}
