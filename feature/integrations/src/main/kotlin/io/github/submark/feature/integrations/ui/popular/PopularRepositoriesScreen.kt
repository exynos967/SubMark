package io.github.submark.feature.integrations.ui.popular

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.model.PopularRepository
import io.github.submark.core.ui.component.ConfirmDialog
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.core.ui.format.asString
import io.github.submark.core.ui.util.SnackbarEffect
import io.github.submark.feature.integrations.R

@Composable
fun PopularRepositoriesRoute(
    onBack: () -> Unit,
    viewModel: PopularRepositoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    SnackbarEffect(viewModel.messages, snackbarHost)
    PopularRepositoriesScreen(
        state = state,
        snackbarHost = snackbarHost,
        onBack = onBack,
        onAdd = { viewModel.openForm() },
        onEdit = { viewModel.openForm(it) },
        onToggle = viewModel::setEnabled,
        onMove = viewModel::move,
        onDelete = { viewModel.askDelete(it) },
        onDismissDelete = { viewModel.askDelete(null) },
        onConfirmDelete = viewModel::delete,
        onAskClearCache = viewModel::askClearCache,
        onClearCache = viewModel::clearCache,
        onFormChange = viewModel::editForm,
        onFormValidate = viewModel::validateUrl,
        onFormSave = viewModel::save,
        onFormDismiss = viewModel::closeForm,
    )
}

@Composable
fun PopularRepositoriesScreen(
    state: RepositoriesUiState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (PopularRepository) -> Unit,
    onToggle: (PopularRepository, Boolean) -> Unit,
    onMove: (PopularRepository, Int) -> Unit,
    onDelete: (PopularRepository) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onAskClearCache: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onFormChange: (String?, String?, String?) -> Unit,
    onFormValidate: () -> Unit,
    onFormSave: () -> Unit,
    onFormDismiss: () -> Unit,
) {
    Scaffold(
        topBar = {
            SubMarkTopAppBar(
                title = stringResource(R.string.integrations_repo_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { onAskClearCache(true) }) {
                        Icon(Icons.Rounded.Cached, contentDescription = stringResource(R.string.integrations_repo_clear_cache))
                    }
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.integrations_popular_add_repository))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        if (state.repositories.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.integrations_repo_empty),
                message = stringResource(R.string.integrations_popular_no_repo_message),
                actionLabel = stringResource(R.string.integrations_popular_add_repository),
                onAction = onAdd,
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
            ) {
                itemsIndexed(state.repositories, key = { _, r -> r.id }) { index, repo ->
                    RepositoryRow(
                        repo = repo,
                        canMoveUp = index > 0,
                        canMoveDown = index < state.repositories.lastIndex,
                        onToggle = { onToggle(repo, it) },
                        onMove = { onMove(repo, it) },
                        onEdit = { onEdit(repo) },
                        onDelete = { onDelete(repo) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    state.confirmDelete?.let { repo ->
        ConfirmDialog(
            title = stringResource(R.string.integrations_repo_delete_title),
            message = stringResource(R.string.integrations_repo_delete_message, repo.name),
            confirmLabel = stringResource(io.github.submark.core.ui.R.string.ui_action_delete),
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDelete,
            destructive = true,
        )
    }
    if (state.confirmClearCache) {
        ConfirmDialog(
            title = stringResource(R.string.integrations_repo_clear_cache),
            message = stringResource(R.string.integrations_repo_clear_cache_message),
            onConfirm = onClearCache,
            onDismiss = { onAskClearCache(false) },
        )
    }
    if (state.form.visible) {
        RepositoryFormSheet(
            form = state.form,
            onChange = onFormChange,
            onValidate = onFormValidate,
            onSave = onFormSave,
            onDismiss = onFormDismiss,
        )
    }
}

@Composable
private fun RepositoryRow(
    repo: PopularRepository,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(repo.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    repo.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val status = repo.lastError
                Text(
                    if (status != null) {
                        stringResource(R.string.integrations_repo_status_error, status)
                    } else {
                        stringResource(
                            R.string.integrations_repo_status_ok,
                            repo.entryCount,
                            repo.lastFetchedAt?.toString()?.take(10)
                                ?: stringResource(R.string.integrations_repo_never_fetched),
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (status != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                Icon(Icons.Rounded.ArrowUpward, contentDescription = stringResource(R.string.integrations_cd_move_up))
            }
            IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                Icon(Icons.Rounded.ArrowDownward, contentDescription = stringResource(R.string.integrations_cd_move_down))
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = stringResource(io.github.submark.core.ui.R.string.ui_action_edit))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(io.github.submark.core.ui.R.string.ui_action_delete))
            }
            Switch(checked = repo.enabled, onCheckedChange = onToggle)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepositoryFormSheet(
    form: RepoFormState,
    onChange: (String?, String?, String?) -> Unit,
    onValidate: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                stringResource(
                    if (form.editing != null) R.string.integrations_repo_edit else R.string.integrations_popular_add_repository,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = form.name,
                onValueChange = { onChange(it, null, null) },
                label = { Text(stringResource(R.string.integrations_repo_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = form.url,
                onValueChange = { onChange(null, it, null) },
                label = { Text(stringResource(R.string.integrations_repo_url)) },
                placeholder = { Text("https://example.com/subscriptions.json") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = form.description,
                onValueChange = { onChange(null, null, it) },
                label = { Text(stringResource(R.string.integrations_repo_description)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            form.validationError?.let {
                Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            form.validationOk?.let {
                Text(
                    stringResource(R.string.integrations_repo_validate_ok, it),
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onValidate, enabled = !form.validating && form.url.isNotBlank()) {
                    if (form.validating) {
                        CircularProgressIndicator(Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.integrations_repo_validate))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(io.github.submark.core.ui.R.string.ui_action_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSave, enabled = !form.saving && form.name.isNotBlank() && form.url.isNotBlank()) {
                    Text(stringResource(io.github.submark.core.ui.R.string.ui_action_save))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
