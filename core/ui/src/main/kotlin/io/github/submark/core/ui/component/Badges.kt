package io.github.submark.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.submark.core.model.IconType
import io.github.submark.core.model.TagColor
import io.github.submark.core.ui.R
import io.github.submark.core.ui.format.BadgeTone
import io.github.submark.core.ui.format.DateLabels
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.format.themedColor
import io.github.submark.core.ui.format.tone
import io.github.submark.core.ui.icon.SubscriptionIcon
import io.github.submark.core.ui.theme.SubMarkTheme
import java.time.LocalDate

/** Container/content colors for a [BadgeTone]. */
data class BadgeColors(val container: Color, val content: Color)

@Composable
@ReadOnlyComposable
fun BadgeTone.colors(): BadgeColors {
    val cs = MaterialTheme.colorScheme
    val ext = SubMarkTheme.extendedColors
    return when (this) {
        BadgeTone.NEUTRAL -> BadgeColors(cs.surfaceContainerHighest, cs.onSurfaceVariant)
        BadgeTone.PRIMARY -> BadgeColors(cs.primaryContainer, cs.onPrimaryContainer)
        BadgeTone.SECONDARY -> BadgeColors(cs.secondaryContainer, cs.onSecondaryContainer)
        BadgeTone.TERTIARY -> BadgeColors(cs.tertiaryContainer, cs.onTertiaryContainer)
        BadgeTone.SUCCESS -> BadgeColors(ext.successContainer, ext.onSuccessContainer)
        BadgeTone.WARNING -> BadgeColors(ext.warningContainer, ext.onWarningContainer)
        BadgeTone.ERROR -> BadgeColors(cs.errorContainer, cs.onErrorContainer)
    }
}

/** Small pill label ("Paused", "Shared", "Success"...). */
@Composable
fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.NEUTRAL,
    icon: ImageVector? = null,
) {
    val colors = tone.colors()
    Surface(modifier = modifier, shape = RoundedCornerShape(6.dp), color = colors.container, contentColor = colors.content) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

/** "Today" / "Tomorrow" / "In 5 days" / "Overdue 2 days", colored by urgency. */
@Composable
fun CountdownBadge(
    dueDate: LocalDate,
    today: LocalDate,
    modifier: Modifier = Modifier,
    soonDays: Int = 7,
) {
    val level = DateLabels.countdownLevel(dueDate, today, soonDays)
    StatusBadge(text = DateLabels.due(dueDate, today).asString(), tone = level.tone, modifier = modifier)
}

/**
 * Category pill with its icon. [color] tints the icon and background (category `colorHex`);
 * clickable/selectable when [onClick] is set.
 */
@Composable
fun CategoryChip(
    name: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    iconType: IconType? = null,
    iconValue: String? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val accent = color ?: MaterialTheme.colorScheme.primary
    ChipSurface(
        modifier = modifier,
        container = if (selected) accent.copy(alpha = 0.28f) else accent.copy(alpha = 0.14f),
        border = if (selected) BorderStroke(1.dp, accent) else null,
        onClick = onClick,
    ) {
        if (iconValue != null) {
            SubscriptionIcon(
                type = iconType,
                value = iconValue,
                fallbackName = name,
                size = 18.dp,
                tint = accent,
                background = Color.Transparent,
            )
        }
        Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Tag pill with a colored dot. Shows a remove button when [onRemove] is set. */
@Composable
fun TagChip(
    name: String,
    color: TagColor,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val accent = color.themedColor()
    ChipSurface(
        modifier = modifier,
        container = if (selected) accent.copy(alpha = 0.30f) else accent.copy(alpha = 0.14f),
        border = if (selected) BorderStroke(1.dp, accent) else null,
        onClick = onClick,
    ) {
        Box(Modifier.size(8.dp).background(accent, CircleShape))
        Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onRemove != null) {
            Surface(onClick = onRemove, shape = CircleShape, color = Color.Transparent) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.ui_action_remove_named, name),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ChipSurface(
    modifier: Modifier,
    container: Color,
    border: BorderStroke?,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val inner = @Composable {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) { content() }
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = container, border = border, content = inner)
    } else {
        Surface(modifier = modifier, shape = shape, color = container, border = border, content = inner)
    }
}

@Preview(showBackground = true)
@Composable
private fun BadgesPreview() {
    SubMarkTheme {
        androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val today = LocalDate.of(2026, 3, 10)
            CountdownBadge(today.minusDays(2), today)
            CountdownBadge(today, today)
            CountdownBadge(today.plusDays(4), today)
            StatusBadge("Paused", tone = BadgeTone.NEUTRAL)
            CategoryChip("Video", color = Color(0xFFE5484D), iconType = IconType.SYMBOL, iconValue = "movie")
            TagChip("Family", TagColor.GREEN, onRemove = {})
        }
    }
}
