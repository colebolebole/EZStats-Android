package com.ezstats.android.ui

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset

/**
 * One set of springs for the whole app, so every movement has the same weight. Springs keep their velocity when
 * they are interrupted or retargeted (a back swipe in the middle of an opening animation, say), which is what
 * makes motion feel continuous instead of restarting.
 */
object Motion {
    /** A screen growing into place: a touch of overshoot, then settles. */
    val open = spring<Float>(dampingRatio = 0.88f, stiffness = 330f)

    /** A screen folding away: critically damped, so it never undershoots its target. */
    val close = spring<Float>(dampingRatio = 1f, stiffness = 460f)

    /** Letting go of a drag, or cancelling a back swipe. */
    val settle = spring<Float>(dampingRatio = 1f, stiffness = 650f)

    /** Small changes to a value that is on screen (the dial's arc, a chevron, a press). */
    val quick = spring<Float>(dampingRatio = 1f, stiffness = 800f)

    /** A screen sliding in from the side. */
    val slide = spring<IntOffset>(dampingRatio = 1f, stiffness = 420f, visibilityThreshold = IntOffset.VisibilityThreshold)

    /** Cards finding their new place in the list. */
    val place = spring<IntOffset>(dampingRatio = 0.82f, stiffness = 380f, visibilityThreshold = IntOffset.VisibilityThreshold)
}
