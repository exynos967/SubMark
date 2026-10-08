package io.github.submark.feature.settings.theme

import io.github.submark.core.data.settings.AppSettings
import io.github.submark.core.data.settings.FontFamilyOption
import io.github.submark.core.data.settings.FontSize
import io.github.submark.core.data.settings.ThemeMode
import io.github.submark.core.ui.theme.AppFontFamily
import io.github.submark.core.ui.theme.DarkMode
import io.github.submark.core.ui.theme.ThemeConfig

/** Maps the persisted preferences to what `SubMarkTheme` needs. Used by the app shell and the font preview. */
fun AppSettings.toThemeConfig(): ThemeConfig = ThemeConfig(
    darkMode = display.theme.toDarkMode(),
    dynamicColor = display.dynamicColor,
    fontFamily = font.family.toAppFontFamily(),
    fontScale = font.size.scale,
    lineSpacing = font.lineSpacing.coerceIn(FontPresets.MIN_LINE_SPACING, FontPresets.MAX_LINE_SPACING),
    followSystemFontScale = font.followSystemSize,
    colorfulCards = display.colorfulMode,
)

fun ThemeMode.toDarkMode(): DarkMode = when (this) {
    ThemeMode.SYSTEM -> DarkMode.SYSTEM
    ThemeMode.LIGHT -> DarkMode.LIGHT
    ThemeMode.DARK -> DarkMode.DARK
}

/** No font files are bundled; CJK-optimized uses the platform default, which already ships Noto CJK. */
fun FontFamilyOption.toAppFontFamily(): AppFontFamily = when (this) {
    FontFamilyOption.SYSTEM, FontFamilyOption.CJK_OPTIMIZED -> AppFontFamily.SYSTEM
    FontFamilyOption.ROUNDED -> AppFontFamily.ROUNDED
    FontFamilyOption.SERIF -> AppFontFamily.SERIF
    FontFamilyOption.MONOSPACE -> AppFontFamily.MONOSPACE
}

val FontSize.scale: Float
    get() = when (this) {
        FontSize.SMALL -> 0.9f
        FontSize.MEDIUM -> 1.0f
        FontSize.LARGE -> 1.12f
        FontSize.EXTRA_LARGE -> 1.25f
    }
