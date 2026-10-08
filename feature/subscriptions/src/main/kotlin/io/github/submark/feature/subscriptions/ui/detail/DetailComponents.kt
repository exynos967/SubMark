package io.github.submark.feature.subscriptions.ui.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.submark.core.data.seed.SystemPaymentMethods
import io.github.submark.core.data.service.SystemNotes
import io.github.submark.core.model.CustomFieldType
import io.github.submark.core.model.PaymentMethod
import io.github.submark.core.ui.component.currentLocale
import io.github.submark.core.ui.format.DateLabels
import io.github.submark.core.ui.format.labelRes
import io.github.submark.feature.subscriptions.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/* Small building blocks and formatting helpers shared by the detail sections. */

@Composable
@ReadOnlyComposable
internal fun formatDay(date: LocalDate): String = DateLabels.formatDate(date, FormatStyle.MEDIUM, currentLocale())

@Composable
@ReadOnlyComposable
internal fun formatInstant(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(currentLocale())
        .format(instant.atZone(ZoneId.systemDefault()))

/** Label/value row inside a detail card. */
@Composable
internal fun DetailRow(
    label: String,
    value: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Spacer(Modifier.width(12.dp))
        Row(Modifier.weight(0.6f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            trailing?.invoke()
        }
    }
}

/** One key figure tile. */
@Composable
internal fun MetricTile(label: String, value: String, modifier: Modifier = Modifier, supporting: (@Composable () -> Unit)? = null) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(12.dp).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            supporting?.invoke()
        }
    }
}

/** Lays tiles out two per row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MetricGrid(tiles: List<@Composable (Modifier) -> Unit>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2,
    ) {
        tiles.forEach { tile -> tile(Modifier.weight(1f)) }
        if (tiles.size % 2 == 1) Spacer(Modifier.weight(1f))
    }
}

/** Human text for a stored custom-field value (see `CustomFieldValue` encoding). */
@Composable
internal fun FieldDisplay.displayText(): String = when (definition.type) {
    CustomFieldType.BOOLEAN -> stringResource(if (value == "true") R.string.subscriptions_detail_yes else R.string.subscriptions_detail_no)
    CustomFieldType.DROPDOWN -> options.firstOrNull { it.id == value }?.label ?: value
    CustomFieldType.RATING -> value.toIntOrNull()?.coerceIn(0, 5)?.let { "★".repeat(it) + "☆".repeat(5 - it) } ?: value
    CustomFieldType.DATE -> runCatching { LocalDate.parse(value) }.getOrNull()?.let { formatDay(it) } ?: value
    CustomFieldType.DATETIME -> runCatching { Instant.parse(value) }.getOrNull()?.let { formatInstant(it) } ?: value
    else -> value
}

/** Localized note text: data-layer notes start with '@'. */
@Composable
internal fun localizedNote(note: String?): String? = when {
    note.isNullOrBlank() -> null
    !SystemNotes.isSystem(note) -> note
    else -> stringResource(
        when (note) {
            SystemNotes.PAYMENT_DELETED -> R.string.subscriptions_detail_note_payment_deleted
            SystemNotes.PAYMENT_EDITED -> R.string.subscriptions_detail_note_payment_edited
            SystemNotes.SUBSCRIPTION_DELETED -> R.string.subscriptions_detail_note_subscription_deleted
            SystemNotes.STORED_VALUE_RECORD_DELETED -> R.string.subscriptions_detail_note_record_deleted
            SystemNotes.INITIAL_BALANCE -> R.string.subscriptions_detail_note_initial_balance
            else -> R.string.subscriptions_detail_note_system
        },
    )
}

@Composable
internal fun PaymentRowLabel.text(): String = when (this) {
    is PaymentRowLabel.Kind -> stringResource(kind.labelRes)
    is PaymentRowLabel.Source -> stringResource(source.labelRes)
    is PaymentRowLabel.Timing -> stringResource(timing.labelRes)
    is PaymentRowLabel.Extension ->
        if (from != null && to != null) {
            stringResource(R.string.subscriptions_detail_payment_extension_range, formatDay(from), formatDay(to))
        } else {
            stringResource(R.string.subscriptions_detail_payment_extension)
        }
}

/** Preset payment methods are stored with English names; show them localized. */
@Composable
internal fun PaymentMethod.localizedName(): String = when (id) {
    SystemPaymentMethods.NONE -> stringResource(R.string.subscriptions_detail_pm_none)
    SystemPaymentMethods.ALIPAY -> stringResource(R.string.subscriptions_detail_pm_alipay)
    SystemPaymentMethods.APPLE_PAY -> stringResource(R.string.subscriptions_detail_pm_apple_pay)
    SystemPaymentMethods.BANK_CARD -> stringResource(R.string.subscriptions_detail_pm_bank_card)
    SystemPaymentMethods.CASH -> stringResource(R.string.subscriptions_detail_pm_cash)
    SystemPaymentMethods.CREDIT_CARD -> stringResource(R.string.subscriptions_detail_pm_credit_card)
    SystemPaymentMethods.GOOGLE_PAY -> stringResource(R.string.subscriptions_detail_pm_google_pay)
    SystemPaymentMethods.PAYPAL -> stringResource(R.string.subscriptions_detail_pm_paypal)
    SystemPaymentMethods.WECHAT_PAY -> stringResource(R.string.subscriptions_detail_pm_wechat_pay)
    else -> name
}

/** Opens [url] in another app; false when nothing can handle it. */
internal fun openUrl(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    false
}
