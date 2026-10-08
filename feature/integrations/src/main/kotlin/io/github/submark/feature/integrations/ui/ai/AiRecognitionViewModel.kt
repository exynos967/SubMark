package io.github.submark.feature.integrations.ui.ai

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.model.SubscriptionPrefill
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.navigation.AiRecognitionRoute
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.integrations.R
import io.github.submark.feature.integrations.data.ai.AiRecognitionService
import io.github.submark.feature.integrations.data.ai.RecognitionResult
import io.github.submark.feature.integrations.ui.common.uiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class AiRecognitionUiState(
    /** Deep-linked / picked image waiting to be analyzed. */
    val imagePath: String? = null,
    val analyzing: Boolean = false,
    val results: List<RecognitionResult> = emptyList(),
    val selected: Set<Int> = emptySet(),
    /** Selected results already consumed via "use"; index = order of use. */
    val consumed: Int = 0,
    val error: UiText? = null,
) {
    val remaining: List<Pair<Int, RecognitionResult>>
        get() = selected.sorted().drop(consumed).mapNotNull { i -> results.getOrNull(i)?.let { i to it } }
}

@HiltViewModel
class AiRecognitionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val service: AiRecognitionService,
    private val json: Json,
) : ViewModel() {

    private val initialPath = savedStateHandle.toRoute<AiRecognitionRoute>().imagePath

    private val _state = MutableStateFlow(AiRecognitionUiState())
    val state = _state.asStateFlow()

    private val messagesCh = Channel<SnackbarMessage>(Channel.BUFFERED)
    val messages = messagesCh.receiveAsFlow()

    private val prefillCh = Channel<String>(Channel.BUFFERED)
    val prefill = prefillCh.receiveAsFlow()

    init {
        if (!initialPath.isNullOrBlank()) {
            _state.update { it.copy(imagePath = initialPath) }
            analyze(initialPath)
        }
    }

    /** Photo picker result; the uri is copied to the cache first (stable path). */
    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            val path = service.importImage(uri)
            if (path == null) {
                messagesCh.send(SnackbarMessage(UiText.res(R.string.integrations_error_ai_image)))
                return@launch
            }
            _state.update { it.copy(imagePath = path) }
            analyze(path)
        }
    }

    fun analyze(path: String) {
        if (_state.value.analyzing) return
        viewModelScope.launch {
            _state.update { it.copy(analyzing = true, error = null) }
            service.recognize(path)
                .onSuccess { results ->
                    _state.update {
                        it.copy(
                            analyzing = false,
                            results = results,
                            selected = results.indices.toSet(),
                            consumed = 0,
                            error = if (results.isEmpty()) UiText.res(R.string.integrations_ai_no_results) else null,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(analyzing = false, error = e.uiMessage()) }
                }
        }
    }

    fun toggle(index: Int) {
        _state.update {
            it.copy(selected = if (index in it.selected) it.selected - index else it.selected + index)
        }
    }

    fun setAll(select: Boolean) {
        _state.update {
            it.copy(selected = if (select) it.results.indices.toSet() else emptySet())
        }
    }

    /**
     * Uses the first selected result (navigates to the add form). Additional selected results stay
     * listed; the user can come back and use the next one.
     */
    fun useFirst() {
        val s = _state.value
        val index = s.selected.sorted().getOrNull(s.consumed) ?: return
        val result = s.results.getOrNull(index) ?: return
        _state.update { it.copy(consumed = it.consumed + 1) }
        viewModelScope.launch {
            prefillCh.send(json.encodeToString(SubscriptionPrefill.serializer(), result.toPrefill()))
        }
    }
}
