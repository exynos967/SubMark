package io.github.submark.core.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.submark.core.ui.R
import io.github.submark.core.ui.theme.SubMarkTheme

/**
 * Two-button confirmation. [destructive] paints the confirm button and icon in the error color.
 * [onConfirm] does not dismiss automatically; callers usually hide the dialog in both callbacks.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = stringResource(R.string.ui_action_confirm),
    dismissLabel: String = stringResource(R.string.ui_action_cancel),
    destructive: Boolean = false,
    icon: ImageVector? = null,
) {
    val accent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { { Icon(it, contentDescription = null, tint = accent) } },
        title = { Text(title) },
        text = message?.let { { Text(it) } },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = accent)) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}

@Preview
@Composable
private fun ConfirmDialogPreview() {
    SubMarkTheme {
        ConfirmDialog(
            title = "Delete subscription?",
            message = "Payment history will be removed too.",
            onConfirm = {},
            onDismiss = {},
            confirmLabel = "Delete",
            destructive = true,
        )
    }
}
