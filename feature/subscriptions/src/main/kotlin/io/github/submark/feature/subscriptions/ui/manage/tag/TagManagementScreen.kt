package io.github.submark.feature.subscriptions.ui.manage.tag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import io.github.submark.core.data.repository.TagFolderWithTags
import io.github.submark.core.data.repository.TagWithUsage
import io.github.submark.core.model.Tag
import io.github.submark.core.model.TagColor
import io.github.submark.core.model.TagMatchMode
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.core.ui.component.DragHandleIcon
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.ReorderableItemsColumn
import io.github.submark.core.ui.component.SearchField
import io.github.submark.core.ui.component.SegmentedTabs
import io.github.submark.core.ui.component.StatusBadge
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.component.TagChip
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.format.labelRes
import io.github.submark.core.ui.format.themedColor
import io.github.submark.core.ui.icon.SubscriptionIcon
import io.github.submark.core.ui.navigation.IconPickerRoute
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.core.ui.util.colorFromHex
import io.github.submark.core.ui.util.contentColorFor
import io.github.submark.feature.subscriptions.R
import io.github.submark.feature.subscriptions.ui.common.IconResultEffect
import io.github.submark.feature.subscriptions.ui.common.TagPickerSheet
import io.github.submark.feature.subscriptions.ui.manage.ColorSwatches
import io.github.submark.feature.subscriptions.ui.manage.EditorSectionLabel
import io.github.submark.feature.subscriptions.ui.manage.IconEditor
import io.github.submark.feature.subscriptions.ui.manage.ManageLogic
import io.github.submark.feature.subscriptions.ui.manage.ManageLogic.moved
import io.github.submark.core.ui.R as CoreR

@Composable
fun TagManagementScreenRoute(backStackEntry: NavBackStackEntry, onBack: () -> Unit, onNavigate: (Any) -> Unit) {
    val viewModel: TagManagementViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    SnackbarEffect(viewModel.messages, snackbarHost)
    IconResultEffect(backStackEntry) { viewModel.onIconPicked(it) }
    TagManagementScreen(
        state = state,
        snackbarHost = snackbarHost,
        actions = TagManagementActions(
            onBack = onBack,
            onSelectTab = viewModel::selectTab,
            onQueryChange = viewModel::setQuery,
            onAddTag = viewModel::startAddTag,
            onEditTag = viewModel::startEditTag,
            onDeleteTag = viewModel::deleteTag,
            onTagEditorChange = viewModel::updateTagEditor,
            onTagEditorSave = viewModel::saveTag,
            onTagEditorDismiss = viewModel::dismissTagEditor,
            onAddFolder = viewModel::startAddFolder,
            onEditFolder = viewModel::startEditFolder,
            onDeleteFolder = { viewModel.deleteFolder(it.folder) },
            onReorderFolders = viewModel::reorderFolders,
            onFolderEditorChange = viewModel::updateFolderEditor,
            onFolderEditorSave = viewModel::saveFolder,
            onFolderEditorDismiss = viewModel::dismissFolderEditor,
            onCreateTag = viewModel::createTag,
            onPickIcon = { target, query ->
                viewModel.requestIcon(target)
                onNavigate(IconPickerRoute(query = query.ifBlank { null }))
            },
        ),
    )
}

class TagManagementActions(
    val onBack: () -> Unit,
    val onSelectTab: (TagManagementTab) -> Unit,
    val onQueryChange: (String) -> Unit,
    val onAddTag: () -> Unit,
    val onEditTag: (Tag) -> Unit,
    val onDeleteTag: (Tag) -> Unit,
    val onTagEditorChange: ((TagEditorState) -> TagEditorState) -> Unit,
    val onTagEditorSave: () -> Unit,
    val onTagEditorDismiss: () -> Unit,
    val onAddFolder: () -> Unit,
    val onEditFolder: (TagFolderWithTags) -> Unit,
    val onDeleteFolder: (TagFolderWithTags) -> Unit,
    val onReorderFolders: (List<String>) -> Unit,
    val onFolderEditorChange: ((FolderEditorState) -> FolderEditorState) -> Unit,
    val onFolderEditorSave: () -> Unit,
    val onFolderEditorDismiss: () -> Unit,
    val onCreateTag: suspend (String) -> String?,
    val onPickIcon: (IconTarget, String) -> Unit,
)

