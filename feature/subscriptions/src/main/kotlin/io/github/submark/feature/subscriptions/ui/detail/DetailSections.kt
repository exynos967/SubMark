package io.github.submark.feature.subscriptions.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.submark.core.domain.BillingCalculator
import io.github.submark.core.domain.CostCalculator
import io.github.submark.core.domain.StoredValueCalculator
import io.github.submark.core.domain.StoredValueStatus
import io.github.submark.core.model.BundleRole
import io.github.submark.core.model.PaymentRecord
import io.github.submark.core.model.PaymentStatus
import io.github.submark.core.model.RenewalType
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionPhoto
import io.github.submark.core.model.SubscriptionStatus
import io.github.submark.core.ui.component.CategoryChip
import io.github.submark.core.ui.component.CountdownBadge
import io.github.submark.core.ui.component.SectionCard
import io.github.submark.core.ui.component.StatusBadge
import io.github.submark.core.ui.component.SubscriptionBadge
import io.github.submark.core.ui.component.TagChip
import io.github.submark.core.ui.component.formatMoney
import io.github.submark.core.ui.format.BadgeTone
import io.github.submark.core.ui.format.CycleLabels
import io.github.submark.core.ui.format.DateLabels
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.format.displayName
import io.github.submark.core.ui.format.labelRes
import io.github.submark.core.ui.format.tone
import io.github.submark.core.ui.icon.SubscriptionIcon
import io.github.submark.core.ui.navigation.AddPaymentRoute
import io.github.submark.core.ui.navigation.PaymentHistoryRoute
import io.github.submark.core.ui.navigation.PaymentRecordRoute
import io.github.submark.core.ui.navigation.SharedSettingsRoute
import io.github.submark.core.ui.navigation.StoredValueRecordsRoute
import io.github.submark.core.ui.navigation.SubscriptionDetailRoute
import io.github.submark.core.ui.navigation.WalletActivityRoute
import io.github.submark.core.ui.util.colorFromHex
import io.github.submark.feature.subscriptions.R
import io.github.submark.feature.subscriptions.data.PhotoStorage
import java.io.File
import java.math.BigDecimal

