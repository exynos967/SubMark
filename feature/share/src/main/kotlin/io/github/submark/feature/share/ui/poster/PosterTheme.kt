package io.github.submark.feature.share.ui.poster

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import io.github.submark.core.data.settings.PosterStyle

/** Background/foreground palette for each poster style. */
data class PosterPalette(
    val background: Brush,
    val card: Color,
    val onBackground: Color,
    val accent: Color,
)

fun posterPalette(style: PosterStyle, accent: Color): PosterPalette = when (style) {
    PosterStyle.MINIMAL -> PosterPalette(
        background = Brush.verticalGradient(listOf(Color.White, Color.White)),
        card = Color(0xFFF6F6F6),
        onBackground = Color(0xFF1A1A1A),
        accent = Color(0xFF1A1A1A),
    )
    PosterStyle.MODERN -> PosterPalette(
        background = Brush.verticalGradient(listOf(Color(0xFFF5F7FA), Color(0xFFE9EDF3))),
        card = Color.White,
        onBackground = Color(0xFF20242A),
        accent = accent,
    )
    PosterStyle.GRADIENT -> PosterPalette(
        background = Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.55f), Color(0xFF22262D))),
        card = Color(0x33FFFFFF),
        onBackground = Color.White,
        accent = Color.White,
    )
    PosterStyle.COLORFUL -> PosterPalette(
        background = Brush.linearGradient(
            listOf(
                Color(0xFFFF9A9E),
                Color(0xFFFAD0C4),
                Color(0xFFA18CD1),
                Color(0xFF84FAB0),
            ),
        ),
        card = Color(0xE6FFFFFF),
        onBackground = Color(0xFF332B4D),
        accent = accent,
    )
}

/** Accent inferred from the category color, defaulting to a brand-ish blue. */
val FallbackAccent = Color(0xFF3D5AF1)