@Composable
fun TagManagementScreen(state: TagManagementUiState, snackbarHost: SnackbarHostState, actions: TagManagementActions) {
    var pendingTagDelete by remember { mutableStateOf<TagWithUsage?>(null) }
    var pendingFolderDelete by remember { mutableStateOf<TagFolderWithTags?>(null) }
    val addLabel = stringResource(
        if (state.tab == TagManagementTab.TAGS) R.string.subscriptions_manage_tag_add else R.string.subscriptions_manage_folder_add,
    )
    val onAdd = if (state.tab == TagManagementTab.TAGS) actions.onAddTag else actions.onAddFolder
    Scaffold(
        topBar = {
            SubMarkTopAppBar(
                title = stringResource(R.string.subscriptions_manage_tag_title),
                onBack = actions.onBack,
                actions = {
                    IconButton(onClick = onAdd) { Icon(Icons.Rounded.Add, contentDescription = addLabel) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Rounded.Add, contentDescription = addLabel) }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SegmentedTabs(
                items = TagManagementTab.entries,
                selected = state.tab,
                onSelect = actions.onSelectTab,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                stringResource(if (it == TagManagementTab.TAGS) R.string.subscriptions_manage_tab_tags else R.string.subscriptions_manage_tab_folders)
            }
            SearchField(
                query = state.query,
                onQueryChange = actions.onQueryChange,
                placeholder = stringResource(
                    if (state.tab == TagManagementTab.TAGS) R.string.subscriptions_manage_tag_search else R.string.subscriptions_manage_folder_search,
                ),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            when {
                state.loading -> LoadingState()
                state.tab == TagManagementTab.TAGS -> TagList(state, actions, onDelete = { pendingTagDelete = it })
                else -> FolderList(state, actions)
            }
        }
    }

    state.tagEditor?.let { editor ->
        TagEditorSheet(
            editor = editor,
            actions = actions,
            onDelete = {
                val usage = state.tags.firstOrNull { it.tag.id == editor.id }
                    ?: state.allTags.firstOrNull { it.id == editor.id }?.let { TagWithUsage(it, 0) }
                pendingTagDelete = usage
            },
        )
    }
    state.folderEditor?.let { editor ->
        FolderEditorSheet(
            editor = editor,
            allTags = state.allTags,
            actions = actions,
            onDelete = {
                pendingFolderDelete = state.folders.firstOrNull { it.folder.folder.id == editor.existing?.id }?.folder
                    ?: editor.existing?.let { TagFolderWithTags(it, editor.tagIds) }
            },
        )
    }
    pendingTagDelete?.let { usage ->
        ConfirmDialog(
            title = stringResource(R.string.subscriptions_manage_tag_delete_title, usage.tag.name),
            message = if (usage.subscriptionCount > 0) {
                pluralStringResource(R.plurals.subscriptions_manage_tag_delete_in_use, usage.subscriptionCount, usage.subscriptionCount)
            } else {
                stringResource(R.string.subscriptions_manage_tag_delete_unused)
            },
            onConfirm = {
                pendingTagDelete = null
                actions.onTagEditorDismiss()
                actions.onDeleteTag(usage.tag)
            },
            onDismiss = { pendingTagDelete = null },
            confirmLabel = stringResource(CoreR.string.ui_action_delete),
            destructive = true,
        )
    }
    pendingFolderDelete?.let { folder ->
        ConfirmDialog(
            title = stringResource(R.string.subscriptions_manage_folder_delete_title, folder.folder.name),
            message = stringResource(R.string.subscriptions_manage_folder_delete_message),
            onConfirm = {
                pendingFolderDelete = null
                actions.onDeleteFolder(folder)
            },
            onDismiss = { pendingFolderDelete = null },
            confirmLabel = stringResource(CoreR.string.ui_action_delete),
            destructive = true,
        )
    }
}

@Composable
private fun TagList(state: TagManagementUiState, actions: TagManagementActions, onDelete: (TagWithUsage) -> Unit) {
    if (state.tags.isEmpty()) {
        if (state.allTags.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.subscriptions_manage_tag_empty_title),
                message = stringResource(R.string.subscriptions_manage_tag_empty_message),
                icon = Icons.Rounded.Sell,
                actionLabel = stringResource(R.string.subscriptions_manage_tag_add),
                onAction = actions.onAddTag,
            )
        } else {
            EmptyState(title = stringResource(R.string.subscriptions_manage_no_matches), icon = Icons.Rounded.Sell)
        }
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.tags.forEach { usage -> TagRow(usage, onClick = { actions.onEditTag(usage.tag) }, onDelete = { onDelete(usage) }) }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun TagRow(usage: TagWithUsage, onClick: () -> Unit, onDelete: () -> Unit) {
    val tag = usage.tag
    val accent = tag.color.themedColor()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (tag.iconValue != null) {
                SubscriptionIcon(tag.iconType, tag.iconValue, tag.name, size = 32.dp, tint = accent, background = accent.copy(alpha = 0.16f))
            } else {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(12.dp).background(accent, CircleShape)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tag.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (usage.subscriptionCount > 0) {
                        pluralStringResource(R.plurals.subscriptions_manage_usage_count, usage.subscriptionCount, usage.subscriptionCount)
                    } else {
                        stringResource(R.string.subscriptions_manage_tag_unused)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.subscriptions_manage_delete_named, tag.name))
            }
        }
    }
}

