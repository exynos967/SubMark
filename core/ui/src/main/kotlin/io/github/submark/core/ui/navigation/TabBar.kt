package io.github.submark.core.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Height of the app's bottom navigation bar, including the system navigation bar under it. The bar is drawn over
 * the screens so it can slide away when a detail screen opens without resizing the screens mid-transition.
 */
val LocalTabBarHeight = compositionLocalOf { 0.dp }

/** Wraps a top-level tab screen: keeps its content above the bottom navigation bar. */
@Composable
fun TabDestination(content: @Composable () -> Unit) {
    val height: Dp = LocalTabBarHeight.current
    val bar = PaddingValues(bottom = height)
    // The bar already covers the system navigation bar, so inner Scaffolds must not pad for it again.
    Box(Modifier.padding(bar).consumeWindowInsets(bar)) { content() }
}
