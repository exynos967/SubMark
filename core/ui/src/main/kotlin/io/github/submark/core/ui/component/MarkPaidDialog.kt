package io.github.submark.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.submark.core.domain.BillingCalculator
import io.github.submark.core.model.MarkTiming
import io.github.submark.core.ui.R
import io.github.submark.core.ui.format.DateLabels
import io.github.submark.core.ui.theme.SubMarkTheme
import java.time.LocalDate
import java.time.format.FormatStyle

/** Where today falls relative to the due date being marked. */
sealed interface MarkPaidSituation {
    data object OnTime : MarkPaidSituation
    data class Early(val days: Int) : MarkPaidSituation
    data class Overdue(val days: Int) : MarkPaidSituation

    companion object {
        fun of(dueDate: LocalDate, today: LocalDate): MarkPaidSituation {
            val days = DateLabels.daysBetween(today, dueDate).toInt()
            return when {
                days == 0 -> OnTime
                days > 0 -> Early(days)
                else -> Overdue(-days)
            }
        }
    }
}

/**
 * Pure-UI "mark paid" confirmation. On time: plain confirm. Early/overdue: the user picks
 * Original cycle (keep the schedule) or New cycle (restart from today).
 * [onConfirm] receives the resulting [MarkTiming]; the caller performs the actual marking.
 *
 * @param amountText pre-formatted amount, e.g. from [formatMoney].
 */
@Composable
fun MarkPaidDialog(
    name: String,
    amountText: String,
    dueDate: LocalDate,
    today: LocalDate,
    onConfirm: (MarkTiming) -> Unit,
    onDismiss: () -> Unit,
) {
    val situation = MarkPaidSituation.of(dueDate, today)
    var newCycle by rememberSaveable { mutableStateOf(false) }
    val dueText = DateLabels.formatDate(dueDate, FormatStyle.MEDIUM, currentLocale())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (situation) {
                    MarkPaidSituation.OnTime -> stringResource(R.string.ui_mark_paid_title)
                    is MarkPaidSituation.Early -> stringResource(R.string.ui_mark_paid_early_title)
                    is MarkPaidSituation.Overdue -> stringResource(R.string.ui_mark_paid_overdue_title)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    when (situation) {
                        MarkPaidSituation.OnTime -> stringResource(R.string.ui_mark_paid_message, name, amountText)
                        is MarkPaidSituation.Early ->
                            pluralStringResource(R.plurals.ui_mark_paid_early_message, situation.days, name, dueText, situation.days, amountText)
                        is MarkPaidSituation.Overdue ->
                            pluralStringResource(R.plurals.ui_mark_paid_overdue_message, situation.days, name, dueText, situation.days, amountText)
                    },
                )
                if (situation != MarkPaidSituation.OnTime) {
                    Column(Modifier.selectableGroup()) {
                        CycleOption(
                            title = stringResource(R.string.ui_mark_paid_original_cycle),
                            description = stringResource(R.string.ui_mark_paid_original_cycle_desc),
                            selected = !newCycle,
                            onClick = { newCycle = false },
                        )
                        CycleOption(
                            title = stringResource(R.string.ui_mark_paid_new_cycle),
                            description = stringResource(R.string.ui_mark_paid_new_cycle_desc),
                            selected = newCycle,
                            onClick = { newCycle = true },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(BillingCalculator.timingOf(dueDate, today, newCycle)) }) {
                Text(stringResource(R.string.ui_mark_paid_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_action_cancel)) } },
    )
}

@Composable
private fun CycleOption(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview
@Composable
private fun MarkPaidDialogPreview() {
    SubMarkTheme {
        MarkPaidDialog(
            name = "Music Plus",
            amountText = "$9.99",
            dueDate = LocalDate.of(2026, 3, 14),
            today = LocalDate.of(2026, 3, 10),
            onConfirm = {},
            onDismiss = {},
        )
    }
}
