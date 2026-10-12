package io.github.submark.core.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * [composable] whose page turns into a card (rounded corners and a shadow) while it leaves. A page shrinking away
 * under the back gesture then stands out from the page below instead of blending into the same background.
 */
inline fun <reified T : Any> NavGraphBuilder.screen(
    deepLinks: List<NavDeepLink> = emptyList(),
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<T>(deepLinks = deepLinks) { entry -> LeavingPageFrame { content(entry) } }
}

@Composable
fun AnimatedContentScope.LeavingPageFrame(content: @Composable () -> Unit) {
    // Same length and easing as the app's back transition, so the corners grow with the finger.
    val leaving = transition.animateFloat(transitionSpec = { tween(LEAVE_MS, easing = LinearEasing) }, label = "leaving") {
        if (it == EnterExitState.PostExit) 1f else 0f
    }
    Box(
        Modifier.graphicsLayer {
            val progress = leaving.value
            if (progress > 0f) {
                shape = RoundedCornerShape(CORNER.toPx() * progress)
                clip = true
                shadowElevation = ELEVATION.toPx() * progress
            }
        },
    ) { content() }
}

// Keep in step with NavMotion.DURATION in :app.
private const val LEAVE_MS = 300
private val CORNER = 28.dp
private val ELEVATION = 8.dp
