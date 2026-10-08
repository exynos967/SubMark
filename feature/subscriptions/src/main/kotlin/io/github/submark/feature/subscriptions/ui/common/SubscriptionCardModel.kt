package io.github.submark.feature.subscriptions.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.submark.core.data.repository.SubscriptionItem
import io.github.submark.core.data.settings.ListSettings
import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.RenewalType
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import io.github.submark.core.ui.component.SubscriptionBadge
import io.github.submark.core.ui.component.SubscriptionCard
import io.github.submark.core.ui.component.SubscriptionCardVariant
import io.github.submark.core.ui.component.currentLocale
import io.github.submark.core.ui.component.formatMoney
import io.github.submark.core.ui.format.CycleLabels
import io.github.submark.core.ui.format.DateLabels
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.format.displayName
import io.github.submark.core.ui.util.colorFromHex
import io.github.submark.feature.subscriptions.R
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.FormatStyle

enum class CardDateKind { NEXT, END, BOUGHT }

data class CardDate(val kind: CardDateKind, val date: LocalDate)

/** Pure display decisions for a subscription card. */
object SubscriptionCardModel {

    /** One date line: purchase date for lifetime, end date when preferred, else the next payment, else the end date. */
    fun date(sub: Subscription, list: ListSettings): CardDate? {
        if (sub.kind == SubscriptionKind.LIFETIME) return CardDate(CardDateKind.BOUGHT, sub.startDate)
        if (sub.kind == SubscriptionKind.WISHLIST) return null
        val end = sub.endDate
        if (list.showEndDateForFixedCycle && end != null) return CardDate(CardDateKind.END, end)
        sub.nextPaymentDate?.let { return CardDate(CardDateKind.NEXT, it) }
        return end?.let { CardDate(CardDateKind.END, it) }
    }

    /** Stored-value balance instead of the price when balance mode is on. */
    fun amount(sub: Subscription, list: ListSettings): BigDecimal =
        if (list.storedValueBalanceMode && sub.kind == SubscriptionKind.STORED_VALUE) sub.storedValueBalance else sub.price

    fun badges(sub: Subscription, today: LocalDate, list: ListSettings): List<SubscriptionBadge> =
        SubscriptionBadge.of(sub, today).filter { it != SubscriptionBadge.LIFETIME || list.showLifetimeLabel }

    /** Countdown only for active recurring items with a next date. */
    fun dueDate(sub: Subscription): LocalDate? =
        sub.nextPaymentDate?.takeIf {
            sub.status == SubscriptionStatus.ACTIVE && (sub.kind == SubscriptionKind.REGULAR || sub.kind == SubscriptionKind.STORED_VALUE)
        }

    /** Whether "Mark paid" is offered: active recurring, has a due date and is not in a trial. */
    fun isMarkable(sub: Subscription): Boolean =
        sub.status == SubscriptionStatus.ACTIVE &&
            (sub.kind == SubscriptionKind.REGULAR || sub.kind == SubscriptionKind.STORED_VALUE) &&
            sub.nextPaymentDate != null && sub.renewalType != RenewalType.TRIAL &&
            (sub.billingCycle != null || sub.isSingleCycle)
}

/** Formats a date compactly ("Mar 3") within [today]'s year, else with the year. */
@Composable
fun shortDate(date: LocalDate, today: LocalDate): String {
    val locale = currentLocale()
    return if (date.year == today.year) DateLabels.formatMonthDay(date, locale) else DateLabels.formatDate(date, FormatStyle.MEDIUM, locale)
}

/** [SubscriptionCard] for a [SubscriptionItem] honouring the list display settings. */
@Composable
fun SubscriptionItemCard(
    item: SubscriptionItem,
    list: ListSettings,
    today: LocalDate,
    symbols: Map<String, String>,
    modifier: Modifier = Modifier,
    variant: SubscriptionCardVariant = SubscriptionCardVariant.LIST,
    dimmed: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val sub = item.subscription
    val amount = SubscriptionCardModel.amount(sub, list)
    val priceText = if (amount.signum() == 0 && sub.kind != SubscriptionKind.STORED_VALUE) {
        stringResource(R.string.subscriptions_list_free)
    } else {
        formatMoney(amount, sub.currencyCode, symbols[sub.currencyCode])
    }
    val cycleText = when {
        sub.kind == SubscriptionKind.LIFETIME || sub.kind == SubscriptionKind.WISHLIST && sub.billingCycle == null -> null
        sub.isSingleCycle -> " · " + CycleLabels.label(sub).asString()
        sub.billingCycle == BillingCycle.CUSTOM && list.showCustomCycleAsYmd -> " · " + CycleLabels.label(sub, showYmd = true).asString()
        else -> CycleLabels.perSuffix(sub.billingCycle, sub.customCycleCount, sub.customCycleUnit).asString().ifEmpty { null }
    }
    val date = SubscriptionCardModel.date(sub, list)
    val dateText = date?.let {
        val formatted = shortDate(it.date, today)
        when (it.kind) {
            CardDateKind.NEXT -> stringResource(R.string.subscriptions_list_date_next, formatted)
            CardDateKind.END -> stringResource(R.string.subscriptions_list_date_end, formatted)
            CardDateKind.BOUGHT -> stringResource(R.string.subscriptions_list_date_bought, formatted)
        }
    }
    SubscriptionCard(
        name = sub.name,
        priceText = priceText,
        modifier = modifier,
        cycleText = cycleText,
        dateText = dateText,
        secondaryText = item.category?.displayName()?.asString()?.ifBlank { null },
        iconType = sub.iconType,
        iconValue = sub.iconValue,
        dueDate = SubscriptionCardModel.dueDate(sub),
        today = today,
        badges = SubscriptionCardModel.badges(sub, today, list),
        accentColor = colorFromHex(item.category?.colorHex),
        variant = variant,
        dimmed = dimmed,
        onClick = onClick,
        onLongClick = onLongClick,
    )
}
