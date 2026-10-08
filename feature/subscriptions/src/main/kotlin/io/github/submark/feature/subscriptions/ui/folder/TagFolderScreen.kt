package io.github.submark.feature.subscriptions.ui.folder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.model.IconType
import io.github.submark.core.model.TagMatchMode
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.component.formatMoney
import io.github.submark.core.ui.icon.SubscriptionIcon
import io.github.submark.core.ui.navigation.SubscriptionDetailRoute
import io.github.submark.core.ui.util.colorFromHex
import io.github.submark.feature.subscriptions.R
import io.github.submark.feature.subscriptions.ui.common.SubscriptionItemCard

@Composable
fun TagFolderScreenRoute(onBack: () -> Unit, onNavigate: (Any) -> Unit) {
    val viewModel: TagFolderViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TagFolderScreen(state, onBack = onBack, onOpen = { onNavigate(SubscriptionDetailRoute(it)) })
}

@Composable
fun TagFolderScreen(state: TagFolderUiState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    Scaffold(
        topBar = {
            SubMarkTopAppBar(
                title = state.folder?.folder?.name ?: stringResource(R.string.subscriptions_folder_title),
                onBack = onBack,
            )
        },
    ) { padding ->
        val folder = state.folder
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.notFound || folder == null -> EmptyState(
                title = stringResource(R.string.subscriptions_folder_not_found),
                icon = Icons.Rounded.FolderOff,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "header") { FolderHeader(state) }
                if (state.items.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            title = stringResource(R.string.subscriptions_folder_empty_title),
                            message = stringResource(R.string.subscriptions_folder_empty_message),
                        )
                    }
                }
                items(state.items, key = { it.id }) { item ->
                    SubscriptionItemCard(
                        item = item,
                        list = state.listSettings,
                        today = state.today,
                        symbols = state.symbols,
                        dimmed = item.subscription.status == io.github.submark.core.model.SubscriptionStatus.PAUSED,
                        onClick = { onOpen(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderHeader(state: TagFolderUiState) {
    val folder = state.folder!!.folder
    val accent = colorFromHex(folder.colorHex) ?: MaterialTheme.colorScheme.primary
    Card(colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f)), shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SubscriptionIcon(
                    type = IconType.SYMBOL,
                    value = folder.iconValue ?: "folder",
                    fallbackName = folder.name,
                    size = 48.dp,
                    tint = accent,
                    background = accent.copy(alpha = 0.18f),
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(folder.name, style = MaterialTheme.typography.titleLarge)
                    Text(tagSummary(state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row {
                Stat(stringResource(R.string.subscriptions_folder_stat_count), pluralStringResource(R.plurals.subscriptions_folder_count, state.items.size, state.items.size), Modifier.weight(1f))
                val s = state.summary
                if (s != null) {
                    if (s.monthly.signum() != 0) {
                        Stat(
                            stringResource(R.string.subscriptions_folder_stat_monthly),
                            formatMoney(s.monthly, s.currencyCode, state.symbols[s.currencyCode]),
                            Modifier.weight(1f),
                        )
                    }
                    if (s.total.signum() != 0) {
                        Stat(
                            stringResource(R.string.subscriptions_folder_stat_one_time),
                            formatMoney(s.total, s.currencyCode, state.symbols[s.currencyCode]),
                            Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun tagSummary(state: TagFolderUiState): String {
    val names = state.folderTags.map { it.name }
    val mode = stringResource(
        if (state.folder?.folder?.matchMode == TagMatchMode.ALL) R.string.subscriptions_folder_match_all else R.string.subscriptions_folder_match_any,
    )
    val tags = when {
        names.isEmpty() -> stringResource(R.string.subscriptions_folder_no_tags)
        names.size == 1 -> names.first()
        else -> pluralStringResource(R.plurals.subscriptions_folder_tags_more, names.size - 1, names.first(), names.size - 1)
    }
    return "$tags · $mode"
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
