package io.github.submark.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.submark.core.ui.R
import io.github.submark.core.ui.theme.SubMarkTheme
import io.github.submark.core.ui.util.contentColorFor
import io.github.submark.core.ui.util.toHex

/** Preset swatches offered by [ColorPickerDialog]. */
val DefaultColorPresets: List<Color> = listOf(
    0xFFE5484D, 0xFFF76B15, 0xFFFFB224, 0xFFF5D90A, 0xFF99D52A, 0xFF30A46C,
    0xFF12A594, 0xFF05A2C2, 0xFF0090FF, 0xFF3E63DD, 0xFF6E56CF, 0xFF8E4EC6,
    0xFFD6409F, 0xFFE93D82, 0xFFAD7F58, 0xFF8B8D98, 0xFF4A4A55, 0xFF1C1C21,
).map { Color(it.toInt()) }

/**
 * Preset + custom HSV color picker.
 * [onConfirm] receives the chosen color, or null for "use default" (only offered when [allowDefault]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    initial: Color?,
    onConfirm: (Color?) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.ui_color_picker_title),
    presets: List<Color> = DefaultColorPresets,
    allowDefault: Boolean = true,
) {
    val start = initial ?: presets.firstOrNull() ?: Color.Blue
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(start.toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var brightness by remember { mutableFloatStateOf(hsv[2]) }
    var tab by remember { mutableIntStateOf(if (initial == null || initial in presets) 0 else 1) }
    val current = Color.hsv(hue, saturation, brightness)

    fun select(color: Color) {
        val out = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), out)
        hue = out[0]; saturation = out[1]; brightness = out[2]
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(40.dp).background(current, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                    Text(current.toHex(), style = MaterialTheme.typography.bodyLarge)
                }
                SegmentedTabs(
                    items = listOf(0, 1),
                    selected = tab,
                    onSelect = { tab = it },
                    label = { stringResource(if (it == 0) R.string.ui_color_presets else R.string.ui_color_custom) },
                )
                if (tab == 0) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.forEach { preset ->
                            val isSelected = preset.toArgb() == current.toArgb()
                            val hex = preset.toHex()
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(preset, CircleShape)
                                    .semantics {
                                        contentDescription = hex
                                        selected = isSelected
                                    }
                                    .clickable(role = Role.RadioButton) { select(preset) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) Icon(Icons.Rounded.Check, null, tint = preset.contentColorFor(), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                } else {
                    HsvSlider(
                        label = stringResource(R.string.ui_color_hue),
                        value = hue / 360f,
                        onChange = { hue = it * 360f },
                        brush = Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }),
                    )
                    HsvSlider(
                        label = stringResource(R.string.ui_color_saturation),
                        value = saturation,
                        onChange = { saturation = it },
                        brush = Brush.horizontalGradient(listOf(Color.hsv(hue, 0f, brightness), Color.hsv(hue, 1f, brightness))),
                    )
                    HsvSlider(
                        label = stringResource(R.string.ui_color_brightness),
                        value = brightness,
                        onChange = { brightness = it },
                        brush = Brush.horizontalGradient(listOf(Color.Black, Color.hsv(hue, saturation, 1f))),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current) }) { Text(stringResource(R.string.ui_action_done)) }
        },
        dismissButton = {
            Row {
                if (allowDefault) {
                    TextButton(onClick = { onConfirm(null) }) { Text(stringResource(R.string.ui_color_use_default)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_action_cancel)) }
            }
        },
    )
}

@Composable
private fun HsvSlider(label: String, value: Float, onChange: (Float) -> Unit, brush: Brush) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(10.dp).padding(horizontal = 10.dp).background(brush, RoundedCornerShape(5.dp)))
            Slider(
                value = value.coerceIn(0f, 1f),
                onValueChange = onChange,
                colors = SliderDefaults.colors(
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    thumbColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    }
}

@Preview
@Composable
private fun ColorPickerPreview() {
    SubMarkTheme { ColorPickerDialog(initial = Color(0xFF3E63DD), onConfirm = {}, onDismiss = {}) }
}
