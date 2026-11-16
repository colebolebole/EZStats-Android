package com.ezstats.android.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * One set of springs and curves for the whole app, so every movement has the same weight. Springs keep their
 * velocity when they are interrupted or retargeted (a back swipe in the middle of an opening animation, say),
 * which is what makes motion feel continuous instead of restarting.
 *
 * They are tuned to sit alongside Material 3 Expressive's motion scheme (which the components use): its
 * spatial springs are damped 0.6 to 0.9, so things travel with a small, lively overshoot.
 */
object Motion {
    /** Material's emphasized curves: a quick start that glides into place. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** A screen growing into place: a touch of overshoot, then settles. */
    val open = spring<Float>(dampingRatio = 0.82f, stiffness = 320f)

    /** A screen folding away: critically damped, so it never undershoots its target. */
    val close = spring<Float>(dampingRatio = 1f, stiffness = 420f)

    /** Letting go of a drag, or cancelling a back swipe. */
    val settle = spring<Float>(dampingRatio = 1f, stiffness = 650f)

    /** Small changes to a value that is on screen (the dial's arc, a chevron, a press). */
    val quick = spring<Float>(dampingRatio = 1f, stiffness = 800f)

    /** Something that should feel physical: a bouncy press release, a toggled control. */
    val bouncy = spring<Float>(dampingRatio = 0.6f, stiffness = 800f)

    /** Slow drift for values the server reports (a room's temperature creeping up). */
    val drift = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessVeryLow)

    /** A screen sliding in from the side. */
    val slide = spring<IntOffset>(dampingRatio = 1f, stiffness = 420f, visibilityThreshold = IntOffset.VisibilityThreshold)

    /** Cards finding their new place in the list. */
    val place = spring<IntOffset>(dampingRatio = 0.85f, stiffness = 380f, visibilityThreshold = IntOffset.VisibilityThreshold)

    /** Content growing or shrinking (a home unfolding, a card gaining a line). */
    val size = spring<IntSize>(dampingRatio = 1f, stiffness = 500f, visibilityThreshold = IntSize.VisibilityThreshold)

    /** Colours that follow state (a tile turning orange as it starts heating). */
    val color = tween<Color>(durationMillis = 500, easing = Emphasized)

    /** Fades in and out: in is a little slower than out, so new things arrive gently and old ones get out of the way. */
    fun fadeIn(delay: Int = 0) = tween<Float>(durationMillis = 260, delayMillis = delay, easing = EmphasizedDecelerate)
    fun fadeOut() = tween<Float>(durationMillis = 140, easing = EmphasizedAccelerate)
}
