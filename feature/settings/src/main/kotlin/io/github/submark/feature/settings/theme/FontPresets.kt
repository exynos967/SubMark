package io.github.submark.feature.settings.theme

import io.github.submark.core.data.settings.FontFamilyOption
import io.github.submark.core.data.settings.FontSettings
import io.github.submark.core.data.settings.FontSize
import io.github.submark.core.data.settings.FontTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** Family / size / line-spacing tuple a [FontTheme] stands for. */
data class FontPreset(val family: FontFamilyOption, val size: FontSize, val lineSpacing: Float)

object FontPresets {
    const val MIN_LINE_SPACING = 0.9f
    const val MAX_LINE_SPACING = 1.5f
    const val LINE_SPACING_STEP = 0.05f

    private val presets: Map<FontTheme, FontPreset> = mapOf(
        FontTheme.MODERN to FontPreset(FontFamilyOption.SYSTEM, FontSize.MEDIUM, 1.0f),
        FontTheme.COMFORTABLE to FontPreset(FontFamilyOption.SYSTEM, FontSize.LARGE, 1.3f),
        FontTheme.COMPACT to FontPreset(FontFamilyOption.SYSTEM, FontSize.SMALL, 0.9f),
        FontTheme.ELEGANT to FontPreset(FontFamilyOption.SERIF, FontSize.MEDIUM, 1.15f),
        FontTheme.ACCESSIBLE to FontPreset(FontFamilyOption.SYSTEM, FontSize.EXTRA_LARGE, 1.4f),
    )

    /** Selectable presets in display order ([FontTheme.CUSTOM] is derived, never picked). */
    val themes: List<FontTheme> = presets.keys.toList()

    /** null for [FontTheme.CUSTOM]. */
    fun of(theme: FontTheme): FontPreset? = presets[theme]

    /** The preset matching the tuple exactly, otherwise [FontTheme.CUSTOM]. */
    fun themeFor(family: FontFamilyOption, size: FontSize, lineSpacing: Float): FontTheme =
        presets.entries.firstOrNull { (_, p) ->
            p.family == family && p.size == size && abs(p.lineSpacing - lineSpacing) < 0.001f
        }?.key ?: FontTheme.CUSTOM

    /** Snaps a slider value to the 0.05 grid inside the allowed range. */
    fun normalizeLineSpacing(value: Float): Float {
        val clamped = value.coerceIn(MIN_LINE_SPACING, MAX_LINE_SPACING)
        return (clamped / LINE_SPACING_STEP).roundToInt() * LINE_SPACING_STEP
    }
}

fun FontSettings.applyPreset(theme: FontTheme): FontSettings {
    val preset = FontPresets.of(theme) ?: return copy(theme = FontTheme.CUSTOM)
    return copy(theme = theme, family = preset.family, size = preset.size, lineSpacing = preset.lineSpacing)
}

/** Changes one or more of the tuple values and re-derives the theme (custom unless it matches a preset). */
fun FontSettings.customize(
    family: FontFamilyOption = this.family,
    size: FontSize = this.size,
    lineSpacing: Float = this.lineSpacing,
): FontSettings {
    val spacing = FontPresets.normalizeLineSpacing(lineSpacing)
    return copy(family = family, size = size, lineSpacing = spacing, theme = FontPresets.themeFor(family, size, spacing))
}
