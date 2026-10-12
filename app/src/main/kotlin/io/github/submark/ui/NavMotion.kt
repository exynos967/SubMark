package io.github.submark.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy

/**
 * Material 3 navigation motion:
 * - between top-level tabs (no spatial relationship): fade through
 * - into a sub-page (hierarchy): shared axis X; the bottom bar slides down / back up
 * - back out of it: the page shrinks away over the page below, which is visible from the start. The back gesture
 *   seeks this transition with the finger (predictive back), so the preview must show something from the first pixel.
 */
internal class NavMotion(density: Density) {
    private val slideDistancePx = with(density) { 30.dp.roundToPx() }

    fun enter(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition =
        if (scope.isTabSwitch()) fadeThroughIn() else sharedAxisIn()

    fun exit(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition =
        if (scope.isTabSwitch()) fadeThroughOut() else sharedAxisOut()

    fun popEnter(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition =
        if (scope.isTabSwitch()) fadeThroughIn() else backIn()

    fun popExit(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition =
        if (scope.isTabSwitch()) fadeThroughOut() else backOut()

    fun barEnter(): EnterTransition = slideInVertically(tween(DURATION, easing = EmphasizedDecelerate)) { it }

    fun barExit(): ExitTransition = slideOutVertically(tween(BAR_EXIT, easing = EmphasizedAccelerate)) { it }

    private fun sharedAxisIn(): EnterTransition =
        slideInHorizontally(tween(DURATION, easing = EmphasizedDecelerate)) { slideDistancePx } +
            fadeIn(tween(FADE_IN, delayMillis = FADE_OUT, easing = LinearEasing))

    private fun sharedAxisOut(): ExitTransition =
        slideOutHorizontally(tween(DURATION, easing = EmphasizedDecelerate)) { -slideDistancePx } +
            fadeOut(tween(FADE_OUT, easing = LinearEasing))

    /**
     * Linear, so the page tracks the finger. It stays opaque while the gesture previews it (a see-through page mixes
     * both screens' content) and only fades in the last stretch, after the back is committed.
     */
    private fun backOut(): ExitTransition =
        scaleOut(tween(DURATION, easing = LinearEasing), targetScale = BACK_SCALE) +
            slideOutHorizontally(tween(DURATION, easing = LinearEasing)) { slideDistancePx } +
            fadeOut(tween(FADE_OUT, delayMillis = DURATION - FADE_OUT, easing = LinearEasing))

    private fun backIn(): EnterTransition =
        slideInHorizontally(tween(DURATION, easing = LinearEasing)) { -slideDistancePx }

    private fun fadeThroughIn(): EnterTransition =
        fadeIn(tween(FADE_IN, delayMillis = FADE_OUT, easing = EmphasizedDecelerate)) +
            scaleIn(tween(FADE_IN, delayMillis = FADE_OUT, easing = EmphasizedDecelerate), initialScale = 0.92f)

    private fun fadeThroughOut(): ExitTransition = fadeOut(tween(FADE_OUT, easing = LinearEasing))

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
        initialState.isTopTab() && targetState.isTopTab()

    private fun NavBackStackEntry.isTopTab(): Boolean =
        TopTab.entries.any { tab -> destination.hierarchy.any { it.hasRoute(tab.routeClass) } }

    private companion object {
        const val DURATION = 300
        const val FADE_OUT = 90
        const val FADE_IN = DURATION - FADE_OUT
        const val BAR_EXIT = 200
        const val BACK_SCALE = 0.9f

        /** M3 "emphasized decelerate" easing: fast start, long gentle settle. */
        val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

        /** M3 "emphasized accelerate" easing, for elements leaving the screen. */
        val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    }
}
