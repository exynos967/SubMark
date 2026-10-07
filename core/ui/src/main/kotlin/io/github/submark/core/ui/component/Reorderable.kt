package io.github.submark.core.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.submark.core.ui.R
import io.github.submark.core.ui.theme.SubMarkTheme
import sh.calvin.reorderable.ReorderableColumn

/**
 * Drag-to-reorder column for short lists (overview/analytics components, categories, tags...).
 * Not lazy: keep it to a few dozen items, and place it inside a scrolling parent if needed.
 *
 * [onMove] is called once per completed drag (and per accessibility "Move up/down" action) with
 * the from/to indices; the caller reorders its list. [itemContent] receives a `dragHandle` modifier
 * to put on a handle (e.g. [DragHandleIcon]); dragging is started from that element only.
 */
@Composable
fun <T> ReorderableItemsColumn(
    items: List<T>,
    key: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    itemContent: @Composable (item: T, isDragging: Boolean, dragHandle: Modifier) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val moveUp = stringResource(R.string.ui_action_move_up)
    val moveDown = stringResource(R.string.ui_action_move_down)
    ReorderableColumn(
        list = items,
        onSettle = { from, to -> if (from != to) onMove(from, to) },
        onMove = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
        modifier = modifier,
        verticalArrangement = verticalArrangement,
    ) { index, item, isDragging ->
        key(key(item)) {
            val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "dragElevation")
            val handle = Modifier.draggableHandle(
                onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
            )
            Surface(
                shadowElevation = elevation,
                shape = MaterialTheme.shapes.medium,
                color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
                modifier = Modifier.semantics {
                    customActions = buildList {
                        if (index > 0) add(CustomAccessibilityAction(moveUp) { onMove(index, index - 1); true })
                        if (index < items.lastIndex) add(CustomAccessibilityAction(moveDown) { onMove(index, index + 1); true })
                    }
                },
            ) {
                itemContent(item, isDragging, handle)
            }
        }
    }
}

/** Standard drag handle icon; pass the `dragHandle` modifier from [ReorderableItemsColumn]. */
@Composable
fun DragHandleIcon(dragHandle: Modifier, modifier: Modifier = Modifier) {
    IconButton(onClick = {}, modifier = modifier.then(dragHandle)) {
        Icon(Icons.Rounded.DragHandle, contentDescription = stringResource(R.string.ui_action_reorder))
    }
}

@Preview(showBackground = true)
@Composable
private fun ReorderablePreview() {
    SubMarkTheme {
        var items by remember { mutableStateOf(listOf("Expense overview", "Coming up", "Recent payments")) }
        Column {
            ReorderableItemsColumn(
                items = items,
                key = { it },
                onMove = { from, to -> items = items.toMutableList().apply { add(to, removeAt(from)) } },
            ) { item, _, handle ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item, Modifier.weight(1f))
                    DragHandleIcon(handle)
                }
            }
        }
    }
}
