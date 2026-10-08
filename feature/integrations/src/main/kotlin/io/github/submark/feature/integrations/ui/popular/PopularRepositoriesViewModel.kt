package io.github.submark.feature.integrations.ui.popular

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.model.PopularRepository
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.integrations.R
import io.github.submark.feature.integrations.data.popular.CatalogFetchException
import io.github.submark.feature.integrations.data.popular.PopularService
import io.github.submark.feature.integrations.ui.common.uiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RepoFormState(
    val visible: Boolean = false,
    /** Non-null when editing. */
    val editing: PopularRepository? = null,
    val name: String = "",
    val url: String = "",
    val description: String = "",
    val validating: Boolean = false,
    val saving: Boolean = false,
    /** Validated entry count (green) or error (red). */
    val validationOk: Int? = null,
    val validationError: UiText? = null,
)

data class RepositoriesUiState(
    val repositories: List<PopularRepository> = emptyList(),
    val form: RepoFormState = RepoFormState(),
    val confirmDelete: PopularRepository? = null,
    val confirmClearCache: Boolean = false,
)

@HiltViewModel
class PopularRepositoriesViewModel @Inject constructor(
    private val service: PopularService,
) : ViewModel() {

    private val local = MutableStateFlow(RepositoriesUiState())

    val state: StateFlow<RepositoriesUiState> =
        combine(local, service.observeRepositories()) { l, repos -> l.copy(repositories = repos) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RepositoriesUiState())

    private val messagesCh = Channel<SnackbarMessage>(Channel.BUFFERED)
    val messages = messagesCh.receiveAsFlow()

    fun openForm(editing: PopularRepository? = null) {
        local.update {
            it.copy(
                form = RepoFormState(
                    visible = true,
                    editing = editing,
                    name = editing?.name ?: "",
                    url = editing?.url ?: "",
                    description = editing?.description ?: "",
                ),
            )
        }
    }

    fun closeForm() = local.update { it.copy(form = RepoFormState()) }

    fun editForm(name: String? = null, url: String? = null, description: String? = null) {
        local.update {
            it.copy(
                form = it.form.copy(
                    name = name ?: it.form.name,
                    url = url ?: it.form.url,
                    description = description ?: it.form.description,
                    validationOk = if (url != null) null else it.form.validationOk,
                    validationError = if (url != null) null else it.form.validationError,
                ),
            )
        }
    }

    /** "Validate" on the add/edit form: GET + parse, then show the entry count or the failure. */
    fun validateUrl() {
        val form = state.value.form
        if (form.url.isBlank() || form.validating) return
        viewModelScope.launch {
            local.update { it.copy(form = it.form.copy(validating = true, validationOk = null, validationError = null)) }
            service.fetchAndParse(form.url.trim())
                .onSuccess { catalog ->
                    local.update { it.copy(form = it.form.copy(validating = false, validationOk = catalog.subscriptions.size)) }
                }
                .onFailure { e ->
                    local.update {
                        it.copy(form = it.form.copy(validating = false, validationError = e.uiMessage()))
                    }
                }
        }
    }

    fun save() {
        val form = state.value.form
        if (form.saving) return
        val name = form.name.trim()
        val url = form.url.trim()
        if (name.isEmpty()) {
            viewModelScope.launch { messagesCh.send(SnackbarMessage(UiText.res(R.string.integrations_error_blank_name))) }
            return
        }
        viewModelScope.launch {
            local.update { it.copy(form = it.form.copy(saving = true)) }
            try {
                val editing = form.editing
                val repo = if (editing != null) {
                    editing.copy(
                        name = name,
                        url = url,
                        description = form.description.trim().takeIf { it.isNotEmpty() },
                    ).also { service.updateRepository(it) }
                } else {
                    service.addRepository(name, url, form.description.trim().takeIf { it.isNotEmpty() })
                }
                // Fetch once immediately so counts/errors show up right away.
                service.loadCatalog(forceRefresh = true)
                local.update { it.copy(form = RepoFormState()) }
                messagesCh.send(SnackbarMessage(UiText.res(R.string.integrations_repo_saved, repo.name)))
            } catch (e: Exception) {
                local.update { it.copy(form = it.form.copy(saving = false)) }
                messagesCh.send(SnackbarMessage(e.uiMessage()))
            }
        }
    }

    fun setEnabled(repo: PopularRepository, enabled: Boolean) {
        viewModelScope.launch { service.setEnabled(repo, enabled) }
    }

    fun move(repo: PopularRepository, delta: Int) {
        val repos = state.value.repositories
        val index = repos.indexOfFirst { it.id == repo.id }
        val target = index + delta
        if (index < 0 || target !in repos.indices) return
        val ids = repos.map { it.id }.toMutableList()
        ids.add(target, ids.removeAt(index))
        viewModelScope.launch { service.reorder(ids) }
    }

    fun askDelete(repo: PopularRepository?) = local.update { it.copy(confirmDelete = repo) }

    fun delete() {
        val repo = state.value.confirmDelete ?: return
        local.update { it.copy(confirmDelete = null) }
        viewModelScope.launch {
            service.deleteRepository(repo)
            messagesCh.send(SnackbarMessage(UiText.res(R.string.integrations_repo_deleted, repo.name)))
        }
    }

    fun askClearCache(show: Boolean) = local.update { it.copy(confirmClearCache = show) }

    fun clearCache() {
        local.update { it.copy(confirmClearCache = false) }
        viewModelScope.launch {
            service.clearCaches()
            messagesCh.send(SnackbarMessage(UiText.res(R.string.integrations_repo_cache_cleared)))
        }
    }
}
