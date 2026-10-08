package io.github.submark.feature.integrations.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.settings.AiProvider
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.integrations.R
import io.github.submark.feature.integrations.data.ai.AiRecognitionService
import io.github.submark.feature.integrations.ui.common.uiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiSettingsUiState(
    val enabled: Boolean = false,
    val provider: AiProvider? = null,
    val endpoint: String = "",
    val model: String = "",
    val keySet: Boolean = false,
    val models: List<String> = emptyList(),
    val defaultEndpoint: String = "",
    val testing: Boolean = false,
)

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val service: AiRecognitionService,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val testing = MutableStateFlow(false)
    private val keySet = MutableStateFlow(false)
    private val endpointOverride = MutableStateFlow<String?>(null)
    private val modelOverride = MutableStateFlow<String?>(null)

    val state: StateFlow<AiSettingsUiState> = combine(
        settings.settings,
        combine(testing, keySet, endpointOverride, modelOverride) { t, k, e, m -> Quad(t, k, e, m) },
    ) { s, extra ->
        val provider = s.integrations.aiProvider
        AiSettingsUiState(
            enabled = s.integrations.aiEnabled,
            provider = provider,
            endpoint = extra.endpoint ?: s.integrations.aiEndpoint ?: provider?.let(service::defaultEndpoint).orEmpty(),
            model = extra.model ?: s.integrations.aiModel ?: provider?.let { service.modelPresets(it).firstOrNull() }.orEmpty(),
            keySet = extra.keySet,
            models = provider?.let(service::modelPresets).orEmpty(),
            defaultEndpoint = provider?.let(service::defaultEndpoint).orEmpty(),
            testing = extra.testing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiSettingsUiState())

    private data class Quad(val testing: Boolean, val keySet: Boolean, val endpoint: String?, val model: String?)

    private val messagesCh = Channel<SnackbarMessage>(Channel.BUFFERED)
    val messages = messagesCh.receiveAsFlow()

    init {
        viewModelScope.launch { keySet.value = service.apiKey() != null }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                val provider = settings.settings.first().integrations.aiProvider
                if (provider == null || service.apiKey() == null) {
                    messagesCh.send(SnackbarMessage(UiText.res(R.string.integrations_error_ai_config)))
                    return@launch
                }
            }
            settings.update { it.copy(integrations = it.integrations.copy(aiEnabled = enabled)) }
        }
    }

    fun setProvider(provider: AiProvider) {
        endpointOverride.value = null
        modelOverride.value = null
        viewModelScope.launch {
            settings.update {
                it.copy(
                    integrations = it.integrations.copy(
                        aiProvider = provider,
                        aiEndpoint = null,
                        aiModel = null,
                    ),
                )
            }
        }
    }

    fun setEndpoint(endpoint: String) {
        endpointOverride.value = endpoint
        viewModelScope.launch {
            settings.update { it.copy(integrations = it.integrations.copy(aiEndpoint = endpoint.trim().takeIf { e -> e.isNotEmpty() })) }
        }
    }

    fun setModel(model: String) {
        modelOverride.value = model
        viewModelScope.launch {
            settings.update { it.copy(integrations = it.integrations.copy(aiModel = model.trim().takeIf { m -> m.isNotEmpty() })) }
        }
    }

    fun setKey(key: String) {
        viewModelScope.launch {
            if (key.isBlank()) {
                service.clearApiKey()
                keySet.value = false
            } else {
                service.setApiKey(key)
                keySet.value = true
            }
            messagesCh.send(SnackbarMessage(UiText.res(R.string.integrations_ai_key_saved)))
        }
    }

    fun testConnection() {
        if (testing.value) return
        viewModelScope.launch {
            testing.value = true
            service.testConnection()
                .onSuccess { messagesCh.send(SnackbarMessage(UiText.res(R.string.integrations_ai_test_ok))) }
                .onFailure { messagesCh.send(SnackbarMessage(it.uiMessage())) }
            testing.value = false
        }
    }
}
