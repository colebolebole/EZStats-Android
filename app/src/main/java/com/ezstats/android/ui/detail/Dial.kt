package com.ezstats.android.ui.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezstats.android.data.Device
import com.ezstats.android.ui.common.LocalUnits
import com.ezstats.android.ui.common.RollingText
import com.ezstats.android.ui.theme.CoolColor
import com.ezstats.android.ui.theme.HeatColor
import com.ezstats.android.ui.theme.HeroNumber
import com.ezstats.android.ui.theme.Motion
import com.ezstats.android.ui.theme.numeric
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** The range a thermostat's target can be set within, in °F. */
fun targetRange(d: Device) = if (d.pool) 75.0 to 104.0 else 60.0 to 85.0

private const val START = 135f
private const val SWEEP = 270f

/** The colour of a thermostat's arc: orange heating, blue cooling, the theme's colour idle, grey off. */
@Composable
fun dialColor(d: Device): Color {
    val scheme = MaterialTheme.colorScheme
    val target = when {
        !d.enabled -> scheme.outline
        d.mode == "heating" -> HeatColor
        d.mode == "cooling" -> CoolColor
        else -> scheme.primary
    }
    val c by animateColorAsState(target, Motion.color, label = "dial colour")
    return c
}

private fun DrawScope.arcPoint(center: Offset, radius: Float, f: Float): Offset {
    val a = Math.toRadians((START + SWEEP * f).toDouble())
    return Offset(center.x + radius * cos(a).toFloat(), center.y + radius * sin(a).toFloat())
}

/**
 * The full-size 270° dial: the target is the arc and the thumb, the room's temperature the small marker on it.
 * Drag or tap to set the target when [interactive]; [onChange] gets the °F under the finger (the caller snaps it).
 */
