package io.github.submark.core.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination

/**
 * Switches to a top-level tab route ([OverviewRoute], [SubscriptionsRoute], …).
 * Always use this instead of a plain `navigate(tabRoute)`: pushing a tab onto another tab's back stack
 * makes the start tab restore that stack when it is selected again, so the user can never get back to it.
 */
fun NavController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
