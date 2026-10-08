package io.github.submark.core.ui.util

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.submark.core.ui.format.UiText
import kotlinx.coroutines.flow.Flow

/** One-off snackbar request emitted by a ViewModel (via `Channel(...).receiveAsFlow()`). */
data class SnackbarMessage(
    val text: UiText,
    val actionLabel: UiText? = null,
    val duration: SnackbarDuration = SnackbarDuration.Short,
    /** Called when the user taps the action. */
    val onAction: (() -> Unit)? = null,
)

/** Collects [messages] while composed and shows each one in [hostState]. */
@Composable
fun SnackbarEffect(messages: Flow<SnackbarMessage>, hostState: SnackbarHostState) {
    val context = LocalContext.current
    val currentHost = rememberUpdatedState(hostState)
    LaunchedEffect(messages) {
        messages.collect { msg ->
            val result = currentHost.value.showSnackbar(
                message = msg.text.asString(context),
                actionLabel = msg.actionLabel?.asString(context),
                duration = msg.duration,
                withDismissAction = msg.duration == SnackbarDuration.Indefinite,
            )
            if (result == SnackbarResult.ActionPerformed) msg.onAction?.invoke()
        }
    }
}

/** Appends the modifier built by [block] only when [condition] holds; the existing chain is always kept. */
inline fun Modifier.thenIf(condition: Boolean, block: Modifier.() -> Modifier): Modifier =
    if (condition) then(Modifier.block()) else this
