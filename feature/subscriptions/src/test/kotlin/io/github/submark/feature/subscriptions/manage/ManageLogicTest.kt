package io.github.submark.feature.subscriptions.manage

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.data.repository.CustomFieldWithOptions
import io.github.submark.core.data.repository.TagFolderWithTags
import io.github.submark.core.data.repository.TagWithUsage
import io.github.submark.core.data.result.InvalidReason
import io.github.submark.core.model.CustomFieldDefinition
import io.github.submark.core.model.CustomFieldType
import io.github.submark.core.model.IconType
import io.github.submark.core.model.Tag
import io.github.submark.core.model.TagFolder
import io.github.submark.core.model.TagMatchMode
import io.github.submark.feature.subscriptions.ui.manage.ManageLogic
import io.github.submark.feature.subscriptions.ui.manage.ManageLogic.FieldFilter
import io.github.submark.feature.subscriptions.ui.manage.ManageLogic.OptionDraft
import io.github.submark.feature.subscriptions.ui.manage.ManageLogic.blankToNull
import io.github.submark.feature.subscriptions.ui.manage.ManageLogic.moved
import org.junit.Test
import java.time.Instant

class ManageLogicTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun moved_reordersAndIgnoresInvalidIndices() {
        val list = listOf("a", "b", "c", "d")
        assertThat(list.moved(0, 2)).containsExactly("b", "c", "a", "d").inOrder()
        assertThat(list.moved(3, 0)).containsExactly("d", "a", "b", "c").inOrder()
        assertThat(list.moved(1, 1)).isSameInstanceAs(list)
        assertThat(list.moved(-1, 2)).isSameInstanceAs(list)
        assertThat(list.moved(0, 4)).isSameInstanceAs(list)
    }

    @Test
    fun parseManualIcon_recognisesCatalogueNamesAndEmoji() {
        assertThat(ManageLogic.parseManualIcon("  movie ")).isEqualTo(IconType.SYMBOL to "movie")
        assertThat(ManageLogic.parseManualIcon("🎬")).isEqualTo(IconType.EMOJI to "🎬")
        assertThat(ManageLogic.parseManualIcon("")).isNull()
        assertThat(ManageLogic.parseManualIcon("not-an-icon-name")).isNull()
    }

    @Test
    fun folderIconType_symbolForCatalogueNamesElseEmoji() {
        assertThat(ManageLogic.folderIconType(null)).isNull()
        assertThat(ManageLogic.folderIconType("movie")).isEqualTo(IconType.SYMBOL)
        assertThat(ManageLogic.folderIconType("📁")).isEqualTo(IconType.EMOJI)
    }

    @Test
    fun filterTags_matchesCaseInsensitiveAndSortsByName() {
        val tags = listOf(usage("Work"), usage("family"), usage("Fun"))
        assertThat(ManageLogic.filterTags(tags, "").map { it.tag.name }).containsExactly("family", "Fun", "Work").inOrder()
        assertThat(ManageLogic.filterTags(tags, " FA ").map { it.tag.name }).containsExactly("family")
    }

    @Test
    fun folderMatchCount_respectsMatchMode() {
        val subs = listOf(setOf("a"), setOf("a", "b"), setOf("c"), emptySet())
        assertThat(ManageLogic.folderMatchCount(folder(TagMatchMode.ANY, "a", "b"), subs)).isEqualTo(2)
        assertThat(ManageLogic.folderMatchCount(folder(TagMatchMode.ALL, "a", "b"), subs)).isEqualTo(1)
        assertThat(ManageLogic.folderMatchCount(folder(TagMatchMode.ANY), subs)).isEqualTo(0)
    }

    @Test
    fun filterFields_byScopeAndSortOrder() {
        val fields = listOf(field("g2", null, 2), field("c1", "cat", 1), field("g0", null, 0))
        assertThat(ManageLogic.filterFields(fields, FieldFilter.All).map { it.definition.id }).containsExactly("g0", "c1", "g2").inOrder()
        assertThat(ManageLogic.filterFields(fields, FieldFilter.Global).map { it.definition.id }).containsExactly("g0", "g2").inOrder()
        assertThat(ManageLogic.filterFields(fields, FieldFilter.Category("cat")).map { it.definition.id }).containsExactly("c1")
        assertThat(ManageLogic.filterFields(fields, FieldFilter.Category("none"))).isEmpty()
    }

    @Test
    fun validateField_requiresNameAndDropdownOption() {
        assertThat(ManageLogic.validateField(" ", CustomFieldType.TEXT, emptyList())).isEqualTo(InvalidReason.BLANK_NAME)
        assertThat(ManageLogic.validateField("Plan", CustomFieldType.DROPDOWN, listOf(OptionDraft("1", " "))))
            .isEqualTo(InvalidReason.DROPDOWN_NEEDS_OPTION)
        assertThat(ManageLogic.validateField("Plan", CustomFieldType.DROPDOWN, listOf(OptionDraft("1", "Pro")))).isNull()
        assertThat(ManageLogic.validateField("Plan", CustomFieldType.TEXT, emptyList())).isNull()
    }

    @Test
    fun defaultOptions_twoNumberedOptions() {
        var n = 0
        val options = ManageLogic.defaultOptions({ "Option $it" }, { "id${n++}" })
        assertThat(options).containsExactly(OptionDraft("id0", "Option 1"), OptionDraft("id1", "Option 2")).inOrder()
    }

    @Test
    fun blankToNull_trims() {
        assertThat("  ".blankToNull()).isNull()
        assertThat(" x ".blankToNull()).isEqualTo("x")
    }

    private fun usage(name: String) = TagWithUsage(Tag(id = name, name = name, createdAt = now), 0)

    private fun folder(mode: TagMatchMode, vararg tagIds: String) =
        TagFolderWithTags(TagFolder(name = "F", matchMode = mode, createdAt = now), tagIds.toSet())

    private fun field(id: String, categoryId: String?, order: Int) = CustomFieldWithOptions(
        CustomFieldDefinition(id = id, name = id, type = CustomFieldType.TEXT, categoryId = categoryId, sortOrder = order, createdAt = now),
        emptyList(),
    )
}
