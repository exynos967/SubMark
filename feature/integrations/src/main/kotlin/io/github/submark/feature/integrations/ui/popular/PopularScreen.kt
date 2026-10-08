package io.github.submark.feature.integrations.ui.popular

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.data.settings.PopularRegionScope
import io.github.submark.core.data.settings.PopularSort
import io.github.submark.core.model.IconType
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.ErrorState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.SearchField
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.component.TagChip
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.format.MoneyFormatter
import io.github.submark.core.ui.icon.SubscriptionIcon
import io.github.submark.core.ui.navigation.SubscriptionEditRoute
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.feature.integrations.R
import io.github.submark.feature.integrations.data.popular.BundlePrefillMode
import io.github.submark.feature.integrations.data.popular.CatalogEntry
import io.github.submark.feature.integrations.data.popular.CatalogParser
import io.github.submark.feature.integrations.data.popular.PricingOption
import io.github.submark.feature.integrations.data.popular.RepoCategory

/** Wrapper: wires the ViewModel and navigation. */
@Composable
fun PopularScreenRoute(
    onBack: () -> Unit,
    onNavigate: (Any) -> Unit,
    viewModel: PopularViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    SnackbarEffect(viewModel.messages, snackbarHost)
    androidx.compose.runtime.LaunchedEffect(viewModel.prefill) {
        viewModel.prefill.collect { json -> onNavigate(SubscriptionEditRoute(prefillJson = json)) }
    }
    PopularScreen(
        state = state,
        snackbarHost = snackbarHost,
        onBack = onBack,
        onRefresh = { viewModel.refresh(force = true) },
        onQuery = viewModel::setQuery,
        onCategory = viewModel::setCategory,
        onScope = viewModel::setScope,
        onSort = viewModel::setSort,
        onOpenRepositories = { onNavigate(io.github.submark.core.ui.navigation.PopularRepositoriesRoute) },
        onOpenDetail = viewModel::openDetail,
        onChooseOption = { entry, option, mode -> viewModel.chooseOption(entry, option, mode) },
    )
}

