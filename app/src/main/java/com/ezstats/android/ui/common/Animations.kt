package com.ezstats.android.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.animation.core.VectorConverter
import kotlinx.coroutines.launch
import com.ezstats.android.ui.theme.Motion

/**
 * Text whose characters roll like an odometer when it changes: digits that went up slide up, digits that went
 * down slide down, and the characters that stayed the same don't move at all.
 */
@Composable
fun RollingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    // Each character is keyed by its place counted from the right, so the ones digit stays the ones digit
    // when the number gains or loses a digit.
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        text.forEachIndexed { i, ch ->
            val fromEnd = text.length - i // stable identity per position counted from the right
            androidx.compose.runtime.key(fromEnd) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        val up = (targetState.digitToIntOrNull() ?: 0) >= (initialState.digitToIntOrNull() ?: 0)
                        val dir = if (up) 1 else -1
                        (slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { it * dir } + fadeIn(tween(180)))
                            .togetherWith(slideOutVertically(spring(dampingRatio = 1f, stiffness = 500f)) { -it * dir } + fadeOut(tween(120)))
                            .using(SizeTransform(clip = true))
                    },
                    label = "rolling digit",
                ) { c -> Text(c.toString(), style = style, color = color) }
            }
        }
    }
}

/**
 * Remembers which items have already made their entrance, so a list animates in once when it first appears, and
 * not again when items scroll off screen and back.
 */
class EntranceTracker {
    private val seen = HashSet<Any>()
    private var started = -1L

    /** True the first time it is asked about [key]; false ever after. */
    fun firstTime(key: Any): Boolean = seen.add(key)

    /** The delay for the [index]th item of the first screenful; later items come in without one. */
    fun delayFor(index: Int): Int {
        val now = System.nanoTime()
        if (started < 0) started = now
        val elapsedMs = (now - started) / 1_000_000
        return if (elapsedMs > 600) 0 else (index.coerceAtMost(8) * 45)
    }
}

val LocalEntrance = staticCompositionLocalOf<EntranceTracker?> { null }

/**
 * Fades and rises an item into place the first time it is shown, staggered by [index] so a screenful of cards
 * cascades in. Uses the graphics layer only: no relayout, no recomposition per frame.
 */
fun Modifier.entrance(key: Any, index: Int): Modifier = composed {
    val tracker = LocalEntrance.current
    val first = remember(key) { tracker?.firstTime(key) ?: false }
    if (!first) return@composed this
    val delay = remember(key) { tracker?.delayFor(index) ?: 0 }
    val progress = remember(key) { Animatable(0f) }
    val rise = with(LocalDensity.current) { 24.dp.toPx() }
    LaunchedEffect(key) {
        kotlinx.coroutines.delay(delay.toLong())
        progress.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 260f))
    }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * rise
        val s = 0.96f + 0.04f * p
        scaleX = s
        scaleY = s
    }
}

/** A soft light sweeping across placeholder shapes while the real content loads. */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer x",
    )
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            onDrawWithContent {
                drawContent()
                val w = size.width
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, highlight, Color.Transparent),
                        start = androidx.compose.ui.geometry.Offset(w * x - w * 0.5f, 0f),
                        end = androidx.compose.ui.geometry.Offset(w * x + w * 0.5f, size.height),
                    ),
                    blendMode = BlendMode.SrcAtop,
                )
            }
        }
}

/** Which ways an element can be pulled. */
enum class Pull { HORIZONTAL, BOTH }

/**
 * Rubber-band resistance: follows the finger at first, then less and less, never past [limit].
 * Pulling [d] pixels moves the element this far.
 */
internal fun rubberBand(d: Float, limit: Float): Float {
    if (limit <= 0f) return 0f
    val x = kotlin.math.abs(d) / limit
    return kotlin.math.sign(d) * limit * (1f - 1f / (x * 0.55f + 1f))
}

/**
 * Makes an element springy under the finger: while held, it stretches toward where the finger is pulled (with
 * rubber-band resistance, so it gives a little and no more), and when let go it snaps back with a bounce.
 *
 * It only watches the pointer and never consumes it, so the element still clicks, long-presses and scrolls as
 * before. Elements inside a vertical list should use [Pull.HORIZONTAL], so scrolling the list doesn't drag them
 * along. The pointer is read outside the graphics layer that moves the element, so the movement never feeds
 * back into the pull.
 */
fun Modifier.springyPull(pull: Pull = Pull.BOTH, limit: androidx.compose.ui.unit.Dp = 18.dp): Modifier = composed {
    val offset = remember { Animatable(androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Offset.VectorConverter) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val max = with(LocalDensity.current) { limit.toPx() }
    this
        .pointerInput(pull, max) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var total = androidx.compose.ui.geometry.Offset.Zero
                scope.launch { offset.stop() }
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    total += change.positionChangeIgnoreConsumed()
                    val target = androidx.compose.ui.geometry.Offset(
                        rubberBand(total.x, max),
                        if (pull == Pull.BOTH) rubberBand(total.y, max) else 0f,
                    )
                    scope.launch { offset.snapTo(target) }
                }
                scope.launch { offset.animateTo(androidx.compose.ui.geometry.Offset.Zero, spring(dampingRatio = 0.42f, stiffness = 520f)) }
            }
        }
        .graphicsLayer {
            val o = offset.value
            translationX = o.x
            translationY = o.y
            // A touch of stretch along the pull, so it reads as elastic rather than just sliding.
            if (size.width > 0f && size.height > 0f) {
                scaleX = 1f + kotlin.math.abs(o.x) / size.width * 0.06f
                scaleY = 1f + kotlin.math.abs(o.y) / size.height * 0.06f
            }
        }
}
