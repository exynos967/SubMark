package io.github.submark.core.ui.util

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth

/**
 * Widens a horizontally scrolling row by [parentPadding] on both sides so it scrolls to the screen edge
 * instead of being clipped at the parent's content padding. Give the row's content the same horizontal
 * padding (e.g. LazyRow `contentPadding`) so the first item still lines up with the rest of the screen.
 */
fun Modifier.bleedHorizontally(parentPadding: Dp): Modifier = layout { measurable, constraints ->
    val extra = parentPadding.roundToPx() * 2
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.constrainWidth(placeable.width - extra), placeable.height) {
        placeable.place(-extra / 2, 0)
    }
}
