package io.github.submark.feature.subscriptions.ui.archive

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.currency.CurrencyRepository
import io.github.submark.core.data.repository.SubscriptionItem
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.service.SubscriptionService
import io.github.submark.core.data.settings.ListSettings
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.navigation.ArchiveRoute
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.subscriptions.R
import io.github.submark.feature.subscriptions.ui.common.toUiText
import io.github.submark.feature.subscriptions.ui.list.ListFilter
import io.github.submark.feature.subscriptions.ui.list.SubscriptionListLogic
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class ArchiveUiState(
    val loading: Boolean = true,
    val lifetime: Boolean = false,
    val query: String = "",
    val items: List<SubscriptionItem> = emptyList(),
    val hasAny: Boolean = false,
    val today: LocalDate = LocalDate.MIN,
    val listSettings: ListSettings = ListSettings(),
    val symbols: Map<String, String> = emptyMap(),
    val confirmRestore: SubscriptionItem? = null,
    val zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
)

/** Paused (archived) items, newest first. Non-recurring wishlist items never appear here. */
object ArchiveLogic {
    fun archived(items: List<SubscriptionItem>, lifetime: Boolean, query: String): List<SubscriptionItem> =
        items.filter { item ->
            val sub = item.subscription
            sub.status == SubscriptionStatus.PAUSED &&
                if (lifetime) sub.kind == SubscriptionKind.LIFETIME else sub.kind == SubscriptionKind.REGULAR || sub.kind == SubscriptionKind.STORED_VALUE
        }
            .filter { SubscriptionListLogic.matches(it, ListFilter(query = query)) }
            .sortedWith(compareByDescending<SubscriptionItem> { it.subscription.pausedAt ?: Instant.EPOCH }.thenBy { it.subscription.name.lowercase() })
}

@HiltViewModel
class ArchiveViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    subscriptionRepository: SubscriptionRepository,
    currencyRepository: CurrencyRepository,
    settingsRepository: SettingsRepository,
    private val subscriptionService: SubscriptionService,
    private val time: TimeProvider,
) : ViewModel() {
    private val lifetime = savedStateHandle.toRoute<ArchiveRoute>().lifetime
    private val query = MutableStateFlow("")
    private val confirm = MutableStateFlow<SubscriptionItem?>(null)
    private val snackbar = Channel<SnackbarMessage>(Channel.BUFFERED)
    val messages: Flow<SnackbarMessage> = snackbar.receiveAsFlow()

    val uiState: StateFlow<ArchiveUiState> = combine(
        subscriptionRepository.observeItems(),
        settingsRepository.settings,
        currencyRepository.observeCurrencies(),
        query,
        confirm,
    ) { items, settings, currencies, q, c ->
        val all = ArchiveLogic.archived(items, lifetime, "")
        ArchiveUiState(
            loading = false,
            lifetime = lifetime,
            query = q,
            items = if (q.isBlank()) all else ArchiveLogic.archived(items, lifetime, q),
            hasAny = all.isNotEmpty(),
            today = time.today(),
            listSettings = settings.list,
            symbols = currencies.associate { it.code to it.symbol },
            confirmRestore = c,
            zone = time.zone(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchiveUiState(lifetime = lifetime))

    fun setQuery(value: String) { query.value = value }

    fun requestRestore(item: SubscriptionItem) { confirm.value = item }

    fun dismiss() { confirm.value = null }

    fun restore(item: SubscriptionItem) {
        confirm.value = null
        viewModelScope.launch {
            val error = subscriptionService.restoreFromArchive(item.id).errorOrNull()
            snackbar.trySend(SnackbarMessage(error?.toUiText() ?: UiText.res(R.string.subscriptions_archive_restored, item.subscription.name)))
        }
    }
}