@Composable
private fun DetailUiState.money(amount: BigDecimal, code: String): String = formatMoney(amount, code, symbol(code))

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HeaderSection(state: DetailUiState, content: DetailContent) {
    val sub = content.subscription
    val category = content.item.category
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SubscriptionIcon(type = sub.iconType, value = sub.iconValue, fallbackName = sub.name, size = 72.dp)
        Text(sub.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (sub.kind != SubscriptionKind.REGULAR) StatusBadge(stringResource(sub.kind.labelRes), tone = BadgeTone.PRIMARY)
            if (sub.kind != SubscriptionKind.WISHLIST) {
                val active = sub.status == SubscriptionStatus.ACTIVE
                StatusBadge(
                    stringResource(if (active) R.string.subscriptions_detail_status_active else R.string.subscriptions_detail_status_inactive),
                    tone = if (active) BadgeTone.SUCCESS else BadgeTone.NEUTRAL,
                )
            }
            if (BillingCalculator.isExpired(sub, state.today)) StatusBadge(stringResource(R.string.subscriptions_detail_status_expired), tone = BadgeTone.ERROR)
            SubscriptionBadge.of(sub, state.today, includeAuto = true)
                .filterNot { it in REDUNDANT_BADGES }
                .forEach { StatusBadge(stringResource(it.labelRes), tone = it.tone) }
        }
        if (category != null) {
            CategoryChip(
                name = category.displayName().asString(),
                color = colorFromHex(category.colorHex),
                iconType = category.iconType,
                iconValue = category.iconValue,
            )
        }
        DetailLogic.trialDaysLeft(sub, state.today)?.let { days ->
            Text(
                pluralStringResource(R.plurals.subscriptions_detail_trial_days_left, days.toInt(), days.toInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

private val REDUNDANT_BADGES = setOf(SubscriptionBadge.PAUSED, SubscriptionBadge.LIFETIME, SubscriptionBadge.WISHLIST, SubscriptionBadge.STORED_VALUE)

@Composable
internal fun MetricsSection(state: DetailUiState, content: DetailContent) {
    val sub = content.subscription
    val showYmd = state.settings.list.showCustomCycleAsYmd
    val cost = if (sub.price.signum() == 0) stringResource(R.string.subscriptions_detail_free) else state.money(sub.price, sub.currencyCode)
    val tiles = mutableListOf<@Composable (Modifier) -> Unit>()
    when (sub.kind) {
        SubscriptionKind.LIFETIME -> {
            val owned = DateLabels.daysBetween(sub.startDate, state.today).coerceAtLeast(0)
            tiles += { m -> MetricTile(stringResource(R.string.subscriptions_detail_metric_price), cost, m) }
            tiles += { m -> MetricTile(stringResource(R.string.subscriptions_detail_metric_purchased), formatDay(sub.startDate), m) }
            tiles += { m -> MetricTile(stringResource(R.string.subscriptions_detail_metric_owned_for), DateLabels.duration(sub.startDate, maxOf(sub.startDate, state.today)).asString(), m) }
            tiles += { m ->
                MetricTile(
                    stringResource(R.string.subscriptions_detail_metric_daily_cost),
                    state.money(CostCalculator.lifetimeDailyCost(sub.price, owned), sub.currencyCode),
                    m,
                )
            }
        }
        SubscriptionKind.WISHLIST -> {
            tiles += { m -> MetricTile(stringResource(R.string.subscriptions_detail_metric_planned_price), cost, m) }
            tiles += { m -> MetricTile(stringResource(R.string.subscriptions_detail_metric_cycle), CycleLabels.label(sub, showYmd).asString(), m) }
        }
        else -> {
            val userShare = content.shared?.userShare
            tiles += { m ->
                MetricTile(stringResource(R.string.subscriptions_detail_metric_cost), cost, m) {
                    Text(CycleLabels.label(sub, showYmd).asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (userShare != null) {
                tiles += { m -> MetricTile(stringResource(R.string.subscriptions_detail_metric_your_share), state.money(userShare, sub.currencyCode), m) }
            }
            tiles += { m -> NextPaymentTile(sub, state, m) }
            DetailLogic.currentPeriod(sub)?.let { period ->
                tiles += { m ->
                    MetricTile(
                        stringResource(R.string.subscriptions_detail_metric_current_period),
                        stringResource(R.string.subscriptions_detail_range, formatDay(period.start), formatDay(period.end)),
                        m,
                    )
                }
            }
            CostCalculator.annualOf(sub.copy(status = SubscriptionStatus.ACTIVE))?.let { annual ->
                tiles += { m ->
                    MetricTile(stringResource(R.string.subscriptions_detail_metric_annual), state.money(annual, sub.currencyCode), m) {
                        val def = state.defaultCurrency
                        if (def != sub.currencyCode) {
                            state.converter?.convert(annual, sub.currencyCode, def)?.let { converted ->
                                Text(
                                    stringResource(R.string.subscriptions_detail_approx, state.money(converted, def)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (sub.kind != SubscriptionKind.WISHLIST) {
        val total = DetailLogic.historicalTotal(content.payments, sub.currencyCode, state.converter)
        tiles += { m ->
            MetricTile(stringResource(R.string.subscriptions_detail_metric_total_spent), state.money(total.amount, total.currencyCode), m) {
                if (total.missingRates) {
                    Text(
                        stringResource(R.string.subscriptions_detail_missing_rates),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
    MetricGrid(tiles)
}

@Composable
private fun NextPaymentTile(sub: Subscription, state: DetailUiState, modifier: Modifier) {
    val next = sub.nextPaymentDate
    when {
        sub.status == SubscriptionStatus.PAUSED ->
            MetricTile(stringResource(R.string.subscriptions_detail_metric_next_payment), stringResource(R.string.subscriptions_detail_paused_no_payment), modifier)
        next != null -> MetricTile(stringResource(R.string.subscriptions_detail_metric_next_payment), formatDay(next), modifier) {
            CountdownBadge(next, state.today)
        }
        sub.endDate != null -> MetricTile(stringResource(R.string.subscriptions_detail_metric_valid_until), formatDay(sub.endDate!!), modifier)
        else -> MetricTile(stringResource(R.string.subscriptions_detail_metric_next_payment), stringResource(R.string.subscriptions_detail_unknown), modifier)
    }
}

@Composable
internal fun StoredValueSection(state: DetailUiState, sub: Subscription, onNavigate: (Any) -> Unit) {
    val balance = sub.storedValueBalance
    val status = StoredValueCalculator.status(balance, sub.price)
    SectionCard(
        title = stringResource(R.string.subscriptions_detail_stored_value_title),
        actionLabel = stringResource(R.string.subscriptions_detail_records),
        onAction = { onNavigate(StoredValueRecordsRoute(sub.id)) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.money(balance, sub.currencyCode), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            StatusBadge(
                stringResource(status.labelRes()),
                tone = when (status) {
                    StoredValueStatus.SUFFICIENT -> BadgeTone.SUCCESS
                    StoredValueStatus.LOW -> BadgeTone.WARNING
                    else -> BadgeTone.ERROR
                },
            )
        }
        val text = if (balance.signum() < 0) {
            val cycles = StoredValueCalculator.debtCycles(balance, sub.price).toInt()
            pluralStringResource(R.plurals.subscriptions_detail_stored_value_debt_cycles, cycles, cycles)
        } else {
            StoredValueCalculator.payableCycles(balance, sub.price)?.toInt()?.let {
                pluralStringResource(R.plurals.subscriptions_detail_stored_value_cycles, it, it)
            } ?: stringResource(R.string.subscriptions_detail_stored_value_unlimited)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.subscriptions_detail_stored_value_fee, state.money(sub.price, sub.currencyCode)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun StoredValueStatus.labelRes(): Int = when (this) {
    StoredValueStatus.SUFFICIENT -> R.string.subscriptions_detail_sv_sufficient
    StoredValueStatus.LOW -> R.string.subscriptions_detail_sv_low
    StoredValueStatus.EMPTY -> R.string.subscriptions_detail_sv_empty
    StoredValueStatus.ZERO -> R.string.subscriptions_detail_sv_zero
    StoredValueStatus.DEBT -> R.string.subscriptions_detail_sv_debt
}

@Composable
internal fun SharedSection(state: DetailUiState, content: DetailContent, onNavigate: (Any) -> Unit) {
    val sub = content.subscription
    val shared = content.shared
    if (shared == null) {
        if (sub.kind == SubscriptionKind.REGULAR && sub.bundleRole != BundleRole.CHILD) {
            SectionCard(onClick = { onNavigate(SharedSettingsRoute(sub.id)) }) {
                NavLine(stringResource(R.string.subscriptions_detail_shared_setup), stringResource(R.string.subscriptions_detail_shared_setup_hint))
            }
        }
        return
    }
    val active = shared.members.count { it.status == io.github.submark.core.model.MemberStatus.ACTIVE }
    SectionCard(
        title = stringResource(R.string.subscriptions_detail_shared_title),
        actionLabel = stringResource(R.string.subscriptions_detail_manage),
        onAction = { onNavigate(SharedSettingsRoute(sub.id)) },
    ) {
        DetailRow(stringResource(R.string.subscriptions_detail_shared_members), pluralStringResource(R.plurals.subscriptions_detail_members, active, active))
        shared.config?.let { DetailRow(stringResource(R.string.subscriptions_detail_shared_mode), stringResource(it.splitMode.labelRes)) }
        shared.userShare?.let { DetailRow(stringResource(R.string.subscriptions_detail_metric_your_share), state.money(it, sub.currencyCode)) }
        val names = shared.members.take(4).joinToString(", ") { it.name }
        if (names.isNotEmpty()) {
            val more = shared.members.size - 4
            Text(
                if (more > 0) stringResource(R.string.subscriptions_detail_names_more, names, more) else names,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun WalletSection(state: DetailUiState, summary: WalletSummary, onNavigate: (Any) -> Unit) {
    val wallet = summary.wallet
    SectionCard(
        title = stringResource(R.string.subscriptions_detail_wallet_title),
        onClick = { onNavigate(WalletActivityRoute(wallet.id)) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(wallet.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(wallet.kind.labelRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(state.money(wallet.balance, wallet.currencyCode), style = MaterialTheme.typography.titleMedium)
        }
        when {
            wallet.deletedAt != null -> WalletNote(stringResource(R.string.subscriptions_detail_wallet_deleted), error = true)
            !wallet.isActive -> WalletNote(stringResource(R.string.subscriptions_detail_wallet_inactive), error = true)
            else -> summary.coverage?.let { coverage ->
                val cycles = coverage.payableCycles
                when {
                    cycles == null -> WalletNote(stringResource(R.string.subscriptions_detail_wallet_unlimited))
                    cycles == 0L -> WalletNote(stringResource(R.string.subscriptions_detail_wallet_not_enough), error = true)
                    else -> {
                        val n = cycles.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        WalletNote(pluralStringResource(R.plurals.subscriptions_detail_wallet_cycles, n, n))
                        coverage.coversUntil?.let { WalletNote(stringResource(R.string.subscriptions_detail_wallet_until, formatDay(it))) }
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletNote(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
internal fun BundleSection(state: DetailUiState, content: DetailContent, onNavigate: (Any) -> Unit) {
    val sub = content.subscription
    val parent = content.parent
    if (sub.bundleRole == BundleRole.CHILD && parent != null) {
        SectionCard(onClick = { onNavigate(SubscriptionDetailRoute(parent.id)) }) {
            NavLine(stringResource(R.string.subscriptions_detail_bundle_part_of, parent.name), stringResource(R.string.subscriptions_detail_bundle_open_main))
        }
    }
    if (sub.bundleRole != BundleRole.MAIN) return
    val children = content.item.children.sortedBy { it.name.lowercase() }
    SectionCard(title = pluralStringResource(R.plurals.subscriptions_detail_bundle_children, children.size, children.size)) {
        if (children.isEmpty()) {
            Text(stringResource(R.string.subscriptions_detail_bundle_empty), style = MaterialTheme.typography.bodyMedium)
        }
        children.forEachIndexed { index, child ->
            if (index > 0) HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().clickable { onNavigate(SubscriptionDetailRoute(child.id)) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SubscriptionIcon(child.iconType, child.iconValue, child.name, size = 32.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(child.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        CycleLabels.label(child, state.settings.list.showCustomCycleAsYmd).asString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(state.money(child.price, child.currencyCode), style = MaterialTheme.typography.bodyMedium)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
            }
        }
        val total = DetailLogic.bundleTotal(sub, children, state.converter)
        HorizontalDivider()
        DetailRow(stringResource(R.string.subscriptions_detail_bundle_total), state.money(total.amount, total.currencyCode))
        if (total.missingRates) WalletNote(stringResource(R.string.subscriptions_detail_missing_rates), error = true)
    }
}

@Composable
private fun NavLine(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
internal fun PaymentHistorySection(state: DetailUiState, content: DetailContent, onNavigate: (Any) -> Unit) {
    val sub = content.subscription
    val payments = content.payments
    val lifetime = sub.kind == SubscriptionKind.LIFETIME
    SectionCard(
        title = stringResource(if (lifetime) R.string.subscriptions_detail_purchase_history else R.string.subscriptions_detail_payment_history),
        actionLabel = if (payments.isNotEmpty()) stringResource(R.string.subscriptions_detail_view_all, payments.size) else null,
        onAction = { onNavigate(PaymentHistoryRoute(sub.id)) },
    ) {
        if (payments.isEmpty()) {
            Text(
                stringResource(R.string.subscriptions_detail_no_payments),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        payments.take(DetailLogic.PREVIEW_PAYMENTS).forEachIndexed { index, record ->
            if (index > 0) HorizontalDivider()
            PaymentRow(state, record) { onNavigate(PaymentRecordRoute(record.id)) }
        }
        TextButton(onClick = { onNavigate(AddPaymentRoute(sub.id)) }, modifier = Modifier.padding(top = 4.dp)) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text(stringResource(R.string.subscriptions_detail_add_payment), modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun PaymentRow(state: DetailUiState, record: PaymentRecord, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(DetailLogic.paymentLabel(record).text(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val note = localizedNote(record.note)
            Text(
                listOfNotNull(formatDay(record.paymentDate), note).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(state.money(record.amount, record.currencyCode), style = MaterialTheme.typography.bodyMedium)
            if (record.status != PaymentStatus.SUCCESS) StatusBadge(stringResource(record.status.labelRes), tone = record.status.tone)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailsSection(
    state: DetailUiState,
    content: DetailContent,
    onOpenUrl: (String) -> Unit,
) {
    val sub = content.subscription
    val currency = state.currencies[sub.currencyCode]
    SectionCard(title = stringResource(R.string.subscriptions_detail_details_title)) {
        if (sub.kind != SubscriptionKind.WISHLIST) {
            DetailRow(
                stringResource(R.string.subscriptions_detail_row_status),
                stringResource(if (sub.status == SubscriptionStatus.ACTIVE) R.string.subscriptions_detail_status_active else R.string.subscriptions_detail_status_inactive),
            )
        }
        DetailRow(
            stringResource(if (sub.kind == SubscriptionKind.LIFETIME) R.string.subscriptions_detail_row_purchased else R.string.subscriptions_detail_row_start),
            formatDay(sub.startDate),
        )
        sub.endDate?.let { DetailRow(stringResource(R.string.subscriptions_detail_row_end), formatDay(it)) }
        if (sub.renewalType == RenewalType.TRIAL) {
            BillingCalculator.trialEndDate(sub)?.let { DetailRow(stringResource(R.string.subscriptions_detail_row_trial_ends), formatDay(it)) }
        }
        if (DetailLogic.isRecurring(sub)) {
            DetailRow(stringResource(R.string.subscriptions_detail_row_renewal), stringResource(sub.renewalType.labelRes))
            sub.fixedPaymentDay?.let { DetailRow(stringResource(R.string.subscriptions_detail_row_fixed_day), stringResource(R.string.subscriptions_detail_fixed_day_value, it)) }
        }
        DetailRow(
            stringResource(R.string.subscriptions_detail_row_currency),
            currency?.let { "${it.code} · ${it.name}" } ?: sub.currencyCode,
        )
        content.paymentMethod?.let { DetailRow(stringResource(R.string.subscriptions_detail_row_payment_method), it.localizedName()) }
        content.item.category?.let { DetailRow(stringResource(R.string.subscriptions_detail_row_category), it.displayName().asString()) }
        if (content.item.tags.isNotEmpty()) {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.subscriptions_detail_row_tags), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    content.item.tags.forEach { TagChip(it.name, it.color) }
                }
            }
        }
        sub.website?.takeIf { it.isNotBlank() }?.let { site ->
            DetailRow(
                stringResource(R.string.subscriptions_detail_row_website),
                site,
                onClick = { onOpenUrl(DetailLogic.websiteUrl(site)) },
                trailing = { OpenIcon(stringResource(R.string.subscriptions_detail_open_website)) },
            )
        }
        sub.appStoreId?.takeIf { it.isNotBlank() }?.let { appId ->
            DetailRow(
                stringResource(R.string.subscriptions_detail_row_app_store),
                appId,
                onClick = { onOpenUrl(DetailLogic.appStoreUrl(appId)) },
                trailing = { OpenIcon(stringResource(R.string.subscriptions_detail_open_app_store)) },
            )
        }
        DetailRow(stringResource(R.string.subscriptions_detail_row_created), formatInstant(sub.createdAt))
    }
}

@Composable
private fun OpenIcon(description: String) {
    Icon(
        Icons.AutoMirrored.Rounded.OpenInNew,
        contentDescription = description,
        modifier = Modifier.padding(start = 6.dp).size(18.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
}

@Composable
internal fun CustomFieldsSection(fields: List<FieldDisplay>) {
    if (fields.isEmpty()) return
    SectionCard(title = stringResource(R.string.subscriptions_detail_custom_fields)) {
        fields.forEach { field -> DetailRow(field.definition.name, field.displayText()) }
    }
}

@Composable
internal fun NoteSection(note: String?, onCopy: (String) -> Unit) {
    val text = note?.takeIf { it.isNotBlank() } ?: return
    SectionCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.subscriptions_detail_note), style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = { onCopy(text) }) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.subscriptions_detail_copy_note))
            }
        }
    }
}

@Composable
internal fun PhotosSection(photos: List<SubscriptionPhoto>, onOpen: (SubscriptionPhoto) -> Unit) {
    if (photos.isEmpty()) return
    val context = LocalContext.current
    SectionCard(title = pluralStringResource(R.plurals.subscriptions_detail_photos, photos.size, photos.size)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(photos, key = { it.id }) { photo ->
                AsyncImage(
                    model = photoFile(context, photo.fileName),
                    contentDescription = stringResource(R.string.subscriptions_detail_photo_open),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpen(photo) },
                )
            }
        }
    }
}

internal fun photoFile(context: android.content.Context, fileName: String): File =
    File(File(context.filesDir, PhotoStorage.PHOTO_DIR), fileName)
