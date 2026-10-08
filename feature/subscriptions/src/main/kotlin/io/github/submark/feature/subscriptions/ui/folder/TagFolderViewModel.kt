package io.github.submark.feature.subscriptions.ui.folder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.currency.CurrencyRepository
import io.github.submark.core.data.repository.SubscriptionItem
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.repository.TagFolderWithTags
import io.github.submark.core.data.repository.TagRepository
import io.github.submark.core.data.settings.ListSettings
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.settings.SubscriptionPrefs
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.Tag
import io.github.submark.core.ui.navigation.TagFolderRoute
import io.github.submark.feature.subscriptions.ui.list.ListSummary
import io.github.submark.feature.subscriptions.ui.list.SubscriptionListLogic
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class TagFolderUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val folder: TagFolderWithTags? = null,
    val folderTags: List<Tag> = emptyList(),
    val items: List<SubscriptionItem> = emptyList(),
    val summary: ListSummary? = null,
    val today: LocalDate = LocalDate.MIN,
    val listSettings: ListSettings = ListSettings(),
    val symbols: Map<String, String> = emptyMap(),
)

object TagFolderLogic {
    /** Non-wishlist, non-archived items matching [folder] (children only when shown), sorted by name. */
    fun members(items: List<SubscriptionItem>, folder: TagFolderWithTags, prefs: SubscriptionPrefs): List<SubscriptionItem> =
        items.filter { item ->
            item.subscription.kind != SubscriptionKind.WISHLIST &&
                !SubscriptionListLogic.isArchived(item, prefs) &&
                !SubscriptionListLogic.isChildHidden(item, prefs) &&
                folder.matches(item.tags.map { it.id }.toSet())
        }.sortedBy { it.subscription.name.lowercase() }
}

@HiltViewModel
class TagFolderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    subscriptionRepository: SubscriptionRepository,
    tagRepository: TagRepository,
    currencyRepository: CurrencyRepository,
    settingsRepository: SettingsRepository,
    time: TimeProvider,
) : ViewModel() {
    private val folderId = savedStateHandle.toRoute<TagFolderRoute>().id

    val uiState: StateFlow<TagFolderUiState> = combine(
        subscriptionRepository.observeItems(),
        tagRepository.observeFolders(),
        tagRepository.observeTags(),
        settingsRepository.settings,
        combine(currencyRepository.observeConverter(), currencyRepository.observeCurrencies()) { c, list -> c to list },
    ) { items, folders, tags, settings, (converter, currencies) ->
        val folder = folders.firstOrNull { it.folder.id == folderId }
        if (folder == null) {
            TagFolderUiState(loading = false, notFound = true)
        } else {
            val members = TagFolderLogic.members(items, folder, settings.subscriptions)
            TagFolderUiState(
                loading = false,
                folder = folder,
                folderTags = tags.filter { it.id in folder.tagIds }.sortedBy { it.name.lowercase() },
                items = members,
                summary = SubscriptionListLogic.summary(members, converter, settings.money.defaultCurrencyCode),
                today = time.today(),
                listSettings = settings.list,
                symbols = currencies.associate { it.code to it.symbol },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagFolderUiState())
}
