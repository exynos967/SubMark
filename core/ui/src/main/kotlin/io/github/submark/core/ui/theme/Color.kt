package io.github.submark.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Brand palette: a calm blue primary with slate secondary and teal tertiary. Used when dynamic color is off/unavailable. */
internal val BrandLightColors = lightColorScheme(
    primary = Color(0xFF2C5DB8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF001945),
    secondary = Color(0xFF575E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBE2F9),
    onSecondaryContainer = Color(0xFF141B2C),
    tertiary = Color(0xFF00687A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFADECFF),
    onTertiaryContainer = Color(0xFF001F26),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF757780),
    outlineVariant = Color(0xFFC5C6D0),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F7),
    inversePrimary = Color(0xFFB0C6FF),
    surfaceTint = Color(0xFF2C5DB8),
    surfaceBright = Color(0xFFFAF8FF),
    surfaceDim = Color(0xFFDAD9E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F3FA),
    surfaceContainer = Color(0xFFEEEDF4),
    surfaceContainerHigh = Color(0xFFE8E7EF),
    surfaceContainerHighest = Color(0xFFE2E2E9),
)

internal val BrandDarkColors = darkColorScheme(
    primary = Color(0xFFB0C6FF),
    onPrimary = Color(0xFF002D6F),
    primaryContainer = Color(0xFF12449E),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF293041),
    secondaryContainer = Color(0xFF3F4759),
    onSecondaryContainer = Color(0xFFDBE2F9),
    tertiary = Color(0xFF85D2E7),
    onTertiary = Color(0xFF003640),
    tertiaryContainer = Color(0xFF004E5C),
    onTertiaryContainer = Color(0xFFADECFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    outline = Color(0xFF8F9099),
    outlineVariant = Color(0xFF44474F),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2F3036),
    inversePrimary = Color(0xFF2C5DB8),
    surfaceTint = Color(0xFFB0C6FF),
    surfaceBright = Color(0xFF38393F),
    surfaceDim = Color(0xFF121318),
    surfaceContainerLowest = Color(0xFF0D0E13),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF292A2F),
    surfaceContainerHighest = Color(0xFF34343A),
)

/** Semantic colors Material 3 does not define (success / warning). Overdue uses `colorScheme.error`. */
@Immutable
data class ExtendedColors(
    val isDark: Boolean,
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

internal val LightExtendedColors = ExtendedColors(
    isDark = false,
    success = Color(0xFF2E7D32),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFC8F0C4),
    onSuccessContainer = Color(0xFF002106),
    warning = Color(0xFF9A5B00),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFDDB5),
    onWarningContainer = Color(0xFF2E1800),
)

internal val DarkExtendedColors = ExtendedColors(
    isDark = true,
    success = Color(0xFF8FD88A),
    onSuccess = Color(0xFF00390D),
    successContainer = Color(0xFF1B5E20),
    onSuccessContainer = Color(0xFFC8F0C4),
    warning = Color(0xFFFFB95C),
    onWarning = Color(0xFF4B2800),
    warningContainer = Color(0xFF6B3F00),
    onWarningContainer = Color(0xFFFFDDB5),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/**
 * Categorical chart palette (validated for color-vision deficiency on adjacent pairs, light and dark steps).
 * Assign in this fixed order by entity; fold anything past slot 8 into an "Other" bucket using [ChartOtherColor].
 */
val ChartPaletteLight: List<Color> = listOf(
    0xFF2A78D6, 0xFFEB6834, 0xFF1BAF7A, 0xFFEDA100, 0xFFE87BA4, 0xFF008300, 0xFF4A3AA7, 0xFFE34948,
).map { Color(it.toInt()) }

val ChartPaletteDark: List<Color> = listOf(
    0xFF3987E5, 0xFFD95926, 0xFF199E70, 0xFFC98500, 0xFFD55181, 0xFF008300, 0xFF9085E9, 0xFFE66767,
).map { Color(it.toInt()) }

val ChartOtherColor = Color(0xFF8B8D98)
