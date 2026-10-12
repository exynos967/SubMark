package io.github.submark.feature.settings

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import io.github.submark.core.ui.navigation.AboutRoute
import io.github.submark.core.ui.navigation.AppIconRoute
import io.github.submark.core.ui.navigation.AppearanceSettingsRoute
import io.github.submark.core.ui.navigation.CustomizationRoute
import io.github.submark.core.ui.navigation.DeveloperOptionsRoute
import io.github.submark.core.ui.navigation.DocumentRoute
import io.github.submark.core.ui.navigation.FontSettingsRoute
import io.github.submark.core.ui.navigation.InterfaceSettingsRoute
import io.github.submark.core.ui.navigation.OnboardingRoute
import io.github.submark.core.ui.navigation.RegionCurrencySettingsRoute
import io.github.submark.core.ui.navigation.SecuritySettingsRoute
import io.github.submark.core.ui.navigation.SettingsRoute
import io.github.submark.core.ui.navigation.TabDestination
import io.github.submark.core.ui.navigation.screen
import io.github.submark.feature.settings.ui.about.AboutScreenRoute
import io.github.submark.feature.settings.ui.appearance.AppearanceScreenRoute
import io.github.submark.feature.settings.ui.appicon.AppIconScreenRoute
import io.github.submark.feature.settings.ui.customization.CustomizationScreenRoute
import io.github.submark.feature.settings.ui.developer.DeveloperOptionsScreenRoute
import io.github.submark.feature.settings.ui.document.DocumentScreenRoute
import io.github.submark.feature.settings.ui.font.FontSettingsScreenRoute
import io.github.submark.feature.settings.ui.interfaces.InterfaceSettingsScreenRoute
import io.github.submark.feature.settings.ui.onboarding.OnboardingScreenRoute
import io.github.submark.feature.settings.ui.region.RegionCurrencyScreenRoute
import io.github.submark.feature.settings.ui.root.SettingsRootScreenRoute
import io.github.submark.feature.settings.ui.security.SecuritySettingsScreenRoute

/**
 * Registers the Settings tab and every settings page owned by this module.
 * [OnboardingRoute] pops itself when finished; the app shell decides when to show it
 * (e.g. `navigate(OnboardingRoute) { popUpTo(...) }` while `onboardingCompleted` is false).
 */
fun NavGraphBuilder.settingsGraph(navController: NavController) {
    val navigate: (Any) -> Unit = { route -> navController.navigate(route) }
    val back: () -> Unit = { navController.popBackStack() }

    screen<SettingsRoute> { TabDestination { SettingsRootScreenRoute(onNavigate = navigate) } }
    screen<AppearanceSettingsRoute> { AppearanceScreenRoute(onBack = back, onNavigate = navigate) }
    screen<FontSettingsRoute> { FontSettingsScreenRoute(onBack = back) }
    screen<AppIconRoute> { AppIconScreenRoute(onBack = back) }
    screen<InterfaceSettingsRoute> { InterfaceSettingsScreenRoute(onBack = back) }
    screen<CustomizationRoute> { CustomizationScreenRoute(onBack = back, onNavigate = navigate) }
    screen<RegionCurrencySettingsRoute> { RegionCurrencyScreenRoute(onBack = back, onNavigate = navigate) }
    screen<SecuritySettingsRoute> { SecuritySettingsScreenRoute(onBack = back) }
    screen<DeveloperOptionsRoute> { DeveloperOptionsScreenRoute(onBack = back, onNavigate = navigate) }
    screen<AboutRoute> { AboutScreenRoute(onBack = back, onNavigate = navigate) }
    screen<DocumentRoute> { DocumentScreenRoute(onBack = back) }
    screen<OnboardingRoute> { OnboardingScreenRoute(onFinished = back) }
}
