package io.github.submark.core.ui.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing

/**
 * Back navigation modelled on the system's cross-activity back animation. The back gesture scrubs the pop transition
 * by finger position, so its first half is the preview the finger drags (the page shrinks and nudges toward the
 * gesture, the page below eases in from the left under a fading scrim) and its second half is the commit glide,
 * which carries the page on off-screen in the same direction once the finger lets go.
 */
object BackMotion {
    const val DURATION_MS = 400

    /** Share of the transition the finger preview takes; the commit glide covers the rest. */
    private const val PREVIEW = 0.5f

    const val LEAVING_SCALE = 0.9f

    /** The previous page starts this far left, as a share of the width. */
    const val ENTERING_SHIFT = 0.1f

    const val SCRIM_ALPHA = 0.2f

    /** Gesture feedback should be most visible at the start (Material: decelerate the gesture progress). */
    private val PreviewEasing = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** Reaches the full value at the end of the preview and holds it through the commit. */
    val previewOnly = Easing { t -> PreviewEasing.transform((t / PREVIEW).coerceAtMost(1f)) }

    /** [previewShare] of the way during the preview, the rest during the commit glide. */
    fun previewThenCommit(previewShare: Float) = Easing { t ->
        if (t <= PREVIEW) {
            previewShare * PreviewEasing.transform(t / PREVIEW)
        } else {
            previewShare + (1f - previewShare) * FastOutSlowInEasing.transform((t - PREVIEW) / (1f - PREVIEW))
        }
    }

    /** The leaving page only nudges during the preview and leaves the screen on commit. */
    val leavingSlide = previewThenCommit(0.08f)

    val enteringSlide = previewThenCommit(0.3f)
}

/** Set while the back stack is popping, so page frames dress up only back navigation. */
class NavPopState {
    var popping: Boolean = false
}
