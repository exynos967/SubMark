package io.github.submark.feature.subscriptions.ui.manage.category

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import io.github.submark.core.model.Category
import io.github.submark.core.ui.component.CategoryChip
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.core.ui.component.DragHandleIcon
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.ReorderableItemsColumn
import io.github.submark.core.ui.component.SectionHeader
import io.github.submark.core.ui.component.StatusBadge
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.format.displayName
import io.github.submark.core.ui.format.labelRes
import io.github.submark.core.ui.icon.SubscriptionIcon
import io.github.submark.core.ui.navigation.IconPickerRoute
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.core.ui.util.colorFromHex
import io.github.submark.feature.subscriptions.R
import io.github.submark.feature.subscriptions.ui.common.IconResultEffect
import io.github.submark.feature.subscriptions.ui.manage.ColorSwatches
import io.github.submark.feature.subscriptions.ui.manage.EditorSectionLabel
import io.github.submark.feature.subscriptions.ui.manage.IconEditor
import io.github.submark.feature.subscriptions.ui.manage.ManageLogic.moved
import io.github.submark.core.data.seed.SystemCategories
import io.github.submark.core.ui.R as CoreR

@Composable
fun CategoryManagementScreenRoute(backStackEntry: NavBackStackEntry, onBack: () -> Unit, onNavigate: (Any) -> Unit) {
    val viewModel: CategoryManagementViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    SnackbarEffect(viewModel.messages, snackbarHost)
    IconResultEffect(backStackEntry) { viewModel.onIconPicked(it) }
    CategoryManagementScreen(
        state = state,
        snackbarHost = snackbarHost,
        onBack = onBack,
        onAdd = viewModel::startAdd,
        onEdit = viewModel::startEdit,
        onHide = viewModel::hide,
        onRestore = viewModel::restore,
        onDelete = viewModel::delete,
        onReorder = viewModel::reorder,
        onResetToDefault = viewModel::resetToDefault,
        onEditorChange = viewModel::updateEditor,
        onEditorSave = viewModel::save,
        onEditorDismiss = viewModel::dismissEditor,
        onEditorResetPreset = viewModel::resetPresetStyle,
        onPickIcon = { query -> onNavigate(IconPickerRoute(query = query.ifBlank { null })) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    state: CategoryManagementUiState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Category) -> Unit,
    onHide: (Category) -> Unit,
    onRestore: (Category) -> Unit,
    onDelete: (Category) -> Unit,
    onReorder: (List<String>) -> Unit,
    onResetToDefault: () -> Unit,
    onEditorChange: ((CategoryEditorState) -> CategoryEditorState) -> Unit,
    onEditorSave: () -> Unit,
    onEditorDismiss: () -> Unit,
    onEditorResetPreset: () -> Unit,
    onPickIcon: (String) -> Unit,
) {
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Category?>(null) }
    var pendingHide by remember { mutableStateOf<Category?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SubMarkTopAppBar(
                title = stringResource(R.string.subscriptions_manage_category_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.subscriptions_manage_category_add))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.subscriptions_manage_more_actions))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.subscriptions_manage_category_reset_default)) },
                                leadingIcon = { Icon(Icons.Rounded.RestartAlt, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    confirmReset = true
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.subscriptions_manage_category_add))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        var items by remember(state.visible) { mutableStateOf(state.visible) }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.subscriptions_manage_category_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            ReorderableItemsColumn(
                items = items,
                key = { it.id },
                onMove = { from, to ->
                    items = items.moved(from, to)
                    onReorder(items.map { it.id } + state.hidden.map { it.id })
                },
            ) { category, isDragging, handle ->
                CategoryRow(
                    category = category,
                    elevated = isDragging,
                    dragHandle = handle,
                    onEdit = { onEdit(category) },
                    onHide = if (category.isSystem && category.id != SystemCategories.OTHER_ID) {
                        { pendingHide = category }
                    } else {
                        null
                    },
                    onDelete = if (!category.isSystem) {
                        { pendingDelete = category }
                    } else {
                        null
                    },
                )
            }
            if (state.hidden.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SectionHeader(stringResource(R.string.subscriptions_manage_category_hidden_section))
                state.hidden.forEach { category ->
                    HiddenCategoryRow(category, onRestore = { onRestore(category) })
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    state.editor?.let { editor ->
        CategoryEditorSheet(
            editor = editor,
            onChange = onEditorChange,
            onSave = onEditorSave,
            onDismiss = onEditorDismiss,
            onResetPreset = onEditorResetPreset,
            onPickIcon = onPickIcon,
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.subscriptions_manage_category_reset_title),
            message = stringResource(R.string.subscriptions_manage_category_reset_message),
            onConfirm = {
                confirmReset = false
                onResetToDefault()
            },
            onDismiss = { confirmReset = false },
            confirmLabel = stringResource(R.string.subscriptions_manage_category_reset_confirm),
            destructive = true,
        )
    }
    pendingDelete?.let { category ->
        ConfirmDialog(
            title = stringResource(R.string.subscriptions_manage_category_delete_title, category.displayName().asString()),
            message = stringResource(R.string.subscriptions_manage_category_delete_message),
            onConfirm = {
                pendingDelete = null
                onDelete(category)
            },
            onDismiss = { pendingDelete = null },
            confirmLabel = stringResource(CoreR.string.ui_action_delete),
            destructive = true,
        )
    }
    pendingHide?.let { category ->
        ConfirmDialog(
            title = stringResource(R.string.subscriptions_manage_category_hide_title, category.displayName().asString()),
            message = stringResource(R.string.subscriptions_manage_category_hide_message),
            onConfirm = {
                pendingHide = null
                onHide(category)
            },
            onDismiss = { pendingHide = null },
            confirmLabel = stringResource(R.string.subscriptions_manage_category_hide),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryRow(
    category: Category,
    elevated: Boolean,
    dragHandle: Modifier,
    onEdit: () -> Unit,
    onHide: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    val accent = colorFromHex(category.colorHex) ?: MaterialTheme.colorScheme.primary
    val name = category.displayName().asString().ifBlank { stringResource(R.string.subscriptions_manage_category_no_name) }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (elevated) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (elevated) 6.dp else 0.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onEdit, onLongClick = { menu = true })
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SubscriptionIcon(
                type = category.iconType,
                value = category.iconValue,
                fallbackName = name,
                size = 36.dp,
                tint = accent,
                background = accent.copy(alpha = 0.16f),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                if (category.isSystem) {
                    StatusBadge(stringResource(R.string.subscriptions_manage_category_builtin), modifier = Modifier.padding(top = 2.dp))
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.subscriptions_manage_actions_for, name))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(CoreR.string.ui_action_edit)) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            menu = false
                            onEdit()
                        },
                    )
                    if (onHide != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.subscriptions_manage_category_hide)) },
                            leadingIcon = { Icon(Icons.Rounded.VisibilityOff, contentDescription = null) },
                            onClick = {
                                menu = false
                                onHide()
                            },
                        )
                    }
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(CoreR.string.ui_action_delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menu = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            DragHandleIcon(dragHandle)
        }
    }
}

