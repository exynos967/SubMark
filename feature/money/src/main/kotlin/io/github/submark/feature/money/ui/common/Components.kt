package io.github.submark.feature.money.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.submark.core.data.result.DataError
import io.github.submark.core.model.DateAdjustmentMode
import io.github.submark.core.model.MarkTiming
import io.github.submark.core.model.PaymentKind
import io.github.submark.core.model.PaymentRecord
import io.github.submark.core.model.PaymentSource
import io.github.submark.core.model.Wallet
import io.github.submark.core.model.WalletKind
import io.github.submark.core.ui.component.SearchField
import io.github.submark.core.ui.component.StatusBadge
import io.github.submark.core.ui.component.LocalMoneyDisplayOptions
import io.github.submark.core.ui.component.currentLocale
import io.github.submark.core.ui.format.MoneyFormatter
import io.github.submark.core.ui.component.formatMoney
import io.github.submark.core.ui.format.BadgeTone
import io.github.submark.core.ui.format.DateLabels
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.format.labelRes
import io.github.submark.core.ui.icon.IconCatalog
import io.github.submark.core.ui.icon.IconGroup
import io.github.submark.core.ui.util.colorFromHex
import io.github.submark.feature.money.R
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.FormatStyle

// ---------------------------------------------------------------- formatting

@Composable
fun formatDate(date: LocalDate, style: FormatStyle = FormatStyle.MEDIUM): String = DateLabels.formatDate(date, style, currentLocale())

/** Non-composable money formatter for lambdas, bound to the current locale and display options. */
@Composable
fun rememberMoneyFormatter(currencyCode: String, symbol: String?): (BigDecimal) -> String {
    val locale = currentLocale()
    val hide = LocalMoneyDisplayOptions.current.hideDecimals
    return remember(currencyCode, symbol, locale, hide) {
        { v: BigDecimal -> MoneyFormatter.format(v, currencyCode, symbol, locale, hideDecimals = hide) }
    }
}

// ---------------------------------------------------------------- payment badges

data class RecordBadge(@StringRes val label: Int, val tone: BadgeTone)

/** Badges derived from kind / source / timing / adjustment (records carry no generated note text). */
fun paymentBadges(record: PaymentRecord): List<RecordBadge> = buildList {
    when (record.kind) {
        PaymentKind.REGULAR -> Unit
        PaymentKind.IN_APP_PURCHASE -> add(RecordBadge(record.kind.labelRes, BadgeTone.TERTIARY))
        PaymentKind.EXTENSION -> add(RecordBadge(record.kind.labelRes, BadgeTone.SECONDARY))
        PaymentKind.LIFETIME_PURCHASE -> add(RecordBadge(record.kind.labelRes, BadgeTone.PRIMARY))
        PaymentKind.STORED_VALUE_DEPOSIT -> add(RecordBadge(record.kind.labelRes, BadgeTone.SECONDARY))
    }
    if (record.source != PaymentSource.USER_MANUAL) add(RecordBadge(record.source.labelRes, BadgeTone.NEUTRAL))
    if (record.markTiming != MarkTiming.ON_TIME) add(RecordBadge(record.markTiming.labelRes, BadgeTone.NEUTRAL))
    when (record.dateAdjustmentMode) {
        DateAdjustmentMode.NONE -> Unit
        DateAdjustmentMode.END_DATE -> add(RecordBadge(R.string.money_badge_end_date_set, BadgeTone.NEUTRAL))
        DateAdjustmentMode.NEXT_BILLING -> add(RecordBadge(R.string.money_badge_next_date_changed, BadgeTone.NEUTRAL))
    }
    if (record.walletTransactionId != null) add(RecordBadge(R.string.money_badge_wallet, BadgeTone.PRIMARY))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BadgeRow(badges: List<RecordBadge>, modifier: Modifier = Modifier) {
    if (badges.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        badges.forEach { StatusBadge(stringResource(it.label), tone = it.tone) }
    }
}

// ---------------------------------------------------------------- rows & cards

/** Label on the left, value on the right. */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            modifier = Modifier.weight(0.55f),
        )
    }
}

/** Big number with a caption, used by summary cards. */
@Composable
fun StatCell(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Column(modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Bulleted hints with a lightbulb header. */
@Composable
fun TipsCard(title: String, lines: List<String>, modifier: Modifier = Modifier) {
    if (lines.isEmpty()) return
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(6.dp))
        lines.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
    }
}

/** Read-only field that opens something (currency picker, kind menu) when tapped. */
@Composable
fun PickerField(
    label: String,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: String? = null,
) {
    Box(modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Rounded.ExpandMore, contentDescription = null) },
            supportingText = supportingText?.let { { Text(it) } },
            singleLine = true,
        )
        Box(Modifier.matchParentSize().clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick))
    }
}

// ---------------------------------------------------------------- wallets

/** A wallet offered by a picker together with the reason it can't pay right now (null = can pay). */
data class WalletChoice(val wallet: Wallet, val problem: DataError?)

@Composable
fun WalletIconBadge(wallet: Wallet, modifier: Modifier = Modifier, size: Int = 36) {
    val color = colorFromHex(wallet.colorHex) ?: MaterialTheme.colorScheme.primary
    Box(
        modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            IconCatalog.vector(wallet.iconValue) ?: Icons.Rounded.AccountBalanceWallet,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size((size * 0.55f).dp),
        )
    }
}

/** Single-choice wallet list. Wallets with a [WalletChoice.problem] are shown but cannot be selected. */
@Composable
fun WalletSelector(
    choices: List<WalletChoice>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    symbols: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        if (choices.isEmpty()) {
            Text(
                stringResource(R.string.money_wallet_selector_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        choices.forEach { choice ->
            val wallet = choice.wallet
            val selectable = choice.problem == null
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = selectable, role = Role.RadioButton) { onSelect(wallet.id) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = wallet.id == selectedId, onClick = null, enabled = selectable)
                Spacer(Modifier.width(8.dp))
                WalletIconBadge(wallet, size = 32)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(wallet.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val balance = formatMoney(wallet.balance, wallet.currencyCode, symbols[wallet.currencyCode])
                    Text(
                        stringResource(R.string.money_wallet_selector_balance, stringResource(wallet.kind.labelRes), balance),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    choice.problem?.let {
                        Text(it.toUiText().asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/** Remaining credit for CREDIT wallets with a limit; null = unlimited / not applicable. */
fun Wallet.availableCredit() = if (kind == WalletKind.CREDIT) creditLimit?.let { balance + it } else null

// ---------------------------------------------------------------- icon picker

/** Grid of catalogue icons with search; finance icons first. */
@Composable
fun IconGridPickerDialog(selected: String?, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val entries = remember(query) {
        if (query.isBlank()) IconCatalog.group(IconGroup.FINANCE) + IconCatalog.all.filter { it.group != IconGroup.FINANCE }
        else IconCatalog.search(query)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.money_icon_picker_title)) },
        text = {
            Column {
                SearchField(query = query, onQueryChange = { query = it })
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(columns = GridCells.Adaptive(48.dp), modifier = Modifier.heightIn(max = 360.dp)) {
                    items(entries, key = { it.name }) { entry ->
                        val isSelected = entry.name == selected
                        Box(
                            Modifier
                                .padding(4.dp)
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable(onClickLabel = entry.name) { onSelect(entry.name) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(entry.vector, contentDescription = entry.name)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(io.github.submark.core.ui.R.string.ui_action_cancel)) } },
    )
}
