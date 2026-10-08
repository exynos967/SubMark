package io.github.submark.feature.integrations.panel.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.model.ServiceType
import io.github.submark.core.ui.navigation.ServiceConnectionEditRoute
import io.github.submark.feature.integrations.panel.data.PanelErrorReason
import io.github.submark.feature.integrations.panel.data.PanelFetchException
import io.github.submark.feature.integrations.panel.data.PanelRepository
import io.github.submark.feature.integrations.panel.data.ServiceConnectionForm
import io.github.submark.feature.integrations.panel.data.service.EmbyClient
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServiceEditUiState(
    val loading: Boolean = true,
    val id: String? = null,
    val type: ServiceType = ServiceType.CLASH,
    val name: String = "",
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val apiKey: String = "",
    val hasStoredPassword: Boolean = false,
    val hasStoredApiKey: Boolean = false,
    val enabled: Boolean = true,
    val autoRefresh: Boolean = false,
    val intervalMinutes: String = "15",
    val colorHex: String? = null,
    val createdAtSeconds: Long? = null,
    val lastRefreshSeconds: Long? = null,
    val lastError: String? = null,
    val saving: Boolean = false,
    val testing: Boolean = false,
    val fieldError: ServiceFieldError? = null,
) {
    val isEdit: Boolean get() = id != null

    fun form(): ServiceConnectionForm = ServiceConnectionForm(
        id = id,
        name = name,
        type = type,
        url = url,
        username = username.ifBlank { null },
        password = password.ifBlank { null },
        apiKey = apiKey.ifBlank { null },
        enabled = enabled,
        autoRefresh = autoRefresh,
        refreshIntervalMinutes = intervalMinutes.toIntOrNull() ?: PanelRepository.DEFAULT_REFRESH_INTERVAL_MINUTES,
        colorHex = colorHex,
    )
}

enum class ServiceFieldError { NAME_REQUIRED, URL_REQUIRED, URL_INVALID, AUTH_REQUIRED, INTERVAL_INVALID }

sealed interface ServiceEditEvent {
    data object Saved : ServiceEditEvent
    data class Message(val reason: PanelErrorReason) : ServiceEditEvent
    data object TestSuccess : ServiceEditEvent
}

@HiltViewModel
class ServiceEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PanelRepository,
) : ViewModel() {

    private val route: ServiceConnectionEditRoute = savedStateHandle.toRoute()

    private val _state = MutableStateFlow(ServiceEditUiState())
    val state: StateFlow<ServiceEditUiState> = _state.asStateFlow()

    private val _events = Channel<ServiceEditEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val id = route.id
            _state.value = if (id == null) {
                ServiceEditUiState(loading = false)
            } else {
                val connection = repository.getService(id)
                if (connection == null) {
                    _events.send(ServiceEditEvent.Saved)
                    return@launch
                }
                ServiceEditUiState(
                    loading = false,
                    id = connection.id,
                    type = connection.type,
                    name = connection.name,
                    url = connection.url,
                    username = connection.username.orEmpty(),
                    password = "",
                    apiKey = "",
                    hasStoredPassword = !repository.servicePassword(id).isNullOrBlank(),
                    hasStoredApiKey = !repository.serviceApiKey(id).isNullOrBlank(),
                    enabled = connection.enabled,
                    autoRefresh = connection.autoRefresh,
                    intervalMinutes = connection.refreshIntervalMinutes.toString(),
                    colorHex = connection.colorHex,
                    createdAtSeconds = connection.createdAt.epochSecond,
                    lastRefreshSeconds = connection.lastRefreshAt?.epochSecond,
                    lastError = connection.lastError,
                )
            }
        }
    }

    fun update(transform: (ServiceEditUiState) -> ServiceEditUiState) {
        _state.value = transform(_state.value).copy(fieldError = null)
    }

    fun testConnection() {
        val s = _state.value
        val error = validate(s, forTest = true)
        if (error != null) {
            _state.value = s.copy(fieldError = error)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(testing = true)
            try {
                repository.testService(s.form())
                _events.send(ServiceEditEvent.TestSuccess)
            } catch (e: PanelFetchException) {
                _events.send(ServiceEditEvent.Message(e.reason))
            } catch (e: Exception) {
                _events.send(ServiceEditEvent.Message(PanelErrorReason.UNKNOWN))
            } finally {
                _state.value = _state.value.copy(testing = false)
            }
        }
    }

    fun save() {
        val s = _state.value
        val error = validate(s, forTest = false)
        if (error != null) {
            _state.value = s.copy(fieldError = error)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            try {
                repository.saveService(s.form())
                _events.send(ServiceEditEvent.Saved)
            } finally {
                _state.value = _state.value.copy(saving = false)
            }
        }
    }

    fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            repository.deleteService(id)
            _events.send(ServiceEditEvent.Saved)
        }
    }

    private fun validate(s: ServiceEditUiState, forTest: Boolean): ServiceFieldError? {
        if (!forTest && s.name.isBlank()) return ServiceFieldError.NAME_REQUIRED
        if (s.url.isBlank()) return ServiceFieldError.URL_REQUIRED
        val normalized = when (s.type) {
            ServiceType.CLASH -> s.url.trim()
            ServiceType.EMBY -> EmbyClient.normalizeBase(s.url)
        } ?: return ServiceFieldError.URL_INVALID
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) return ServiceFieldError.URL_INVALID
        if (s.type == ServiceType.EMBY) {
            val hasKey = s.apiKey.isNotBlank() || s.hasStoredApiKey
            val hasLogin = (s.username.isNotBlank()) && (s.password.isNotBlank() || s.hasStoredPassword)
            if (!hasKey && !hasLogin) return ServiceFieldError.AUTH_REQUIRED
        }
        val interval = s.intervalMinutes.toIntOrNull()
        if (!forTest && s.autoRefresh && (interval == null || interval < PanelRepository.MIN_REFRESH_INTERVAL_MINUTES)) {
            return ServiceFieldError.INTERVAL_INVALID
        }
        return null
    }
}
