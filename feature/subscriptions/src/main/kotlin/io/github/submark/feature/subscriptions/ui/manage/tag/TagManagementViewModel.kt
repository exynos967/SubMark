package io.github.submark.feature.subscriptions.ui.manage.tag

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.repository.TagFolderWithTags
import io.github.submark.core.data.repository.TagRepository
import io.github.submark.core.data.repository.TagWithUsage
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.result.InvalidReason
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.model.IconType
import io.github.submark.core.model.Tag
import io.github.submark.core.model.TagColor
import io.github.submark.core.model.TagFolder
import io.github.submark.core.model.TagMatchMode
import io.github.submark.core.model.newId
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.icon.IconChoice
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.subscriptions.R
import io.github.submark.feature.subscriptions.ui.common.toUiText
import io.github.submark.feature.subscriptions.ui.manage.ManageLogic
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TagManagementTab { TAGS, FOLDERS }

data class TagEditorState(
    val id: String? = null,
    val name: String = "",
    val color: TagColor = TagColor.BLUE,
    val iconType: IconType? = null,
    val iconValue: String? = null,
    val nameError: UiText? = null,
    val saving: Boolean = false,
) {
    val isNew: Boolean get() = id == null
}

data class FolderEditorState(
    val existing: TagFolder? = null,
    val name: String = "",
    val iconValue: String? = null,
    val colorHex: String? = null,
    val tagIds: Set<String> = emptySet(),
    val matchMode: TagMatchMode = TagMatchMode.ANY,
    val showInMainList: Boolean = true,
    val enabled: Boolean = true,
    val nameError: UiText? = null,
    val tagError: UiText? = null,
    val saving: Boolean = false,
) {
    val isNew: Boolean get() = existing == null
}

data class FolderRow(val folder: TagFolderWithTags, val tagNames: List<String>, val matchCount: Int)

/** Which editor asked the icon picker, so the returned icon goes to the right draft. */
enum class IconTarget { TAG, FOLDER }

data class TagManagementUiState(
    val loading: Boolean = true,
    val tab: TagManagementTab = TagManagementTab.TAGS,
    val query: String = "",
    val tags: List<TagWithUsage> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val folders: List<FolderRow> = emptyList(),
    val tagEditor: TagEditorState? = null,
    val folderEditor: FolderEditorState? = null,
)

private data class Ui(
    val tab: TagManagementTab = TagManagementTab.TAGS,
    val query: String = "",
    val tagEditor: TagEditorState? = null,
    val folderEditor: FolderEditorState? = null,
    val iconTarget: IconTarget? = null,
)

