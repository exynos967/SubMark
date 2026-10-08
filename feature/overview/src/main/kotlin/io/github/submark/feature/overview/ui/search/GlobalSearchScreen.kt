package io.github.submark.feature.overview.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.submark.core.ui.component.SearchField
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.icon.SubscriptionIcon
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.feature.overview.R

@Composable
fun GlobalSearchRoute(
    onBack: () -> Unit,
    onOpenSubscription: (String) -> Unit,
    onOpenPayment: (String) -> Unit,
    viewModel: GlobalSearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    SnackbarEffect(messages = viewModel.snackbars, hostState = snackbarHostState)

    GlobalSearchScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onScopeChange = viewModel::onScopeChange,
        onRecentClick = viewModel::onRecentClick,
        onClearRecent = viewModel::onClearRecent,
        onResultClick = { result ->
            viewModel.onResultClick(result) { nav ->
                when (nav) {
                    is SearchNavTarget.OpenSubscription -> onOpenSubscription(nav.subscriptionId)
                    is SearchNavTarget.OpenPayment -> onOpenPayment(nav.paymentId)
                    is SearchNavTarget.OpenCategory -> onOpenSubscription(nav.categoryId) // fallback
                }
            }
        },
    )
}

@Composable
private fun GlobalSearchScreen(
    uiState: GlobalSearchUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onScopeChange: (SearchScope) -> Unit,
    onRecentClick: (String) -> Unit,
    onClearRecent: () -> Unit,
    onResultClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SubMarkTopAppBar(
                title = stringResource(R.string.search_title),
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Search field
            SearchField(
                query = uiState.query,
                onQueryChange = onQueryChange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = stringResource(R.string.search_placeholder),
            )

            // Scope tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchScope.entries.forEach { scope ->
                    val isSelected = scope == uiState.scope
                    androidx.compose.material3.FilterChip(
                        selected = isSelected,
                        onClick = { onScopeChange(scope) },
                        label = { Text(stringResource(scope.labelRes), maxLines = 1) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            when {
                uiState.isSearching -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.showTips -> {
                    // Tips + recent searches
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text(
                                stringResource(R.string.search_tips_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        item {
                            listOf(
                                R.string.search_tip_1,
                                R.string.search_tip_2,
                                R.string.search_tip_3,
                                R.string.search_tip_4,
                            ).forEach { stringRes ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Rounded.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(stringRes), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        if (uiState.recentSearches.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Rounded.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.search_recent),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = onClearRecent) {
                                        Text(stringResource(R.string.search_clear_recent), style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                            items(uiState.recentSearches, key = { it }) { query ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onRecentClick(query) }
                                        .padding(horizontal = 24.dp, vertical = 12.dp),
                                ) {
                                    Text(query, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                !uiState.hasQuery -> {
                    // Empty query, show tips
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.search_empty_query),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                uiState.results.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outline,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.search_no_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                else -> {
                    // Results
                    val subResults = uiState.results.filterIsInstance<SearchResult.SubscriptionRow>()
                    val payResults = uiState.results.filterIsInstance<SearchResult.PaymentRow>()
                    val catResults = uiState.results.filterIsInstance<SearchResult.CategoryRow>()

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (subResults.isNotEmpty() && uiState.scope != SearchScope.CATEGORIES && uiState.scope != SearchScope.PAYMENTS) {
                            item { ResultSectionHeader(stringResource(R.string.search_result_section_subscriptions)) }
                            items(subResults, key = { it.id }) { row ->
                                ResultRow(
                                    title = row.title,
                                    subtitle = row.subtitle,
                                    iconType = row.iconType,
                                    iconValue = row.iconValue,
                                    onClick = { onResultClick(row) },
                                )
                            }
                        }
                        if (payResults.isNotEmpty() && uiState.scope != SearchScope.CATEGORIES && uiState.scope != SearchScope.SUBSCRIPTIONS) {
                            item { ResultSectionHeader(stringResource(R.string.search_result_section_payments)) }
                            items(payResults, key = { it.id }) { row ->
                                ResultRow(
                                    title = row.title,
                                    subtitle = row.subtitle,
                                    iconType = row.iconType,
                                    iconValue = row.iconValue,
                                    onClick = { onResultClick(row) },
                                )
                            }
                        }
                        if (catResults.isNotEmpty() && uiState.scope != SearchScope.PAYMENTS && uiState.scope != SearchScope.SUBSCRIPTIONS) {
                            item { ResultSectionHeader(stringResource(R.string.search_result_section_categories)) }
                            items(catResults, key = { it.id }) { row ->
                                ResultRow(
                                    title = row.title,
                                    subtitle = row.subtitle,
                                    iconType = row.iconType,
                                    iconValue = row.iconValue,
                                    onClick = { onResultClick(row) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultSectionHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultRow(
    title: String,
    subtitle: String?,
    iconType: io.github.submark.core.model.IconType?,
    iconValue: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SubscriptionIcon(
            type = iconType,
            value = iconValue,
            fallbackName = title,
            size = 36.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
