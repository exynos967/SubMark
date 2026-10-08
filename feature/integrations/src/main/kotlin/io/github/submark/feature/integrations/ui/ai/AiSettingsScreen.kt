package io.github.submark.feature.integrations.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.data.settings.AiProvider
import io.github.submark.core.ui.component.SettingsGroup
import io.github.submark.core.ui.component.SettingsSwitchRow
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.feature.integrations.R

@Composable
fun AiSettingsRoute(
    onBack: () -> Unit,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    SnackbarEffect(viewModel.messages, snackbarHost)
    AiSettingsScreen(
        state = state,
        snackbarHost = snackbarHost,
        onBack = onBack,
        onEnabled = viewModel::setEnabled,
        onProvider = viewModel::setProvider,
        onEndpoint = viewModel::setEndpoint,
        onModel = viewModel::setModel,
        onKey = viewModel::setKey,
        onTest = viewModel::testConnection,
    )
}

@Composable
fun AiSettingsScreen(
    state: AiSettingsUiState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onProvider: (AiProvider) -> Unit,
    onEndpoint: (String) -> Unit,
    onModel: (String) -> Unit,
    onKey: (String) -> Unit,
    onTest: () -> Unit,
) {
    Scaffold(
        topBar = { SubMarkTopAppBar(title = stringResource(R.string.integrations_ai_settings_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SettingsGroup {
                SettingsSwitchRow(
                    title = stringResource(R.string.integrations_ai_enable),
                    subtitle = stringResource(R.string.integrations_ai_enable_hint),
                    checked = state.enabled,
                    onCheckedChange = onEnabled,
                )
            }
            SettingsGroup(title = stringResource(R.string.integrations_ai_provider)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow {
                        AiProvider.entries.forEach { provider ->
                            FilterChip(
                                selected = state.provider == provider,
                                onClick = { onProvider(provider) },
                                label = {
                                    Text(
                                        when (provider) {
                                            AiProvider.OPENAI -> stringResource(R.string.integrations_ai_provider_openai)
                                            AiProvider.QWEN -> stringResource(R.string.integrations_ai_provider_qwen)
                                            AiProvider.CUSTOM -> stringResource(R.string.integrations_ai_provider_custom)
                                        },
                                    )
                                },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    var keyInput by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = {
                            Text(
                                stringResource(
                                    if (state.keySet) R.string.integrations_ai_key_replace else R.string.integrations_ai_key,
                                ),
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { onKey(keyInput); keyInput = "" }, enabled = keyInput.isNotBlank()) {
                        Text(stringResource(io.github.submark.core.ui.R.string.ui_action_save))
                    }
                    Text(
                        stringResource(R.string.integrations_ai_key_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.provider != null) {
                SettingsGroup(title = stringResource(R.string.integrations_ai_model_group)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        if (state.models.isNotEmpty()) {
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow {
                                state.models.forEach { model ->
                                    FilterChip(
                                        selected = state.model == model,
                                        onClick = { onModel(model) },
                                        label = { Text(model) },
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        OutlinedTextField(
                            value = state.model,
                            onValueChange = onModel,
                            label = { Text(stringResource(R.string.integrations_ai_model)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.endpoint,
                            onValueChange = onEndpoint,
                            label = { Text(stringResource(R.string.integrations_ai_endpoint)) },
                            placeholder = { Text(state.defaultEndpoint.ifBlank { "https://your-gateway/v1" }) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onTest,
                            enabled = !state.testing && state.keySet && state.model.isNotBlank() && state.endpoint.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
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
}
