package io.github.submark.feature.settings.ui.font

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.data.settings.AppSettings
import io.github.submark.core.data.settings.FontFamilyOption
import io.github.submark.core.data.settings.FontSettings
import io.github.submark.core.data.settings.FontSize
import io.github.submark.core.data.settings.FontTheme
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.core.ui.component.SegmentedTabs
import io.github.submark.core.ui.component.SettingsGroup
import io.github.submark.core.ui.component.SettingsSwitchRow
import io.github.submark.core.ui.theme.SubMarkTheme
import io.github.submark.feature.settings.R
import io.github.submark.feature.settings.theme.FontPresets
import io.github.submark.feature.settings.theme.applyPreset
import io.github.submark.feature.settings.theme.customize
import io.github.submark.feature.settings.theme.toThemeConfig
import io.github.submark.feature.settings.ui.common.AppSettingsViewModel
import io.github.submark.feature.settings.ui.common.SettingsPage
import io.github.submark.feature.settings.ui.common.descriptionRes
import io.github.submark.feature.settings.ui.common.labelRes
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun FontSettingsScreenRoute(onBack: () -> Unit, viewModel: AppSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    FontSettingsScreen(
        settings = settings,
        onBack = onBack,
        onFontChange = { transform -> viewModel.update { it.copy(font = transform(it.font)) } },
    )
}

@Composable
internal fun FontSettingsScreen(
    settings: AppSettings?,
    onBack: () -> Unit,
    onFontChange: ((FontSettings) -> FontSettings) -> Unit,
) {
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    SettingsPage(
        title = stringResource(R.string.settings_fonts_title),
        onBack = onBack,
        loading = settings == null,
        actions = {
            IconButton(onClick = { confirmReset = true }) {
                Icon(Icons.Rounded.RestartAlt, stringResource(R.string.settings_font_reset))
            }
        },
    ) {
        val s = settings ?: return@SettingsPage
        val font = s.font

        FontPreview(s)

        SettingsGroup(title = stringResource(R.string.settings_font_theme_group)) {
            Column(Modifier.selectableGroup()) {
                (FontPresets.themes + FontTheme.CUSTOM).forEach { theme ->
                    val isCustom = theme == FontTheme.CUSTOM
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(
                                selected = font.theme == theme,
                                enabled = !isCustom,
                                role = Role.RadioButton,
                                onClick = { onFontChange { it.applyPreset(theme) } },
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = font.theme == theme, onClick = null, enabled = !isCustom || font.theme == theme)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(stringResource(theme.labelRes), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(theme.descriptionRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        SettingsGroup(title = stringResource(R.string.settings_font_family_group)) {
            FlowRow(
                Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FontFamilyOption.entries.forEach { family ->
                    FilterChip(
                        selected = font.family == family,
                        onClick = { onFontChange { it.customize(family = family) } },
                        label = { Text(stringResource(family.labelRes)) },
                    )
                }
            }
        }

        SettingsGroup(
            title = stringResource(R.string.settings_font_size_group),
            footer = if (font.followSystemSize) stringResource(R.string.settings_font_follow_system_footer) else null,
        ) {
            SegmentedTabs(
                items = FontSize.entries,
                selected = font.size,
                onSelect = { size -> onFontChange { it.customize(size = size) } },
                label = { stringResource(it.labelRes) },
                modifier = Modifier.padding(16.dp),
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_font_follow_system),
                subtitle = stringResource(R.string.settings_font_follow_system_desc),
                icon = Icons.Rounded.FormatSize,
                checked = font.followSystemSize,
                onCheckedChange = { on -> onFontChange { it.copy(followSystemSize = on) } },
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_font_line_spacing_group)) {
            LineSpacingSlider(font.lineSpacing) { value -> onFontChange { it.customize(lineSpacing = value) } }
        }

        OutlinedButton(
            onClick = { confirmReset = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) { Text(stringResource(R.string.settings_font_reset)) }

        if (confirmReset) {
            ConfirmDialog(
                title = stringResource(R.string.settings_font_reset_title),
                message = stringResource(R.string.settings_font_reset_message),
                confirmLabel = stringResource(R.string.settings_font_reset),
                onConfirm = { onFontChange { FontSettings() }; confirmReset = false },
                onDismiss = { confirmReset = false },
            )
        }
    }
}

@Composable
private fun LineSpacingSlider(value: Float, onChange: (Float) -> Unit) {
    // Local state keeps the drag smooth; the setting is written when the drag ends.
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(value) }
    val shown = if (dragging) local else value
    val steps = ((FontPresets.MAX_LINE_SPACING - FontPresets.MIN_LINE_SPACING) / FontPresets.LINE_SPACING_STEP).roundToInt() - 1
    val label = String.format(Locale.ROOT, "%.2f×", shown)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_font_line_spacing_tight), style = MaterialTheme.typography.bodySmall)
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(stringResource(R.string.settings_font_line_spacing_loose), style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = shown,
            onValueChange = { dragging = true; local = it },
            onValueChangeFinished = { dragging = false; onChange(local) },
            valueRange = FontPresets.MIN_LINE_SPACING..FontPresets.MAX_LINE_SPACING,
            steps = steps,
            modifier = Modifier.semantics { stateDescription = label },
        )
    }
}

@Composable
private fun FontPreview(settings: AppSettings) {
    SettingsGroup(title = stringResource(R.string.settings_font_preview_group)) {
        SubMarkTheme(config = settings.toThemeConfig()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_font_preview_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.settings_font_preview_body), style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_font_preview_amount_label), style = MaterialTheme.typography.labelMedium)
                        Text(stringResource(R.string.settings_font_preview_amount), style = MaterialTheme.typography.headlineSmall)
                    }
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.settings_font_preview_tag)) })
                }
                Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_font_preview_button)) }
            }
        }
    }
}
