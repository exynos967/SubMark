package io.github.submark.feature.subscriptions.ui.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.data.repository.SubscriptionItem
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.SearchField
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.navigation.SubscriptionDetailRoute
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.feature.subscriptions.R
import io.github.submark.feature.subscriptions.ui.common.SubscriptionItemCard
import io.github.submark.feature.subscriptions.ui.common.shortDate

@Composable
fun ArchiveScreenRoute(onBack: () -> Unit, onNavigate: (Any) -> Unit) {
    val viewModel: ArchiveViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    SnackbarEffect(viewModel.messages, snackbarHost)
    ArchiveScreen(
        state = state,
        snackbarHost = snackbarHost,
        onBack = onBack,
        onQuery = viewModel::setQuery,
        onOpen = { onNavigate(SubscriptionDetailRoute(it.id)) },
        onRequestRestore = viewModel::requestRestore,
        onRestore = viewModel::restore,
        onDismiss = viewModel::dismiss,
    )
}

@Composable
fun ArchiveScreen(
    state: ArchiveUiState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onOpen: (SubscriptionItem) -> Unit,
    onRequestRestore: (SubscriptionItem) -> Unit,
    onRestore: (SubscriptionItem) -> Unit,
    onDismiss: () -> Unit,
) {
    Scaffold(
        topBar = {
            SubMarkTopAppBar(
                title = stringResource(if (state.lifetime) R.string.subscriptions_archive_title_lifetime else R.string.subscriptions_archive_title),
                subtitle = stringResource(if (state.lifetime) R.string.subscriptions_archive_subtitle_lifetime else R.string.subscriptions_archive_subtitle),
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            !state.hasAny -> EmptyState(
                title = stringResource(R.string.subscriptions_archive_empty_title),
                message = stringResource(R.string.subscriptions_archive_empty_message),
                icon = Icons.Rounded.Archive,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "search") {
                    SearchField(state.query, onQuery, placeholder = stringResource(R.string.subscriptions_archive_search))
                }
                if (state.items.isEmpty()) {
                    item(key = "none") {
                        EmptyState(title = stringResource(R.string.subscriptions_list_no_match_title), icon = Icons.Rounded.Search)
                    }
                }
                items(state.items, key = { it.id }) { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SubscriptionItemCard(
                            item = item,
                            list = state.listSettings,
                            today = state.today,
                            symbols = state.symbols,
                            dimmed = true,
                            onClick = { onOpen(item) },
                        )
                        androidx.compose.foundation.layout.Row(
                            Modifier.padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val pausedOn = item.subscription.pausedAt?.atZone(state.zone)?.toLocalDate()
                            Text(
                                if (pausedOn != null) {
                                    stringResource(R.string.subscriptions_archive_paused_on, shortDate(pausedOn, state.today))
                                } else {
                                    stringResource(R.string.subscriptions_archive_paused)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            FilledTonalButton(onClick = { onRequestRestore(item) }) {
                                Icon(Icons.Rounded.Restore, contentDescription = null)
                                Text(stringResource(R.string.subscriptions_archive_restore), modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    state.confirmRestore?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.subscriptions_archive_restore_title, item.subscription.name),
            message = stringResource(
                if (state.lifetime) R.string.subscriptions_archive_restore_message_lifetime else R.string.subscriptions_archive_restore_message,
            ),
            confirmLabel = stringResource(R.string.subscriptions_archive_restore),
            onConfirm = { onRestore(item) },
            onDismiss = onDismiss,
        )
    }
}
