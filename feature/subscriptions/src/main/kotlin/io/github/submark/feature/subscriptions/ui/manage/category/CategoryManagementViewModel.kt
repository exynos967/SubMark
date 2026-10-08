package io.github.submark.feature.subscriptions.ui.manage.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.repository.CategoryRepository
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.result.InvalidReason
import io.github.submark.core.data.seed.SystemCategories
import io.github.submark.core.model.Category
import io.github.submark.core.model.IconType
import io.github.submark.core.model.SystemCategory
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.icon.IconChoice
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.subscriptions.R
import io.github.submark.feature.subscriptions.ui.common.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Draft of the category being added or edited. [id] null = new custom category. */
data class CategoryEditorState(
    val id: String? = null,
    val systemKey: SystemCategory? = null,
    val name: String = "",
    val iconType: IconType = IconType.SYMBOL,
    val iconValue: String = DEFAULT_ICON,
    val colorHex: String = DEFAULT_COLOR,
    val nameError: UiText? = null,
    val saving: Boolean = false,
) {
    val isNew: Boolean get() = id == null
    val isSystem: Boolean get() = systemKey != null

    companion object {
        const val DEFAULT_ICON = "category"
        const val DEFAULT_COLOR = "#3E63DD"
    }
}

data class CategoryManagementUiState(
    val loading: Boolean = true,
    val visible: List<Category> = emptyList(),
    val hidden: List<Category> = emptyList(),
    val editor: CategoryEditorState? = null,
)

@HiltViewModel
class CategoryManagementViewModel @Inject constructor(
    private val repository: CategoryRepository,
) : ViewModel() {

    private val editor = MutableStateFlow<CategoryEditorState?>(null)
    private val snackbar = Channel<SnackbarMessage>(Channel.BUFFERED)
    val messages: Flow<SnackbarMessage> = snackbar.receiveAsFlow()

    val uiState: StateFlow<CategoryManagementUiState> = combine(repository.observeAll(), editor) { all, edit ->
        val sorted = all.sortedBy { it.sortOrder }
        CategoryManagementUiState(
            loading = false,
            visible = sorted.filter { !it.isHidden },
            hidden = sorted.filter { it.isHidden },
            editor = edit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryManagementUiState())

    fun startAdd() {
        editor.value = CategoryEditorState()
    }

    fun startEdit(category: Category) {
        editor.value = CategoryEditorState(
            id = category.id,
            systemKey = category.systemKey,
            name = category.name.orEmpty(),
            iconType = category.iconType,
            iconValue = category.iconValue,
            colorHex = category.colorHex,
        )
    }

    fun dismissEditor() {
        editor.value = null
    }

    fun updateEditor(transform: (CategoryEditorState) -> CategoryEditorState) {
        editor.update { it?.let(transform)?.copy(nameError = null) }
    }

    fun onIconPicked(choice: IconChoice) {
        editor.update { it?.copy(iconType = choice.type, iconValue = choice.value) }
    }

    fun save() {
        val draft = editor.value ?: return
        if (!draft.isSystem && draft.name.isBlank()) {
            editor.update { it?.copy(nameError = InvalidReason.BLANK_NAME.toUiText()) }
            return
        }
        editor.update { it?.copy(saving = true) }
        viewModelScope.launch {
            val result = if (draft.id == null) {
                repository.create(draft.name, draft.iconType, draft.iconValue, draft.colorHex)
            } else {
                val current = repository.get(draft.id)
                if (current == null) {
                    DataResult.Failure(io.github.submark.core.data.result.DataError.NotFound)
                } else {
                    repository.update(
                        current.copy(
                            name = draft.name.trim().ifEmpty { null },
                            iconType = draft.iconType,
                            iconValue = draft.iconValue,
                            colorHex = draft.colorHex,
                        ),
                    )
                }
            }
            when (result) {
                is DataResult.Success -> editor.value = null
                is DataResult.Failure -> {
                    editor.update { it?.copy(saving = false) }
                    snackbar.send(SnackbarMessage(result.error.toUiText()))
                }
            }
        }
    }

    /** Restores the editor's preset icon, color and name (system categories only). */
    fun resetPresetStyle() {
        val draft = editor.value ?: return
        val key = draft.systemKey ?: return
        val preset = SystemCategories.default(key)
        editor.update { it?.copy(name = "", iconType = preset.iconType, iconValue = preset.iconValue, colorHex = preset.colorHex) }
    }

    fun hide(category: Category) = run(repository::hide, category.id, R.string.subscriptions_manage_category_hidden_done)

    fun restore(category: Category) = run(repository::restore, category.id, R.string.subscriptions_manage_category_restored_done)

    fun delete(category: Category) = run(repository::delete, category.id, R.string.subscriptions_manage_category_deleted_done)

    fun reorder(orderedIds: List<String>) {
        viewModelScope.launch {
            repository.reorder(orderedIds).errorOrNull()?.let { snackbar.send(SnackbarMessage(it.toUiText())) }
        }
    }

    fun resetToDefault() {
        viewModelScope.launch {
            when (val result = repository.resetToDefault()) {
                is DataResult.Success -> snackbar.send(SnackbarMessage(UiText.res(R.string.subscriptions_manage_category_reset_done)))
                is DataResult.Failure -> snackbar.send(SnackbarMessage(result.error.toUiText()))
            }
        }
    }

    private fun run(action: suspend (String) -> DataResult<Unit>, id: String, successRes: Int) {
        viewModelScope.launch {
            when (val result = action(id)) {
                is DataResult.Success -> snackbar.send(SnackbarMessage(UiText.res(successRes)))
                is DataResult.Failure -> snackbar.send(SnackbarMessage(result.error.toUiText()))
            }
        }
    }
}
