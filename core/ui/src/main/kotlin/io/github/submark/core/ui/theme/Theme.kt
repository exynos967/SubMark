package io.github.submark.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color

@Composable
fun SubMarkTheme(
    config: ThemeConfig = ThemeConfig(),
    content: @Composable () -> Unit,
) {
    val dark = when (config.darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        config.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> BrandDarkColors
        else -> BrandLightColors
    }
    val typography = remember(config.fontFamily, config.fontScale, config.lineSpacing) { subMarkTypography(config) }
    val density = LocalDensity.current
    val effectiveDensity = if (config.followSystemFontScale) density else Density(density.density, fontScale = 1f)

    CompositionLocalProvider(
        LocalThemeConfig provides config,
        LocalExtendedColors provides if (dark) DarkExtendedColors else LightExtendedColors,
        LocalDensity provides effectiveDensity,
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
    }
}

/** Accessors for SubMark-specific theme values, mirroring `MaterialTheme.*`. */
object SubMarkTheme {
    val extendedColors: ExtendedColors
        @Composable @ReadOnlyComposable get() = LocalExtendedColors.current
    /** Categorical chart colors for the current light/dark mode. */
    val chartPalette: List<Color>
        @Composable @ReadOnlyComposable get() = if (LocalExtendedColors.current.isDark) ChartPaletteDark else ChartPaletteLight
    val config: ThemeConfig
        @Composable @ReadOnlyComposable get() = LocalThemeConfig.current
}
