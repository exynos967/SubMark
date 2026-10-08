package io.github.submark.feature.subscriptions.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.data.repository.SubscriptionItem
import io.github.submark.core.data.settings.ListSegment
import io.github.submark.core.data.settings.ListStyle
import io.github.submark.core.data.settings.SortDirection
import io.github.submark.core.data.settings.SortField
import io.github.submark.core.model.BundleRole
import io.github.submark.core.model.IconType
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import io.github.submark.core.ui.component.CategoryChip
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.MarkPaidDialog
import io.github.submark.core.ui.component.SearchField
import io.github.submark.core.ui.component.SegmentedTabs
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.component.SubscriptionCardVariant
import io.github.submark.core.ui.component.formatMoney
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.format.displayName
import io.github.submark.core.ui.icon.SubscriptionIcon
import io.github.submark.core.ui.navigation.ArchiveRoute
import io.github.submark.core.ui.navigation.CategoryManagementRoute
import io.github.submark.core.ui.navigation.CustomFieldManagementRoute
import io.github.submark.core.ui.navigation.SubscriptionDetailRoute
import io.github.submark.core.ui.navigation.SubscriptionEditRoute
import io.github.submark.core.ui.navigation.TagFolderRoute
import io.github.submark.core.ui.navigation.TagManagementRoute
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.core.ui.util.bleedHorizontally
import io.github.submark.core.ui.util.colorFromHex
import io.github.submark.feature.subscriptions.R
import io.github.submark.feature.subscriptions.ui.common.SubscriptionItemCard
import io.github.submark.feature.subscriptions.ui.common.SubscriptionCardModel
import io.github.submark.feature.subscriptions.ui.common.TagPickerSheet
import java.math.BigDecimal
import io.github.submark.core.ui.R as CoreR

@Composable
fun SubscriptionListScreenRoute(onNavigate: (Any) -> Unit) {
    val viewModel: SubscriptionListViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    SnackbarEffect(viewModel.messages, snackbarHost)
    SubscriptionListScreen(
        state = state,
        snackbarHost = snackbarHost,
        actions = SubscriptionListActions(
            onSegment = viewModel::selectSegment,
            onQuery = viewModel::setQuery,
            onCategory = viewModel::setCategory,
            onTags = viewModel::setTags,
            onClearFilters = viewModel::clearFilters,
            onSort = viewModel::setSort,
            onToggleStyle = viewModel::toggleStyle,
            onOpen = { onNavigate(SubscriptionDetailRoute(it.id)) },
            onEdit = { onNavigate(SubscriptionEditRoute(id = it.id)) },
            onAdd = { onNavigate(SubscriptionEditRoute()) },
            onOpenFolder = { onNavigate(TagFolderRoute(it)) },
            onOpenArchive = { onNavigate(ArchiveRoute(lifetime = state.segment == ListSegment.LIFETIME)) },
            onNavigate = onNavigate,
            onRequestMarkPaid = viewModel::requestMarkPaid,
            onRequestPause = viewModel::requestPause,
            onRequestActivate = viewModel::requestActivate,
            onRequestDelete = viewModel::requestDelete,
            onDismissDialog = viewModel::dismissDialog,
            onMarkPaid = viewModel::markPaid,
            onPause = viewModel::pause,
            onActivate = viewModel::activate,
            onDelete = viewModel::delete,
            onNoteCopied = viewModel::noteCopied,
        ),
    )
}

