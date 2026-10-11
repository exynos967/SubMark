package io.github.submark.core.data.seed

import io.github.submark.core.model.Category
import io.github.submark.core.model.IconType
import io.github.submark.core.model.SystemCategory

/** Preset categories with stable ids. Names are null so the UI shows the localized preset name. */
object SystemCategories {
    fun idOf(key: SystemCategory) = "cat_${key.name.lowercase()}"

    val OTHER_ID = idOf(SystemCategory.OTHER)

    private val style = mapOf(
        SystemCategory.VIDEO to ("movie" to "#E53935"),
        SystemCategory.MUSIC to ("music" to "#8E24AA"),
        SystemCategory.ENTERTAINMENT to ("theater" to "#FB8C00"),
        SystemCategory.GAMING to ("gamepad" to "#3949AB"),
        SystemCategory.PRODUCTIVITY to ("work" to "#1E88E5"),
        SystemCategory.UTILITY to ("build" to "#546E7A"),
        SystemCategory.AI to ("sparkle" to "#00ACC1"),
        SystemCategory.NEWS to ("newspaper" to "#6D4C41"),
        SystemCategory.LIFESTYLE to ("spa" to "#43A047"),
        SystemCategory.OTHER to ("category" to "#757575"),
    )

    /** Factory-state preset rows (visible, preset icon and color, preset order). */
    val defaults: List<Category> = SystemCategory.entries.mapIndexed { index, key ->
        val (icon, color) = style.getValue(key)
        Category(id = idOf(key), systemKey = key, iconType = IconType.SYMBOL, iconValue = icon, colorHex = color, sortOrder = index)
    }

    fun default(key: SystemCategory): Category = defaults.first { it.systemKey == key }
}
