package io.github.submark.core.ui.format

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Text that is either raw or a resource reference resolved at display time.
 * Arguments may themselves be [UiText]; they are resolved recursively.
 * Lets ViewModels and pure formatters produce localized text without a Context.
 */
sealed interface UiText {
    data class Raw(val value: String) : UiText

    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Plural(@PluralsRes val id: Int, val quantity: Int, val args: List<Any> = listOf(quantity)) : UiText

    /** [parts] joined with [separator]. */
    data class Concat(val parts: List<UiText>, val separator: String = "") : UiText

    fun asString(context: Context): String = when (this) {
        is Raw -> value
        is Res -> context.getString(id, *resolveArgs(context, args))
        is Plural -> context.resources.getQuantityString(id, quantity, *resolveArgs(context, args))
        is Concat -> parts.joinToString(separator) { it.asString(context) }
    }

    companion object {
        fun raw(value: String): UiText = Raw(value)
        fun res(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())
        fun plural(@PluralsRes id: Int, quantity: Int, vararg args: Any): UiText =
            Plural(id, quantity, if (args.isEmpty()) listOf(quantity) else args.toList())
    }
}

private fun resolveArgs(context: Context, args: List<Any>): Array<Any> =
    args.map { if (it is UiText) it.asString(context) else it }.toTypedArray()

@Composable
@ReadOnlyComposable
fun UiText.asString(): String {
    LocalConfiguration.current // re-resolve when locale/configuration changes
    return asString(LocalContext.current)
}