/** Callbacks of [SubscriptionListScreen]. */
data class SubscriptionListActions(
    val onSegment: (ListSegment) -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onCategory: (String?) -> Unit = {},
    val onTags: (Set<String>) -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onSort: (SortField, SortDirection) -> Unit = { _, _ -> },
    val onToggleStyle: () -> Unit = {},
    val onOpen: (SubscriptionItem) -> Unit = {},
    val onEdit: (SubscriptionItem) -> Unit = {},
    val onAdd: () -> Unit = {},
    val onOpenFolder: (String) -> Unit = {},
    val onOpenArchive: () -> Unit = {},
    val onNavigate: (Any) -> Unit = {},
    val onRequestMarkPaid: (SubscriptionItem) -> Unit = {},
    val onRequestPause: (SubscriptionItem) -> Unit = {},
    val onRequestActivate: (SubscriptionItem) -> Unit = {},
    val onRequestDelete: (SubscriptionItem) -> Unit = {},
    val onDismissDialog: () -> Unit = {},
    val onMarkPaid: (SubscriptionItem, io.github.submark.core.model.MarkTiming) -> Unit = { _, _ -> },
    val onPause: (SubscriptionItem) -> Unit = {},
    val onActivate: (SubscriptionItem) -> Unit = {},
    val onDelete: (SubscriptionItem, Boolean) -> Unit = { _, _ -> },
    val onNoteCopied: () -> Unit = {},
)

@Composable
fun SubscriptionListScreen(
    state: SubscriptionListUiState,
    snackbarHost: SnackbarHostState,
    actions: SubscriptionListActions,
) {
    var showTagSheet by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { ListTopBar(state, actions) },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            FloatingActionButton(onClick = actions.onAdd) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.subscriptions_list_add))
            }
        },
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        val grid = state.style == ListStyle.GRID
        LazyVerticalGrid(
            columns = if (grid) GridCells.Adaptive(160.dp) else GridCells.Fixed(1),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            fullWidth("segments") {
                SegmentedTabs(state.segments, state.segment, actions.onSegment) { segmentLabel(it) }
            }
            fullWidth("summary") { SummaryStrip(state) }
            fullWidth("search") {
                SearchField(
                    query = state.filter.query,
                    onQueryChange = actions.onQuery,
                    placeholder = stringResource(R.string.subscriptions_list_search_placeholder),
                )
            }
            fullWidth("filters") { FilterRow(state, actions, onOpenTags = { showTagSheet = true }) }
            listBody(state, actions, grid)
        }
    }
    if (showTagSheet) {
        TagPickerSheet(
            tags = state.tags,
            selectedIds = state.filter.tagIds,
            onDone = {
                actions.onTags(it)
                showTagSheet = false
            },
            onDismiss = { showTagSheet = false },
            title = stringResource(R.string.subscriptions_list_tag_filter_title),
            emptyMessage = stringResource(R.string.subscriptions_list_tag_filter_empty),
        )
    }
    ListDialogs(state, actions)
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun LazyGridScope.listBody(state: SubscriptionListUiState, actions: SubscriptionListActions, grid: Boolean) {
    if (!state.segmentHasItems) {
        fullWidth("empty") {
            EmptyState(
                title = stringResource(R.string.subscriptions_list_empty_title),
                message = stringResource(
                    when (state.segment) {
                        ListSegment.SUBSCRIPTIONS -> R.string.subscriptions_list_empty_message
                        ListSegment.LIFETIME -> R.string.subscriptions_list_empty_lifetime_message
                        ListSegment.WISHLIST -> R.string.subscriptions_list_empty_wishlist_message
                    },
                ),
                actionLabel = stringResource(R.string.subscriptions_list_add),
                onAction = actions.onAdd,
            )
        }
        return
    }
    if (state.folders.isEmpty() && state.items.isEmpty()) {
        fullWidth("no_match") {
            EmptyState(
                title = stringResource(R.string.subscriptions_list_no_match_title),
                message = stringResource(R.string.subscriptions_list_no_match_message),
                icon = Icons.Rounded.Search,
                actionLabel = stringResource(R.string.subscriptions_list_clear_filters),
                onAction = actions.onClearFilters,
            )
        }
        return
    }
    state.folders.forEach { entry ->
        fullWidth("folder_${entry.folder.folder.id}") { FolderCard(entry, state, onClick = { actions.onOpenFolder(entry.folder.folder.id) }) }
    }
    items(state.items, key = { it.id }) { item ->
        CardWithMenu(item, state, actions, grid)
    }
}

@Composable
private fun segmentLabel(segment: ListSegment): String = stringResource(
    when (segment) {
        ListSegment.SUBSCRIPTIONS -> R.string.subscriptions_list_segment_subscriptions
        ListSegment.LIFETIME -> R.string.subscriptions_list_segment_lifetime
        ListSegment.WISHLIST -> R.string.subscriptions_list_segment_wishlist
    },
)

