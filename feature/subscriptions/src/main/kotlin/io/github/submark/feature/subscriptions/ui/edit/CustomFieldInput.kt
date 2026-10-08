package io.github.submark.feature.subscriptions.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.submark.core.data.repository.CustomFieldWithOptions
import io.github.submark.core.model.CustomFieldType
import io.github.submark.core.ui.component.DatePickerField
import io.github.submark.core.ui.component.TimePickerField
import io.github.submark.feature.subscriptions.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** Typed input for one custom field. [value] is the encoded value ("" = empty). */
@Composable
fun CustomFieldInput(
    field: CustomFieldWithOptions,
    value: String,
    onValueChange: (String) -> Unit,
    today: LocalDate,
    zone: ZoneId,
    showErrors: Boolean,
) {
    val def = field.definition
    val label = if (def.isRequired) stringResource(R.string.subscriptions_edit_field_required_label, def.name) else def.name
    val missing = def.isRequired && value.isBlank()
    val invalid = !SubscriptionFormLogic.isFieldValueValid(field, value)
    val error = when {
        invalid && value.isNotBlank() -> stringResource(R.string.subscriptions_edit_field_invalid_value, invalidHint(def.type))
        missing && showErrors -> stringResource(R.string.subscriptions_edit_field_missing)
        else -> null
    }
    val supporting = error ?: def.helpText?.takeIf { it.isNotBlank() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (def.type) {
            CustomFieldType.TEXT, CustomFieldType.MULTILINE, CustomFieldType.NUMBER, CustomFieldType.DECIMAL,
            CustomFieldType.EMAIL, CustomFieldType.PHONE, CustomFieldType.URL,
            -> OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                placeholder = def.placeholder?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                supportingText = supporting?.let { { Text(it) } },
                isError = error != null,
                singleLine = def.type != CustomFieldType.MULTILINE,
                minLines = if (def.type == CustomFieldType.MULTILINE) 3 else 1,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardFor(def.type)),
                modifier = Modifier.fillMaxWidth(),
            )
            CustomFieldType.DATE -> {
                val date = parseDate(value)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DatePickerField(
                        label = label,
                        date = date,
                        onDateChange = { onValueChange(it.toString()) },
                        today = today,
                        supportingText = supporting,
                        isError = error != null,
                        modifier = Modifier.weight(1f),
                    )
                    ClearButton(visible = value.isNotBlank()) { onValueChange("") }
                }
            }
            CustomFieldType.DATETIME -> {
                val instant = parseInstant(value)
                val local = instant?.atZone(zone)
                Text(label, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DatePickerField(
                        label = stringResource(R.string.subscriptions_edit_field_date),
                        date = local?.toLocalDate(),
                        onDateChange = { d ->
                            val time = local?.toLocalTime() ?: LocalTime.MIDNIGHT
                            onValueChange(d.atTime(time).atZone(zone).toInstant().toString())
                        },
                        today = today,
                        isError = error != null,
                        modifier = Modifier.weight(1f),
                    )
                    TimePickerField(
                        label = stringResource(R.string.subscriptions_edit_field_time),
                        time = local?.toLocalTime(),
                        onTimeChange = { t ->
                            val d = local?.toLocalDate() ?: today
                            onValueChange(d.atTime(t).atZone(zone).toInstant().toString())
                        },
                        modifier = Modifier.weight(1f),
                    )
                    ClearButton(visible = value.isNotBlank()) { onValueChange("") }
                }
                supporting?.let { if (error != null) ErrorText(it) else HintText(it) }
            }
            CustomFieldType.BOOLEAN -> {
                val checked = value == "true"
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = checked, role = Role.Switch, onValueChange = { onValueChange(it.toString()) })
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = checked, onCheckedChange = null)
                }
                supporting?.let { if (error != null) ErrorText(it) else HintText(it) }
            }
            CustomFieldType.DROPDOWN -> {
                val none = stringResource(R.string.subscriptions_edit_field_none)
                val options = listOf("" to none) + field.options.sortedBy { it.sortOrder }.map { it.id to it.label }
                SelectField(
                    label = label,
                    value = field.options.firstOrNull { it.id == value }?.label ?: (def.placeholder?.takeIf { it.isNotBlank() && value.isBlank() } ?: if (value.isBlank()) "" else none),
                    options = options,
                    onSelect = onValueChange,
                    supportingText = supporting,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            CustomFieldType.RATING -> {
                val rating = value.toIntOrNull() ?: 0
                Text(label, style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    (1..5).forEach { star ->
                        IconButton(onClick = { onValueChange(if (star == rating) "" else star.toString()) }) {
                            Icon(
                                if (star <= rating) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                contentDescription = pluralStringResource(R.plurals.subscriptions_edit_field_stars, star, star),
                                tint = if (star <= rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
                supporting?.let { if (error != null) ErrorText(it) else HintText(it) }
            }
        }
    }
}

@Composable
private fun ClearButton(visible: Boolean, onClear: () -> Unit) {
    if (visible) {
        TextButton(onClick = onClear) { Text(stringResource(R.string.subscriptions_edit_clear)) }
    }
}

@Composable
private fun invalidHint(type: CustomFieldType): String = stringResource(
    when (type) {
        CustomFieldType.NUMBER -> R.string.subscriptions_edit_field_expect_integer
        CustomFieldType.DECIMAL -> R.string.subscriptions_edit_field_expect_decimal
        CustomFieldType.EMAIL -> R.string.subscriptions_edit_field_expect_email
        CustomFieldType.PHONE -> R.string.subscriptions_edit_field_expect_phone
        CustomFieldType.URL -> R.string.subscriptions_edit_field_expect_url
        else -> R.string.subscriptions_edit_field_expect_other
    },
)

private fun keyboardFor(type: CustomFieldType): KeyboardType = when (type) {
    CustomFieldType.NUMBER -> KeyboardType.Number
    CustomFieldType.DECIMAL -> KeyboardType.Decimal
    CustomFieldType.EMAIL -> KeyboardType.Email
    CustomFieldType.PHONE -> KeyboardType.Phone
    CustomFieldType.URL -> KeyboardType.Uri
    else -> KeyboardType.Text
}

private fun parseDate(value: String): LocalDate? = try {
    value.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
} catch (e: DateTimeParseException) {
    null
}

private fun parseInstant(value: String): Instant? = try {
    value.takeIf { it.isNotBlank() }?.let(Instant::parse)
} catch (e: DateTimeParseException) {
    null
}
