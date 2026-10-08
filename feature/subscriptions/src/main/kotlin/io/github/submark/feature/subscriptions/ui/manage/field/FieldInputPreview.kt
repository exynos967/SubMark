package io.github.submark.feature.subscriptions.ui.manage.field

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.submark.core.model.CustomFieldType
import io.github.submark.core.ui.component.DatePickerField
import io.github.submark.core.ui.component.TimePickerField
import io.github.submark.feature.subscriptions.R
import io.github.submark.feature.subscriptions.ui.manage.ManageLogic.OptionDraft
import java.time.LocalDate
import java.time.LocalTime

@get:StringRes
internal val CustomFieldType.labelRes: Int
    get() = when (this) {
        CustomFieldType.TEXT -> R.string.subscriptions_manage_field_type_text
        CustomFieldType.MULTILINE -> R.string.subscriptions_manage_field_type_multiline
        CustomFieldType.NUMBER -> R.string.subscriptions_manage_field_type_number
        CustomFieldType.DECIMAL -> R.string.subscriptions_manage_field_type_decimal
        CustomFieldType.DATE -> R.string.subscriptions_manage_field_type_date
        CustomFieldType.DATETIME -> R.string.subscriptions_manage_field_type_datetime
        CustomFieldType.BOOLEAN -> R.string.subscriptions_manage_field_type_boolean
        CustomFieldType.DROPDOWN -> R.string.subscriptions_manage_field_type_dropdown
        CustomFieldType.EMAIL -> R.string.subscriptions_manage_field_type_email
        CustomFieldType.PHONE -> R.string.subscriptions_manage_field_type_phone
        CustomFieldType.URL -> R.string.subscriptions_manage_field_type_url
        CustomFieldType.RATING -> R.string.subscriptions_manage_field_type_rating
    }

@get:StringRes
internal val CustomFieldType.descriptionRes: Int
    get() = when (this) {
        CustomFieldType.TEXT -> R.string.subscriptions_manage_field_type_text_desc
        CustomFieldType.MULTILINE -> R.string.subscriptions_manage_field_type_multiline_desc
        CustomFieldType.NUMBER -> R.string.subscriptions_manage_field_type_number_desc
        CustomFieldType.DECIMAL -> R.string.subscriptions_manage_field_type_decimal_desc
        CustomFieldType.DATE -> R.string.subscriptions_manage_field_type_date_desc
        CustomFieldType.DATETIME -> R.string.subscriptions_manage_field_type_datetime_desc
        CustomFieldType.BOOLEAN -> R.string.subscriptions_manage_field_type_boolean_desc
        CustomFieldType.DROPDOWN -> R.string.subscriptions_manage_field_type_dropdown_desc
        CustomFieldType.EMAIL -> R.string.subscriptions_manage_field_type_email_desc
        CustomFieldType.PHONE -> R.string.subscriptions_manage_field_type_phone_desc
        CustomFieldType.URL -> R.string.subscriptions_manage_field_type_url_desc
        CustomFieldType.RATING -> R.string.subscriptions_manage_field_type_rating_desc
    }

/** Interactive, throw-away preview of how the field will look in the subscription form. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FieldInputPreview(
    type: CustomFieldType,
    name: String,
    placeholder: String,
    helpText: String,
    required: Boolean,
    options: List<OptionDraft>,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val label = (name.ifBlank { stringResource(R.string.subscriptions_manage_field_preview_name) }) + if (required) " *" else ""
    val help = helpText.takeIf { it.isNotBlank() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (type) {
            CustomFieldType.BOOLEAN -> {
                var checked by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = checked, onCheckedChange = { checked = it })
                }
            }
            CustomFieldType.RATING -> {
                var rating by remember { mutableStateOf(0) }
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Row {
                    (1..5).forEach { star ->
                        IconButton(onClick = { rating = if (rating == star) 0 else star }) {
                            Icon(
                                if (star <= rating) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                contentDescription = stringResource(R.string.subscriptions_manage_field_rating_star, star),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            CustomFieldType.DATE -> {
                var date by remember { mutableStateOf<LocalDate?>(null) }
                DatePickerField(label = label, date = date, onDateChange = { date = it }, today = today, modifier = Modifier.fillMaxWidth())
            }
            CustomFieldType.DATETIME -> {
                var date by remember { mutableStateOf<LocalDate?>(null) }
                var time by remember { mutableStateOf<LocalTime?>(null) }
                Row {
                    DatePickerField(label = label, date = date, onDateChange = { date = it }, today = today, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    TimePickerField(
                        label = stringResource(R.string.subscriptions_manage_field_preview_time),
                        time = time,
                        onTimeChange = { time = it },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            CustomFieldType.DROPDOWN -> {
                var expanded by remember { mutableStateOf(false) }
                var selected by remember { mutableStateOf<String?>(null) }
                val labels = options.map { it.label }.filter { it.isNotBlank() }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selected.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(label) },
                        placeholder = placeholder.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        labels.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = {
                                selected = option
                                expanded = false
                            })
                        }
                    }
                }
            }
            else -> {
                var text by remember(type) { mutableStateOf("") }
                val keyboard = when (type) {
                    CustomFieldType.NUMBER -> KeyboardType.Number
                    CustomFieldType.DECIMAL -> KeyboardType.Decimal
                    CustomFieldType.EMAIL -> KeyboardType.Email
                    CustomFieldType.PHONE -> KeyboardType.Phone
                    CustomFieldType.URL -> KeyboardType.Uri
                    else -> KeyboardType.Text
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    placeholder = placeholder.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                    singleLine = type != CustomFieldType.MULTILINE,
                    minLines = if (type == CustomFieldType.MULTILINE) 3 else 1,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (help != null) {
            Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        }
    }
}
