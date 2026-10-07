package io.github.submark.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified

internal fun AppFontFamily.toFontFamily(): FontFamily = when (this) {
    AppFontFamily.SYSTEM -> FontFamily.Default
    AppFontFamily.SERIF -> FontFamily.Serif
    AppFontFamily.MONOSPACE -> FontFamily.Monospace
    AppFontFamily.ROUNDED -> FontFamily.SansSerif
}

/** Material 3 default type scale adjusted by the user's font family, size and line spacing. */
fun subMarkTypography(config: ThemeConfig): Typography {
    val family = config.fontFamily.toFontFamily()
    val size = config.fontScale.coerceIn(0.5f, 2.5f)
    val spacing = config.lineSpacing.coerceIn(0.5f, 2.5f)
    fun TextStyle.adjust() = copy(
        fontFamily = family,
        fontSize = fontSize.scaled(size),
        lineHeight = lineHeight.scaled(size * spacing),
    )
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.adjust(),
        displayMedium = base.displayMedium.adjust(),
        displaySmall = base.displaySmall.adjust(),
        headlineLarge = base.headlineLarge.adjust(),
        headlineMedium = base.headlineMedium.adjust(),
        headlineSmall = base.headlineSmall.adjust(),
        titleLarge = base.titleLarge.adjust(),
        titleMedium = base.titleMedium.adjust(),
        titleSmall = base.titleSmall.adjust(),
        bodyLarge = base.bodyLarge.adjust(),
        bodyMedium = base.bodyMedium.adjust(),
        bodySmall = base.bodySmall.adjust(),
        labelLarge = base.labelLarge.adjust(),
        labelMedium = base.labelMedium.adjust(),
        labelSmall = base.labelSmall.adjust(),
    )
}

private fun TextUnit.scaled(factor: Float): TextUnit = if (isSpecified) this * factor else this
