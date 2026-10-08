package io.github.submark.feature.subscriptions.ui.manage

import io.github.submark.core.data.repository.CustomFieldWithOptions
import io.github.submark.core.data.repository.TagFolderWithTags
import io.github.submark.core.data.repository.TagWithUsage
import io.github.submark.core.data.result.InvalidReason
import io.github.submark.core.model.CustomFieldType
import io.github.submark.core.model.IconType
import io.github.submark.core.ui.icon.IconCatalog

/** Pure helpers shared by the category, tag/folder and custom-field management screens. */
object ManageLogic {

    /** [this] with the element at [from] moved to [to]; out-of-range indices return the list unchanged. */
    fun <T> List<T>.moved(from: Int, to: Int): List<T> {
        if (from !in indices || to !in indices || from == to) return this
        return toMutableList().apply { add(to, removeAt(from)) }
    }

    /**
     * Interprets a manually typed icon: a catalogue name becomes SYMBOL, anything else that contains no
     * letters or digits (an emoji) becomes EMOJI. Returns null for blank or unrecognised input.
     */
    fun parseManualIcon(text: String): Pair<IconType, String>? {
        val t = text.trim()
        if (t.isEmpty()) return null
        IconCatalog[t]?.let { return IconType.SYMBOL to it.name }
        if (t.none { it.isLetterOrDigit() } && t.codePointCount(0, t.length) <= MAX_EMOJI_CODE_POINTS) return IconType.EMOJI to t
        return null
    }

    private const val MAX_EMOJI_CODE_POINTS = 8

    /** Folders persist only an icon value: catalogue names render as symbols, anything else as emoji. */
    fun folderIconType(value: String?): IconType? = when {
        value.isNullOrBlank() -> null
        IconCatalog[value] != null -> IconType.SYMBOL
        else -> IconType.EMOJI
    }

    /** Tags whose name contains [query] (case-insensitive), sorted by name. */
    fun filterTags(tags: List<TagWithUsage>, query: String): List<TagWithUsage> {
        val q = query.trim()
        return tags.filter { q.isEmpty() || it.tag.name.contains(q, ignoreCase = true) }.sortedBy { it.tag.name.lowercase() }
    }

    fun filterFolders(folders: List<TagFolderWithTags>, query: String): List<TagFolderWithTags> {
        val q = query.trim()
        return folders.filter { q.isEmpty() || it.folder.name.contains(q, ignoreCase = true) }
    }

    /** Number of subscriptions (id -> tag ids) matched by an enabled folder; disabled folders still report what they would match. */
    fun folderMatchCount(folder: TagFolderWithTags, subscriptionTags: Collection<Set<String>>): Int =
        subscriptionTags.count { io.github.submark.core.data.repository.TagFolderMatcher.matches(folder.folder.matchMode, folder.tagIds, it) }

    // ---- custom fields ----

    sealed interface FieldFilter {
        data object All : FieldFilter
        data object Global : FieldFilter
        data class Category(val id: String) : FieldFilter
    }

    fun filterFields(fields: List<CustomFieldWithOptions>, filter: FieldFilter): List<CustomFieldWithOptions> {
        val sorted = fields.sortedBy { it.definition.sortOrder }
        return when (filter) {
            FieldFilter.All -> sorted
            FieldFilter.Global -> sorted.filter { it.definition.categoryId == null }
            is FieldFilter.Category -> sorted.filter { it.definition.categoryId == filter.id }
        }
    }

    data class OptionDraft(val id: String, val label: String)

    /** Options to show when the editor switches to DROPDOWN with none yet. */
    fun defaultOptions(labelFor: (Int) -> String, newId: () -> String): List<OptionDraft> =
        listOf(OptionDraft(newId(), labelFor(1)), OptionDraft(newId(), labelFor(2)))

    /** First validation problem of a field editor, or null when it can be saved. */
    fun validateField(name: String, type: CustomFieldType, options: List<OptionDraft>): InvalidReason? = when {
        name.isBlank() -> InvalidReason.BLANK_NAME
        type == CustomFieldType.DROPDOWN && options.none { it.label.isNotBlank() } -> InvalidReason.DROPDOWN_NEEDS_OPTION
        else -> null
    }

    /** Blank-to-null for optional text inputs. */
    fun String.blankToNull(): String? = trim().takeIf { it.isNotEmpty() }
}
