package io.github.submark.feature.settings.ui.interfaces

import io.github.submark.core.data.settings.NavigationSettings
import io.github.submark.core.data.settings.StartupTab

/** Tabs the user may hide; Subscriptions and Settings are always shown. */
enum class ToggleableTab { OVERVIEW, CALENDAR, ANALYTICS, PANEL }

internal object TabRules {

    fun isVisible(nav: NavigationSettings, tab: ToggleableTab): Boolean = when (tab) {
        ToggleableTab.OVERVIEW -> nav.showOverviewTab
        ToggleableTab.CALENDAR -> nav.showCalendarTab
        ToggleableTab.ANALYTICS -> nav.showAnalyticsTab
        ToggleableTab.PANEL -> nav.showPanelTab
    }

    fun isStartupAvailable(nav: NavigationSettings, tab: StartupTab): Boolean = when (tab) {
        StartupTab.SUBSCRIPTIONS -> true
        StartupTab.OVERVIEW -> nav.showOverviewTab
        StartupTab.CALENDAR -> nav.showCalendarTab
        StartupTab.ANALYTICS -> nav.showAnalyticsTab
    }

    /** Hiding the startup tab moves startup to Subscriptions, which can never be hidden. */
    fun setVisible(nav: NavigationSettings, tab: ToggleableTab, visible: Boolean): NavigationSettings {
        val updated = when (tab) {
            ToggleableTab.OVERVIEW -> nav.copy(showOverviewTab = visible)
            ToggleableTab.CALENDAR -> nav.copy(showCalendarTab = visible)
            ToggleableTab.ANALYTICS -> nav.copy(showAnalyticsTab = visible)
            ToggleableTab.PANEL -> nav.copy(showPanelTab = visible)
        }
        return if (isStartupAvailable(updated, updated.startupTab)) updated else updated.copy(startupTab = StartupTab.SUBSCRIPTIONS)
    }
}
