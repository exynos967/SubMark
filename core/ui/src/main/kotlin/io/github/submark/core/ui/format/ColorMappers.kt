package io.github.submark.core.ui.format

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import io.github.submark.core.model.Category
import io.github.submark.core.model.TagColor
import io.github.submark.core.ui.R
import io.github.submark.core.ui.theme.LocalExtendedColors

/** Tag palette tuned separately for light and dark surfaces. */
fun TagColor.color(dark: Boolean = false): Color = Color(
    if (dark) {
        when (this) {
            TagColor.BLUE -> 0xFF4D8EFF
            TagColor.BROWN -> 0xFFB89670
            TagColor.CYAN -> 0xFF5CCFF5
            TagColor.GRAY -> 0xFF9A9AA2
            TagColor.GREEN -> 0xFF4CCB6E
            TagColor.INDIGO -> 0xFF7C7AF0
            TagColor.MINT -> 0xFF5EDDD4
            TagColor.ORANGE -> 0xFFFFA23E
            TagColor.PINK -> 0xFFFF6B8A
            TagColor.PURPLE -> 0xFFC27AF0
            TagColor.RED -> 0xFFFF6259
            TagColor.TEAL -> 0xFF4FC6D8
            TagColor.YELLOW -> 0xFFF5D142
        }
    } else {
        when (this) {
            TagColor.BLUE -> 0xFF1F6FEB
            TagColor.BROWN -> 0xFF8D6B45
            TagColor.CYAN -> 0xFF1B9BC9
            TagColor.GRAY -> 0xFF75757E
            TagColor.GREEN -> 0xFF23A047
            TagColor.INDIGO -> 0xFF4F4CC9
            TagColor.MINT -> 0xFF0FA89F
            TagColor.ORANGE -> 0xFFE07B00
            TagColor.PINK -> 0xFFE0325A
            TagColor.PURPLE -> 0xFF9A45C8
            TagColor.RED -> 0xFFD93A30
            TagColor.TEAL -> 0xFF1E95A8
            TagColor.YELLOW -> 0xFFC69A00
        }
    }.toInt(),
)

@get:StringRes
val TagColor.labelRes: Int
    get() = when (this) {
        TagColor.BLUE -> R.string.ui_color_blue
        TagColor.BROWN -> R.string.ui_color_brown
        TagColor.CYAN -> R.string.ui_color_cyan
        TagColor.GRAY -> R.string.ui_color_gray
        TagColor.GREEN -> R.string.ui_color_green
        TagColor.INDIGO -> R.string.ui_color_indigo
        TagColor.MINT -> R.string.ui_color_mint
        TagColor.ORANGE -> R.string.ui_color_orange
        TagColor.PINK -> R.string.ui_color_pink
        TagColor.PURPLE -> R.string.ui_color_purple
        TagColor.RED -> R.string.ui_color_red
        TagColor.TEAL -> R.string.ui_color_teal
        TagColor.YELLOW -> R.string.ui_color_yellow
    }

/** Theme-aware [TagColor.color]. */
@Composable
@ReadOnlyComposable
fun TagColor.themedColor(): Color = color(dark = LocalExtendedColors.current.isDark)

/** User-given name, else the localized preset name. */
fun Category.displayName(): UiText = when {
    !name.isNullOrBlank() -> UiText.raw(name!!)
    systemKey != null -> UiText.res(systemKey!!.labelRes)
    else -> UiText.raw("")
}
