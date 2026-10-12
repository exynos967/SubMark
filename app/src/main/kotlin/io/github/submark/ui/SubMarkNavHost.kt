package io.github.submark.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import io.github.submark.core.ui.navigation.LocalTabBarHeight
import androidx.annotation.StringRes
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import io.github.submark.R
import io.github.submark.core.data.settings.AppSettings
import io.github.submark.core.data.settings.StartupTab
import io.github.submark.core.ui.navigation.AnalyticsRoute
import io.github.submark.core.ui.navigation.CalendarRoute
import io.github.submark.core.ui.navigation.OverviewRoute
import io.github.submark.core.ui.navigation.PanelRoute
import io.github.submark.core.ui.navigation.SettingsRoute
import io.github.submark.core.ui.navigation.SubscriptionsRoute
import io.github.submark.core.ui.navigation.navigateToTab
import io.github.submark.feature.analytics.analyticsGraph
import io.github.submark.feature.backup.backupGraph
import io.github.submark.feature.calendar.calendarGraph
import io.github.submark.feature.integrations.integrationsGraph
import io.github.submark.feature.integrations.panel.panelGraph
import io.github.submark.feature.money.moneyGraph
import io.github.submark.feature.notifications.notificationsGraph
import io.github.submark.feature.overview.overviewGraph
import io.github.submark.feature.settings.settingsGraph
import io.github.submark.feature.share.shareGraph
import io.github.submark.feature.subscriptions.subscriptionsGraph
import kotlin.reflect.KClass

enum class TopTab(val route: Any, val routeClass: KClass<*>, @StringRes val label: Int, val icon: ImageVector) {
    OVERVIEW(OverviewRoute, OverviewRoute::class, R.string.tab_overview, Icons.Rounded.Dashboard),
    SUBSCRIPTIONS(SubscriptionsRoute, SubscriptionsRoute::class, R.string.tab_subscriptions, Icons.Rounded.ViewList),
    CALENDAR(CalendarRoute, CalendarRoute::class, R.string.tab_calendar, Icons.Rounded.CalendarMonth),
    ANALYTICS(AnalyticsRoute, AnalyticsRoute::class, R.string.tab_analytics, Icons.Rounded.BarChart),
    PANEL(PanelRoute, PanelRoute::class, R.string.tab_panel, Icons.Rounded.Widgets),
    SETTINGS(SettingsRoute, SettingsRoute::class, R.string.tab_settings, Icons.Rounded.Settings),
}

fun AppSettings.visibleTabs(): List<TopTab> = TopTab.entries.filter {
    when (it) {
        TopTab.OVERVIEW -> navigation.showOverviewTab
        TopTab.CALENDAR -> navigation.showCalendarTab
        TopTab.ANALYTICS -> navigation.showAnalyticsTab
        TopTab.PANEL -> navigation.showPanelTab
        TopTab.SUBSCRIPTIONS, TopTab.SETTINGS -> true
    }
}

fun AppSettings.startTab(): TopTab {
    val wanted = when (navigation.startupTab) {
        StartupTab.OVERVIEW -> TopTab.OVERVIEW
        StartupTab.SUBSCRIPTIONS -> TopTab.SUBSCRIPTIONS
        StartupTab.CALENDAR -> TopTab.CALENDAR
        StartupTab.ANALYTICS -> TopTab.ANALYTICS
    }
    return if (wanted in visibleTabs()) wanted else TopTab.SUBSCRIPTIONS
}

@Composable
fun SubMarkNavHost(navController: NavHostController, settings: AppSettings, startTab: TopTab) {
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val tabs = settings.visibleTabs()
    val currentTab = tabs.firstOrNull { tab -> destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true }
    var lastTab by remember { mutableStateOf(startTab) }
    LaunchedEffect(currentTab) { currentTab?.let { lastTab = it } }

    val density = LocalDensity.current
    val motion = remember(density) { NavMotion(density) }
    val animate = settings.display.animatedBackground
    // Until the bar has been measured, its standard height keeps the first frame of a tab screen in place.
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var barHeight by remember { mutableStateOf(NAVIGATION_BAR_HEIGHT + navigationBarInset) }

    // The bar is drawn over full-size screens instead of shrinking them: when a detail screen opens it slides away
    // with the transition, and no screen is resized mid-animation. Tab screens keep clear of it via TabDestination.
    // Same backdrop the shell Scaffold used to paint: screens fading in and out must not reveal the window behind.
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CompositionLocalProvider(LocalTabBarHeight provides barHeight) {
            NavHost(
                navController = navController,
                startDestination = startTab.route,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { if (animate) motion.enter(this) else EnterTransition.None },
                exitTransition = { if (animate) motion.exit(this) else ExitTransition.None },
                popEnterTransition = { if (animate) motion.popEnter(this) else EnterTransition.None },
                popExitTransition = { if (animate) motion.popExit(this) else ExitTransition.None },
            ) {
                overviewGraph(navController)
                subscriptionsGraph(navController)
                calendarGraph(navController)
                analyticsGraph(navController)
                panelGraph(navController)
                settingsGraph(navController)
                moneyGraph(navController)
                shareGraph(navController)
                notificationsGraph(navController)
                backupGraph(navController)
                integrationsGraph(navController)
            }
        }
        AnimatedVisibility(
            visible = currentTab != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = if (animate) motion.barEnter() else EnterTransition.None,
            exit = if (animate) motion.barExit() else ExitTransition.None,
        ) {
            // Extra side inset keeps the selected-item pill away from the screen edges.
            NavigationBar(
                modifier = Modifier.onSizeChanged { barHeight = with(density) { it.height.toDp() } },
                windowInsets = NavigationBarDefaults.windowInsets.add(WindowInsets(left = 12.dp, right = 12.dp)),
            ) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        // The tab that was showing stays selected while the bar slides away.
                        selected = tab == (currentTab ?: lastTab),
                        onClick = { navController.navigateToTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label), maxLines = 1) },
                    )
                }
            }
        }
    }
}

/** M3 navigation bar container height, without the system navigation bar under it. */
private val NAVIGATION_BAR_HEIGHT = 80.dp
