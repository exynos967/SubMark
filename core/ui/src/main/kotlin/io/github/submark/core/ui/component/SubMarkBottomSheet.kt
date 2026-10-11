package io.github.submark.core.ui.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.delay

/**
 * [ModalBottomSheet] that lets an open keyboard leave first. Shown right away, the sheet rises hidden under the
 * keyboard until its window takes focus, then the keyboard vanishes at once and the sheet seems to lag and jump in.
 * Clearing the focus slides the keyboard away instead, and the sheet rises as it goes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SubMarkBottomSheet(
    onDismissRequest: () -> Unit,
    skipPartiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current
    // Where the keyboard is heading: 0 as soon as it starts to hide, so the sheet rises while it slides away.
    val imeVisible = WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current) > 0
    var show by remember { mutableStateOf(!imeVisible) }
    LaunchedEffect(Unit) {
        if (show) return@LaunchedEffect
        focusManager.clearFocus()
        // A keyboard that does not go away (e.g. owned by another window) must not keep the sheet hidden.
        delay(KEYBOARD_WAIT_MS)
        show = true
    }
    LaunchedEffect(imeVisible) {
        if (!imeVisible) show = true
    }
    if (show) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded),
            content = content,
        )
    }
}

private const val KEYBOARD_WAIT_MS = 400L
