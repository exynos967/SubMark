package io.github.submark.feature.subscriptions.ui.manage.field

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.repository.CategoryRepository
import io.github.submark.core.data.repository.CustomFieldRepository
import io.github.submark.core.data.repository.CustomFieldStats
import io.github.submark.core.data.repository.CustomFieldWithOptions
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.result.InvalidReason
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.model.Category
import io.github.submark.core.model.CustomFieldDefinition
import io.github.submark.core.model.CustomFieldOption
import io.github.submark.core.model.CustomFieldType
import io.github.submark.core.model.newId
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.subscriptions.R
import io.github.submark.feature.subscriptions.ui.common.toUiText
import io.github.submark.feature.subscriptions.ui.manage.ManageLogic
import io.github.submark.feature.subscriptions.ui.manage.ManageLogic.FieldFilter
import io.github.submark.feature.subscriptions.ui.manage.ManageLogic.OptionDraft
import io.github.submark.feature.subscriptions.ui.manage.ManageLogic.blankToNull
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
import java.time.LocalDate
import javax.inject.Inject

data class FieldEditorState(
    val existing: CustomFieldDefinition? = null,
    val name: String = "",
    val type: CustomFieldType = CustomFieldType.TEXT,
    val placeholder: String = "",
    val helpText: String = "",
    val required: Boolean = false,
    val active: Boolean = true,
    val categoryId: String? = null,
    val options: List<OptionDraft> = emptyList(),
    val nameError: UiText? = null,
    val optionsError: UiText? = null,
    val saving: Boolean = false,
) {
    val isNew: Boolean get() = existing == null
}

data class CustomFieldManagementUiState(
    val loading: Boolean = true,
    val filter: FieldFilter = FieldFilter.All,
    val fields: List<CustomFieldWithOptions> = emptyList(),
    val totalCount: Int = 0,
    val categories: List<Category> = emptyList(),
    val stats: Map<String, CustomFieldStats> = emptyMap(),
    val editor: FieldEditorState? = null,
    val today: LocalDate = LocalDate.MIN,
)

private data class FieldUi(val filter: FieldFilter = FieldFilter.All, val editor: FieldEditorState? = null)

@HiltViewModel
class CustomFieldManagementViewModel @Inject constructor(
    private val repository: CustomFieldRepository,
    categories: CategoryRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val ui = MutableStateFlow(FieldUi())
    private val snackbar = Channel<SnackbarMessage>(Channel.BUFFERED)
    val messages: Flow<SnackbarMessage> = snackbar.receiveAsFlow()

    val uiState: StateFlow<CustomFieldManagementUiState> = combine(
        repository.observeFields(),
        categories.observeAll(),
        repository.observeStats(),
        ui,
    ) { fields, cats, stats, u ->
        CustomFieldManagementUiState(
            loading = false,
            filter = u.filter,
            fields = ManageLogic.filterFields(fields, u.filter),
            totalCount = fields.size,
            categories = cats.sortedBy { it.sortOrder },
            stats = stats.associateBy { it.fieldId },
            editor = u.editor,
            today = time.today(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomFieldManagementUiState())

    fun setFilter(filter: FieldFilter) = ui.update { it.copy(filter = filter) }

    fun startAdd() = ui.update { u ->
        val scope = (u.filter as? FieldFilter.Category)?.id
        u.copy(editor = FieldEditorState(categoryId = scope))
    }

    fun startEdit(field: CustomFieldWithOptions) = ui.update {
        val d = field.definition
        it.copy(
            editor = FieldEditorState(
                existing = d, name = d.name, type = d.type, placeholder = d.placeholder.orEmpty(), helpText = d.helpText.orEmpty(),
                required = d.isRequired, active = d.isActive, categoryId = d.categoryId,
                options = field.options.sortedBy { o -> o.sortOrder }.map { o -> OptionDraft(o.id, o.label) },
            ),
        )
    }

    fun dismissEditor() = ui.update { it.copy(editor = null) }

    /** [defaultOptionLabel] produces "Option n" when switching to DROPDOWN with no options. */
    fun updateEditor(defaultOptionLabel: (Int) -> String, transform: (FieldEditorState) -> FieldEditorState) = ui.update { u ->
        val current = u.editor ?: return@update u
        var next = transform(current).copy(nameError = null, optionsError = null)
        if (next.type == CustomFieldType.DROPDOWN && next.options.isEmpty()) {
            next = next.copy(options = ManageLogic.defaultOptions(defaultOptionLabel, ::newId))
        }
        u.copy(editor = next)
    }

    fun save() {
        val draft = ui.value.editor ?: return
        when (ManageLogic.validateField(draft.name, draft.type, draft.options)) {
            InvalidReason.BLANK_NAME -> {
                ui.update { it.copy(editor = draft.copy(nameError = InvalidReason.BLANK_NAME.toUiText())) }
                return
            }
            InvalidReason.DROPDOWN_NEEDS_OPTION -> {
                ui.update { it.copy(editor = draft.copy(optionsError = InvalidReason.DROPDOWN_NEEDS_OPTION.toUiText())) }
                return
            }
            else -> Unit
        }
        ui.update { it.copy(editor = draft.copy(saving = true)) }
        viewModelScope.launch {
            val base = draft.existing ?: CustomFieldDefinition(name = draft.name, type = draft.type, createdAt = time.now())
            val definition = base.copy(
                name = draft.name, type = draft.type, placeholder = draft.placeholder.blankToNull(), helpText = draft.helpText.blankToNull(),
                isRequired = draft.required, isActive = draft.active, categoryId = draft.categoryId,
            )
            val options = draft.options.map { CustomFieldOption(id = it.id, fieldId = definition.id, label = it.label) }
            when (val result = repository.saveField(definition, options)) {
                is DataResult.Success -> ui.update { it.copy(editor = null) }
                is DataResult.Failure -> {
                    ui.update { u -> u.copy(editor = u.editor?.copy(saving = false)) }
                    snackbar.send(SnackbarMessage(result.error.toUiText()))
                }
            }
        }
    }

    fun setActive(id: String, active: Boolean) {
        viewModelScope.launch { repository.setActive(id, active).errorOrNull()?.let { snackbar.send(SnackbarMessage(it.toUiText())) } }
    }

    fun reorder(orderedIds: List<String>) {
        viewModelScope.launch { repository.reorder(orderedIds).errorOrNull()?.let { snackbar.send(SnackbarMessage(it.toUiText())) } }
    }

    fun delete(definition: CustomFieldDefinition) {
        viewModelScope.launch {
            when (val result = repository.deleteField(definition.id)) {
                is DataResult.Success -> {
                    ui.update { it.copy(editor = null) }
                    snackbar.send(SnackbarMessage(UiText.res(R.string.subscriptions_manage_field_deleted_done, definition.name)))
                }
                is DataResult.Failure -> snackbar.send(SnackbarMessage(result.error.toUiText()))
            }
        }
    }
}
