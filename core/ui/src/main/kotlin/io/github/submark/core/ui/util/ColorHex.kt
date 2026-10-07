package io.github.submark.core.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Parses "#RGB", "#RRGGBB" or "#AARRGGBB" (leading # optional). Null for blank/invalid input. */
fun colorFromHex(hex: String?): Color? {
    val s = hex?.trim()?.removePrefix("#") ?: return null
    val argb = when (s.length) {
        3 -> s.map { "$it$it" }.joinToString("").toLongOrNull(16)?.or(0xFF000000)
        6 -> s.toLongOrNull(16)?.or(0xFF000000)
        8 -> s.toLongOrNull(16)
        else -> null
    } ?: return null
    return Color(argb.toInt())
}

/** "#RRGGBB" when opaque, otherwise "#AARRGGBB". Upper case. */
fun Color.toHex(): String {
    val argb = toArgb()
    val alpha = (argb ushr 24) and 0xFF
    return if (alpha == 0xFF) {
        "#%06X".format(argb and 0xFFFFFF)
    } else {
        "#%08X".format(argb)
    }
}

/** Black or white, whichever reads better on this background. */
fun Color.contentColorFor(): Color = if (luminanceApprox() > 0.55f) Color.Black else Color.White

internal fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
