package io.github.submark.feature.subscriptions.ui.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.submark.core.model.Category
import io.github.submark.core.ui.component.CategoryChip
import io.github.submark.core.ui.component.ColorPickerDialog
import io.github.submark.core.ui.component.DefaultColorPresets
import io.github.submark.core.ui.component.SegmentedTabs
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.format.displayName
import io.github.submark.core.ui.util.colorFromHex
import io.github.submark.core.ui.util.toHex
import io.github.submark.feature.subscriptions.R
import io.github.submark.core.ui.R as UiR

/** Read-only text field that opens a dropdown of [options]. */
@Composable
fun <T> SelectField(
    label: String,
    value: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: String? = null,
    isError: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
            supportingText = supportingText?.let { { Text(it) } },
            isError = isError,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(enabled = enabled, role = Role.DropdownList) { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = {
                    expanded = false
                    onSelect(key)
                })
            }
        }
    }
}

@Composable
fun ErrorText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = modifier)
}

@Composable
fun HintText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = modifier)
}

/** Day-of-month grid (1..31) for the fixed payment day. */
@Composable
fun FixedDayDialog(initial: Int?, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(initial ?: 1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subscriptions_edit_fixed_day_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyVerticalGrid(columns = GridCells.Fixed(7), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items((1..31).toList()) { day ->
                        val isSelected = day == selected
                        Surface(
                            onClick = { selected = day },
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) { Text(day.toString(), style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
                if (SubscriptionFormLogic.fixedDayOverflows(selected)) {
                    HintText(stringResource(R.string.subscriptions_edit_fixed_day_overflow, selected))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text(stringResource(UiR.string.ui_action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.ui_action_cancel)) } },
    )
}

/** "Set by duration": N days or N months after the start date. */
@Composable
fun DurationDialog(onConfirm: (amount: Int, months: Boolean) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("1") }
    var months by rememberSaveable { mutableStateOf(true) }
    val amount = text.toIntOrNull()?.takeIf { it in 1..MAX_DURATION }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subscriptions_edit_duration_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SegmentedTabs(listOf(false, true), selected = months, onSelect = { months = it }) {
                    stringResource(if (it) R.string.subscriptions_edit_duration_months else R.string.subscriptions_edit_duration_days)
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() }.take(4) },
                    label = { Text(stringResource(R.string.subscriptions_edit_duration_amount)) },
                    isError = amount == null,
                    supportingText = if (amount == null) {
                        { Text(stringResource(R.string.subscriptions_edit_duration_invalid, MAX_DURATION)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { amount?.let { onConfirm(it, months) } }, enabled = amount != null) {
                Text(stringResource(UiR.string.ui_action_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.ui_action_cancel)) } },
    )
}

private const val MAX_DURATION = 1000

/** Visible categories with "New category" and "Manage". */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryPickerSheet(
    categories: List<Category>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.subscriptions_edit_category_picker_title), style = MaterialTheme.typography.titleLarge)
            val (system, custom) = categories.partition { it.isSystem }
            CategoryGroup(stringResource(R.string.subscriptions_edit_category_system), system, selectedId, onSelect)
            Text(
                stringResource(R.string.subscriptions_edit_category_custom),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (custom.isEmpty()) {
                HintText(stringResource(R.string.subscriptions_edit_category_custom_empty))
            } else {
                CategoryGroup(null, custom, selectedId, onSelect)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onCreate) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(stringResource(R.string.subscriptions_edit_category_new), modifier = Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = onManage) {
                    Icon(Icons.Rounded.Settings, contentDescription = null)
                    Text(stringResource(R.string.subscriptions_edit_category_manage), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryGroup(title: String?, categories: List<Category>, selectedId: String, onSelect: (String) -> Unit) {
    if (title != null) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { category ->
            CategoryChip(
                name = category.displayName().asString(),
                color = colorFromHex(category.colorHex),
                iconType = category.iconType,
                iconValue = category.iconValue,
                selected = category.id == selectedId,
                onClick = { onSelect(category.id) },
            )
        }
    }
}

/** Quick custom category: name + color; the icon can be changed later in category management. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewCategoryDialog(onConfirm: (name: String, colorHex: String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var colorHex by rememberSaveable { mutableStateOf(DefaultColorPresets.first().toHex()) }
    var customPicker by rememberSaveable { mutableStateOf(false) }
    if (customPicker) {
        ColorPickerDialog(
            initial = colorFromHex(colorHex),
            onConfirm = { color ->
                if (color != null) colorHex = color.toHex()
                customPicker = false
            },
            onDismiss = { customPicker = false },
            allowDefault = false,
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subscriptions_edit_category_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.subscriptions_edit_category_name)) },
                    placeholder = { Text(stringResource(R.string.subscriptions_edit_category_name_hint)) },
                    singleLine = true,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DefaultColorPresets.forEach { color ->
                        val hex = color.toHex()
                        Surface(
                            onClick = { colorHex = hex },
                            shape = CircleShape,
                            color = color,
                            border = if (hex == colorHex) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                            modifier = Modifier.size(30.dp),
                        ) {}
                    }
                    Surface(onClick = { customPicker = true }, shape = CircleShape, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Rounded.Palette,
                            contentDescription = stringResource(R.string.subscriptions_edit_category_custom_color),
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                }
                CategoryChip(name = name.ifBlank { "…" }, color = colorFromHex(colorHex), iconValue = "category", iconType = io.github.submark.core.model.IconType.SYMBOL)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim(), colorHex) }, enabled = name.isNotBlank()) {
                Text(stringResource(UiR.string.ui_action_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.ui_action_cancel)) } },
    )
}

/** Trial length: preset chips plus a custom value. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrialDaysInput(text: String, onChange: (String) -> Unit, isError: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.subscriptions_edit_trial_days), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubscriptionFormLogic.TRIAL_PRESETS.forEach { days ->
                FilterChip(
                    selected = text.trim() == days.toString(),
                    onClick = { onChange(days.toString()) },
                    label = { Text(androidx.compose.ui.res.pluralStringResource(R.plurals.subscriptions_edit_days, days, days)) },
                )
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(4)) },
            label = { Text(stringResource(R.string.subscriptions_edit_trial_custom_days)) },
            isError = isError,
            supportingText = if (isError) {
                { Text(stringResource(R.string.subscriptions_edit_error_trial_days)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
