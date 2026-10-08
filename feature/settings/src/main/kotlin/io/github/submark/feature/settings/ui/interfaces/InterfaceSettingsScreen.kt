package io.github.submark.feature.settings.ui.interfaces

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.WidthNormal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.data.settings.AppSettings
import io.github.submark.core.data.settings.CalendarMode
import io.github.submark.core.data.settings.DefaultListStyle
import io.github.submark.core.data.settings.FloatingTabWidth
import io.github.submark.core.data.settings.StartupTab
import io.github.submark.core.ui.component.SettingsGroup
import io.github.submark.core.ui.component.SettingsSwitchRow
import io.github.submark.core.ui.component.SettingsValueRow
import io.github.submark.feature.settings.R
import io.github.submark.feature.settings.ui.common.AppSettingsViewModel
import io.github.submark.feature.settings.ui.common.ChoiceDialog
import io.github.submark.feature.settings.ui.common.SettingsPage
import io.github.submark.feature.settings.ui.common.labelRes

@Composable
internal fun InterfaceSettingsScreenRoute(onBack: () -> Unit, viewModel: AppSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    InterfaceSettingsScreen(settings, onBack, viewModel::update)
}

private enum class InterfaceDialog { STARTUP, WIDTH, LIST_STYLE, CALENDAR }

@Composable
internal fun InterfaceSettingsScreen(
    settings: AppSettings?,
    onBack: () -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<InterfaceDialog?>(null) }
    SettingsPage(title = stringResource(R.string.settings_interface_title), onBack = onBack, loading = settings == null) {
        val s = settings ?: return@SettingsPage
        val nav = s.navigation
        SettingsGroup(title = stringResource(R.string.settings_interface_startup_group)) {
            SettingsValueRow(
                title = stringResource(R.string.settings_startup_tab),
                value = stringResource(nav.startupTab.labelRes),
                icon = Icons.Rounded.Home,
                onClick = { dialog = InterfaceDialog.STARTUP },
            )
            SettingsValueRow(
                title = stringResource(R.string.settings_default_list_style),
                value = stringResource(s.list.defaultStyle.labelRes),
                icon = Icons.Rounded.GridView,
                onClick = { dialog = InterfaceDialog.LIST_STYLE },
            )
            SettingsValueRow(
                title = stringResource(R.string.settings_default_calendar_mode),
                value = stringResource(s.calendar.defaultMode.labelRes),
                icon = Icons.Rounded.CalendarViewWeek,
                onClick = { dialog = InterfaceDialog.CALENDAR },
            )
        }
        SettingsGroup(
            title = stringResource(R.string.settings_interface_tabs_group),
            footer = stringResource(R.string.settings_interface_tabs_footer),
        ) {
            ToggleableTab.entries.forEach { tab ->
                SettingsSwitchRow(
                    title = stringResource(tab.labelRes),
                    icon = tab.icon,
                    checked = TabRules.isVisible(nav, tab),
                    onCheckedChange = { on -> onUpdate { it.copy(navigation = TabRules.setVisible(it.navigation, tab, on)) } },
                )
            }
        }
        SettingsGroup(title = stringResource(R.string.settings_interface_tab_bar_group)) {
            SettingsValueRow(
                title = stringResource(R.string.settings_tab_width),
                value = stringResource(nav.floatingTabWidth.labelRes),
                icon = Icons.Rounded.WidthNormal,
                onClick = { dialog = InterfaceDialog.WIDTH },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_global_search_button),
                subtitle = stringResource(R.string.settings_global_search_button_desc),
                icon = Icons.Rounded.Search,
                checked = nav.globalSearchButton,
                onCheckedChange = { on -> onUpdate { it.copy(navigation = it.navigation.copy(globalSearchButton = on)) } },
            )
        }

        when (dialog) {
            InterfaceDialog.STARTUP -> ChoiceDialog(
                title = stringResource(R.string.settings_startup_tab),
                options = StartupTab.entries,
                selected = nav.startupTab,
                label = { stringResource(it.labelRes) },
                enabled = { TabRules.isStartupAvailable(nav, it) },
                description = { if (TabRules.isStartupAvailable(nav, it)) null else stringResource(R.string.settings_startup_tab_hidden) },
                onSelect = { tab -> onUpdate { it.copy(navigation = it.navigation.copy(startupTab = tab)) } },
                onDismiss = { dialog = null },
            )
            InterfaceDialog.WIDTH -> ChoiceDialog(
                title = stringResource(R.string.settings_tab_width),
                options = FloatingTabWidth.entries,
                selected = nav.floatingTabWidth,
                label = { stringResource(it.labelRes) },
                onSelect = { w -> onUpdate { it.copy(navigation = it.navigation.copy(floatingTabWidth = w)) } },
                onDismiss = { dialog = null },
            )
            InterfaceDialog.LIST_STYLE -> ChoiceDialog(
                title = stringResource(R.string.settings_default_list_style),
                options = DefaultListStyle.entries,
                selected = s.list.defaultStyle,
                label = { stringResource(it.labelRes) },
                onSelect = { style -> onUpdate { it.copy(list = it.list.copy(defaultStyle = style)) } },
                onDismiss = { dialog = null },
            )
            InterfaceDialog.CALENDAR -> ChoiceDialog(
                title = stringResource(R.string.settings_default_calendar_mode),
                options = CalendarMode.entries,
                selected = s.calendar.defaultMode,
                label = { stringResource(it.labelRes) },
                onSelect = { mode -> onUpdate { it.copy(calendar = it.calendar.copy(defaultMode = mode)) } },
                onDismiss = { dialog = null },
            )
            null -> Unit
        }
    }
}

private val ToggleableTab.labelRes: Int
    get() = when (this) {
        ToggleableTab.OVERVIEW -> R.string.settings_tab_overview
        ToggleableTab.CALENDAR -> R.string.settings_tab_calendar
        ToggleableTab.ANALYTICS -> R.string.settings_tab_analytics
        ToggleableTab.PANEL -> R.string.settings_tab_panel
    }

private val ToggleableTab.icon
    get() = when (this) {
        ToggleableTab.OVERVIEW -> Icons.Rounded.Dashboard
        ToggleableTab.CALENDAR -> Icons.Rounded.CalendarMonth
        ToggleableTab.ANALYTICS -> Icons.Rounded.Analytics
        ToggleableTab.PANEL -> Icons.Rounded.SpaceDashboard
    }
