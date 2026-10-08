package io.github.submark.feature.settings.ui.customization

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.data.settings.AddFormSettings
import io.github.submark.core.data.settings.AppSettings
import io.github.submark.core.ui.component.SegmentedTabs
import io.github.submark.core.ui.component.SettingsGroup
import io.github.submark.core.ui.component.SettingsNavRow
import io.github.submark.core.ui.component.SettingsSwitchRow
import io.github.submark.core.ui.navigation.AnalyticsCustomizationRoute
import io.github.submark.core.ui.navigation.CategoryManagementRoute
import io.github.submark.core.ui.navigation.CustomFieldManagementRoute
import io.github.submark.core.ui.navigation.InterfaceSettingsRoute
import io.github.submark.core.ui.navigation.OverviewCustomizationRoute
import io.github.submark.core.ui.navigation.TagManagementRoute
import io.github.submark.feature.settings.R
import io.github.submark.feature.settings.ui.common.AppSettingsViewModel
import io.github.submark.feature.settings.ui.common.SettingsPage

@Composable
internal fun CustomizationScreenRoute(
    onBack: () -> Unit,
    onNavigate: (Any) -> Unit,
    viewModel: AppSettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    CustomizationScreen(settings, onBack, onNavigate, viewModel::update)
}

@Composable
internal fun CustomizationScreen(
    settings: AppSettings?,
    onBack: () -> Unit,
    onNavigate: (Any) -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    SettingsPage(title = stringResource(R.string.settings_customization_title), onBack = onBack, loading = settings == null) {
        val s = settings ?: return@SettingsPage
        SettingsGroup(title = stringResource(R.string.settings_customization_interface_group)) {
            SettingsNavRow(stringResource(R.string.settings_customize_overview), { onNavigate(OverviewCustomizationRoute) }, icon = Icons.Rounded.Dashboard)
            SettingsNavRow(stringResource(R.string.settings_customize_analytics), { onNavigate(AnalyticsCustomizationRoute) }, icon = Icons.Rounded.Insights)
            SettingsNavRow(stringResource(R.string.settings_interface_title), { onNavigate(InterfaceSettingsRoute) }, icon = Icons.Rounded.ViewAgenda)
        }

        val preset = CustomizationSpecs.presetOf(s.addForm)
        SettingsGroup(
            title = stringResource(R.string.settings_customization_form_group),
            footer = stringResource(
                if (preset == AddFormPreset.CUSTOM) R.string.settings_form_preset_custom_footer else R.string.settings_form_footer,
            ),
        ) {
            SegmentedTabs(
                items = listOf(AddFormPreset.FULL, AddFormPreset.MINIMAL),
                selected = preset,
                onSelect = { p ->
                    val form = if (p == AddFormPreset.MINIMAL) AddFormSettings.MINIMAL else AddFormSettings.FULL
                    onUpdate { it.copy(addForm = form) }
                },
                label = { stringResource(if (it == AddFormPreset.MINIMAL) R.string.settings_form_preset_minimal else R.string.settings_form_preset_full) },
                modifier = Modifier.padding(16.dp),
            )
            ToggleRows(CustomizationSpecs.addFormModules, s, onUpdate)
        }

        SettingsGroup(title = stringResource(R.string.settings_customization_modules_group)) {
            ToggleRows(CustomizationSpecs.functionModules, s, onUpdate)
        }

        SettingsGroup(title = stringResource(R.string.settings_customization_list_group)) {
            ToggleRows(CustomizationSpecs.listDisplay, s, onUpdate)
        }

        SettingsGroup(title = stringResource(R.string.settings_customization_manage_group)) {
            SettingsNavRow(stringResource(R.string.settings_manage_categories), { onNavigate(CategoryManagementRoute) }, icon = Icons.Rounded.Category)
            SettingsNavRow(stringResource(R.string.settings_manage_tags), { onNavigate(TagManagementRoute) }, icon = Icons.Rounded.Label)
            SettingsNavRow(stringResource(R.string.settings_manage_custom_fields), { onNavigate(CustomFieldManagementRoute) }, icon = Icons.Rounded.Tune)
        }
    }
}

@Composable
private fun ToggleRows(specs: List<ToggleSpec>, settings: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit) {
    specs.forEach { spec ->
        SettingsSwitchRow(
            title = stringResource(spec.title),
            subtitle = spec.description?.let { stringResource(it) },
            checked = spec.get(settings),
            onCheckedChange = { on -> onUpdate { spec.set(it, on) } },
        )
    }
}
