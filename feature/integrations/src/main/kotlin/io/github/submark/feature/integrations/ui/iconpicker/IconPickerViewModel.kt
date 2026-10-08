package io.github.submark.feature.integrations.ui.iconpicker

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.model.IconRepository
import io.github.submark.core.model.IconType
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.icon.IconCatalog
import io.github.submark.core.ui.icon.IconChoice
import io.github.submark.core.ui.icon.IconEntry
import io.github.submark.core.ui.navigation.IconPickerRoute
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.integrations.R
import io.github.submark.feature.integrations.data.CountryCatalog
import io.github.submark.feature.integrations.data.icons.IconPack
import io.github.submark.feature.integrations.data.icons.IconPackIcon
import io.github.submark.feature.integrations.data.icons.IconPackService
import io.github.submark.feature.integrations.data.icons.LocalIconStorage
import io.github.submark.feature.integrations.data.icons.WebsiteIconService
import io.github.submark.feature.integrations.data.itunes.ItunesResult
import io.github.submark.feature.integrations.data.itunes.ItunesService
import io.github.submark.feature.integrations.data.rawg.RawgGame
import io.github.submark.feature.integrations.data.rawg.RawgService
import io.github.submark.feature.integrations.ui.common.uiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

enum class IconPickerTab { SYSTEM, EMOJI, APP_STORE, WEBSITE, REPOSITORY, GAMES, PHOTO }

data class IconPickerUiState(
    val tab: IconPickerTab = IconPickerTab.SYSTEM,
    /** System tab. */
    val systemQuery: String = "",
    val systemIcons: List<IconEntry> = IconCatalog.all,
    /** App Store tab. */
    val storeQuery: String = "",
    val storeRegion: String = "US",
    val storeSearching: Boolean = false,
    val storeResults: List<ItunesResult> = emptyList(),
    val storeSearched: Boolean = false,
    val showRegionPicker: Boolean = false,
    /** Website tab. */
    val domain: String = "",
    val resolving: Boolean = false,
    val recentDomains: List<String> = emptyList(),
    /** Repository tab. */
    val iconRepos: List<IconRepository> = emptyList(),
    val repoQuery: String = "",
    val repoLoading: Boolean = false,
    val packs: List<IconPackIcon> = emptyList(),
    val repoError: UiText? = null,
    val showRepoAdd: Boolean = false,
    /** Games tab. */
    val gamesQuery: String = "",
    val gamesSearching: Boolean = false,
    val gamesResults: List<RawgGame> = emptyList(),
    val gamesSearched: Boolean = false,
    val rawgConfigured: Boolean = false,
    /** Photo tab. */
    val saving: Boolean = false,
    /** Preview / confirm. */
    val picked: IconChoice? = null,
)

/** A trimmed emoji palette covering common subscription categories; free input is supported too. */
val EMOJI_GRID: List<String> = (
    "🎬 🎵 🎮 📚 📰 💼 🛠 🛒 💳 🏦 ✈️ 🏠 🚗 🍔 ☕ 🍺 💊 🏋 🧘 🐶 " +
        "👶 👔 💄 🎓 💻 📱 🎧 📷 🎨 🎲 ⚽ 🏀 🎾 🚴 🏊 🎤 🎹 🎸 🎻 " +
        "📺 💡 🔒 🔧 ⏰ 📅 💰 💎 🎁 🚀 🌍 ❤️ ⭐ 🔥 ✅ ⚡ 🌙 ☀️ 🌈 " +
        "☁️ 🌧 ❄️ 🌊 🌳 🌸 🍎 🍕 🍜 🍷 🥗 🧋 🧼 🪥 💉 🩹 🧠 👀 🦷"
    ).split(" ").filter { it.isNotEmpty() }