@HiltViewModel
class TagManagementViewModel @Inject constructor(
    private val tags: TagRepository,
    subscriptions: SubscriptionRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val ui = MutableStateFlow(Ui())
    private val snackbar = Channel<SnackbarMessage>(Channel.BUFFERED)
    val messages: Flow<SnackbarMessage> = snackbar.receiveAsFlow()

    private val subscriptionTags = subscriptions.observeItems().map { items -> items.map { item -> item.tags.map { it.id }.toSet() } }

    val uiState: StateFlow<TagManagementUiState> = combine(
        tags.observeTagsWithUsage(),
        tags.observeFolders(),
        subscriptionTags,
        ui,
    ) { usage, folders, subTags, u ->
        val names = usage.associate { it.tag.id to it.tag.name }
        TagManagementUiState(
            loading = false,
            tab = u.tab,
            query = u.query,
            tags = ManageLogic.filterTags(usage, u.query),
            allTags = usage.map { it.tag },
            folders = ManageLogic.filterFolders(folders.sortedBy { it.folder.sortOrder }, u.query).map { f ->
                FolderRow(f, f.tagIds.mapNotNull(names::get).sortedBy { it.lowercase() }, ManageLogic.folderMatchCount(f, subTags))
            },
            tagEditor = u.tagEditor,
            folderEditor = u.folderEditor,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagManagementUiState())

    fun selectTab(tab: TagManagementTab) = ui.update { it.copy(tab = tab) }

    fun setQuery(query: String) = ui.update { it.copy(query = query) }

    // ---- icon picker ----

    fun requestIcon(target: IconTarget) = ui.update { it.copy(iconTarget = target) }

    fun onIconPicked(choice: IconChoice) {
        // Folders store only a value: catalogue symbol names and emoji are supported.
        val folderSupported = choice.type == IconType.SYMBOL || choice.type == IconType.EMOJI
        if (ui.value.iconTarget == IconTarget.FOLDER && !folderSupported) {
            snackbar.trySend(SnackbarMessage(UiText.res(R.string.subscriptions_manage_folder_icon_unsupported)))
        }
        ui.update { u ->
            when (u.iconTarget) {
                IconTarget.TAG -> u.copy(tagEditor = u.tagEditor?.copy(iconType = choice.type, iconValue = choice.value), iconTarget = null)
                IconTarget.FOLDER ->
                    if (folderSupported) u.copy(folderEditor = u.folderEditor?.copy(iconValue = choice.value), iconTarget = null) else u.copy(iconTarget = null)
                null -> u
            }
        }
    }

    // ---- tags ----

    fun startAddTag() = ui.update { it.copy(tagEditor = TagEditorState()) }

    fun startEditTag(tag: Tag) = ui.update {
        it.copy(tagEditor = TagEditorState(id = tag.id, name = tag.name, color = tag.color, iconType = tag.iconType, iconValue = tag.iconValue))
    }

    fun updateTagEditor(transform: (TagEditorState) -> TagEditorState) =
        ui.update { u -> u.copy(tagEditor = u.tagEditor?.let(transform)?.copy(nameError = null)) }

    fun dismissTagEditor() = ui.update { it.copy(tagEditor = null) }

    fun saveTag() {
        val draft = ui.value.tagEditor ?: return
        if (draft.name.isBlank()) {
            ui.update { it.copy(tagEditor = draft.copy(nameError = InvalidReason.BLANK_NAME.toUiText())) }
            return
        }
        ui.update { it.copy(tagEditor = draft.copy(saving = true)) }
        viewModelScope.launch {
            val result: DataResult<*> = if (draft.id == null) {
                tags.create(draft.name, draft.color, draft.iconType, draft.iconValue)
            } else {
                tags.update(Tag(id = draft.id, name = draft.name, color = draft.color, iconType = draft.iconType, iconValue = draft.iconValue, createdAt = time.now()))
            }
            when (result) {
                is DataResult.Success -> ui.update { it.copy(tagEditor = null) }
                is DataResult.Failure -> {
                    val error = result.error
                    val inline = error is DataError.Invalid && (error.reason == InvalidReason.NAME_EXISTS || error.reason == InvalidReason.BLANK_NAME)
                    ui.update { u -> u.copy(tagEditor = u.tagEditor?.copy(saving = false, nameError = if (inline) error.toUiText() else null)) }
                    if (!inline) snackbar.send(SnackbarMessage(error.toUiText()))
                }
            }
        }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch {
            when (val result = tags.delete(tag.id)) {
                is DataResult.Success -> snackbar.send(SnackbarMessage(UiText.res(R.string.subscriptions_manage_tag_deleted_done, tag.name)))
                is DataResult.Failure -> snackbar.send(SnackbarMessage(result.error.toUiText()))
            }
        }
    }

    // ---- folders ----

    fun startAddFolder() = ui.update { it.copy(folderEditor = FolderEditorState()) }

    fun startEditFolder(folder: TagFolderWithTags) = ui.update {
        val f = folder.folder
        it.copy(
            folderEditor = FolderEditorState(
                existing = f, name = f.name, iconValue = f.iconValue, colorHex = f.colorHex, tagIds = folder.tagIds,
                matchMode = f.matchMode, showInMainList = f.showInMainList, enabled = f.isEnabled,
            ),
        )
    }

    fun updateFolderEditor(transform: (FolderEditorState) -> FolderEditorState) =
        ui.update { u -> u.copy(folderEditor = u.folderEditor?.let(transform)?.copy(nameError = null, tagError = null)) }

    fun dismissFolderEditor() = ui.update { it.copy(folderEditor = null) }

    fun saveFolder() {
        val draft = ui.value.folderEditor ?: return
        val nameError = if (draft.name.isBlank()) InvalidReason.BLANK_NAME.toUiText() else null
        val tagError = if (draft.tagIds.isEmpty()) InvalidReason.FOLDER_NEEDS_TAG.toUiText() else null
        if (nameError != null || tagError != null) {
            ui.update { it.copy(folderEditor = draft.copy(nameError = nameError, tagError = tagError)) }
            return
        }
        ui.update { it.copy(folderEditor = draft.copy(saving = true)) }
        viewModelScope.launch {
            val base = draft.existing ?: TagFolder(id = newId(), name = draft.name, createdAt = time.now())
            val folder = base.copy(
                name = draft.name, iconValue = draft.iconValue, colorHex = draft.colorHex, matchMode = draft.matchMode,
                showInMainList = draft.showInMainList, isEnabled = draft.enabled,
            )
            when (val result = tags.saveFolder(folder, draft.tagIds)) {
                is DataResult.Success -> ui.update { it.copy(folderEditor = null) }
                is DataResult.Failure -> {
                    ui.update { u -> u.copy(folderEditor = u.folderEditor?.copy(saving = false)) }
                    snackbar.send(SnackbarMessage(result.error.toUiText()))
                }
            }
        }
    }

    fun deleteFolder(folder: TagFolder) {
        viewModelScope.launch {
            when (val result = tags.deleteFolder(folder.id)) {
                is DataResult.Success -> {
                    ui.update { it.copy(folderEditor = null) }
                    snackbar.send(SnackbarMessage(UiText.res(R.string.subscriptions_manage_folder_deleted_done, folder.name)))
                }
                is DataResult.Failure -> snackbar.send(SnackbarMessage(result.error.toUiText()))
            }
        }
    }

    fun reorderFolders(orderedIds: List<String>) {
        viewModelScope.launch {
            tags.reorderFolders(orderedIds).errorOrNull()?.let { snackbar.send(SnackbarMessage(it.toUiText())) }
        }
    }

    /** Creates a tag from the folder editor's tag picker; returns its id. */
    suspend fun createTag(name: String): String? = when (val result = tags.findOrCreate(name)) {
        is DataResult.Success -> result.value.id
        is DataResult.Failure -> {
            snackbar.send(SnackbarMessage(result.error.toUiText()))
            null
        }
    }
}
