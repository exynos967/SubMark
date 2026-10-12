package io.github.submark.feature.backup

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import io.github.submark.core.ui.navigation.DataManagementRoute
import io.github.submark.core.ui.navigation.WebDavProfileRoute
import io.github.submark.core.ui.navigation.WebDavProfilesRoute
import io.github.submark.core.ui.navigation.screen
import io.github.submark.feature.backup.ui.datamanage.DataManagementRoute as DataManagementDestination
import io.github.submark.feature.backup.ui.editor.ProfileEditorRoute
import io.github.submark.feature.backup.ui.profiles.WebDavProfilesRoute as WebDavProfilesDestination

/**
 * Registers every route owned by feature:backup: Data Management, the WebDAV profiles list and the
 * profile editor (which also hosts the per-profile detail sections).
 */
fun NavGraphBuilder.backupGraph(navController: NavController) {
    screen<DataManagementRoute> {
        DataManagementDestination(
            onBack = { navController.popBackStack() },
            onNavigate = { route -> navController.navigate(route) },
        )
    }
    screen<WebDavProfilesRoute> {
        WebDavProfilesDestination(
            onBack = { navController.popBackStack() },
            onNavigate = { route -> navController.navigate(route) },
        )
    }
    screen<WebDavProfileRoute> {
        ProfileEditorRoute(onBack = { navController.popBackStack() })
    }
}