@Composable
private fun ListTopBar(state: SubscriptionListUiState, actions: SubscriptionListActions) {
    var sortOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    SubMarkTopAppBar(
        title = stringResource(R.string.subscriptions_list_title),
        actions = {
            if (state.archiveMode && state.segment != ListSegment.WISHLIST) {
                IconButton(onClick = actions.onOpenArchive) {
                    Icon(Icons.Rounded.Archive, contentDescription = stringResource(R.string.subscriptions_list_open_archive))
                }
            }
            Box {
                IconButton(onClick = { sortOpen = true }) {
                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.subscriptions_list_sort))
                }
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    SortField.entries.forEach { field ->
                        SortDirection.entries.forEach { direction ->
                            val selected = field == state.sortField && direction == state.sortDirection
                            DropdownMenuItem(
                                text = { Text(sortLabel(field, direction)) },
                                leadingIcon = { if (selected) Icon(Icons.Rounded.Check, contentDescription = null) },
                                onClick = {
                                    sortOpen = false
                                    actions.onSort(field, direction)
                                },
                            )
                        }
                    }
                }
            }
            IconButton(onClick = actions.onToggleStyle) {
                if (state.style == ListStyle.LIST) {
                    Icon(Icons.Rounded.GridView, contentDescription = stringResource(R.string.subscriptions_list_show_grid))
                } else {
                    Icon(Icons.Rounded.ViewAgenda, contentDescription = stringResource(R.string.subscriptions_list_show_list))
                }
            }
            Box {
                IconButton(onClick = { moreOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.subscriptions_list_more))
                }
                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                    MenuEntry(R.string.subscriptions_list_manage_categories, Icons.Rounded.Category) {
                        moreOpen = false
                        actions.onNavigate(CategoryManagementRoute)
                    }
                    MenuEntry(R.string.subscriptions_list_manage_tags, Icons.Rounded.Label) {
                        moreOpen = false
                        actions.onNavigate(TagManagementRoute)
                    }
                    MenuEntry(R.string.subscriptions_list_manage_fields, Icons.Rounded.TextFields) {
                        moreOpen = false
                        actions.onNavigate(CustomFieldManagementRoute)
                    }
                }
            }
        },
    )
}

@Composable
private fun sortLabel(field: SortField, direction: SortDirection): String {
    val fieldText = stringResource(
        when (field) {
            SortField.NAME -> R.string.subscriptions_list_sort_name
            SortField.PRICE -> R.string.subscriptions_list_sort_price
            SortField.DATE -> R.string.subscriptions_list_sort_date
        },
    )
    val arrow = if (direction == SortDirection.ASC) "↑" else "↓"
    return "$fieldText $arrow"
}

