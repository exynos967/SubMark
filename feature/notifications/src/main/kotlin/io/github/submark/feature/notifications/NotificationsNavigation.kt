package io.github.submark.feature.notifications

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.submark.core.ui.navigation.NotificationDiagnosticsRoute
import io.github.submark.core.ui.navigation.NotificationSettingsRoute
import io.github.submark.core.ui.navigation.SubscriptionRemindersRoute
import io.github.submark.feature.notifications.ui.diagnostics.NotificationDiagnosticsScreenRoute
import io.github.submark.feature.notifications.ui.reminders.SubscriptionRemindersScreenRoute
import io.github.submark.feature.notifications.ui.settings.NotificationSettingsScreenRoute

/** Registers every notification route owned by this module. */
fun NavGraphBuilder.notificationsGraph(navController: NavController) {
    val navigate: (Any) -> Unit = { route -> navController.navigate(route) }
    val back: () -> Unit = { navController.popBackStack() }

    composable<NotificationSettingsRoute> { NotificationSettingsScreenRoute(onBack = back, onNavigate = navigate) }
    composable<SubscriptionRemindersRoute> { SubscriptionRemindersScreenRoute(onBack = back) }
    composable<NotificationDiagnosticsRoute> { NotificationDiagnosticsScreenRoute(onBack = back) }
}
