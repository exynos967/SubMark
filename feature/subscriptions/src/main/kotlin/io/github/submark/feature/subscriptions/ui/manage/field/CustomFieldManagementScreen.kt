package io.github.submark.feature.subscriptions.ui.manage.field

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.data.repository.CustomFieldStats
import io.github.submark.core.data.repository.CustomFieldWithOptions
import io.github.submark.core.model.Category
import io.github.submark.core.model.CustomFieldDefinition
import io.github.submark.core.model.CustomFieldType
import io.github.submark.core.model.newId
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.core.ui.component.DragHandleIcon
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.ReorderableItemsColumn
import io.github.submark.core.ui.component.StatusBadge
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.component.currentLocale
import io.github.submark.core.ui.format.BadgeTone
import io.github.submark.core.ui.format.DateLabels
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.format.displayName
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.feature.subscriptions.R
import io.github.submark.feature.subscriptions.ui.manage.EditorSectionLabel
import io.github.submark.feature.subscriptions.ui.manage.ManageLogic.FieldFilter
import io.github.submark.feature.subscriptions.ui.manage.ManageLogic.OptionDraft
import io.github.submark.feature.subscriptions.ui.manage.ManageLogic.moved
import io.github.submark.feature.subscriptions.ui.manage.tag.SwitchLine
import java.time.LocalDate
import java.time.format.FormatStyle
import io.github.submark.core.ui.R as CoreR

@Composable
fun CustomFieldManagementScreenRoute(onBack: () -> Unit) {
    val viewModel: CustomFieldManagementViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    SnackbarEffect(viewModel.messages, snackbarHost)
    val optionLabel: (Int) -> String = { n -> context.getString(R.string.subscriptions_manage_field_option_default, n) }
    CustomFieldManagementScreen(
        state = state,
        snackbarHost = snackbarHost,
        onBack = onBack,
        onFilter = viewModel::setFilter,
        onAdd = viewModel::startAdd,
        onEdit = viewModel::startEdit,
        onSetActive = viewModel::setActive,
        onReorder = viewModel::reorder,
        onEditorChange = { transform -> viewModel.updateEditor(optionLabel, transform) },
        onEditorSave = viewModel::save,
        onEditorDismiss = viewModel::dismissEditor,
        onDelete = viewModel::delete,
    )
}