@Composable
private fun MenuEntry(labelRes: Int, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
private fun SummaryStrip(state: SubscriptionListUiState) {
    val s = state.summary
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                SummaryCell(stringResource(R.string.subscriptions_list_summary_count), s.count.toString(), Modifier.weight(1f))
                if (state.segment == ListSegment.SUBSCRIPTIONS) {
                    SummaryCell(stringResource(R.string.subscriptions_list_summary_monthly), amountOrNone(s.monthly, s.currencyCode, state), Modifier.weight(1f))
                    SummaryCell(stringResource(R.string.subscriptions_list_summary_annual), amountOrNone(s.annual, s.currencyCode, state), Modifier.weight(1f))
                    SummaryCell(
                        stringResource(R.string.subscriptions_list_summary_average),
                        s.average?.let { amountOrNone(it, s.currencyCode, state) } ?: stringResource(R.string.subscriptions_list_summary_none),
                        Modifier.weight(1f),
                    )
                } else {
                    SummaryCell(
                        stringResource(
                            if (state.segment == ListSegment.LIFETIME) R.string.subscriptions_list_summary_total else R.string.subscriptions_list_summary_planned,
                        ),
                        amountOrNone(s.total, s.currencyCode, state),
                        Modifier.weight(2f),
                    )
                }
            }
            if (s.missingRateCodes.isNotEmpty()) {
                Text(
                    stringResource(R.string.subscriptions_list_summary_missing_rates, s.missingRateCodes.sorted().joinToString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun amountOrNone(amount: BigDecimal, code: String, state: SubscriptionListUiState): String =
    if (amount.signum() == 0) stringResource(R.string.subscriptions_list_summary_none) else formatMoney(amount, code, state.symbols[code], compact = true)

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun FilterRow(state: SubscriptionListUiState, actions: SubscriptionListActions, onOpenTags: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LazyRow(
            modifier = Modifier.bleedHorizontally(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item(key = "tags") {
                val count = state.filter.tagIds.size
                FilterChip(
                    selected = count > 0,
                    onClick = onOpenTags,
                    label = {
                        Text(
                            if (count > 0) {
                                pluralStringResource(R.plurals.subscriptions_list_tag_filter_count, count, count)
                            } else {
                                stringResource(R.string.subscriptions_list_tag_filter)
                            },
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.Label, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
            item(key = "all") {
                FilterChip(
                    selected = state.filter.categoryId == null,
                    onClick = { actions.onCategory(null) },
                    label = { Text(stringResource(R.string.subscriptions_list_category_all)) },
                )
            }
            items(state.categories, key = { it.id }) { category ->
                val selected = state.filter.categoryId == category.id
                CategoryChip(
                    name = category.displayName().asString(),
                    color = colorFromHex(category.colorHex),
                    iconType = category.iconType,
                    iconValue = category.iconValue,
                    selected = selected,
                    onClick = { actions.onCategory(if (selected) null else category.id) },
                )
            }
        }
        if (state.filter.isActive) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(R.plurals.subscriptions_list_result_count, state.summary.count, state.summary.count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = actions.onClearFilters) { Text(stringResource(R.string.subscriptions_list_clear_filters)) }
            }
        }
    }
}

@Composable
private fun FolderCard(entry: FolderEntry, state: SubscriptionListUiState, onClick: () -> Unit) {
    val folder = entry.folder.folder
    val accent = colorFromHex(folder.colorHex) ?: MaterialTheme.colorScheme.primary
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f)),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SubscriptionIcon(
                type = IconType.SYMBOL,
                value = folder.iconValue ?: "folder",
                fallbackName = folder.name,
                size = 40.dp,
                tint = accent,
                background = accent.copy(alpha = 0.18f),
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    pluralStringResource(R.plurals.subscriptions_folder_count, entry.items.size, entry.items.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val s = entry.summary
            val total = if (state.segment == ListSegment.SUBSCRIPTIONS) s.monthly else s.total
            if (total.signum() != 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatMoney(total, s.currencyCode, state.symbols[s.currencyCode]), style = MaterialTheme.typography.titleSmall)
                    if (state.segment == ListSegment.SUBSCRIPTIONS) {
                        Text(
                            stringResource(R.string.subscriptions_folder_per_month),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun CardWithMenu(item: SubscriptionItem, state: SubscriptionListUiState, actions: SubscriptionListActions, grid: Boolean) {
    var menuOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val sub = item.subscription
    Box {
        SubscriptionItemCard(
            item = item,
            list = state.listSettings,
            today = state.today,
            symbols = state.symbols,
            variant = if (grid) SubscriptionCardVariant.GRID else SubscriptionCardVariant.LIST,
            dimmed = sub.status == SubscriptionStatus.PAUSED && sub.kind != SubscriptionKind.WISHLIST,
            onClick = { actions.onOpen(item) },
            onLongClick = { menuOpen = true },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (SubscriptionCardModel.isMarkable(sub)) {
                MenuEntry(R.string.subscriptions_list_action_mark_paid, Icons.Rounded.TaskAlt) {
                    menuOpen = false
                    actions.onRequestMarkPaid(item)
                }
            }
            MenuEntry(CoreR.string.ui_action_edit, Icons.Rounded.Edit) {
                menuOpen = false
                actions.onEdit(item)
            }
            if (sub.kind != SubscriptionKind.WISHLIST) {
                if (sub.status == SubscriptionStatus.ACTIVE) {
                    MenuEntry(R.string.subscriptions_list_action_pause, Icons.Rounded.Pause) {
                        menuOpen = false
                        actions.onRequestPause(item)
                    }
                } else {
                    MenuEntry(R.string.subscriptions_list_action_activate, Icons.Rounded.PlayArrow) {
                        menuOpen = false
                        actions.onRequestActivate(item)
                    }
                }
            }
            val note = sub.note
            if (state.listSettings.showCopyNotesButton && !note.isNullOrBlank()) {
                MenuEntry(R.string.subscriptions_list_action_copy_note, Icons.Rounded.ContentCopy) {
                    menuOpen = false
                    clipboard.setText(AnnotatedString(note))
                    actions.onNoteCopied()
                }
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(CoreR.string.ui_action_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    menuOpen = false
                    actions.onRequestDelete(item)
                },
            )
        }
    }
}

@Composable
private fun ListDialogs(state: SubscriptionListUiState, actions: SubscriptionListActions) {
    when (val dialog = state.dialog) {
        null -> Unit
        is ListDialog.MarkPaid -> {
            val sub = dialog.item.subscription
            val due = sub.nextPaymentDate
            if (due == null) {
                androidx.compose.runtime.LaunchedEffect(dialog) { actions.onDismissDialog() }
            } else {
                MarkPaidDialog(
                    name = sub.name,
                    amountText = formatMoney(sub.price, sub.currencyCode, state.symbols[sub.currencyCode]),
                    dueDate = due,
                    today = state.today,
                    onConfirm = { actions.onMarkPaid(dialog.item, it) },
                    onDismiss = actions.onDismissDialog,
                )
            }
        }
        is ListDialog.Pause -> ConfirmDialog(
            title = stringResource(R.string.subscriptions_list_pause_title, dialog.item.subscription.name),
            message = stringResource(
                if (state.archiveMode) R.string.subscriptions_list_pause_message_archive else R.string.subscriptions_list_pause_message,
            ),
            confirmLabel = stringResource(R.string.subscriptions_list_action_pause),
            onConfirm = { actions.onPause(dialog.item) },
            onDismiss = actions.onDismissDialog,
        )
        is ListDialog.Activate -> ConfirmDialog(
            title = stringResource(R.string.subscriptions_list_activate_title, dialog.item.subscription.name),
            message = stringResource(R.string.subscriptions_list_activate_message),
            confirmLabel = stringResource(R.string.subscriptions_list_action_activate),
            onConfirm = { actions.onActivate(dialog.item) },
            onDismiss = actions.onDismissDialog,
        )
        is ListDialog.Delete -> DeleteDialog(dialog, actions)
    }
}

/** Delete confirmation; offers keep/reverse when wallet charges exist and warns about bundle children. */
@Composable
internal fun DeleteDialog(dialog: ListDialog.Delete, actions: SubscriptionListActions) {
    val item = dialog.item
    val childCount = if (item.subscription.bundleRole == BundleRole.MAIN) item.children.size else 0
    AlertDialog(
        onDismissRequest = actions.onDismissDialog,
        icon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.subscriptions_list_delete_title, item.subscription.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.subscriptions_list_delete_message))
                if (childCount > 0) {
                    Text(
                        pluralStringResource(R.plurals.subscriptions_list_delete_children, childCount, childCount),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (dialog.hasWalletCharges) Text(stringResource(R.string.subscriptions_list_delete_wallet_message))
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (dialog.hasWalletCharges) {
                    TextButton(onClick = { actions.onDelete(item, true) }) {
                        Text(stringResource(R.string.subscriptions_list_delete_reverse), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { actions.onDelete(item, false) }) {
                        Text(stringResource(R.string.subscriptions_list_delete_keep), color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(onClick = { actions.onDelete(item, false) }) {
                        Text(stringResource(CoreR.string.ui_action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(0.dp))
            }
        },
        dismissButton = { TextButton(onClick = actions.onDismissDialog) { Text(stringResource(CoreR.string.ui_action_cancel)) } },
    )
}
