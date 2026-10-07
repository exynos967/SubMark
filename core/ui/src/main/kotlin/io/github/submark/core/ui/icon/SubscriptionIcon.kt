package io.github.submark.core.ui.icon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import io.github.submark.core.model.IconType
import io.github.submark.core.ui.theme.SubMarkTheme
import java.io.File

/** Icon reference as passed through navigation results ([io.github.submark.core.ui.navigation.NavResults.ICON]). */
data class IconChoice(val type: IconType, val value: String) {
    /** "TYPE|value". */
    fun encode(): String = "${type.name}|$value"

    companion object {
        fun decode(encoded: String?): IconChoice? {
            val idx = encoded?.indexOf('|') ?: return null
            if (idx <= 0) return null
            val type = IconType.entries.firstOrNull { it.name == encoded.substring(0, idx) } ?: return null
            val value = encoded.substring(idx + 1).takeIf { it.isNotEmpty() } ?: return null
            return IconChoice(type, value)
        }
    }
}

/** Directory (inside `filesDir`) holding FILE icons. */
const val ICON_DIR = "icons"

/** First letter (code point) of [name], upper-cased; "?" when blank. */
fun monogramOf(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "?"
    val end = trimmed.offsetByCodePoints(0, 1)
    return trimmed.substring(0, end).uppercase()
}

/**
 * Renders any subscription/category icon reference. Falls back to a monogram of [fallbackName] when
 * the reference is missing, unknown or fails to load.
 *
 * @param tint SYMBOL / monogram color.
 * @param background container color; [Color.Transparent] for a bare icon.
 */
@Composable
fun SubscriptionIcon(
    type: IconType?,
    value: String?,
    fallbackName: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    background: Color = MaterialTheme.colorScheme.primaryContainer,
    shape: Shape = RoundedCornerShape(size * 0.28f),
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        val monogram = @Composable { Monogram(fallbackName, size, tint) }
        when {
            value.isNullOrBlank() || type == null -> monogram()
            type == IconType.SYMBOL -> {
                val vector = IconCatalog.vector(value)
                if (vector != null) {
                    Icon(vector, contentDescription, tint = tint, modifier = Modifier.size(size * 0.58f))
                } else {
                    monogram()
                }
            }
            type == IconType.EMOJI -> {
                val fontSize = with(LocalDensity.current) { (size * 0.56f).toSp() }
                Text(text = value, fontSize = fontSize, textAlign = TextAlign.Center)
            }
            else -> {
                val model: Any = if (type == IconType.FILE) {
                    File(File(LocalContext.current.filesDir, ICON_DIR), value)
                } else {
                    value
                }
                RemoteIcon(model, contentDescription, size, monogram)
            }
        }
    }
}

@Composable
private fun RemoteIcon(model: Any, contentDescription: String?, size: Dp, fallback: @Composable () -> Unit) {
    var loaded by remember(model) { mutableStateOf(false) }
    if (!loaded) fallback()
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
        onState = { loaded = it is AsyncImagePainter.State.Success },
    )
}

@Composable
private fun Monogram(name: String, size: Dp, color: Color) {
    val fontSize = with(LocalDensity.current) { (size * 0.42f).toSp() }
    Text(
        text = monogramOf(name),
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(2.dp),
    )
}

@Preview
@Composable
private fun SubscriptionIconPreview() {
    SubMarkTheme {
        androidx.compose.foundation.layout.Row {
            SubscriptionIcon(IconType.SYMBOL, "movie", "Netflix")
            SubscriptionIcon(IconType.EMOJI, "🎵", "Music")
            SubscriptionIcon(null, null, "Spotify")
        }
    }
}
