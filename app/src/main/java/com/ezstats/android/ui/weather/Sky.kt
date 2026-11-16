package com.ezstats.android.ui.weather

import com.ezstats.android.ui.theme.LocalIsDark
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.ezstats.android.data.Station
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/** What the sky is doing, for the backdrop of a station. */
enum class Sky(val top: Color, val bottom: Color) {
    CLEAR(Color(0xFF1F6FD6), Color(0xFF5FB8F0)),
    CLOUDY(Color(0xFF4E5D72), Color(0xFF8495AA)),
    RAIN(Color(0xFF22313F), Color(0xFF486580)),
    THUNDER(Color(0xFF17142A), Color(0xFF3C3466)),
    SNOW(Color(0xFF41618A), Color(0xFF9FB7D0)),
    OFFLINE(Color(0xFF30353C), Color(0xFF555C66)),
}

fun skyOf(sky: String?): Sky = when (sky) {
    "clear" -> Sky.CLEAR
    "rain" -> Sky.RAIN
    "thunder" -> Sky.THUNDER
    "snow" -> Sky.SNOW
    null -> Sky.OFFLINE
    else -> Sky.CLOUDY
}

/** The sky behind a station: today's forecast, or a grey one when it isn't forecasting. */
fun skyOf(s: Station?): Sky = if (s == null || !s.working) Sky.OFFLINE else skyOf(s.today?.sky ?: "cloudy")

/** A gradient for a small card in this sky's colours (the station list). */
fun Sky.brush(dark: Boolean): Brush = Brush.linearGradient(listOf(top.dim(dark), bottom.dim(dark)))

private fun Color.dim(dark: Boolean) = if (dark) lerp(this, Color.Black, 0.28f) else this

/** Whether the system's "remove animations" setting is on: then the sky holds still. */
@Composable
private fun animationsOff(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** A clock for the sky's particles. Only the drawing reads it, so it never recomposes anything. */
@Stable
private class SkyClock {
    var millis by mutableLongStateOf(0L)
}

/**
 * The animated backdrop of the Weather tab: one sky per page of [skies], blended by [position] (the pager's page
 * plus how far it has been swiped), so the sky changes with your finger. Reading the position only while
 * drawing means a swipe redraws the sky without recomposing anything.
 */
@Composable
fun SkyBackdrop(skies: List<Sky>, position: () -> Float, animated: Boolean = true, modifier: Modifier = Modifier) {
    val dark = LocalIsDark.current
    val still = !animated || animationsOff()
    val clock = remember { SkyClock() }
    if (!still) {
        LaunchedEffect(Unit) {
            val start = withFrameMillis { it }
            while (true) withFrameMillis { clock.millis = it - start }
        }
    }
    Box(
        modifier.fillMaxSize().drawBehind {
            val pos = position().coerceIn(0f, (skies.size - 1).coerceAtLeast(0).toFloat())
            val i = pos.toInt()
            val from = skies.getOrElse(i) { Sky.OFFLINE }
            val to = skies.getOrElse(i + 1) { from }
            val p = pos - i
            val t = clock.millis / 1000f
            drawRect(
                Brush.verticalGradient(
                    listOf(lerp(from.top, to.top, p).dim(dark), lerp(from.bottom, to.bottom, p).dim(dark)),
                ),
            )
            if (from == to) {
                drawSky(from, t, 1f)
            } else {
                drawSky(from, t, 1f - p)
                drawSky(to, t, p)
            }
        },
    )
}

private fun DrawScope.drawSky(sky: Sky, t: Float, alpha: Float) {
    if (alpha <= 0.01f) return
    when (sky) {
        Sky.CLEAR -> sun(t, alpha)
        Sky.CLOUDY -> clouds(t, alpha, 0.10f)
        Sky.RAIN -> {
            clouds(t, alpha, 0.08f)
            rain(t, alpha, 70, 1f)
        }
        Sky.THUNDER -> {
            clouds(t, alpha, 0.07f)
            rain(t, alpha, 95, 1.35f)
            lightning(t, alpha)
        }
        Sky.SNOW -> snow(t, alpha)
        Sky.OFFLINE -> Unit
    }
}

/** A cheap, repeatable "random" number in [0, 1) for particle [i]. */
private fun hash(i: Int, salt: Int = 0): Float {
    var x = i * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFFFF) / 16777216f
}

