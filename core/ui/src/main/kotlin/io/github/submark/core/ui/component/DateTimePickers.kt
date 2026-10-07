package io.github.submark.core.ui.component

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.submark.core.ui.R
import io.github.submark.core.ui.format.DateLabels
import io.github.submark.core.ui.format.DateQuickPick
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.theme.SubMarkTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Material date pickers work in UTC midnight millis. */
internal fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun Long.pickerMillisToDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * Read-only field that opens a [DatePickerDialog] with quick picks. The relative label ("In 3 days")
 * is shown under the field when [showRelative] is set.
 *
 * @param today injected "today" (never `LocalDate.now()` in callers' logic).
 * @param minDate / [maxDate] inclusive bounds; quick picks outside them are hidden.
 */
@Composable
fun DatePickerField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate) -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier,
    quickPicks: List<DateQuickPick> = DateQuickPick.entries,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    showRelative: Boolean = false,
    enabled: Boolean = true,
    supportingText: String? = null,
    isError: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val locale = currentLocale()
    val text = date?.let { DateLabels.formatDate(it, FormatStyle.MEDIUM, locale) }.orEmpty()
    val support = supportingText ?: if (showRelative && date != null) DateLabels.relative(date, today).asString() else null
    PickerFieldBox(
        label = label,
        text = text,
        icon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
        supportingText = support,
        enabled = enabled,
        isError = isError,
        onClick = { open = true },
        modifier = modifier,
    )
    if (open) {
        SubMarkDatePickerDialog(
            initial = date ?: today,
            today = today,
            onConfirm = { open = false; onDateChange(it) },
            onDismiss = { open = false },
            quickPicks = quickPicks,
            minDate = minDate,
            maxDate = maxDate,
        )
    }
}

/** Standalone date dialog (also used by [DatePickerField]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubMarkDatePickerDialog(
    initial: LocalDate,
    today: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    quickPicks: List<DateQuickPick> = DateQuickPick.entries,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
) {
    fun inRange(d: LocalDate) = (minDate == null || d >= minDate) && (maxDate == null || d <= maxDate)
    val selectable = remember(minDate, maxDate) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = inRange(utcTimeMillis.pickerMillisToDate())
        }
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.toPickerMillis(), selectableDates = selectable)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onConfirm(it.pickerMillisToDate()) } },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.ui_action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_action_cancel)) } },
    ) {
        val picks = quickPicks.filter { inRange(it.apply(today)) }
        if (picks.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                picks.forEach { pick ->
                    AssistChip(
                        onClick = {
                            val millis = pick.apply(today).toPickerMillis()
                            state.selectedDateMillis = millis
                            state.displayedMonthMillis = millis
                        },
                        label = { Text(stringResource(pick.labelRes)) },
                    )
                }
            }
        }
        DatePicker(state = state, showModeToggle = true)
    }
}

/** Read-only field that opens a time picker dialog. 24-hour mode follows the system setting by default. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerField(
    label: String,
    time: LocalTime?,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    is24Hour: Boolean = DateFormat.is24HourFormat(LocalContext.current),
    enabled: Boolean = true,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val locale = currentLocale()
    val text = time?.let { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(it) }.orEmpty()
    PickerFieldBox(
        label = label,
        text = text,
        icon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
        supportingText = null,
        enabled = enabled,
        isError = false,
        onClick = { open = true },
        modifier = modifier,
    )
    if (open) {
        val initial = time ?: LocalTime.of(9, 0)
        val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = { open = false; onTimeChange(LocalTime.of(state.hour, state.minute)) }) {
                    Text(stringResource(R.string.ui_action_ok))
                }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.ui_action_cancel)) } },
        )
    }
}

@Composable
private fun PickerFieldBox(
    label: String,
    text: String,
    icon: @Composable () -> Unit,
    supportingText: String?,
    enabled: Boolean,
    isError: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            enabled = enabled,
            isError = isError,
            label = { Text(label) },
            trailingIcon = icon,
            supportingText = supportingText?.let { { Text(it) } },
            singleLine = true,
        )
        // Overlay catches taps; a read-only text field would otherwise just take focus.
        Box(
            Modifier
                .matchParentSize()
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PickerFieldsPreview() {
    SubMarkTheme {
        Column {
            DatePickerField("Start date", LocalDate.of(2026, 3, 1), {}, today = LocalDate.of(2026, 3, 10), showRelative = true)
            TimePickerField("Reminder time", LocalTime.of(9, 30), {})
        }
    }
}