private val RepoCategory.resId: Int
    get() = when (this) {
        RepoCategory.VIDEO -> io.github.submark.core.ui.R.string.ui_category_video
        RepoCategory.MUSIC -> io.github.submark.core.ui.R.string.ui_category_music
        RepoCategory.ENTERTAINMENT -> io.github.submark.core.ui.R.string.ui_category_entertainment
        RepoCategory.GAMING -> io.github.submark.core.ui.R.string.ui_category_gaming
        RepoCategory.PRODUCTIVITY -> io.github.submark.core.ui.R.string.ui_category_productivity
        RepoCategory.UTILITY -> io.github.submark.core.ui.R.string.ui_category_utility
        RepoCategory.AI -> io.github.submark.core.ui.R.string.ui_category_ai
        RepoCategory.NEWS -> io.github.submark.core.ui.R.string.ui_category_news
        RepoCategory.LIFESTYLE -> io.github.submark.core.ui.R.string.ui_category_lifestyle
        RepoCategory.OTHER -> io.github.submark.core.ui.R.string.ui_category_other
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PopularScreen(
    state: PopularUiState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onQuery: (String) -> Unit,
    onCategory: (RepoCategory?) -> Unit,
    onScope: (PopularRegionScope) -> Unit,
    onSort: (PopularSort) -> Unit,
    onOpenRepositories: () -> Unit,
    onOpenDetail: (CatalogEntry?) -> Unit,
    onChooseOption: (CatalogEntry, PricingOption, BundlePrefillMode) -> Unit,
) {
    Scaffold(
        topBar = {
            SubMarkTopAppBar(
                title = stringResource(R.string.integrations_popular_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(io.github.submark.core.ui.R.string.ui_action_retry))
                    }
                    IconButton(onClick = onOpenRepositories) {
                        Icon(
                            Icons.Rounded.Dns,
                            contentDescription = stringResource(R.string.integrations_popular_repositories),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            when {
                state.loading -> LoadingState()
                state.loadError != null && state.entries.isEmpty() ->
                    ErrorState(message = state.loadError.asString(), onRetry = onRefresh)
                state.noRepositories && state.entries.isEmpty() -> NoRepositoryState(onOpenRepositories)
                else -> Column(Modifier.fillMaxSize()) {
                    SearchField(
                        query = state.query,
                        onQueryChange = onQuery,
                        placeholder = stringResource(R.string.integrations_popular_search_hint),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    FilterRow(state, onCategory, onScope, onSort)
                    val filtered = state.filtered
                    if (filtered.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.integrations_popular_empty_filter),
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(filtered, key = { it.id }) { entry ->
                                PopularRow(entry, onClick = { onOpenDetail(entry) })
                            }
                        }
                    }
                }
            }
        }
        state.detail?.let { entry ->
            PopularDetailSheet(
                entry = entry,
                onDismiss = { onOpenDetail(null) },
                onChoose = { option, mode -> onChooseOption(entry, option, mode) },
            )
        }
    }
}

@Composable
private fun NoRepositoryState(onOpenRepositories: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(
            title = stringResource(R.string.integrations_popular_no_repo_title),
            message = stringResource(R.string.integrations_popular_no_repo_message),
            actionLabel = stringResource(R.string.integrations_popular_add_repository),
            onAction = onOpenRepositories,
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.integrations_popular_format_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.integrations_popular_format_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    SAMPLE_JSON,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val SAMPLE_JSON = """{
  "version": "1.0",
  "lastUpdated": "2026-01-01T00:00:00Z",
  "subscriptions": [
    {
      "id": "example-service",
      "name": "Example Service",
      "icon": "https://example.com/icon.png",
      "category": "video",
      "commonPrices": [
        { "price": 9.99, "currency": "USD", "billingCycle": "monthly", "isPermanent": false }
      ],
      "tags": ["streaming"],
      "region": ["Global"]
    }
  ]
}"""

@Composable
private fun FilterRow(
    state: PopularUiState,
    onCategory: (RepoCategory?) -> Unit,
    onScope: (PopularRegionScope) -> Unit,
    onSort: (PopularSort) -> Unit,
) {
    val presentCategories = state.entries.map { it.category }.distinct()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = state.category == null,
                onClick = { onCategory(null) },
                label = { Text(stringResource(R.string.integrations_filter_all)) },
            )
        }
        items(presentCategories) { category ->
            FilterChip(
                selected = state.category == category,
                onClick = { onCategory(if (state.category == category) null else category) },
                label = { Text(stringResource(category.resId)) },
            )
        }
        item {
            FilterChip(
                selected = state.scope == PopularRegionScope.LOCAL,
                onClick = { onScope(if (state.scope == PopularRegionScope.LOCAL) PopularRegionScope.ALL else PopularRegionScope.LOCAL) },
                label = {
                    Text(
                        if (state.scope == PopularRegionScope.LOCAL) {
                            stringResource(R.string.integrations_popular_scope_local, state.storeRegion)
                        } else stringResource(R.string.integrations_popular_scope_all),
                    )
                },
            )
        }
        item {
            FilterChip(
                selected = state.sort == PopularSort.CATEGORY,
                onClick = { onSort(if (state.sort == PopularSort.NAME) PopularSort.CATEGORY else PopularSort.NAME) },
                label = {
                    Text(
                        if (state.sort == PopularSort.NAME) {
                            stringResource(R.string.integrations_popular_sort_name)
                        } else stringResource(R.string.integrations_popular_sort_category),
                    )
                },
            )
        }
    }
}

@Composable
private fun PopularRow(entry: CatalogEntry, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SubscriptionIcon(
                type = entry.icon?.takeIf { it.startsWith("http") }?.let { IconType.URL },
                value = entry.icon,
                fallbackName = entry.name,
                size = 44.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (entry.isBundle) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.integrations_popular_bundle_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                val from = entry.commonPrices.minByOrNull { it.price }
                Text(
                    buildString {
                        append(stringResource(entry.category.resId))
                        if (from != null) {
                            append(" · ")
                            append(
                                if (from.isPermanent) {
                                    MoneyFormatter.format(java.math.BigDecimal.valueOf(from.price), from.currency)
                                } else {
                                    stringResource(
                                        R.string.integrations_popular_from_price,
                                        MoneyFormatter.format(java.math.BigDecimal.valueOf(from.price), from.currency),
                                    )
                                },
                            )
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PopularDetailSheet(
    entry: CatalogEntry,
    onDismiss: () -> Unit,
    onChoose: (PricingOption, BundlePrefillMode) -> Unit,
) {
    var bundleChoice by androidx.compose.runtime.remember(entry.id) {
        androidx.compose.runtime.mutableStateOf<PricingOption?>(null)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SubscriptionIcon(
                    type = entry.icon?.takeIf { it.startsWith("http") }?.let { IconType.URL },
                    value = entry.icon,
                    fallbackName = entry.name,
                    size = 52.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(entry.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(entry.category.resId),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            entry.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entry.tags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    entry.tags.take(6).forEach { TagChip(it, io.github.submark.core.model.TagColor.BLUE) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.integrations_popular_pricing), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            entry.commonPrices.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (option.isPermanent) {
                                stringResource(
                                    R.string.integrations_popular_price_lifetime,
                                    MoneyFormatter.format(java.math.BigDecimal.valueOf(option.price), option.currency),
                                )
                            } else {
                                stringResource(
                                    R.string.integrations_popular_price_cycle,
                                    MoneyFormatter.format(java.math.BigDecimal.valueOf(option.price), option.currency),
                                    cycleLabel(option.billingCycle),
                                )
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        option.description?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!option.isPermanent) {
                            Text(
                                stringResource(io.github.submark.core.ui.R.string.ui_renewal_auto),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Button(onClick = {
                        if (entry.isBundle) bundleChoice = option else onChoose(option, BundlePrefillMode.FULL_BUNDLE)
                    }) {
                        Text(stringResource(R.string.integrations_popular_use))
                    }
                }
                HorizontalDivider()
            }
            if (entry.isBundle && !entry.bundledSubscriptions.isNullOrEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.integrations_popular_included, entry.bundledSubscriptions.orEmpty().size),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(6.dp))
                entry.bundledSubscriptions.orEmpty().forEach { child ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        SubscriptionIcon(
                            type = child.icon?.takeIf { it.startsWith("http") }?.let { IconType.URL },
                            value = child.icon,
                            fallbackName = child.name,
                            size = 28.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(child.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val price = java.math.BigDecimal.valueOf(child.price)
                        Text(
                            if (price.signum() == 0) {
                                stringResource(R.string.integrations_popular_free)
                            } else {
                                MoneyFormatter.format(price, child.currency)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // Total value / savings vs the chosen main price (same currency only).
                val mainOption = entry.commonPrices.firstOrNull()
                if (mainOption != null) {
                    val total = CatalogParser.bundleTotalValue(entry, mainOption.currency)
                    if (total != null) {
                        Spacer(Modifier.height(10.dp))
                        val savings = total.subtract(java.math.BigDecimal.valueOf(
                            entry.bundledSubscriptions.orEmpty().sumOf { it.price },
                        ))
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(R.string.integrations_popular_total_value, MoneyFormatter.format(total, mainOption.currency)),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (savings.signum() > 0) {
                                Text(
                                    stringResource(R.string.integrations_popular_save, MoneyFormatter.format(savings, mainOption.currency)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    // Bundle mode chooser.
    bundleChoice?.let { option ->
        val children = entry.bundledSubscriptions.orEmpty()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { bundleChoice = null },
            title = { Text(entry.name) },
            text = {
                Text(
                    stringResource(
                        R.string.integrations_popular_bundle_choice_message,
                        children.size,
                        MoneyFormatter.format(java.math.BigDecimal.valueOf(option.price), option.currency),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { bundleChoice = null; onChoose(option, BundlePrefillMode.FULL_BUNDLE) }) {
                    Text(stringResource(R.string.integrations_popular_bundle_full, children.size))
                }
            },
            dismissButton = {
                TextButton(onClick = { bundleChoice = null; onChoose(option, BundlePrefillMode.MAIN_ONLY) }) {
                    Text(stringResource(R.string.integrations_popular_bundle_main_only))
                }
            },
        )
    }
}

@Composable
private fun cycleLabel(cycle: io.github.submark.feature.integrations.data.popular.RepoBillingCycle): String =
    stringResource(
        when (cycle) {
            io.github.submark.feature.integrations.data.popular.RepoBillingCycle.MONTHLY -> io.github.submark.core.ui.R.string.ui_cycle_monthly
            io.github.submark.feature.integrations.data.popular.RepoBillingCycle.QUARTERLY -> io.github.submark.core.ui.R.string.ui_cycle_quarterly
            io.github.submark.feature.integrations.data.popular.RepoBillingCycle.SEMI_ANNUALLY -> io.github.submark.core.ui.R.string.ui_cycle_semiannually
            io.github.submark.feature.integrations.data.popular.RepoBillingCycle.ANNUALLY -> io.github.submark.core.ui.R.string.ui_cycle_annually
            io.github.submark.feature.integrations.data.popular.RepoBillingCycle.CUSTOM -> io.github.submark.core.ui.R.string.ui_cycle_custom
        },
    )
