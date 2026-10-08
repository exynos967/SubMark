package io.github.submark.feature.subscriptions.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavBackStackEntry
import io.github.submark.core.ui.icon.IconChoice
import io.github.submark.core.ui.navigation.NavResults

/**
 * Delivers icons returned by `IconPickerRoute` to [onResult]. The picker writes into the previous entry's
 * SavedStateHandle, i.e. [backStackEntry] of the screen that opened it; the value is cleared once consumed.
 */
@Composable
fun IconResultEffect(backStackEntry: NavBackStackEntry, onResult: (IconChoice) -> Unit) {
    val callback = rememberUpdatedState(onResult)
    LaunchedEffect(backStackEntry) {
        val handle = backStackEntry.savedStateHandle
        handle.getStateFlow<String?>(NavResults.ICON, null).collect { encoded ->
            if (encoded != null) {
                handle[NavResults.ICON] = null
                IconChoice.decode(encoded)?.let { callback.value(it) }
            }
        }
    }
}
