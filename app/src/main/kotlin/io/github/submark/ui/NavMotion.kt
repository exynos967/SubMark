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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy

/**
 * Material 3 navigation motion:
 * - between top-level tabs (no spatial relationship): fade through
 * - into / out of a sub-page (hierarchy): shared axis X, mirrored on back
 * The same pop transitions drive the predictive back gesture, so the preview follows the finger.
 */
internal class NavMotion(density: Density) {
    private val slideDistancePx = with(density) { 30.dp.roundToPx() }

    fun enter(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition =
        if (scope.isTabSwitch()) fadeThroughIn() else sharedAxisIn(forward = true)

    fun exit(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition =
        if (scope.isTabSwitch()) fadeThroughOut() else sharedAxisOut(forward = true)

    fun popEnter(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition =
        if (scope.isTabSwitch()) fadeThroughIn() else sharedAxisIn(forward = false)

    fun popExit(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition =
        if (scope.isTabSwitch()) fadeThroughOut() else sharedAxisOut(forward = false)

    private fun sharedAxisIn(forward: Boolean): EnterTransition =
        slideInHorizontally(tween(DURATION, easing = EmphasizedDecelerate)) { if (forward) slideDistancePx else -slideDistancePx } +
            fadeIn(tween(FADE_IN, delayMillis = FADE_OUT, easing = LinearEasing))

    private fun sharedAxisOut(forward: Boolean): ExitTransition =
        slideOutHorizontally(tween(DURATION, easing = EmphasizedDecelerate)) { if (forward) -slideDistancePx else slideDistancePx } +
            fadeOut(tween(FADE_OUT, easing = LinearEasing))

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

        /** M3 "emphasized decelerate" easing: fast start, long gentle settle. */
        val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    }
}
