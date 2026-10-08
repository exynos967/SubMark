package io.github.submark.feature.integrations

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import io.github.submark.core.ui.icon.IconChoice
import io.github.submark.core.ui.navigation.AiRecognitionRoute
import io.github.submark.core.ui.navigation.AiSettingsRoute
import io.github.submark.core.ui.navigation.IconPickerRoute
import io.github.submark.core.ui.navigation.NavResults
import io.github.submark.core.ui.navigation.PopularRepositoriesRoute
import io.github.submark.core.ui.navigation.PopularSubscriptionsRoute
import io.github.submark.core.ui.navigation.PriceMonitorRoute
import io.github.submark.core.ui.navigation.PriceMonitorSettingsRoute
import io.github.submark.core.ui.navigation.RawgSettingsRoute
import io.github.submark.feature.integrations.ui.ai.AiRecognitionRoute as AiRecognitionScreenRoute
import io.github.submark.feature.integrations.ui.ai.AiSettingsRoute as AiSettingsScreenRoute
import io.github.submark.feature.integrations.ui.iconpicker.IconPickerRoute as IconPickerScreenRoute
import io.github.submark.feature.integrations.ui.popular.PopularRepositoriesRoute as PopularRepositoriesScreenRoute
import io.github.submark.feature.integrations.ui.popular.PopularScreenRoute
import io.github.submark.feature.integrations.ui.price.PriceMonitorRoute as PriceMonitorScreenRoute
import io.github.submark.feature.integrations.ui.price.PriceMonitorSettingsRoute as PriceMonitorSettingsScreenRoute
import io.github.submark.feature.integrations.ui.rawg.RawgSettingsRoute as RawgSettingsScreenRoute

/**
 * Registers every destination owned by the integrations feature:
 * popular catalogue, price monitoring, AI recognition and the icon picker.
 * The API budget / service panel routes (PanelRoute, ApiBudgetEditRoute, ServiceConnectionEditRoute)
 * are registered separately in `panel/PanelNavigation.kt`.
 */
fun NavGraphBuilder.integrationsGraph(navController: NavController) {
    val navigate: (Any) -> Unit = { route -> navController.navigate(route) }
    val back: () -> Unit = { navController.popBackStack() }

    composable<PopularSubscriptionsRoute> {
        PopularScreenRoute(onBack = back, onNavigate = navigate)
    }
    composable<PopularRepositoriesRoute> {
        PopularRepositoriesScreenRoute(onBack = back)
    }
    composable<PriceMonitorRoute> {
        PriceMonitorScreenRoute(onBack = back)
    }
    composable<PriceMonitorSettingsRoute> {
        PriceMonitorSettingsScreenRoute(onBack = back)
    }
    composable<AiRecognitionRoute>(
        deepLinks = listOf(navDeepLink<AiRecognitionRoute>(basePath = "submark://ai-recognize")),
    ) {
        AiRecognitionScreenRoute(
            onBack = back,
            onNavigate = navigate,
            onOpenSettings = { navigate(AiSettingsRoute) },
        )
    }
    composable<AiSettingsRoute> {
        AiSettingsScreenRoute(onBack = back)
    }
    composable<RawgSettingsRoute> {
        RawgSettingsScreenRoute(onBack = back)
    }
    composable<IconPickerRoute> {
        IconPickerScreenRoute(
            onBack = back,
            onResult = { choice: IconChoice ->
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(NavResults.ICON, choice.encode())
                navController.popBackStack()
            },
            onOpenRawgSettings = { navigate(RawgSettingsRoute) },
        )
    }
}