@HiltViewModel
class IconPickerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val itunes: ItunesService,
    private val websiteIcons: WebsiteIconService,
    private val packs: IconPackService,
    private val rawg: RawgService,
    private val photos: LocalIconStorage,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val seedQuery = savedStateHandle.toRoute<IconPickerRoute>().query.orEmpty()

    private val local = MutableStateFlow(IconPickerUiState(storeQuery = seedQuery, gamesQuery = seedQuery, systemQuery = seedQuery))

    val state: StateFlow<IconPickerUiState> =
        combine(local, packs.observeRepositories()) { l, repos -> l.copy(iconRepos = repos) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), local.value)

    private val messagesCh = Channel<SnackbarMessage>(Channel.BUFFERED)
    val messages = messagesCh.receiveAsFlow()

    private val resultCh = Channel<IconChoice>(Channel.BUFFERED)
    val result = resultCh.receiveAsFlow()

    init {
        viewModelScope.launch {
            val region = settings.settings.first().integrations.storeRegion?.uppercase()
                ?: Locale.getDefault().country.uppercase().ifBlank { "US" }
            local.value = local.value.copy(storeRegion = region, rawgConfigured = rawg.apiKey() != null)
        }
    }

    fun setTab(tab: IconPickerTab) = set { copy(tab = tab) }

    private inline fun set(transform: IconPickerUiState.() -> IconPickerUiState) {
        local.value = local.value.transform()
    }

    fun setSystemQuery(query: String) = set { copy(systemQuery = query, systemIcons = IconCatalog.search(query)) }

    fun pickSystem(name: String) = confirm(IconChoice(IconType.SYMBOL, name))

    fun pickEmoji(emoji: String) {
        if (emoji.isBlank()) return
        confirm(IconChoice(IconType.EMOJI, emoji.trim()))
    }

    // ---- App Store ----

    fun setStoreQuery(query: String) = set { copy(storeQuery = query) }

    fun setRegionPicker(show: Boolean) = set { copy(showRegionPicker = show) }

    fun setStoreRegion(code: String) {
        set { copy(storeRegion = code, showRegionPicker = false, storeResults = emptyList(), storeSearched = false) }
        viewModelScope.launch {
            settings.update { it.copy(integrations = it.integrations.copy(storeRegion = code.lowercase())) }
        }
    }

    fun searchStore() {
        val s = local.value
        if (s.storeQuery.isBlank() || s.storeSearching) return
        viewModelScope.launch {
            set { copy(storeSearching = true) }
            itunes.searchApps(local.value.storeQuery, local.value.storeRegion)
                .onSuccess { results ->
                    set { copy(storeSearching = false, storeResults = results, storeSearched = true) }
                }
                .onFailure { e ->
                    set { copy(storeSearching = false, storeSearched = true) }
                    messagesCh.send(SnackbarMessage(e.uiMessage()))
                }
        }
    }

    fun pickStoreApp(app: ItunesResult) {
        val url = app.artworkUrl512 ?: app.artworkUrl100
        if (url == null) {
            viewModelScope.launch { messagesCh.send(SnackbarMessage(UiText.res(R.string.integrations_icon_no_artwork))) }
            return
        }
        confirm(IconChoice(IconType.URL, url), local.value.storeQuery.takeIf { it.isBlank() }?.let { app.trackName })
    }

    // ---- Website ----

    fun setDomain(domain: String) = set { copy(domain = domain) }

    fun resolveDomain() {
        val s = local.value
        val domain = websiteIcons.normalizeDomain(s.domain) ?: run {
            viewModelScope.launch { messagesCh.send(SnackbarMessage(UiText.res(R.string.integrations_icon_invalid_domain))) }
            return
        }
        if (s.resolving) return
        viewModelScope.launch {
            set { copy(resolving = true) }
            val url = websiteIcons.resolve(domain)
            set {
                copy(
                    resolving = false,
                    recentDomains = (listOf(domain) + recentDomains.filterNot { it == domain }).take(8),
                )
            }
            if (url == null) {
                messagesCh.send(SnackbarMessage(UiText.res(R.string.integrations_icon_no_website)))
            } else {
                confirm(IconChoice(IconType.URL, url))
            }
        }
    }

    // ---- Repository ----

    fun setRepoQuery(query: String) = set { copy(repoQuery = query) }

    fun setRepoAdd(show: Boolean) = set { copy(showRepoAdd = show) }

    fun addRepo(name: String, url: String) {
        if (name.isBlank() || url.isBlank()) return
        viewModelScope.launch {
            set { copy(repoLoading = true, repoError = null, showRepoAdd = false) }
            val repo = packs.add(name, url)
            loadPack(repo, refresh = true)
        }
    }

    fun deleteRepo(repo: IconRepository) {
        viewModelScope.launch {
            packs.delete(repo)
            set { copy(packs = emptyList()) }
        }
    }

    fun loadPack(repo: IconRepository, refresh: Boolean = false) {
        viewModelScope.launch {
            set { copy(repoLoading = true, repoError = null) }
            packs.fetch(repo)
                .onSuccess { pack -> set { copy(repoLoading = false, packs = pack.icons) } }
                .onFailure { e -> set { copy(repoLoading = false, repoError = e.uiMessage()) } }
        }
    }

    fun pickPackIcon(icon: IconPackIcon) = confirm(IconChoice(IconType.URL, icon.url))

    // ---- Games (RAWG) ----

    fun setGamesQuery(query: String) = set { copy(gamesQuery = query) }

    fun searchGames() {
        val s = local.value
        if (s.gamesQuery.isBlank() || s.gamesSearching) return
        viewModelScope.launch {
            set { copy(gamesSearching = true) }
            rawg.search(local.value.gamesQuery)
                .onSuccess { results ->
                    set { copy(gamesSearching = false, gamesResults = results, gamesSearched = true) }
                }
                .onFailure { e ->
                    set { copy(gamesSearching = false, gamesSearched = true) }
                    messagesCh.send(SnackbarMessage(e.uiMessage()))
                }
        }
    }

    fun pickGame(game: RawgGame) {
        val url = game.backgroundImage ?: run {
            viewModelScope.launch { messagesCh.send(SnackbarMessage(UiText.res(R.string.integrations_icon_no_cover))) }
            return
        }
        confirm(IconChoice(IconType.URL, url))
    }

    // ---- Photo ----

    fun onPhotoPicked(uri: Uri) {
        viewModelScope.launch {
            set { copy(saving = true) }
            val fileName = photos.savePhoto(uri)
            set { copy(saving = false) }
            if (fileName == null) {
                messagesCh.send(SnackbarMessage(UiText.res(R.string.integrations_icon_photo_failed)))
            } else {
                confirm(IconChoice(IconType.FILE, fileName))
            }
        }
    }

    // ---- Confirm ----

    /** Finalize: show a small confirm when the choice is remote/photo; symbols/emoji finish right away. */
    private fun confirm(choice: IconChoice, unused: String? = null) {
        when (choice.type) {
            IconType.SYMBOL, IconType.EMOJI -> viewModelScope.launch { resultCh.send(choice) }
            else -> set { copy(picked = choice) }
        }
    }

    fun confirmPicked() {
        val choice = local.value.picked ?: return
        set { copy(picked = null) }
        viewModelScope.launch { resultCh.send(choice) }
    }

    fun dismissPicked() = set { copy(picked = null) }

    fun countryName(code: String): String = CountryCatalog.nameOf(code)
}
