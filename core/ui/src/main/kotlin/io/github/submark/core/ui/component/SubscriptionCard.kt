package io.github.submark.core.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.submark.core.domain.BillingCalculator
import io.github.submark.core.model.BundleRole
import io.github.submark.core.model.IconType
import io.github.submark.core.model.RenewalType
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import io.github.submark.core.ui.R
import io.github.submark.core.ui.format.BadgeTone
import io.github.submark.core.ui.icon.SubscriptionIcon
import io.github.submark.core.ui.theme.LocalThemeConfig
import io.github.submark.core.ui.theme.SubMarkTheme
import io.github.submark.core.ui.theme.ThemeConfig
import io.github.submark.core.ui.util.contentColorFor
import java.time.LocalDate

enum class SubscriptionBadge(@StringRes val labelRes: Int, val tone: BadgeTone) {
    OVERDUE(R.string.ui_badge_overdue, BadgeTone.ERROR),
    PAUSED(R.string.ui_badge_paused, BadgeTone.NEUTRAL),
    TRIAL(R.string.ui_badge_trial, BadgeTone.TERTIARY),
    SHARED(R.string.ui_badge_shared, BadgeTone.SECONDARY),
    SINGLE_CYCLE(R.string.ui_badge_single_cycle, BadgeTone.NEUTRAL),
    STORED_VALUE(R.string.ui_badge_stored_value, BadgeTone.PRIMARY),
    CHILD(R.string.ui_badge_child, BadgeTone.SECONDARY),
    LIFETIME(R.string.ui_badge_lifetime, BadgeTone.SUCCESS),
    WISHLIST(R.string.ui_badge_wishlist, BadgeTone.TERTIARY),
    AUTO(R.string.ui_badge_auto, BadgeTone.NEUTRAL),

    ;

    companion object {
        /** Badges derived from a subscription, most important first. AUTO is opt-in because it applies to most rows. */
        fun of(sub: Subscription, today: LocalDate, includeAuto: Boolean = false): List<SubscriptionBadge> = buildList {
            if (BillingCalculator.isOverdue(sub, today)) add(OVERDUE)
            if (sub.status == SubscriptionStatus.PAUSED) add(PAUSED)
            if (sub.renewalType == RenewalType.TRIAL) add(TRIAL)
            if (sub.isShared) add(SHARED)
            if (sub.isSingleCycle) add(SINGLE_CYCLE)
            if (sub.kind == SubscriptionKind.STORED_VALUE) add(STORED_VALUE)
            if (sub.bundleRole == BundleRole.CHILD) add(CHILD)
            if (sub.kind == SubscriptionKind.LIFETIME) add(LIFETIME)
            if (sub.kind == SubscriptionKind.WISHLIST) add(WISHLIST)
            if (includeAuto && sub.renewalType == RenewalType.AUTO && sub.kind != SubscriptionKind.LIFETIME) add(AUTO)
        }
    }
}

enum class SubscriptionCardVariant { LIST, GRID }

