package io.github.submark.feature.settings.ui.appearance

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AppShortcut
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.data.settings.AppLanguage
import io.github.submark.core.data.settings.AppSettings
import io.github.submark.core.data.settings.ThemeMode
import io.github.submark.core.ui.component.SettingsGroup
import io.github.submark.core.ui.component.SettingsNavRow
import io.github.submark.core.ui.component.SettingsSwitchRow
import io.github.submark.core.ui.component.SettingsValueRow
import io.github.submark.core.ui.navigation.AppIconRoute
import io.github.submark.core.ui.navigation.FontSettingsRoute
import io.github.submark.feature.settings.R
import io.github.submark.feature.settings.ui.common.AppSettingsViewModel
import io.github.submark.feature.settings.ui.common.ChoiceDialog
import io.github.submark.feature.settings.ui.common.SettingsPage
import io.github.submark.feature.settings.ui.common.labelRes

@Composable
internal fun AppearanceScreenRoute(
    onBack: () -> Unit,
    onNavigate: (Any) -> Unit,
    viewModel: AppSettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    AppearanceScreen(
        settings = settings,
        language = language ?: settings?.display?.language ?: AppLanguage.SYSTEM,
        onBack = onBack,
        onNavigate = onNavigate,
        onLanguage = viewModel::setLanguage,
        onUpdate = viewModel::update,
    )
}

private enum class AppearanceDialog { LANGUAGE, THEME }

@Composable
internal fun AppearanceScreen(
    settings: AppSettings?,
    language: AppLanguage,
    onBack: () -> Unit,
    onNavigate: (Any) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<AppearanceDialog?>(null) }
    SettingsPage(title = stringResource(R.string.settings_appearance_title), onBack = onBack, loading = settings == null) {
        val s = settings ?: return@SettingsPage
        val display = s.display
        SettingsGroup(title = stringResource(R.string.settings_appearance_language_group)) {
            SettingsValueRow(
                title = stringResource(R.string.settings_language),
                value = stringResource(language.labelRes),
                icon = Icons.Rounded.Language,
                onClick = { dialog = AppearanceDialog.LANGUAGE },
            )
        }
        SettingsGroup(title = stringResource(R.string.settings_appearance_display_group)) {
            SettingsValueRow(
                title = stringResource(R.string.settings_theme),
                value = stringResource(display.theme.labelRes),
                icon = Icons.Rounded.DarkMode,
                onClick = { dialog = AppearanceDialog.THEME },
            )
            val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            SettingsSwitchRow(
                title = stringResource(R.string.settings_dynamic_color),
                subtitle = stringResource(
                    if (dynamicSupported) R.string.settings_dynamic_color_desc else R.string.settings_dynamic_color_unsupported,
                ),
                icon = Icons.Rounded.ColorLens,
                checked = display.dynamicColor && dynamicSupported,
                enabled = dynamicSupported,
                onCheckedChange = { on -> onUpdate { it.copy(display = it.display.copy(dynamicColor = on)) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_colorful_cards),
                subtitle = stringResource(R.string.settings_colorful_cards_desc),
                icon = Icons.Rounded.Gradient,
                checked = display.colorfulMode,
                onCheckedChange = { on -> onUpdate { it.copy(display = it.display.copy(colorfulMode = on)) } },
            )
            SettingsNavRow(
                title = stringResource(R.string.settings_fonts_title),
                value = stringResource(s.font.theme.labelRes),
                icon = Icons.Rounded.TextFields,
                onClick = { onNavigate(FontSettingsRoute) },
            )
            SettingsNavRow(
                title = stringResource(R.string.settings_app_icon_title),
                icon = Icons.Rounded.AppShortcut,
                onClick = { onNavigate(AppIconRoute) },
            )
        }
        SettingsGroup(title = stringResource(R.string.settings_appearance_interaction_group)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_animations),
                subtitle = stringResource(R.string.settings_animations_desc),
                icon = Icons.Rounded.Animation,
                checked = display.animatedBackground,
                onCheckedChange = { on -> onUpdate { it.copy(display = it.display.copy(animatedBackground = on)) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_haptics),
                subtitle = stringResource(R.string.settings_haptics_desc),
                icon = Icons.Rounded.Vibration,
                checked = display.haptics,
                onCheckedChange = { on -> onUpdate { it.copy(display = it.display.copy(haptics = on)) } },
            )
        }

        when (dialog) {
            AppearanceDialog.LANGUAGE -> ChoiceDialog(
                title = stringResource(R.string.settings_language),
                options = AppLanguage.entries,
                selected = language,
                label = { stringResource(it.labelRes) },
                onSelect = onLanguage,
                onDismiss = { dialog = null },
            )
            AppearanceDialog.THEME -> ChoiceDialog(
                title = stringResource(R.string.settings_theme),
                options = ThemeMode.entries,
                selected = display.theme,
                label = { stringResource(it.labelRes) },
                onSelect = { mode -> onUpdate { it.copy(display = it.display.copy(theme = mode)) } },
                onDismiss = { dialog = null },
            )
            null -> Unit
        }
    }
}
