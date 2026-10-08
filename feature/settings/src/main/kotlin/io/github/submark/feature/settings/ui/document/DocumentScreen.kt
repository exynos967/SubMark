package io.github.submark.feature.settings.ui.document

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.submark.core.ui.component.EmptyState
import io.github.submark.core.ui.component.ErrorState
import io.github.submark.core.ui.component.LoadingState
import io.github.submark.core.ui.component.SubMarkTopAppBar
import io.github.submark.feature.settings.R
import io.github.submark.feature.settings.data.DocumentType
import kotlinx.coroutines.launch

@Composable
internal fun DocumentScreenRoute(onBack: () -> Unit, viewModel: DocumentViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val language = stringResource(R.string.settings_doc_language)
    LaunchedEffect(language) { viewModel.load(language) }
    DocumentScreen(
        type = viewModel.type,
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onToggle = viewModel::toggle,
        onSetAllExpanded = viewModel::setAllExpanded,
    )
}

@Composable
internal fun DocumentScreen(
    type: DocumentType?,
    state: DocumentUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggle: (String) -> Unit,
    onSetAllExpanded: (Boolean) -> Unit,
) {
    val fallbackTitle = stringResource(
        when (type) {
            DocumentType.FAQ -> R.string.settings_faq
            DocumentType.PRIVACY -> R.string.settings_privacy
            DocumentType.TERMS -> R.string.settings_terms
            null -> R.string.settings_document
        },
    )
    val listState = rememberLazyListState()
    Scaffold(
        topBar = {
            SubMarkTopAppBar(
                title = (state as? DocumentUiState.Content)?.document?.title ?: fallbackTitle,
                onBack = onBack,
                actions = {
                    if (state is DocumentUiState.Content && state.allKeys.isNotEmpty()) {
                        val expandAll = !state.allExpanded
                        IconButton(onClick = { onSetAllExpanded(expandAll) }) {
                            Icon(
                                if (expandAll) Icons.Rounded.UnfoldMore else Icons.Rounded.UnfoldLess,
                                stringResource(if (expandAll) R.string.settings_doc_expand_all else R.string.settings_doc_collapse_all),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            DocumentUiState.Loading -> LoadingState(Modifier.padding(padding), stringResource(R.string.settings_doc_loading))
            DocumentUiState.NotFound -> EmptyState(
                title = stringResource(R.string.settings_doc_not_found),
                icon = Icons.Rounded.Description,
                modifier = Modifier.padding(padding),
            )
            is DocumentUiState.Error -> ErrorState(
                message = stringResource(R.string.settings_doc_load_failed),
                onRetry = onRetry,
                modifier = Modifier.padding(padding),
            )
            is DocumentUiState.Content -> DocumentContent(state, listState, PaddingValues(), onToggle, Modifier.padding(padding))
        }
    }
}

@Composable
private fun DocumentContent(
    state: DocumentUiState.Content,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onToggle: (String) -> Unit,
    modifier: Modifier,
) {
    val doc = state.document
    val scope = rememberCoroutineScope()
    // Item indexes: 0 = header, then one item per section.
    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
        item(key = "header") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                doc.intro?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (state.allKeys.isNotEmpty()) {
                    Text(
                        stringResource(R.string.settings_doc_faq_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                doc.updated?.let {
                    Text(
                        stringResource(R.string.settings_doc_updated, it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (doc.sections.size > 2) {
                    Text(
                        stringResource(R.string.settings_doc_contents),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        doc.sections.forEachIndexed { i, section ->
                            AssistChip(
                                onClick = { scope.launch { listState.animateScrollToItem(i + 1) } },
                                label = { Text(section.title) },
                            )
                        }
                    }
                }
            }
        }
        doc.sections.forEachIndexed { s, section ->
            item(key = "section_$s") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    section.content?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    section.items.forEachIndexed { i, item ->
                        val key = DocumentUiState.key(s, i)
                        FaqCard(item.question, item.answer, key in state.expanded) { onToggle(key) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FaqCard(question: String, answer: String, expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val stateText = stringResource(if (expanded) R.string.settings_doc_expanded else R.string.settings_doc_collapsed)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = stateText }
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(question, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(rotation))
            }
            AnimatedVisibility(expanded) {
                Text(
                    answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