@Composable
private fun HiddenCategoryRow(category: Category, onRestore: () -> Unit) {
    val accent = colorFromHex(category.colorHex) ?: MaterialTheme.colorScheme.primary
    val name = category.displayName().asString()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SubscriptionIcon(category.iconType, category.iconValue, name, size = 36.dp, tint = accent.copy(alpha = 0.6f), background = accent.copy(alpha = 0.08f))
            Spacer(Modifier.width(12.dp))
            Text(name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = onRestore) {
                Icon(Icons.Rounded.Visibility, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.subscriptions_manage_category_restore))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryEditorSheet(
    editor: CategoryEditorState,
    onChange: ((CategoryEditorState) -> CategoryEditorState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onResetPreset: () -> Unit,
    onPickIcon: (String) -> Unit,
) {
    val presetName = editor.systemKey?.let { stringResource(it.labelRes) }
    val previewName = editor.name.trim().ifEmpty { presetName ?: stringResource(R.string.subscriptions_manage_category_no_name) }
    val accent = colorFromHex(editor.colorHex) ?: MaterialTheme.colorScheme.primary
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (editor.isNew) R.string.subscriptions_manage_category_new_title else R.string.subscriptions_manage_category_edit_title),
                style = MaterialTheme.typography.titleLarge,
            )
            EditorSectionLabel(stringResource(R.string.subscriptions_manage_preview))
            CategoryChip(previewName, color = accent, iconType = editor.iconType, iconValue = editor.iconValue)

            OutlinedTextField(
                value = editor.name,
                onValueChange = { value -> onChange { it.copy(name = value) } },
                label = { Text(stringResource(R.string.subscriptions_manage_category_name)) },
                placeholder = { Text(presetName ?: stringResource(R.string.subscriptions_manage_category_name_placeholder)) },
                supportingText = editor.nameError?.let { { Text(it.asString()) } }
                    ?: presetName?.let { { Text(stringResource(R.string.subscriptions_manage_category_name_preset_hint)) } },
                isError = editor.nameError != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            EditorSectionLabel(stringResource(R.string.subscriptions_manage_icon))
            IconEditor(
                iconType = editor.iconType,
                iconValue = editor.iconValue,
                fallbackName = previewName,
                accent = accent,
                onPick = { onPickIcon(editor.name.ifBlank { presetName.orEmpty() }) },
                onManual = { type, value -> onChange { it.copy(iconType = type, iconValue = value) } },
            )
            EditorSectionLabel(stringResource(R.string.subscriptions_manage_color))
            ColorSwatches(selectedHex = editor.colorHex, onSelect = { hex -> if (hex != null) onChange { it.copy(colorHex = hex) } })
            if (editor.isSystem) {
                OutlinedButton(onClick = onResetPreset, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.subscriptions_manage_category_reset_preset))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.ui_action_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSave, enabled = !editor.saving) { Text(stringResource(CoreR.string.ui_action_save)) }
            }
        }
    }
}
