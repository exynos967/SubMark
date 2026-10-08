package io.github.submark.feature.settings.ui.document

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.ui.navigation.DocumentRoute
import io.github.submark.feature.settings.data.AppDocument
import io.github.submark.feature.settings.data.DocumentLoad
import io.github.submark.feature.settings.data.DocumentRepository
import io.github.submark.feature.settings.data.DocumentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DocumentUiState {
    data object Loading : DocumentUiState
    data object NotFound : DocumentUiState
    data class Error(val message: String?) : DocumentUiState
    /** [expanded] holds "section/item" keys of open FAQ answers. */
    data class Content(val document: AppDocument, val expanded: Set<String> = emptySet()) : DocumentUiState {
        val allKeys: Set<String> = document.sections.flatMapIndexed { s, sec -> sec.items.indices.map { key(s, it) } }.toSet()
        val allExpanded: Boolean get() = allKeys.isNotEmpty() && expanded.containsAll(allKeys)
    }

    companion object {
        fun key(section: Int, item: Int) = "$section/$item"
    }
}

@HiltViewModel
class DocumentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DocumentRepository,
) : ViewModel() {

    val type: DocumentType? = DocumentType.from(savedStateHandle.toRoute<DocumentRoute>().type)

    private val _state = MutableStateFlow<DocumentUiState>(DocumentUiState.Loading)
    val state: StateFlow<DocumentUiState> = _state.asStateFlow()
    private var language: String? = null

    /** Called by the screen with the language of the current configuration; reloads when it changes. */
    fun load(language: String, force: Boolean = false) {
        if (!force && language == this.language && _state.value !is DocumentUiState.Error) return
        this.language = language
        val type = type ?: run { _state.value = DocumentUiState.NotFound; return }
        _state.value = DocumentUiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = repository.load(type, language)) {
                is DocumentLoad.Loaded -> DocumentUiState.Content(result.document)
                DocumentLoad.NotFound -> DocumentUiState.NotFound
                is DocumentLoad.Failed -> DocumentUiState.Error(result.message)
            }
        }
    }

    fun retry() {
        language?.let { load(it, force = true) }
    }

    fun toggle(key: String) = updateContent { c ->
        c.copy(expanded = if (key in c.expanded) c.expanded - key else c.expanded + key)
    }

    fun setAllExpanded(expanded: Boolean) = updateContent { c -> c.copy(expanded = if (expanded) c.allKeys else emptySet()) }

    private fun updateContent(transform: (DocumentUiState.Content) -> DocumentUiState.Content) {
        _state.update { if (it is DocumentUiState.Content) transform(it) else it }
    }
}