/**
 * Subscription row/tile built from plain display values (format them with [formatMoney],
 * [io.github.submark.core.ui.format.CycleLabels], [io.github.submark.core.ui.format.DateLabels]).
 *
 * @param priceText e.g. "$9.99" (or the stored-value balance).
 * @param cycleText e.g. "/mo" or "Monthly".
 * @param dateText the single date line, e.g. "Next: Mar 14".
 * @param dueDate with [today], shows a [CountdownBadge].
 * @param accentColor category color; used for the gradient when colorful cards are on.
 * @param dimmed lowers emphasis (paused/archived rows).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubscriptionCard(
    name: String,
    priceText: String,
    modifier: Modifier = Modifier,
    cycleText: String? = null,
    dateText: String? = null,
    secondaryText: String? = null,
    iconType: IconType? = null,
    iconValue: String? = null,
    dueDate: LocalDate? = null,
    today: LocalDate? = null,
    badges: List<SubscriptionBadge> = emptyList(),
    accentColor: Color? = null,
    variant: SubscriptionCardVariant = SubscriptionCardVariant.LIST,
    dimmed: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val colorful = LocalThemeConfig.current.colorfulCards && accentColor != null
    val container = MaterialTheme.colorScheme.surfaceContainerLow
    val contentColor = if (colorful) accentColor!!.contentColorFor() else MaterialTheme.colorScheme.onSurface
    val clickModifier = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
    } else {
        Modifier
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (colorful) Color.Transparent else container, contentColor = contentColor),
    ) {
        Box(
            Modifier
                .then(clickModifier)
                .then(
                    if (colorful) {
                        Modifier.background(Brush.linearGradient(listOf(accentColor!!, lerp(accentColor, Color.Black, 0.35f))))
                    } else {
                        Modifier
                    },
                )
                .alpha(if (dimmed) 0.6f else 1f),
        ) {
            if (colorful) {
                // Large faded watermark of the icon in the corner.
                SubscriptionIcon(
                    type = iconType,
                    value = iconValue,
                    fallbackName = name,
                    size = 96.dp,
                    tint = contentColor.copy(alpha = 0.14f),
                    background = Color.Transparent,
                    modifier = Modifier.align(Alignment.BottomEnd).offset(x = 16.dp, y = 20.dp),
                )
            }
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                val icon = @Composable {
                    SubscriptionIcon(
                        type = iconType,
                        value = iconValue,
                        fallbackName = name,
                        size = 44.dp,
                        background = if (colorful) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primaryContainer,
                        tint = if (colorful) contentColor else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                val countdown = @Composable {
                    if (dueDate != null && today != null) CountdownBadge(dueDate, today)
                }
                when (variant) {
                    SubscriptionCardVariant.LIST -> ListLayout(name, priceText, cycleText, dateText, secondaryText, badges, colorful, icon, countdown)
                    SubscriptionCardVariant.GRID -> GridLayout(name, priceText, cycleText, dateText, secondaryText, badges, colorful, icon, countdown)
                }
            }
        }
    }
}

@Composable
private fun ListLayout(
    name: String,
    priceText: String,
    cycleText: String?,
    dateText: String?,
    secondaryText: String?,
    badges: List<SubscriptionBadge>,
    colorful: Boolean,
    icon: @Composable () -> Unit,
    countdown: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            SubtleLine(listOfNotNull(secondaryText, dateText).joinToString(" · "), colorful)
            BadgeRow(badges)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PriceLine(priceText, cycleText, colorful)
            countdown()
        }
    }
}

@Composable
private fun GridLayout(
    name: String,
    priceText: String,
    cycleText: String?,
    dateText: String?,
    secondaryText: String?,
    badges: List<SubscriptionBadge>,
    colorful: Boolean,
    icon: @Composable () -> Unit,
    countdown: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            icon()
            Spacer(Modifier.weight(1f))
            countdown()
        }
        Spacer(Modifier.width(4.dp))
        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        PriceLine(priceText, cycleText, colorful)
        SubtleLine(listOfNotNull(secondaryText, dateText).joinToString(" · "), colorful)
        BadgeRow(badges)
    }
}

@Composable
private fun PriceLine(priceText: String, cycleText: String?, colorful: Boolean) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(priceText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (!cycleText.isNullOrEmpty()) {
            Text(
                cycleText,
                style = MaterialTheme.typography.bodySmall,
                color = subtleColor(colorful),
                maxLines = 1,
                modifier = Modifier.padding(start = 2.dp, bottom = 1.dp),
            )
        }
    }
}

@Composable
private fun SubtleLine(text: String, colorful: Boolean) {
    if (text.isNotEmpty()) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = subtleColor(colorful), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun subtleColor(colorful: Boolean): Color =
    if (colorful) LocalContentColor.current.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BadgeRow(badges: List<SubscriptionBadge>) {
    if (badges.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 2.dp),
    ) {
        badges.forEach { StatusBadge(stringResource(it.labelRes), tone = it.tone) }
    }
}

@Preview(showBackground = true)
@Composable
private fun SubscriptionCardPreview() {
    val today = LocalDate.of(2026, 3, 10)
    SubMarkTheme {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SubscriptionCard(
                name = "Stream Max",
                priceText = "$15.49",
                cycleText = "/mo",
                dateText = "Mar 12",
                secondaryText = "Video",
                iconType = IconType.SYMBOL,
                iconValue = "movie",
                dueDate = today.plusDays(2),
                today = today,
                badges = listOf(SubscriptionBadge.SHARED, SubscriptionBadge.TRIAL),
            )
            SubscriptionCard(
                name = "Cloud Drive",
                priceText = "¥21",
                cycleText = "/mo",
                dateText = "Mar 8",
                dueDate = today.minusDays(2),
                today = today,
                badges = listOf(SubscriptionBadge.OVERDUE),
                variant = SubscriptionCardVariant.GRID,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SubscriptionCardColorfulPreview() {
    SubMarkTheme(ThemeConfig(colorfulCards = true, dynamicColor = false)) {
        SubscriptionCard(
            name = "Music Plus",
            priceText = "$9.99",
            cycleText = "/mo",
            dateText = "Mar 20",
            iconType = IconType.SYMBOL,
            iconValue = "music",
            accentColor = Color(0xFFD6409F),
        )
    }
}