@Composable
private fun FolderList(state: TagManagementUiState, actions: TagManagementActions) {
    if (state.folders.isEmpty()) {
        EmptyState(
            title = stringResource(if (state.query.isBlank()) R.string.subscriptions_manage_folder_empty_title else R.string.subscriptions_manage_no_matches),
            message = if (state.query.isBlank()) stringResource(R.string.subscriptions_manage_folder_empty_message) else null,
            icon = Icons.Rounded.Folder,
            actionLabel = if (state.query.isBlank()) stringResource(R.string.subscriptions_manage_folder_add) else null,
            onAction = if (state.query.isBlank()) actions.onAddFolder else null,
        )
        return
    }
    val reorderable = state.query.isBlank()
    var rows by remember(state.folders) { mutableStateOf(state.folders) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.subscriptions_manage_folder_order_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (reorderable) {
            ReorderableItemsColumn(
                items = rows,
                key = { it.folder.folder.id },
                onMove = { from, to ->
                    rows = rows.moved(from, to)
                    actions.onReorderFolders(rows.map { it.folder.folder.id })
                },
            ) { row, dragging, handle ->
                FolderRowCard(row, dragging, onClick = { actions.onEditFolder(row.folder) }) { DragHandleIcon(handle) }
            }
        } else {
            rows.forEach { row -> FolderRowCard(row, false, onClick = { actions.onEditFolder(row.folder) }) {} }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun FolderRowCard(row: FolderRow, elevated: Boolean, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    val folder = row.folder.folder
    val accent = colorFromHex(folder.colorHex) ?: MaterialTheme.colorScheme.primary
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (elevated) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (elevated) 6.dp else 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconType = ManageLogic.folderIconType(folder.iconValue)
            if (iconType != null) {
                SubscriptionIcon(iconType, folder.iconValue, folder.name, size = 36.dp, tint = accent, background = accent.copy(alpha = 0.16f))
            } else {
                Box(Modifier.size(36.dp).background(accent.copy(alpha = 0.16f), MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = accent)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(folder.name, style = MaterialTheme.typography.bodyLarge)
                    if (!folder.isEnabled) StatusBadge(stringResource(R.string.subscriptions_manage_folder_disabled_badge))
                }
                Text(
                    tagSummary(row.tagNames, folder.matchMode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    pluralStringResource(R.plurals.subscriptions_manage_usage_count, row.matchCount, row.matchCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing()
        }
    }
}

@Composable
private fun tagSummary(names: List<String>, mode: TagMatchMode): String {
    val modeText = stringResource(if (mode == TagMatchMode.ANY) R.string.subscriptions_manage_folder_match_any_short else R.string.subscriptions_manage_folder_match_all_short)
    val list = when {
        names.isEmpty() -> stringResource(R.string.subscriptions_manage_folder_no_tags)
        names.size <= 2 -> names.joinToString(", ")
        else -> pluralStringResource(R.plurals.subscriptions_manage_folder_tags_more, names.size - 2, names.take(2).joinToString(", "), names.size - 2)
    }
    return "$list · $modeText"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TagEditorSheet(editor: TagEditorState, actions: TagManagementActions, onDelete: () -> Unit) {
    val accent = editor.color.themedColor()
    val previewName = editor.name.trim().ifEmpty { stringResource(R.string.subscriptions_manage_tag_name_placeholder) }
    ModalBottomSheet(onDismissRequest = actions.onTagEditorDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (editor.isNew) R.string.subscriptions_manage_tag_new_title else R.string.subscriptions_manage_tag_edit_title),
                style = MaterialTheme.typography.titleLarge,
            )
            EditorSectionLabel(stringResource(R.string.subscriptions_manage_preview))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editor.iconValue != null) {
                    SubscriptionIcon(editor.iconType, editor.iconValue, previewName, size = 28.dp, tint = accent, background = accent.copy(alpha = 0.16f))
                }
                TagChip(previewName, editor.color)
            }
            OutlinedTextField(
                value = editor.name,
                onValueChange = { value -> actions.onTagEditorChange { it.copy(name = value) } },
                label = { Text(stringResource(R.string.subscriptions_manage_tag_name)) },
                placeholder = { Text(stringResource(R.string.subscriptions_manage_tag_name_placeholder)) },
                supportingText = editor.nameError?.let { { Text(it.asString()) } },
                isError = editor.nameError != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            EditorSectionLabel(stringResource(R.string.subscriptions_manage_color))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TagColor.entries.forEach { color -> TagColorSwatch(color, editor.color == color) { actions.onTagEditorChange { it.copy(color = color) } } }
            }
            EditorSectionLabel(stringResource(R.string.subscriptions_manage_icon_optional))
            IconEditor(
                iconType = editor.iconType,
                iconValue = editor.iconValue,
                fallbackName = previewName,
                accent = accent,
                onPick = { actions.onPickIcon(IconTarget.TAG, editor.name) },
                onManual = { type, value -> actions.onTagEditorChange { it.copy(iconType = type, iconValue = value) } },
                onClear = { actions.onTagEditorChange { it.copy(iconType = null, iconValue = null) } },
            )
            EditorButtons(
                saving = editor.saving,
                onSave = actions.onTagEditorSave,
                onCancel = actions.onTagEditorDismiss,
                onDelete = if (editor.isNew) null else onDelete,
            )
        }
    }
}

@Composable
private fun TagColorSwatch(color: TagColor, selected: Boolean, onClick: () -> Unit) {
    val c = color.themedColor()
    val label = stringResource(color.labelRes)
    Box(
        Modifier
            .size(36.dp)
            .background(c, CircleShape)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = c.contentColorFor(), modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FolderEditorSheet(editor: FolderEditorState, allTags: List<Tag>, actions: TagManagementActions, onDelete: () -> Unit) {
    var pickTags by rememberSaveable { mutableStateOf(false) }
    val accent = colorFromHex(editor.colorHex) ?: MaterialTheme.colorScheme.primary
    val previewName = editor.name.trim().ifEmpty { stringResource(R.string.subscriptions_manage_folder_name_placeholder) }
    ModalBottomSheet(onDismissRequest = actions.onFolderEditorDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (editor.isNew) R.string.subscriptions_manage_folder_new_title else R.string.subscriptions_manage_folder_edit_title),
                style = MaterialTheme.typography.titleLarge,
            )
            EditorSectionLabel(stringResource(R.string.subscriptions_manage_folder_section_basic))
            OutlinedTextField(
                value = editor.name,
                onValueChange = { value -> actions.onFolderEditorChange { it.copy(name = value) } },
                label = { Text(stringResource(R.string.subscriptions_manage_folder_name)) },
                placeholder = { Text(stringResource(R.string.subscriptions_manage_folder_name_placeholder)) },
                supportingText = editor.nameError?.let { { Text(it.asString()) } },
                isError = editor.nameError != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            EditorSectionLabel(stringResource(R.string.subscriptions_manage_icon_optional))
            IconEditor(
                iconType = ManageLogic.folderIconType(editor.iconValue),
                iconValue = editor.iconValue,
                fallbackName = previewName,
                accent = accent,
                onPick = { actions.onPickIcon(IconTarget.FOLDER, editor.name) },
                onManual = { _, value -> actions.onFolderEditorChange { it.copy(iconValue = value) } },
                onClear = { actions.onFolderEditorChange { it.copy(iconValue = null) } },
            )
            EditorSectionLabel(stringResource(R.string.subscriptions_manage_color))
            ColorSwatches(
                selectedHex = editor.colorHex,
                onSelect = { hex -> actions.onFolderEditorChange { it.copy(colorHex = hex) } },
                allowDefault = true,
            )

            EditorSectionLabel(stringResource(R.string.subscriptions_manage_folder_section_tags))
            val selected = allTags.filter { it.id in editor.tagIds }
            Text(
                pluralStringResource(R.plurals.subscriptions_manage_folder_selected_count, selected.size, selected.size),
                style = MaterialTheme.typography.bodyMedium,
                color = if (editor.tagError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (selected.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    selected.forEach { tag ->
                        TagChip(tag.name, tag.color, onRemove = { actions.onFolderEditorChange { it.copy(tagIds = it.tagIds - tag.id) } })
                    }
                }
            }
            editor.tagError?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick = { pickTags = true }) { Text(stringResource(R.string.subscriptions_manage_folder_choose_tags)) }

            EditorSectionLabel(stringResource(R.string.subscriptions_manage_folder_section_settings))
            SegmentedTabs(
                items = TagMatchMode.entries,
                selected = editor.matchMode,
                onSelect = { mode -> actions.onFolderEditorChange { it.copy(matchMode = mode) } },
            ) {
                stringResource(if (it == TagMatchMode.ANY) R.string.subscriptions_manage_folder_match_any else R.string.subscriptions_manage_folder_match_all)
            }
            Text(
                stringResource(
                    if (editor.matchMode == TagMatchMode.ANY) R.string.subscriptions_manage_folder_match_any_desc else R.string.subscriptions_manage_folder_match_all_desc,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SwitchLine(
                title = stringResource(R.string.subscriptions_manage_folder_show_in_main),
                subtitle = stringResource(R.string.subscriptions_manage_folder_show_in_main_desc),
                checked = editor.showInMainList,
                onChange = { v -> actions.onFolderEditorChange { it.copy(showInMainList = v) } },
            )
            SwitchLine(
                title = stringResource(R.string.subscriptions_manage_folder_enabled),
                subtitle = stringResource(R.string.subscriptions_manage_folder_enabled_desc),
                checked = editor.enabled,
                onChange = { v -> actions.onFolderEditorChange { it.copy(enabled = v) } },
            )
            Text(
                stringResource(R.string.subscriptions_manage_folder_notes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EditorButtons(
                saving = editor.saving,
                onSave = actions.onFolderEditorSave,
                onCancel = actions.onFolderEditorDismiss,
                onDelete = if (editor.isNew) null else onDelete,
            )
        }
    }
    if (pickTags) {
        TagPickerSheet(
            tags = allTags,
            selectedIds = editor.tagIds,
            onDone = { ids ->
                pickTags = false
                actions.onFolderEditorChange { it.copy(tagIds = ids) }
            },
            onDismiss = { pickTags = false },
            onCreate = actions.onCreateTag,
        )
    }
}

@Composable
internal fun SwitchLine(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun EditorButtons(saving: Boolean, onSave: () -> Unit, onCancel: () -> Unit, onDelete: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onDelete != null) {
            TextButton(onClick = onDelete) {
                Text(stringResource(CoreR.string.ui_action_delete), color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCancel) { Text(stringResource(CoreR.string.ui_action_cancel)) }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onSave, enabled = !saving) { Text(stringResource(CoreR.string.ui_action_save)) }
    }
}
