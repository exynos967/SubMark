package io.github.submark.feature.integrations.ui.rawg

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.ui.component.SettingsGroup
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.integrations.R
import io.github.submark.feature.integrations.data.rawg.RawgService
import io.github.submark.feature.integrations.ui.common.uiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RawgSettingsUiState(
    val keySet: Boolean = false,
    val testing: Boolean = false,
)

@HiltViewModel
class RawgSettingsViewModel @Inject constructor(
    private val service: RawgService,
) : ViewModel() {

    private val keySet = MutableStateFlow(false)
    private val testing = MutableStateFlow(false)

    val state: StateFlow<RawgSettingsUiState> =
        combine(keySet, testing) { k, t -> RawgSettingsUiState(k, t) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RawgSettingsUiState())

    private val messagesCh = Channel<SnackbarMessage>(Channel.BUFFERED)
    val messages = messagesCh.receiveAsFlow()

    init {
        viewModelScope.launch { keySet.value = service.apiKey() != null }
    }

    fun saveKey(key: String) {
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

    fun test() {
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

@Composable
fun RawgSettingsRoute(
    onBack: () -> Unit,
    viewModel: RawgSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    SnackbarEffect(viewModel.messages, snackbarHost)
    RawgSettingsScreen(
        state = state,
        snackbarHost = snackbarHost,
        onBack = onBack,
        onSaveKey = viewModel::saveKey,
        onTest = viewModel::test,
    )
}

@Composable
fun RawgSettingsScreen(
    state: RawgSettingsUiState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onSaveKey: (String) -> Unit,
    onTest: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = { SubMarkTopAppBar(title = stringResource(R.string.integrations_rawg_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SettingsGroup(title = stringResource(R.string.integrations_rawg_group)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.integrations_rawg_explain),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { uriHandler.openUri("https://rawg.io/apidocs") }) {
                        Text(stringResource(R.string.integrations_rawg_get_key))
                    }
                    var key by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = {
                            Text(
                                stringResource(
                                    if (state.keySet) R.string.integrations_ai_key_replace else R.string.integrations_rawg_key,
                                ),
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onSaveKey(key); key = "" }, enabled = key.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(io.github.submark.core.ui.R.string.ui_action_save))
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onTest, enabled = state.keySet && !state.testing, modifier = Modifier.fillMaxWidth()) {
                        if (state.testing) {
                            CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.integrations_ai_test))
                    }
                }
            }
        }
    }
}
