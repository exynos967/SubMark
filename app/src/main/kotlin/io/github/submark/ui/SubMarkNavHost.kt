package io.github.submark.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import io.github.submark.R
import io.github.submark.core.data.settings.AppSettings
import io.github.submark.core.data.settings.StartupTab
import io.github.submark.core.ui.navigation.AnalyticsRoute
import io.github.submark.core.ui.navigation.CalendarRoute
import io.github.submark.core.ui.navigation.GlobalSearchRoute
import io.github.submark.core.ui.navigation.OverviewRoute
import io.github.submark.core.ui.navigation.PanelRoute
import io.github.submark.core.ui.navigation.SettingsRoute
import io.github.submark.core.ui.navigation.SubscriptionsRoute
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

    Scaffold(
        topBar = {
            if (currentTab != null) {
                TopAppBar(
                    title = { Text(stringResource(currentTab.label)) },
                    actions = {
                        if (settings.navigation.globalSearchButton) {
                            IconButton(onClick = { navController.navigate(GlobalSearchRoute) }) {
                                Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.action_search))
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (currentTab != null) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == currentTab,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startTab.route,
            modifier = Modifier.fillMaxSize().padding(padding),
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
}
