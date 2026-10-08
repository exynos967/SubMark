package io.github.submark.feature.subscriptions.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.submark.core.model.Tag
import io.github.submark.core.ui.component.SearchField
import io.github.submark.core.ui.component.TagChip
import io.github.submark.feature.subscriptions.R

/**
 * Searchable multi-select tag sheet. Selection is local until Done.
 * When [onCreate] is set, a query that matches no tag offers "Create tag" (the caller creates it and
 * passes the new tag back through [tags]; [onCreate] returns the created id to select, or null).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagPickerSheet(
    tags: List<Tag>,
    selectedIds: Set<String>,
    onDone: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.subscriptions_tag_picker_title),
    onCreate: (suspend (String) -> String?)? = null,
    emptyMessage: String = stringResource(R.string.subscriptions_tag_picker_empty),
) {
    var selection by rememberSaveable { mutableStateOf(selectedIds.toList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var pendingCreate by rememberSaveable { mutableStateOf<String?>(null) }
    val q = query.trim()
    val filtered = tags.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }.sortedBy { it.name.lowercase() }
    val exact = tags.any { it.name.equals(q, ignoreCase = true) }

    pendingCreate?.let { name ->
        androidx.compose.runtime.LaunchedEffect(name) {
            val id = onCreate?.invoke(name)
            if (id != null) selection = selection + id
            query = ""
            pendingCreate = null
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (selection.isNotEmpty()) {
                TextButton(onClick = { selection = emptyList() }) { Text(stringResource(R.string.subscriptions_tag_picker_clear)) }
            }
            TextButton(onClick = { onDone(selection.toSet()) }) { Text(stringResource(io.github.submark.core.ui.R.string.ui_action_done)) }
        }
        SearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(
                if (onCreate != null) R.string.subscriptions_tag_picker_search_or_create else R.string.subscriptions_tag_picker_search,
            ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onCreate != null && q.isNotEmpty() && !exact) {
                TextButton(onClick = { pendingCreate = q }, enabled = pendingCreate == null) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(stringResource(R.string.subscriptions_tag_picker_create, q), modifier = Modifier.padding(start = 6.dp))
                }
            }
            val selectedTags = tags.filter { it.id in selection }
            if (selectedTags.isNotEmpty()) {
                Text(
                    stringResource(R.string.subscriptions_tag_picker_selected, selectedTags.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectedTags.forEach { tag -> TagChip(tag.name, tag.color, selected = true, onRemove = { selection = selection - tag.id }) }
                }
            }
            Text(
                stringResource(R.string.subscriptions_tag_picker_available),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (filtered.isEmpty()) {
                Text(
                    if (tags.isEmpty()) emptyMessage else stringResource(R.string.subscriptions_tag_picker_no_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filtered.forEach { tag ->
                        val selected = tag.id in selection
                        TagChip(
                            tag.name,
                            tag.color,
                            selected = selected,
                            onClick = { selection = if (selected) selection - tag.id else selection + tag.id },
                        )
                    }
                }
            }
        }
    }
}
