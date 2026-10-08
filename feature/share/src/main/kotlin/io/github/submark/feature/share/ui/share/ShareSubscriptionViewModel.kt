package io.github.submark.feature.share.ui.share

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.settings.PosterStyle
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.model.Category
import io.github.submark.core.model.Subscription
import io.github.submark.core.ui.navigation.ShareSubscriptionRoute
import io.github.submark.feature.share.data.QrPayloadCodec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShareUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val subscription: Subscription? = null,
    val category: Category? = null,
    val children: List<Subscription> = emptyList(),
    val childCategories: Map<String, Category?> = emptyMap(),
    val sharerName: String = "",
    val description: String = "",
    val showQr: Boolean = true,
    val posterStyle: PosterStyle = PosterStyle.MODERN,
    /** null until the user enables the QR toggle for the first time (drives the privacy dialog). */
    val qrPrivacyAcknowledged: Boolean = true,
    val includePrivate: Boolean = false,
    val qrContent: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShareSubscriptionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val settings: SettingsRepository,
    subscriptions: SubscriptionRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ShareSubscriptionRoute>()

    private val childrenFlow = subscriptions.observeChildren(route.subscriptionId)
    private val itemFlow = subscriptions.observeItem(route.subscriptionId)
    private val includePrivateFlow = MutableStateFlow(false)

    /** Local (not yet persisted) edits on top of settings so typing stays smooth. */
    private val localEditsFlow = MutableStateFlow<LocalEdits?>(null)

    private data class LocalEdits(
        val sharerName: String,
        val description: String,
        val showQr: Boolean,
        val posterStyle: PosterStyle,
    )

    val uiState: StateFlow<ShareUiState> = combine(
        itemFlow,
        childrenFlow,
        settings.settings,
        includePrivateFlow,
        localEditsFlow,
    ) { item, children, s, includePrivate, local ->
        val sub = item?.subscription
        if (sub == null) {
            return@combine ShareUiState(loading = false, notFound = true)
        }
        val edits = local ?: LocalEdits(
            sharerName = s.share.sharerName,
            description = s.share.lastDescription,
            showQr = s.share.showQr,
            posterStyle = s.share.posterStyle,
        )
        val childCategories = emptyMap<String, Category?>()
        val qrContent = if (edits.showQr) {
            QrPayloadCodec.encode(
                QrPayloadCodec.fromSubscription(
                    sub = sub,
                    category = item.category,
                    sharerName = edits.sharerName,
                    description = edits.description,
                    includePrivate = includePrivate,
                    children = children,
                    childCategories = childCategories,
                ),
            )
        } else null
        ShareUiState(
            loading = false,
            subscription = sub,
            category = item.category,
            children = children,
            childCategories = childCategories,
            sharerName = edits.sharerName,
            description = edits.description,
            showQr = edits.showQr,
            posterStyle = edits.posterStyle,
            qrPrivacyAcknowledged = s.share.qrPrivacyAcknowledged,
            includePrivate = includePrivate,
            qrContent = qrContent,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShareUiState())

    fun setSharerName(name: String) {
        edit { it.copy(sharerName = name) }
        viewModelScope.launch { settings.update { s -> s.copy(share = s.share.copy(sharerName = name)) } }
    }

    fun setDescription(text: String) {
        edit { it.copy(description = text) }
        viewModelScope.launch { settings.update { s -> s.copy(share = s.share.copy(lastDescription = text)) } }
    }

    fun setShowQr(show: Boolean) {
        // When toggled on for the first time the caller shows the privacy dialog first and only
        // then invokes this with acknowledged=true.
        edit { it.copy(showQr = show) }
        viewModelScope.launch { settings.update { s -> s.copy(share = s.share.copy(showQr = show)) } }
    }

    fun confirmQrPrivacy(includePrivate: Boolean) {
        includePrivateFlow.value = includePrivate
        viewModelScope.launch {
            settings.update { s -> s.copy(share = s.share.copy(qrPrivacyAcknowledged = true, showQr = true)) }
        }
        edit { it.copy(showQr = true) }
    }

    fun setPosterStyle(style: PosterStyle) {
        edit { it.copy(posterStyle = style) }
        viewModelScope.launch { settings.update { s -> s.copy(share = s.share.copy(posterStyle = style)) } }
    }

    private fun edit(transform: (LocalEdits) -> LocalEdits) {
        val state = uiState.value
        val base = localEditsFlow.value ?: LocalEdits(
            sharerName = state.sharerName,
            description = state.description,
            showQr = state.showQr,
            posterStyle = state.posterStyle,
        )
        localEditsFlow.value = transform(base)
    }
}