@Composable
fun CustomFieldManagementScreen(
    state: CustomFieldManagementUiState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onFilter: (FieldFilter) -> Unit,
    onAdd: () -> Unit,
    onEdit: (CustomFieldWithOptions) -> Unit,
    onSetActive: (String, Boolean) -> Unit,
    onReorder: (List<String>) -> Unit,
    onEditorChange: ((FieldEditorState) -> FieldEditorState) -> Unit,
    onEditorSave: () -> Unit,
    onEditorDismiss: () -> Unit,
    onDelete: (CustomFieldDefinition) -> Unit,
) {
    val addLabel = stringResource(R.string.subscriptions_manage_field_add)
    Scaffold(
        topBar = {
            SubMarkTopAppBar(
                title = stringResource(R.string.subscriptions_manage_field_title),
                onBack = onBack,
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = onAdd) { Icon(Icons.Rounded.Add, contentDescription = addLabel) } },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        val categoryNames = state.categories.associate { it.id to it.displayName().asString() }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.filter == FieldFilter.All,
                    onClick = { onFilter(FieldFilter.All) },
                    label = { Text(stringResource(R.string.subscriptions_manage_field_filter_all)) },
                )
                FilterChip(
                    selected = state.filter == FieldFilter.Global,
                    onClick = { onFilter(FieldFilter.Global) },
                    label = { Text(stringResource(R.string.subscriptions_manage_field_scope_global)) },
                )
                state.categories.filter { !it.isHidden }.forEach { category ->
                    FilterChip(
                        selected = state.filter == FieldFilter.Category(category.id),
                        onClick = { onFilter(FieldFilter.Category(category.id)) },
                        label = { Text(categoryNames[category.id].orEmpty()) },
                    )
                }
            }
            HelpCard(Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            if (state.fields.isEmpty()) {
                EmptyState(
                    title = stringResource(
                        if (state.totalCount == 0) R.string.subscriptions_manage_field_empty_title else R.string.subscriptions_manage_field_empty_filtered,
                    ),
                    message = if (state.totalCount == 0) stringResource(R.string.subscriptions_manage_field_empty_message) else null,
                    icon = Icons.Rounded.TextFields,
                )
            } else if (state.filter == FieldFilter.All) {
                var items by remember(state.fields) { mutableStateOf(state.fields) }
                ReorderableItemsColumn(
                    items = items,
                    key = { it.definition.id },
                    onMove = { from, to ->
                        items = items.moved(from, to)
                        onReorder(items.map { it.definition.id })
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { field, dragging, handle ->
                    FieldRow(field, categoryNames, dragging, onClick = { onEdit(field) }, onSetActive = onSetActive) { DragHandleIcon(handle) }
                }
            } else {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.fields.forEach { field ->
                        FieldRow(field, categoryNames, false, onClick = { onEdit(field) }, onSetActive = onSetActive) {}
                    }
                }
            }
            Spacer(Modifier.height(88.dp))
        }
    }

    state.editor?.let { editor ->
        FieldEditorDialog(
            editor = editor,
            categories = state.categories,
            stats = editor.existing?.let { state.stats[it.id] },
            today = state.today,
            onChange = onEditorChange,
            onSave = onEditorSave,
            onDismiss = onEditorDismiss,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun HelpCard(modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(12.dp)) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.subscriptions_manage_field_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun FieldRow(
    field: CustomFieldWithOptions,
    categoryNames: Map<String, String>,
    elevated: Boolean,
    onClick: () -> Unit,
    onSetActive: (String, Boolean) -> Unit,
    trailing: @Composable () -> Unit,
) {
    val def = field.definition
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (elevated) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (elevated) 6.dp else 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(def.name, style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(def.type.labelRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val scope = def.categoryId?.let { categoryNames[it] ?: stringResource(R.string.subscriptions_manage_field_scope_missing) }
                        ?: stringResource(R.string.subscriptions_manage_field_scope_global)
                    StatusBadge(scope, tone = if (def.categoryId == null) BadgeTone.PRIMARY else BadgeTone.SECONDARY)
                    if (def.isRequired) StatusBadge(stringResource(R.string.subscriptions_manage_field_badge_required), tone = BadgeTone.WARNING)
                    if (!def.isActive) StatusBadge(stringResource(R.string.subscriptions_manage_field_badge_inactive))
                }
            }
            Switch(checked = def.isActive, onCheckedChange = { onSetActive(def.id, it) })
            trailing()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldEditorDialog(
    editor: FieldEditorState,
    categories: List<Category>,
    stats: CustomFieldStats?,
    today: LocalDate,
    onChange: ((FieldEditorState) -> FieldEditorState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (CustomFieldDefinition) -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(stringResource(if (editor.isNew) R.string.subscriptions_manage_field_new_title else R.string.subscriptions_manage_field_edit_title))
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.ui_action_cancel))
                        }
                    },
                    actions = {
                        TextButton(onClick = onSave, enabled = !editor.saving) { Text(stringResource(CoreR.string.ui_action_save)) }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EditorSectionLabel(stringResource(R.string.subscriptions_manage_field_section_basic))
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = { v -> onChange { it.copy(name = v) } },
                    label = { Text(stringResource(R.string.subscriptions_manage_field_name)) },
                    placeholder = { Text(stringResource(R.string.subscriptions_manage_field_name_placeholder)) },
                    supportingText = editor.nameError?.let { { Text(it.asString()) } },
                    isError = editor.nameError != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TypePicker(editor.type) { type -> onChange { it.copy(type = type) } }

                EditorSectionLabel(stringResource(R.string.subscriptions_manage_field_section_display))
                OutlinedTextField(
                    value = editor.placeholder,
                    onValueChange = { v -> onChange { it.copy(placeholder = v) } },
                    label = { Text(stringResource(R.string.subscriptions_manage_field_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = editor.helpText,
                    onValueChange = { v -> onChange { it.copy(helpText = v) } },
                    label = { Text(stringResource(R.string.subscriptions_manage_field_help_text)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                SwitchLine(
                    title = stringResource(R.string.subscriptions_manage_field_required),
                    subtitle = stringResource(R.string.subscriptions_manage_field_required_desc),
                    checked = editor.required,
                    onChange = { v -> onChange { it.copy(required = v) } },
                )
                SwitchLine(
                    title = stringResource(R.string.subscriptions_manage_field_active),
                    subtitle = stringResource(R.string.subscriptions_manage_field_active_desc),
                    checked = editor.active,
                    onChange = { v -> onChange { it.copy(active = v) } },
                )
                ScopePicker(editor.categoryId, categories) { id -> onChange { it.copy(categoryId = id) } }

                if (editor.type == CustomFieldType.DROPDOWN) {
                    EditorSectionLabel(stringResource(R.string.subscriptions_manage_field_section_options))
                    OptionsEditor(editor.options, editor.optionsError?.asString()) { options -> onChange { it.copy(options = options) } }
                }

                EditorSectionLabel(stringResource(R.string.subscriptions_manage_preview))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    FieldInputPreview(
                        type = editor.type,
                        name = editor.name,
                        placeholder = editor.placeholder,
                        helpText = editor.helpText,
                        required = editor.required,
                        options = editor.options,
                        today = today,
                        modifier = Modifier.padding(12.dp),
                    )
                }

                val existing = editor.existing
                if (existing != null) {
                    EditorSectionLabel(stringResource(R.string.subscriptions_manage_field_section_stats))
                    val locale = currentLocale()
                    val created = existing.createdAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    StatLine(
                        stringResource(R.string.subscriptions_manage_field_stat_using),
                        pluralStringResource(R.plurals.subscriptions_manage_usage_count, stats?.subscriptionsUsing ?: 0, stats?.subscriptionsUsing ?: 0),
                    )
                    StatLine(stringResource(R.string.subscriptions_manage_field_stat_filled), (stats?.filledValues ?: 0).toString())
                    StatLine(stringResource(R.string.subscriptions_manage_field_stat_created), DateLabels.formatDate(created, FormatStyle.MEDIUM, locale))
                    OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.subscriptions_manage_field_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(onClick = onSave, enabled = !editor.saving, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(CoreR.string.ui_action_save))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    val existing = editor.existing
    if (confirmDelete && existing != null) {
        ConfirmDialog(
            title = stringResource(R.string.subscriptions_manage_field_delete_title, existing.name),
            message = stringResource(R.string.subscriptions_manage_field_delete_message),
            onConfirm = {
                confirmDelete = false
                onDelete(existing)
            },
            onDismiss = { confirmDelete = false },
            confirmLabel = stringResource(CoreR.string.ui_action_delete),
            destructive = true,
        )
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypePicker(type: CustomFieldType, onSelect: (CustomFieldType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(type.labelRes),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.subscriptions_manage_field_type)) },
            supportingText = { Text(stringResource(type.descriptionRes)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CustomFieldType.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(option.labelRes))
                            Text(
                                stringResource(option.descriptionRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopePicker(categoryId: String?, categories: List<Category>, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val globalLabel = stringResource(R.string.subscriptions_manage_field_scope_global)
    val current = categoryId?.let { id -> categories.firstOrNull { it.id == id }?.displayName()?.asString() }
        ?: if (categoryId == null) globalLabel else stringResource(R.string.subscriptions_manage_field_scope_missing)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.subscriptions_manage_field_scope)) },
            supportingText = {
                Text(
                    stringResource(
                        if (categoryId == null) R.string.subscriptions_manage_field_scope_global_desc else R.string.subscriptions_manage_field_scope_category_desc,
                    ),
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(globalLabel) }, onClick = {
                expanded = false
                onSelect(null)
            })
            categories.filter { !it.isHidden || it.id == categoryId }.forEach { category ->
                DropdownMenuItem(text = { Text(category.displayName().asString()) }, onClick = {
                    expanded = false
                    onSelect(category.id)
                })
            }
        }
    }
}

@Composable
private fun OptionsEditor(options: List<OptionDraft>, error: String?, onChange: (List<OptionDraft>) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, option ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = option.label,
                    onValueChange = { v -> onChange(options.toMutableList().also { it[index] = option.copy(label = v) }) },
                    label = { Text(stringResource(R.string.subscriptions_manage_field_option_label, index + 1)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onChange(options.filterIndexed { i, _ -> i != index }) }, enabled = options.size > 1) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.subscriptions_manage_field_option_remove, index + 1))
                }
            }
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
            onChange(options + OptionDraft(newId(), context.getString(R.string.subscriptions_manage_field_option_default, options.size + 1)))
        }) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.subscriptions_manage_field_option_add))
        }
        Text(
            stringResource(R.string.subscriptions_manage_field_options_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
