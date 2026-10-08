package io.github.submark.feature.calendar

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.submark.core.ui.navigation.CalendarRoute as CalendarDest
import io.github.submark.feature.calendar.ui.calendar.CalendarRoute

/** Registers every screen owned by feature:calendar. */
fun NavGraphBuilder.calendarGraph(navController: NavController) {
    composable<CalendarDest> {
        CalendarRoute(
            onAddSubscription = { navController.navigate(io.github.submark.core.ui.navigation.SubscriptionEditRoute()) },
        )
    }
}
