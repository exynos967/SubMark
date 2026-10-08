package io.github.submark.feature.integrations.panel

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.submark.core.ui.navigation.ApiBudgetEditRoute
import io.github.submark.core.ui.navigation.PanelRoute
import io.github.submark.core.ui.navigation.ServiceConnectionEditRoute
import io.github.submark.feature.integrations.panel.ui.edit.BudgetEditScreenRoute
import io.github.submark.feature.integrations.panel.ui.edit.ServiceEditScreenRoute
import io.github.submark.feature.integrations.panel.ui.panel.PanelScreenRoute

/** Registers the Panel tab plus the API-budget and service-connection edit screens. */
fun NavGraphBuilder.panelGraph(navController: NavController) {
    composable<PanelRoute> {
        PanelScreenRoute(
            onAddService = { navController.navigate(ServiceConnectionEditRoute()) },
            onAddBudget = { navController.navigate(ApiBudgetEditRoute()) },
            onEditService = { id -> navController.navigate(ServiceConnectionEditRoute(id)) },
            onEditBudget = { id -> navController.navigate(ApiBudgetEditRoute(id)) },
        )
    }
    composable<ApiBudgetEditRoute> {
        BudgetEditScreenRoute(onBack = { navController.popBackStack() })
    }
    composable<ServiceConnectionEditRoute> {
        ServiceEditScreenRoute(onBack = { navController.popBackStack() })
    }
}