@Composable
fun ThermostatDial(
    d: Device,
    target: Double,
    interactive: Boolean,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    maxSize: Dp = 320.dp,
) {
    val units = LocalUnits.current
    val scheme = MaterialTheme.colorScheme
    val (minF, maxF) = targetRange(d)
    val arc = dialColor(d)
    val track = scheme.surfaceContainerHighest
    val marker = scheme.onSurface
    val surface = scheme.surface
    val frac by animateFloatAsState(((target - minF) / (maxF - minF)).coerceIn(0.0, 1.0).toFloat(), Motion.quick, label = "dial target")
    val roomFrac by animateFloatAsState(
        d.roomF?.let { ((it - minF) / (maxF - minF)).coerceIn(0.0, 1.0).toFloat() } ?: -1f,
        Motion.drift,
        label = "dial room",
    )
    // The thumb swells while it is held, and springs back when let go.
    val grab = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val active = d.enabled && (d.mode == "heating" || d.mode == "cooling")
    // While it is working, a soft glow breathes behind the arc.
    val breath = rememberInfiniteTransition(label = "breath")
    val glow by breath.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(tween(2200, easing = Motion.Emphasized), RepeatMode.Reverse),
        label = "glow",
    )
    val glowShown by animateFloatAsState(if (active) 1f else 0f, Motion.drift, label = "glow on")
    val latest by rememberUpdatedState(onChange)
    val haptic = LocalHapticFeedback.current

    fun pick(p: Offset, size: IntSize) {
        val dx = p.x - size.width / 2f
        val dy = p.y - size.height / 2f
        var deg = Math.toDegrees(atan2(dy, dx).toDouble())
        if (deg < 0) deg += 360.0
        var off = (deg - START + 360) % 360
        if (off > SWEEP) off = if (off > SWEEP + (360 - SWEEP) / 2) 0.0 else SWEEP.toDouble() // the gap snaps to the nearer end
        latest(minF + off / SWEEP * (maxF - minF))
    }

    Box(
        modifier
            .widthIn(max = maxSize)
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics { contentDescription = "Target ${units.target(target)} degrees, room ${units.one(d.roomF)} degrees" }
            .pointerInput(interactive, minF, maxF) {
                if (interactive) detectTapGestures { pick(it, size) }
            }
            .pointerInput(interactive, minF, maxF) {
                if (interactive) {
                    detectDragGestures(
                        onDragStart = {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) // you've got hold of it
                            scope.launch { grab.animateTo(1f, Motion.bouncy) }
                            pick(it, size)
                        },
                        onDragEnd = { scope.launch { grab.animateTo(0f, Motion.bouncy) } },
                        onDragCancel = { scope.launch { grab.animateTo(0f, Motion.bouncy) } },
                    ) { change, _ ->
                        change.consume()
                        pick(change.position, size)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 22.dp.toPx()
            val diameter = size.minDimension - stroke * 2.4f
            val radius = diameter / 2
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)
            val center = Offset(size.width / 2, size.height / 2)

            // Ticks around the outside, one per degree of the range; every fifth longer.
            val steps = (maxF - minF).toInt()
            for (i in 0..steps) {
                val f = i.toFloat() / steps
                val long = i % 5 == 0
                val a = Math.toRadians((START + SWEEP * f).toDouble())
                val r1 = radius + stroke * 0.85f
                val r2 = r1 + if (long) 7.dp.toPx() else 4.dp.toPx()
                drawLine(
                    color = if (f <= frac && d.enabled) arc.copy(alpha = 0.55f) else track,
                    start = Offset(center.x + r1 * cos(a).toFloat(), center.y + r1 * sin(a).toFloat()),
                    end = Offset(center.x + r2 * cos(a).toFloat(), center.y + r2 * sin(a).toFloat()),
                    strokeWidth = if (long) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            drawArc(track, START, SWEEP, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            // How long the fill is, against the thumb that sits on its end: shorter than the thumb, it would only
            // peek out from under it as a stray sliver, so it isn't drawn at all.
            val fillLength = (Math.toRadians((SWEEP * frac).toDouble()) * radius).toFloat()
            val showFill = frac > 0f && (!d.enabled || fillLength > stroke * 1.6f)
            if (showFill && glowShown > 0f) {
                drawArc(
                    arc.copy(alpha = glow * glowShown), START, SWEEP * frac, false, topLeft, arcSize,
                    style = Stroke(stroke * 2.2f, cap = StrokeCap.Round),
                )
            }
            if (showFill) {
                // A sweep gradient, lighter at the start, so the arc reads as a quantity filling up. The gradient
                // wraps around the circle, and the rounded cap at the start reaches back past 0° into its far end:
                // the far end is the same light colour, so that cap matches the start instead of showing as a
                // dark blob.
                val light = arc.copy(alpha = 0.45f)
                val end = (SWEEP * frac / 360f).coerceIn(0.01f, 0.98f)
                rotate(START, center) {
                    drawArc(
                        Brush.sweepGradient(
                            0f to light,
                            end to arc,
                            (end + (1f - end) * 0.5f) to arc,
                            1f to light,
                            center = center,
                        ),
                        0f, SWEEP * frac, false, topLeft, arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            if (roomFrac >= 0f) {
                val p = arcPoint(center, radius, roomFrac)
                drawCircle(surface, stroke * 0.36f, p)
                drawCircle(marker, stroke * 0.2f, p)
            }
            if (d.enabled) {
                val p = arcPoint(center, radius, frac)
                val g = grab.value
                drawCircle(arc.copy(alpha = 0.25f * g), stroke * (0.9f + 0.7f * g), p)
                drawCircle(surface, stroke * (0.78f + 0.18f * g), p)
                drawCircle(arc, stroke * (0.56f + 0.14f * g), p)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when {
                    !d.enabled -> "Off"
                    d.mode == "cooling" -> "Cooling to"
                    d.mode == "heating" -> "Heating to"
                    else -> "Set to"
                },
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.Top) {
                RollingText(if (d.enabled) units.target(target) else "–", style = HeroNumber)
                if (d.enabled) Text("°", style = HeroNumber.copy(fontSize = 64.sp))
            }
            Text(
                "Now ${units.one(d.roomF)}°${units.symbol}",
                style = MaterialTheme.typography.titleMedium.numeric,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** The small, read-only dial on a dashboard tile: the arc and the target in its middle. */
@Composable
fun MiniDial(d: Device, modifier: Modifier = Modifier) {
    val units = LocalUnits.current
    val (minF, maxF) = targetRange(d)
    val arc = dialColor(d)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val marker = MaterialTheme.colorScheme.onSurface
    val target = d.targetF ?: ((minF + maxF) / 2)
    val frac by animateFloatAsState(
        if (d.enabled) ((target - minF) / (maxF - minF)).coerceIn(0.0, 1.0).toFloat() else 0f,
        spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "mini target",
    )
    val roomFrac by animateFloatAsState(
        d.roomF?.let { ((it - minF) / (maxF - minF)).coerceIn(0.0, 1.0).toFloat() } ?: -1f,
        Motion.drift,
        label = "mini room",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)
            val center = Offset(size.width / 2, size.height / 2)
            drawArc(track, START, SWEEP, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (frac > 0f) drawArc(arc, START, SWEEP * frac, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (roomFrac >= 0f) drawCircle(marker, stroke * 0.22f, arcPoint(center, diameter / 2, roomFrac))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RollingText(if (d.enabled) units.target(target) else "Off", style = MaterialTheme.typography.titleLarge.numeric)
            if (d.enabled) Text("target", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