private fun DrawScope.sun(t: Float, alpha: Float) {
    val c = Offset(size.width * 0.82f, size.height * 0.1f)
    val r = size.minDimension * 0.9f
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFFFF4C2).copy(alpha = 0.55f * alpha), Color(0xFFFFE07A).copy(alpha = 0.18f * alpha), Color.Transparent),
            center = c,
            radius = r,
        ),
        radius = r,
        center = c,
    )
    // Slowly turning rays.
    rotate(t * 3f, c) {
        for (i in 0 until 12) {
            val a = (i / 12f) * 2 * PI.toFloat()
            val len = r * (0.55f + 0.15f * sin(t * 0.6f + i))
            drawLine(
                Color.White.copy(alpha = 0.07f * alpha),
                c,
                Offset(c.x + len * kotlin.math.cos(a), c.y + len * sin(a)),
                strokeWidth = size.minDimension * 0.05f,
                cap = StrokeCap.Round,
            )
        }
    }
    drawCircle(Color(0xFFFFF8DD).copy(alpha = 0.9f * alpha), size.minDimension * 0.07f, c)
}

private fun DrawScope.clouds(t: Float, alpha: Float, opacity: Float) {
    for (i in 0 until 6) {
        val speed = 6f + hash(i, 1) * 10f
        val w = size.width * (0.5f + hash(i, 2) * 0.6f)
        val span = size.width + w * 2
        val x = ((hash(i, 3) * span + t * speed) % span) - w
        val y = size.height * (0.04f + hash(i, 4) * 0.35f)
        // A cloud: a few overlapping soft circles.
        for (k in 0 until 4) {
            val cx = x + w * (k / 3f)
            val cy = y + (if (k % 2 == 0) 0f else -w * 0.08f)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = opacity * alpha), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = w * 0.35f,
                ),
                radius = w * 0.35f,
                center = Offset(cx, cy),
            )
        }
    }
}

private fun DrawScope.rain(t: Float, alpha: Float, drops: Int, speedScale: Float) {
    val h = size.height
    val slant = size.width * 0.06f
    for (i in 0 until drops) {
        val speed = (h * 0.9f + hash(i, 5) * h * 0.6f) * speedScale
        val len = 22f + hash(i, 6) * 30f
        val y = ((hash(i, 7) * h + t * speed) % (h + len)) - len
        val x = hash(i, 8) * (size.width + slant) - slant * (y / h)
        val a = (0.18f + hash(i, 9) * 0.3f) * alpha
        drawLine(Color.White.copy(alpha = a), Offset(x, y), Offset(x - slant * len / h * 4f, y + len), strokeWidth = 2.2f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.snow(t: Float, alpha: Float) {
    val h = size.height
    for (i in 0 until 80) {
        val speed = 30f + hash(i, 10) * 60f
        val r = 2f + hash(i, 11) * 5f
        val y = ((hash(i, 12) * h + t * speed) % (h + 20f)) - 10f
        val sway = sin(t * (0.6f + hash(i, 13)) + i) * 18f
        val x = hash(i, 14) * size.width + sway
        drawCircle(Color.White.copy(alpha = (0.45f + hash(i, 15) * 0.45f) * alpha), r, Offset(x, y))
    }
}

/** Every few seconds the sky flashes white, flickering once or twice. */
private fun DrawScope.lightning(t: Float, alpha: Float) {
    val period = 5.5f
    val cycle = (t % period) / period
    val strike = (t / period).toInt()
    if (hash(strike, 16) < 0.25f) return // some cycles have no strike
    val at = 0.15f + hash(strike, 17) * 0.6f
    val d = abs(cycle - at) * period // seconds from the strike
    val flash = max(0f, 1f - d / 0.12f) + 0.6f * max(0f, 1f - abs(cycle - at - 0.04f) * period / 0.08f)
    if (flash > 0f) drawRect(Color.White.copy(alpha = (0.35f * flash).coerceAtMost(0.45f) * alpha))
}
