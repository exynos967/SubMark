package io.github.submark.core.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** Provided by the app shell; tells page frames whether the running transition is a back navigation. */
val LocalNavPopState = staticCompositionLocalOf { NavPopState() }

/**
 * [composable] whose page takes part in back navigation (see [BackMotion]): the leaving page becomes a card with
 * rounded corners and a shadow, the page below starts under a scrim that clears as it comes forward.
 */
inline fun <reified T : Any> NavGraphBuilder.screen(
    deepLinks: List<NavDeepLink> = emptyList(),
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<T>(deepLinks = deepLinks) { entry -> BackPageFrame { content(entry) } }
}

@Composable
fun AnimatedContentScope.BackPageFrame(content: @Composable () -> Unit) {
    val pop = LocalNavPopState.current
    // Linear fractions of this page's transition; the back gesture scrubs them with the finger.
    val leaving = transition.animateFloat(transitionSpec = { tween(BackMotion.DURATION_MS, easing = LinearEasing) }, label = "leaving") {
        if (it == EnterExitState.PostExit) 1f else 0f
    }
    val entering = transition.animateFloat(transitionSpec = { tween(BackMotion.DURATION_MS, easing = LinearEasing) }, label = "entering") {
        if (it == EnterExitState.PreEnter) 0f else 1f
    }
    Box(
        Modifier
            // Read the animated values before checking [NavPopState.popping], a plain flag: a block that skips the read
            // is not re-run when they change.
            .graphicsLayer {
                val progress = leaving.value
                val card = if (pop.popping) BackMotion.previewOnly.transform(progress) else 0f
                if (card > 0f) {
                    shape = RoundedCornerShape(CORNER.toPx() * card)
                    clip = true
                    shadowElevation = ELEVATION.toPx() * card
                }
            }
            .drawWithContent {
                drawContent()
                val progress = entering.value
                if (pop.popping && progress < 1f) {
                    val scrim = BackMotion.SCRIM_ALPHA * (1f - BackMotion.enteringSlide.transform(progress))
                    drawRect(Color.Black, alpha = scrim)
                }
            },
    ) { content() }
}

private val CORNER = 28.dp
private val ELEVATION = 8.dp
