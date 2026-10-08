package io.github.submark.feature.integrations.ui.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.ErrorState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.format.MoneyFormatter
import io.github.submark.core.ui.format.labelRes
import io.github.submark.core.ui.navigation.SubscriptionEditRoute
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.feature.integrations.R
import io.github.submark.feature.integrations.data.ai.RecognitionResult

@Composable
fun AiRecognitionRoute(
    onBack: () -> Unit,
    onNavigate: (Any) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: AiRecognitionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    SnackbarEffect(viewModel.messages, snackbarHost)
    androidx.compose.runtime.LaunchedEffect(viewModel.prefill) {
        viewModel.prefill.collect { json -> onNavigate(SubscriptionEditRoute(prefillJson = json)) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onImagePicked(uri)
    }
    AiRecognitionScreen(
        state = state,
        snackbarHost = snackbarHost,
        onBack = onBack,
        onPickImage = {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onRetry = { state.imagePath?.let(viewModel::analyze) },
        onToggle = viewModel::toggle,
        onSelectAll = viewModel::setAll,
        onUse = viewModel::useFirst,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
fun AiRecognitionScreen(
    state: AiRecognitionUiState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onPickImage: () -> Unit,
    onRetry: () -> Unit,
    onToggle: (Int) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onUse: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = { SubMarkTopAppBar(title = stringResource(R.string.integrations_ai_recognition_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        when {
            state.analyzing -> LoadingState(Modifier.padding(padding), stringResource(R.string.integrations_ai_analyzing))
            state.error != null && state.results.isEmpty() -> Column(Modifier.padding(padding).fillMaxSize()) {
                ErrorState(message = state.error.asString(), onRetry = onRetry, modifier = Modifier.weight(1f))
                PickButton(onPickImage, onOpenSettings, Modifier.padding(16.dp))
            }
            state.results.isEmpty() && state.imagePath == null -> Column(Modifier.padding(padding).fillMaxSize()) {
                EmptyState(
                    title = stringResource(R.string.integrations_ai_pick_title),
                    message = stringResource(R.string.integrations_ai_pick_message),
                    actionLabel = stringResource(R.string.integrations_ai_pick),
                    onAction = onPickImage,
                    modifier = Modifier.weight(1f),
                )
                PickButton(onPickImage, onOpenSettings, Modifier.padding(16.dp))
            }
            state.results.isEmpty() -> Column(Modifier.padding(padding).fillMaxSize()) {
                EmptyState(
                    title = stringResource(R.string.integrations_ai_no_results),
                    modifier = Modifier.weight(1f),
                )
                PickButton(onPickImage, onOpenSettings, Modifier.padding(16.dp))
            }
            else -> ResultList(state, padding, onToggle, onSelectAll, onUse, onPickImage)
        }
    }
}

@Composable
private fun PickButton(onPickImage: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Button(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.integrations_ai_pick))
        }
        TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.integrations_ai_open_settings))
        }
    }
}

@Composable
private fun ResultList(
    state: AiRecognitionUiState,
    padding: PaddingValues,
    onToggle: (Int) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onUse: () -> Unit,
    onPickImage: () -> Unit,
) {
    val remaining = state.remaining
    Column(Modifier.padding(padding).fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { onSelectAll(true) }) { Text(stringResource(R.string.integrations_ai_select_all)) }
            TextButton(onClick = { onSelectAll(false) }) { Text(stringResource(R.string.integrations_ai_clear)) }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(state.results, key = { index, _ -> index }) { index, result ->
                val used = index in state.selected && state.selected.sorted().indexOf(index) < state.consumed
                ResultRow(result, index in state.selected, used, onClick = { onToggle(index) })
            }
        }
        if (state.error != null) {
            Text(
                state.error.asString(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Column(Modifier.padding(16.dp)) {
            Button(onClick = onUse, enabled = remaining.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (remaining.size > 1) {
                        stringResource(R.string.integrations_ai_use_next, remaining.size)
                    } else {
                        stringResource(R.string.integrations_ai_use)
                    },
                )
            }
            TextButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.integrations_ai_pick_another))
            }
        }
    }
}

@Composable
private fun ResultRow(result: RecognitionResult, selected: Boolean, used: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = when {
                used -> MaterialTheme.colorScheme.surfaceContainerHighest
                selected -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = null, enabled = !used)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    result.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (used) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Row {
                    if (result.price != null && result.currency != null) {
                        Text(
                            MoneyFormatter.format(result.price, result.currency) +
                                (result.billingCycle?.let { " · " + stringResource(it.labelRes) } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (result.isLifetime) {
                        Text(
                            " · " + stringResource(io.github.submark.core.ui.R.string.ui_cycle_lifetime),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                result.category?.let {
                    Text(
                        stringResource(categoryLabelRes(it)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            result.confidence?.let {
                Text(
                    stringResource(R.string.integrations_ai_confidence, (it * 100).toInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (it >= 0.7) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun categoryLabelRes(category: io.github.submark.core.model.SystemCategory): Int = when (category) {
    io.github.submark.core.model.SystemCategory.VIDEO -> io.github.submark.core.ui.R.string.ui_category_video
    io.github.submark.core.model.SystemCategory.MUSIC -> io.github.submark.core.ui.R.string.ui_category_music
    io.github.submark.core.model.SystemCategory.ENTERTAINMENT -> io.github.submark.core.ui.R.string.ui_category_entertainment
    io.github.submark.core.model.SystemCategory.GAMING -> io.github.submark.core.ui.R.string.ui_category_gaming
    io.github.submark.core.model.SystemCategory.PRODUCTIVITY -> io.github.submark.core.ui.R.string.ui_category_productivity
    io.github.submark.core.model.SystemCategory.UTILITY -> io.github.submark.core.ui.R.string.ui_category_utility
    io.github.submark.core.model.SystemCategory.AI -> io.github.submark.core.ui.R.string.ui_category_ai
    io.github.submark.core.model.SystemCategory.NEWS -> io.github.submark.core.ui.R.string.ui_category_news
    io.github.submark.core.model.SystemCategory.LIFESTYLE -> io.github.submark.core.ui.R.string.ui_category_lifestyle
    io.github.submark.core.model.SystemCategory.OTHER -> io.github.submark.core.ui.R.string.ui_category_other
}
