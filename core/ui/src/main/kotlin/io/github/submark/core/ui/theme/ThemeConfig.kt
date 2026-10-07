package io.github.submark.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

enum class DarkMode { SYSTEM, LIGHT, DARK }

/** ROUNDED maps to the platform sans-serif because no font files are bundled. */
enum class AppFontFamily { SYSTEM, SERIF, MONOSPACE, ROUNDED }

/**
 * Everything the theme needs from user preferences. Built by the app from settings and passed to [SubMarkTheme].
 *
 * @param dynamicColor Material You colors on API 31+; ignored (brand palette) below.
 * @param fontScale multiplier applied to every typography size.
 * @param lineSpacing multiplier applied to every line height.
 * @param followSystemFontScale false = ignore the system font size setting and use only [fontScale].
 * @param colorfulCards gradient subscription cards tinted by their accent color.
 */
@Immutable
data class ThemeConfig(
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val fontFamily: AppFontFamily = AppFontFamily.SYSTEM,
    val fontScale: Float = 1f,
    val lineSpacing: Float = 1f,
    val followSystemFontScale: Boolean = true,
    val colorfulCards: Boolean = false,
)

val LocalThemeConfig = staticCompositionLocalOf { ThemeConfig() }
