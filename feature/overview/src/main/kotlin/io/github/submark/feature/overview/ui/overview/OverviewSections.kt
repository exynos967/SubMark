package io.github.submark.feature.overview.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.submark.core.data.settings.SummaryPeriod
import io.github.submark.core.model.WalletKind
import io.github.submark.core.ui.component.formatMoney
import io.github.submark.core.ui.format.DateLabels
import io.github.submark.core.ui.icon.SubscriptionIcon
import io.github.submark.core.ui.theme.SubMarkTheme
import io.github.submark.feature.overview.R
import io.github.submark.feature.overview.data.PaymentOccurrence
import java.time.LocalDate
import java.time.format.FormatStyle

@Composable
internal fun EmptyHero(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.ShoppingBag,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.overview_welcome_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.overview_welcome_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.overview_welcome_add))
        }
    }
}

// ── Hero (actual | scheduled | projected) ─────────────────────────────────────

@Composable
internal fun HeroSection(
    uiState: OverviewUiState,
    onPeriodSelect: (SummaryPeriod) -> Unit,
    onFinancialDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mode = uiState.spendingMode
    val code = uiState.defaultCurrencyCode
    val symbols = uiState.currencySymbols
    val paid = uiState.paidThisPeriod
    val scheduled = uiState.scheduledThisPeriod
    val projected = uiState.periodProjectedTotal

    SectionCard(title = periodLabel(uiState.period), modifier = modifier) {
        if (mode == io.github.submark.core.data.settings.SpendingMode.SUBSCRIPTIONS) {
            PeriodSelector(selected = uiState.period, onSelect = onPeriodSelect)
            Spacer(Modifier.height(8.dp))
            AmountRow(stringResource(R.string.overview_hero_paid), paid, code, symbols)
            if (scheduled.signum() > 0) {
                AmountRow(stringResource(R.string.overview_hero_scheduled), scheduled, code, symbols)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.overview_hero_projected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatMoney(projected, code, symbols[code]),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // Split note
            Text(
                stringResource(
                    R.string.overview_hero_split,
                    formatMoney(paid, code, symbols[code]),
                    formatMoney(scheduled, code, symbols[code]),
                    formatMoney(projected, code, symbols[code]),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                stringResource(R.string.overview_hero_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            // Lifetime mode
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.overview_my_lifetime_mode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatMoney(uiState.lifetimeTotal, code, symbols[code]),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Annual budget gauge
        uiState.annualBudget?.let { budget ->
            if (budget.signum() > 0) {
                Spacer(Modifier.height(8.dp))
                val spentYtd = uiState.annualSpentYtd
                val percent = uiState.budgetUsagePercent ?: 0
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (percent / 100f).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (percent > 90) MaterialTheme.colorScheme.error
                                else if (percent > 70) MaterialTheme.colorScheme.tertiary
                                else SubMarkTheme.extendedColors.success,
                            ),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(
                        R.string.overview_budget_of,
                        formatMoney(spentYtd, code, symbols[code]),
                        formatMoney(budget, code, symbols[code]),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Financial detail link
        TextButton(
            onClick = onFinancialDetail,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.overview_financial_detail_tip), style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
internal fun periodLabel(period: SummaryPeriod): String = when (period) {
    SummaryPeriod.MONTH -> stringResource(R.string.overview_period_this_month)
    SummaryPeriod.QUARTER -> stringResource(R.string.overview_period_this_quarter)
    SummaryPeriod.YEAR -> stringResource(R.string.overview_period_this_year)
}

// ── Coming up ─────────────────────────────────────────────────────────────────

@Composable
internal fun ComingUpSection(
    uiState: OverviewUiState,
    onMarkPaid: (PaymentOccurrence) -> Unit,
    onPause: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val comingUp = uiState.comingUp
    val today = java.time.LocalDate.now()
    SectionCard(title = stringResource(R.string.overview_coming_up), modifier = modifier) {
        if (comingUp.isEmpty()) {
            Text(
                stringResource(R.string.overview_coming_up_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            comingUp.take(7).forEach { occ ->
                OccurrenceRow(
                    occ = occ,
                    today = today,
                    defaultCurrencyCode = uiState.defaultCurrencyCode,
                    symbols = uiState.currencySymbols,
                    onMarkPaid = { onMarkPaid(occ) },
                    modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider()
            }
        }
    }
}

// ── Payment schedule strip ───────────────────────────────────────────────────

@Composable
internal fun PaymentScheduleSection(
    uiState: OverviewUiState,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strip = uiState.scheduleStrip
    val selected = uiState.stripSelectedDate
    SectionCard(title = stringResource(R.string.overview_component_paymentSchedule), modifier = modifier) {
        // Week strip for next 7 days
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            val today = LocalDate.now()
            (0..6).forEach { offset ->
                val date = today.plusDays(offset.toLong())
                val dayOccurrences = strip[date].orEmpty()
                val isSelected = date == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent,
                        )
                        .clickable { onSelectDate(date) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            date == today -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Spacer(Modifier.height(2.dp))
                    // Same markers as the calendar: up to three dots, green = paid, primary = due.
                    Row(
                        modifier = Modifier.height(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dayOccurrences.take(3).forEach { occ ->
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (occ.paid) SubMarkTheme.extendedColors.success else MaterialTheme.colorScheme.primary,
                                    ),
                            )
                        }
                        if (dayOccurrences.size > 3) {
                            Text(
                                text = "+${dayOccurrences.size - 3}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Agenda of selected day
        if (selected != null) {
            val agenda = uiState.stripAgenda
            if (agenda.isEmpty()) {
                Text(
                    stringResource(R.string.overview_schedule_strip_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else {
                Text(
                    stringResource(R.string.overview_schedule_strip_label, agenda.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                agenda.forEach { occ ->
                    OccurrenceRow(
                        occ = occ,
                        today = LocalDate.now(),
                        defaultCurrencyCode = uiState.defaultCurrencyCode,
                        symbols = uiState.currencySymbols,
                        onMarkPaid = null, // strip rows are display-only here; tap the coming-up card
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

// ── Recent payments ───────────────────────────────────────────────────────────

@Composable
internal fun RecentPaymentsSection(
    uiState: OverviewUiState,
    modifier: Modifier = Modifier,
) {
    val recent = uiState.recentPayments
    SectionCard(title = stringResource(R.string.overview_recent_payments), modifier = modifier) {
        if (recent.isEmpty()) {
            Text(
                stringResource(R.string.overview_recent_payments_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            recent.take(5).forEach { occ ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SubscriptionIcon(
                        type = occ.subscription.iconType,
                        value = occ.subscription.iconValue,
                        fallbackName = occ.subscription.name,
                        size = 36.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            occ.subscription.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            DateLabels.formatDate(occ.date, FormatStyle.MEDIUM),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    occ.amount?.let {
                        Text(
                            formatMoney(it, uiState.defaultCurrencyCode, uiState.currencySymbols[uiState.defaultCurrencyCode]),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

// ── Wallet section ────────────────────────────────────────────────────────────

@Composable
internal fun WalletSection(
    uiState: OverviewUiState,
    onWalletManagement: () -> Unit,
    onWalletTopUp: (String) -> Unit,
    onWalletExpense: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val wallets = uiState.wallets
    SectionCard(
        title = stringResource(R.string.overview_wallet_balances),
        modifier = modifier,
        action = {
            TextButton(onClick = onWalletManagement) {
                Text(stringResource(R.string.overview_wallet_manage), style = MaterialTheme.typography.labelLarge)
            }
        },
    ) {
        if (wallets.isEmpty()) {
            Text(
                stringResource(R.string.overview_wallet_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            wallets.take(3).forEach { wallet ->
                val symbol = uiState.currencySymbols[wallet.currencyCode] ?: ""
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Wallet, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            wallet.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            formatMoney(wallet.balance, wallet.currencyCode, symbol),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        if (!wallet.isActive) {
                            Text(
                                stringResource(R.string.overview_wallet_inactive),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (wallet.isActive && wallet.kind == WalletKind.BALANCE_TRACKED) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            TextButton(onClick = { onWalletTopUp(wallet.id) }, modifier = Modifier.height(28.dp)) {
                                Text(stringResource(R.string.overview_wallet_top_up), style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = { onWalletExpense(wallet.id) }, modifier = Modifier.height(28.dp)) {
                                Text(stringResource(R.string.overview_wallet_expense), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                HorizontalDivider()
            }
            if (wallets.size > 3) {
                TextButton(onClick = onWalletManagement) {
                    Text(stringResource(R.string.overview_view_all), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// ── Wishlist prices ───────────────────────────────────────────────────────────

@Composable
internal fun WishlistPricesSection(
    uiState: OverviewUiState,
    onOpenPriceMonitor: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = stringResource(R.string.overview_price_monitor),
        modifier = modifier,
        action = {
            TextButton(onClick = onOpenPriceMonitor) {
                Text(stringResource(R.string.overview_price_monitor_action), style = MaterialTheme.typography.labelLarge)
            }
        },
    ) {
        when (val state = uiState.wishlistPriceStatus) {
            WishlistPriceState.EMPTY -> Text(
                stringResource(R.string.overview_price_monitor_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            is WishlistPriceState.Count -> Text(
                stringResource(R.string.overview_price_monitor_count, state.count),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

// ── My subscriptions & purchases ─────────────────────────────────────────────

@Composable
internal fun MySubscriptionsSection(
    uiState: OverviewUiState,
    mode: io.github.submark.core.data.settings.SpendingMode,
    onSubscriptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = stringResource(R.string.overview_my_subscriptions), modifier = modifier) {
        val subCount = uiState.subscriptionCount
        val purchaseCount = uiState.purchaseCount
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (mode == io.github.submark.core.data.settings.SpendingMode.SUBSCRIPTIONS) {
                    Text(
                        stringResource(R.string.overview_my_subscriptions_count, subCount, purchaseCount),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        stringResource(R.string.overview_my_lifetime_mode),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        formatMoney(uiState.lifetimeTotal, uiState.defaultCurrencyCode, uiState.currencySymbols[uiState.defaultCurrencyCode]),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.overview_lifetime_count, purchaseCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onSubscriptions) {
                Icon(Icons.Rounded.ArrowForward, contentDescription = null)
            }
        }
    }
}

// ── Spending insights ─────────────────────────────────────────────────────────

@Composable
internal fun SpendingInsightsSection(onAnalytics: () -> Unit, modifier: Modifier = Modifier) {
    SectionCard(
        title = stringResource(R.string.overview_spending_insights),
        modifier = modifier,
        action = {
            IconButton(onClick = onAnalytics) {
                Icon(Icons.Rounded.TrendingUp, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        },
    ) {
        Text(
            stringResource(R.string.overview_insights_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
