package io.github.submark.feature.integrations.ui.iconpicker

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.model.IconType
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.SearchField
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.icon.IconCatalog
import io.github.submark.core.ui.icon.IconChoice
import io.github.submark.core.ui.icon.SubscriptionIcon
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.feature.integrations.R
import io.github.submark.feature.integrations.data.icons.IconPackIcon
import io.github.submark.feature.integrations.ui.common.CountryPickerSheet

@Composable
fun IconPickerRoute(
    onBack: () -> Unit,
    onResult: (IconChoice) -> Unit,
    onOpenRawgSettings: () -> Unit,
    viewModel: IconPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    SnackbarEffect(viewModel.messages, snackbarHost)
    androidx.compose.runtime.LaunchedEffect(viewModel.result) {
        viewModel.result.collect { choice -> onResult(choice) }
    }
    IconPickerScreen(
        state = state,
        snackbarHost = snackbarHost,
        onBack = onBack,
        vm = viewModel,
        onOpenRawgSettings = onOpenRawgSettings,
    )
}

@Composable
fun IconPickerScreen(
    state: IconPickerUiState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    vm: IconPickerViewModel,
    onOpenRawgSettings: () -> Unit,
) {
    Scaffold(
        topBar = { SubMarkTopAppBar(title = stringResource(R.string.integrations_icon_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val tabs = IconPickerTab.entries
            ScrollableTabRow(selectedTabIndex = tabs.indexOf(state.tab), edgePadding = 8.dp) {
                tabs.forEach { tab ->
                    Tab(
                        selected = state.tab == tab,
                        onClick = { vm.setTab(tab) },
                        text = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
            when (state.tab) {
                IconPickerTab.SYSTEM -> SystemTab(state, vm)
                IconPickerTab.EMOJI -> EmojiTab(vm)
                IconPickerTab.APP_STORE -> AppStoreTab(state, vm)
                IconPickerTab.WEBSITE -> WebsiteTab(state, vm)
                IconPickerTab.REPOSITORY -> RepositoryTab(state, vm)
                IconPickerTab.GAMES -> GamesTab(state, vm, onOpenRawgSettings)
                IconPickerTab.PHOTO -> PhotoTab(state, vm)
            }
        }
    }

    if (state.showRegionPicker) {
        CountryPickerSheet(
            selectedCode = state.storeRegion,
            onSelect = vm::setStoreRegion,
            onDismiss = { vm.setRegionPicker(false) },
        )
    }
    if (state.showRepoAdd) {
        RepoAddSheet(onAdd = vm::addRepo, onDismiss = { vm.setRepoAdd(false) })
    }
    state.picked?.let { choice ->
        AlertDialog(
            onDismissRequest = vm::dismissPicked,
            title = { Text(stringResource(R.string.integrations_icon_preview)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SubscriptionIcon(type = choice.type, value = choice.value, fallbackName = "?", size = 64.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.integrations_icon_cached_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = vm::confirmPicked) {
                    Text(stringResource(io.github.submark.core.ui.R.string.ui_action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissPicked) {
                    Text(stringResource(io.github.submark.core.ui.R.string.ui_action_cancel))
                }
            },
        )
    }
}

private val IconPickerTab.labelRes: Int
    get() = when (this) {
        IconPickerTab.SYSTEM -> R.string.integrations_icon_tab_system
        IconPickerTab.EMOJI -> R.string.integrations_icon_tab_emoji
        IconPickerTab.APP_STORE -> R.string.integrations_icon_tab_app_store
        IconPickerTab.WEBSITE -> R.string.integrations_icon_tab_website
        IconPickerTab.REPOSITORY -> R.string.integrations_icon_tab_repository
        IconPickerTab.GAMES -> R.string.integrations_icon_tab_games
        IconPickerTab.PHOTO -> R.string.integrations_icon_tab_photo
    }

@Composable
private fun SystemTab(state: IconPickerUiState, vm: IconPickerViewModel) {
    Column(Modifier.fillMaxSize()) {
        SearchField(
            query = state.systemQuery,
            onQueryChange = vm::setSystemQuery,
            placeholder = stringResource(R.string.integrations_icon_search_system),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(64.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(state.systemIcons, key = { it.name }) { entry ->
                IconButton(onClick = { vm.pickSystem(entry.name) }, modifier = Modifier.aspectRatio(1f)) {
                    Icon(entry.vector, contentDescription = entry.name)
                }
            }
        }
    }
}

@Composable
private fun EmojiTab(vm: IconPickerViewModel) {
    var free by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = free,
            onValueChange = { free = it },
            label = { Text(stringResource(R.string.integrations_icon_emoji_input)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.pickEmoji(free); free = "" }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(48.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(EMOJI_GRID) { emoji ->
                Box(Modifier.aspectRatio(1f).clickable { vm.pickEmoji(emoji) }, contentAlignment = Alignment.Center) {
                    Text(emoji, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}

@Composable
private fun AppStoreTab(state: IconPickerUiState, vm: IconPickerViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(
                query = state.storeQuery,
                onQueryChange = vm::setStoreQuery,
                placeholder = stringResource(R.string.integrations_icon_search_store),
                onSearch = vm::searchStore,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.setRegionPicker(true) }) { Text(state.storeRegion) }
        }
        when {
            state.storeSearching -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
            }
            state.storeSearched && state.storeResults.isEmpty() ->
                EmptyState(title = stringResource(R.string.integrations_error_itunes_no_items))
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                items(state.storeResults, key = { it.trackId ?: it.hashCode() }) { app ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.pickStoreApp(app) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SubscriptionIcon(
                            type = IconType.URL,
                            value = app.artworkUrl100 ?: app.artworkUrl512,
                            fallbackName = app.trackName ?: "?",
                            size = 44.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.trackName.orEmpty(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                app.sellerName ?: app.primaryGenreName.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebsiteTab(state: IconPickerUiState, vm: IconPickerViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = state.domain,
            onValueChange = vm::setDomain,
            label = { Text(stringResource(R.string.integrations_icon_domain)) },
            placeholder = { Text("apple.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.resolveDomain() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = vm::resolveDomain, enabled = !state.resolving && state.domain.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            if (state.resolving) {
                CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.integrations_icon_fetch))
        }
        if (state.recentDomains.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.integrations_icon_recent), style = MaterialTheme.typography.titleSmall)
            state.recentDomains.forEach { domain ->
                Text(
                    domain,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable { vm.setDomain(domain) }.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun RepositoryTab(state: IconPickerUiState, vm: IconPickerViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(
                query = state.repoQuery,
                onQueryChange = vm::setRepoQuery,
                placeholder = stringResource(R.string.integrations_icon_search_repo),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.setRepoAdd(true) }) { Text(stringResource(io.github.submark.core.ui.R.string.ui_action_add)) }
        }
        if (state.iconRepos.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.integrations_icon_repo_empty),
                actionLabel = stringResource(io.github.submark.core.ui.R.string.ui_action_add),
                onAction = { vm.setRepoAdd(true) },
            )
            return
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(state.iconRepos, key = { it.id }) { repo ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(repo.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.integrations_icon_repo_count, repo.iconCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { vm.loadPack(repo, refresh = true) }) {
                        Text(stringResource(R.string.integrations_icon_repo_get))
                    }
                    TextButton(onClick = { vm.deleteRepo(repo) }) {
                        Text(stringResource(io.github.submark.core.ui.R.string.ui_action_delete))
                    }
                }
            }
        }
        state.repoError?.let {
            Text(
                it.asString(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (state.repoLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(8.dp))
        }
        val filtered = state.packs.filter {
            state.repoQuery.isBlank() || it.name.contains(state.repoQuery.trim(), true)
        }
        if (filtered.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(56.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filtered, key = { it.url }) { icon ->
                    IconButton(onClick = { vm.pickPackIcon(icon) }, modifier = Modifier.aspectRatio(1f)) {
                        SubscriptionIcon(
                            type = IconType.URL,
                            value = icon.url,
                            fallbackName = icon.name,
                            size = 40.dp,
                            background = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoAddSheet(onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.integrations_icon_repo_add), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.integrations_repo_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.integrations_repo_url)) },
                placeholder = { Text("https://example.com/icons.json") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.integrations_icon_repo_format),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(io.github.submark.core.ui.R.string.ui_action_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onAdd(name, url) }, enabled = name.isNotBlank() && url.isNotBlank()) {
                    Text(stringResource(io.github.submark.core.ui.R.string.ui_action_save))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GamesTab(state: IconPickerUiState, vm: IconPickerViewModel, onOpenRawgSettings: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (!state.rawgConfigured) {
            EmptyState(
                title = stringResource(R.string.integrations_error_rawg_no_key),
                actionLabel = stringResource(R.string.integrations_rawg_title),
                onAction = onOpenRawgSettings,
            )
            return
        }
        SearchField(
            query = state.gamesQuery,
            onQueryChange = vm::setGamesQuery,
            placeholder = stringResource(R.string.integrations_icon_search_games),
            onSearch = vm::searchGames,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        when {
            state.gamesSearching -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
            }
            state.gamesSearched && state.gamesResults.isEmpty() ->
                EmptyState(title = stringResource(R.string.integrations_error_rawg_no_results))
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.gamesResults, key = { it.id ?: it.hashCode() }) { game ->
                    Card(
                        onClick = { vm.pickGame(game) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Column {
                            SubscriptionIcon(
                                type = IconType.URL,
                                value = game.backgroundImage,
                                fallbackName = game.name ?: "?",
                                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                                size = 120.dp,
                                shape = MaterialTheme.shapes.medium,
                            )
                            Text(
                                game.name.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoTab(state: IconPickerUiState, vm: IconPickerViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.onPhotoPicked(uri)
    }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.integrations_icon_photo_explain),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.saving) {
                CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(if (state.saving) R.string.integrations_icon_photo_saving else R.string.integrations_icon_photo_pick))
        }
    }
}
