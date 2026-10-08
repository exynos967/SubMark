package io.github.submark.feature.subscriptions.ui.manage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.FormatColorReset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.submark.core.model.IconType
import io.github.submark.core.ui.component.ColorPickerDialog
import io.github.submark.core.ui.component.DefaultColorPresets
import io.github.submark.core.ui.icon.SubscriptionIcon
import io.github.submark.core.ui.util.colorFromHex
import io.github.submark.core.ui.util.contentColorFor
import io.github.submark.core.ui.util.toHex
import io.github.submark.feature.subscriptions.R

/** Small uppercase-ish label used to title editor sections. */
@Composable
internal fun EditorSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp),
    )
}

/**
 * Icon preview + "Choose icon" (opens the shared icon picker) + manual entry of a catalogue name or emoji.
 * [onClear] adds a "No icon" action for optional icons.
 */
@Composable
internal fun IconEditor(
    iconType: IconType?,
    iconValue: String?,
    fallbackName: String,
    accent: Color,
    onPick: () -> Unit,
    onManual: (IconType, String) -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
) {
    var manual by rememberSaveable { mutableStateOf("") }
    val parsed = ManageLogic.parseManualIcon(manual)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SubscriptionIcon(
                type = iconType,
                value = iconValue,
                fallbackName = fallbackName.ifBlank { "?" },
                size = 48.dp,
                tint = accent,
                background = accent.copy(alpha = 0.16f),
            )
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onPick) { Text(stringResource(R.string.subscriptions_manage_icon_choose)) }
            if (onClear != null && iconValue != null) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.subscriptions_manage_icon_clear)) }
            }
        }
        OutlinedTextField(
            value = manual,
            onValueChange = { manual = it },
            label = { Text(stringResource(R.string.subscriptions_manage_icon_manual_label)) },
            supportingText = {
                Text(
                    when {
                        manual.isBlank() -> stringResource(R.string.subscriptions_manage_icon_manual_hint)
                        parsed == null -> stringResource(R.string.subscriptions_manage_icon_manual_invalid)
                        else -> stringResource(R.string.subscriptions_manage_icon_manual_valid)
                    },
                )
            },
            isError = manual.isNotBlank() && parsed == null,
            singleLine = true,
            trailingIcon = {
                TextButton(
                    onClick = {
                        parsed?.let { (type, value) -> onManual(type, value) }
                        manual = ""
                    },
                    enabled = parsed != null,
                ) { Text(stringResource(R.string.subscriptions_manage_icon_apply)) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Preset swatches plus a custom color button. [selectedHex] null = default color (only selectable when
 * [allowDefault]); [onSelect] receives "#RRGGBB" or null.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColorSwatches(
    selectedHex: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    allowDefault: Boolean = false,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val selected = colorFromHex(selectedHex)
    val isCustom = selected != null && DefaultColorPresets.none { it.toHex() == selected.toHex() }
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (allowDefault) {
            val label = stringResource(R.string.subscriptions_manage_color_default)
            SwatchCircle(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                selected = selected == null,
                description = label,
                onClick = { onSelect(null) },
            ) { Icon(Icons.Rounded.FormatColorReset, contentDescription = null, modifier = Modifier.size(18.dp)) }
        }
        DefaultColorPresets.forEach { color ->
            val hex = color.toHex()
            SwatchCircle(color, selected = selected?.toHex() == hex, description = hex, onClick = { onSelect(hex) })
        }
        val customLabel = stringResource(R.string.subscriptions_manage_color_custom)
        SwatchCircle(
            color = if (isCustom) selected!! else MaterialTheme.colorScheme.surfaceContainerHighest,
            selected = isCustom,
            description = customLabel,
            onClick = { showPicker = true },
        ) {
            if (!isCustom) Icon(Icons.Rounded.Colorize, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
    if (showPicker) {
        ColorPickerDialog(
            initial = selected,
            onConfirm = { color ->
                showPicker = false
                if (color != null || allowDefault) onSelect(color?.toHex())
            },
            onDismiss = { showPicker = false },
            allowDefault = allowDefault,
        )
    }
}

@Composable
private fun SwatchCircle(
    color: Color,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit = {},
) {
    Box(
        Modifier
            .size(36.dp)
            .background(color, CircleShape)
            .border(
                if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                CircleShape,
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = description
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = color.contentColorFor(), modifier = Modifier.size(18.dp))
        } else {
            content()
        }
    }
}
