package com.ezstats.android.ui.weather

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Masks
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezstats.android.data.Station
import com.ezstats.android.ui.common.BAND
import com.ezstats.android.ui.common.LocalUnits
import com.ezstats.android.ui.common.RollingText
import com.ezstats.android.ui.common.UnitFmt
import com.ezstats.android.ui.common.WIND
import com.ezstats.android.ui.common.WidthClass
import com.ezstats.android.ui.common.widthClass
import com.ezstats.android.ui.common.cap
import com.ezstats.android.ui.common.isShortScreen
import com.ezstats.android.ui.common.plural
import com.ezstats.android.ui.common.skyName
import com.ezstats.android.ui.theme.HeroNumber
import com.ezstats.android.ui.theme.Motion
import com.ezstats.android.ui.theme.numeric
import kotlin.math.roundToInt

/** White on the sky, and the softer white for secondary text. */
internal val OnSky = Color.White
internal val OnSkyMuted = Color.White.copy(alpha = 0.72f)

/** The gold of a starred station. */
internal val Favorite = Color(0xFFFFD54F)

private val COMPASS = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")

/** Degrees clockwise from north for a compass point such as "NW", or null. */
fun compassDegrees(point: String?): Float? {
    val i = COMPASS.indexOf(point?.trim()?.uppercase() ?: return null)
    return if (i < 0) null else i * 22.5f
}

fun idleWhy(s: Station) = when {
    s.working -> ""
    !s.enabled -> "Switched off by an admin."
    !s.intact -> "Its tower is damaged. It forecasts again once it is repaired."
    else -> "It hasn't measured the weather yet."
}

fun idleWord(s: Station) = when {
    !s.enabled -> "Off"
    !s.intact -> "Damaged"
    else -> "Waiting"
}

/** A sentence about today, from what the station knows: "Clear. Gusty afternoon. Wind NW at 12 mph." */
internal fun todaySentence(s: Station, units: UnitFmt): String? {
    val today = s.today ?: return null
    val parts = mutableListOf(skyName(today.sky, s.phase) + " today")
    if (today.note.isNotBlank()) parts += today.note.trim().trimEnd('.')
    if (today.rain >= 0.2 && today.sky != "clear") parts += "${(today.rain * 100).roundToInt()}% chance of rain"
    if (today.thunder >= 0.2) parts += "thunder likely"
    (s.wind ?: today.wind)?.let { w ->
        val level = WIND[w.level]?.lowercase()
        parts += "wind ${w.from?.let { "$it " } ?: ""}at ${units.speed(w.speedMs)}" + (level?.let { ", $it" } ?: "")
    }
    return parts.joinToString(". ") { it.replaceFirstChar { c -> c.uppercase() } } + "."
}

/**
 * One station as a page of a weather app: the place, a huge temperature, today, the week, and tiles for wind,
 * air, rain and lightning. The headline shrinks and fades as the page scrolls up under it.
 */
@Composable
fun StationPage(station: Station, nearestFor: List<String>, scroll: ScrollState, top: Dp, bottom: Dp) {
    val units = LocalUnits.current
    val today = station.today
    val short = isShortScreen()
    val width = widthClass()
    val wide = width == WidthClass.EXPANDED
    val roomy = width != WidthClass.COMPACT // a tablet: a bigger headline and more air around it
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = if (roomy) 24.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(top + if (short) 0.dp else 24.dp))

        // The headline, with a little parallax: it drifts up slower than the page and fades as it goes.
        Column(
            Modifier.fillMaxWidth().graphicsLayer {
                val s = scroll.value.toFloat()
                translationY = s * 0.45f
                alpha = (1f - s / 520f).coerceIn(0f, 1f)
                val k = (1f - s / 2400f).coerceIn(0.85f, 1f)
                scaleX = k
                scaleY = k
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(station.label, style = MaterialTheme.typography.headlineMedium, color = OnSky, textAlign = TextAlign.Center)
            Text(
                "${station.world ?: "Unknown world"} · ${station.x}, ${station.y}, ${station.z}",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSkyMuted,
            )
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 4.dp)) {
                RollingText(
                    station.outdoorF?.let { units.show(it).roundToInt().toString() } ?: "–",
                    style = when {
                        short -> HeroNumber.copy(fontSize = 80.sp, lineHeight = 84.sp)
                        roomy -> HeroNumber.copy(fontSize = 156.sp, lineHeight = 160.sp)
                        else -> HeroNumber.copy(fontSize = 120.sp, lineHeight = 124.sp)
                    },
                    color = OnSky,
                )
                if (station.outdoorF != null) Text("°", style = HeroNumber.copy(fontSize = if (short) 56.sp else if (roomy) 108.sp else 84.sp), color = OnSky)
            }
            Text(
                if (station.working) today?.let { skyName(it.sky, station.phase) } ?: "Outside now" else idleWord(station),
                style = MaterialTheme.typography.titleLarge,
                color = OnSky,
            )
            if (today != null) {
                Text(
                    "H ${units.whole(today.highF)}   L ${units.whole(today.lowF)}",
                    style = MaterialTheme.typography.titleMedium.numeric,
                    color = OnSky,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (nearestFor.isNotEmpty()) {
                Surface(shape = MaterialTheme.shapes.large, color = Color.White.copy(alpha = 0.16f), modifier = Modifier.padding(top = 14.dp)) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Thermostat, null, Modifier.size(16.dp), tint = OnSky)
                        Spacer(Modifier.width(6.dp))
                        Text("Nearest to ${nearestFor.joinToString(", ")}", style = MaterialTheme.typography.labelLarge, color = OnSky)
                    }
                }
            }
        }

        Spacer(Modifier.height(if (short) 20.dp else 40.dp))

        // Today and the week, then wind, air, rain, lightning and the place. On a tablet they sit side by side.
        val summary: @Composable ColumnScope.() -> Unit = {
            if (!station.working) {
                GlassCard(null, null) {
                    Text(idleWhy(station), style = MaterialTheme.typography.bodyLarge, color = OnSky)
                }
            }
            todaySentence(station, units)?.let { s ->
                GlassCard(null, null) { Text(s, style = MaterialTheme.typography.bodyLarge, color = OnSky) }
            }
            if (station.forecast.isNotEmpty()) {
                GlassCard("${station.forecast.size}-day forecast", Icons.Rounded.CalendarMonth) {
                    ForecastRows(station.forecast, station.outdoorF, OnSky)
                }
            }
        }
        val details: @Composable ColumnScope.() -> Unit = {
            if (station.working) {
                TileRow({ WindTile(station) }, { AirTile(station) })
                TileRow({ RainTile(station) }, { LightningTile(station) })
            }
            GlassCard("Location", Icons.Rounded.Place) {
                Text(station.world ?: "Unknown world", style = MaterialTheme.typography.titleLarge, color = OnSky)
                Text("x ${station.x}   y ${station.y}   z ${station.z}", style = MaterialTheme.typography.bodyLarge.numeric, color = OnSkyMuted)
            }
        }
        if (wide) {
            Row(Modifier.widthIn(max = 1040.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), content = summary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), content = details)
            }
        } else {
            Column(Modifier.widthIn(max = if (roomy) 680.dp else 600.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                summary()
                details()
            }
        }
        Spacer(Modifier.height(bottom + 24.dp))
    }
}

/** Two tiles side by side, the same height. */
@Composable
private fun TileRow(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f).fillMaxHeight()) { left() }
        Box(Modifier.weight(1f).fillMaxHeight()) { right() }
    }
}

/** A frosted card on the sky. */
@Composable
fun GlassCard(title: String?, icon: ImageVector?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.White.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        modifier = modifier.fillMaxWidth().fillMaxHeight(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, null, Modifier.size(16.dp), tint = OnSkyMuted)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = OnSkyMuted)
                }
            }
            content()
        }
    }
}

@Composable
private fun WindTile(s: Station) {
    val units = LocalUnits.current
    val w = s.wind ?: s.today?.wind
    GlassCard("Wind", Icons.Rounded.Air) {
        if (w == null) {
            Text("No reading", color = OnSkyMuted)
            return@GlassCard
        }
        val from = compassDegrees(w.from)
        val heading by animateFloatAsState((from ?: 0f) + 180f, Motion.drift, label = "wind")
        val measurer = rememberTextMeasurer()
        val labelStyle = remember { TextStyle(color = OnSkyMuted, fontSize = 11.sp) }
        Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(4.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                val c = center
                for (i in 0 until 72) {
                    val long = i % 18 == 0
                    rotate(i * 5f, c) {
                        drawLine(
                            Color.White.copy(alpha = if (long) 0.8f else 0.28f),
                            Offset(c.x, c.y - r),
                            Offset(c.x, c.y - r + if (long) 10f else 6f),
                            strokeWidth = if (long) 3f else 1.5f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
                listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (t, deg) ->
                    val layout = measurer.measure(t, labelStyle)
                    val a = Math.toRadians((deg - 90).toDouble())
                    val rr = r - 24f
                    drawText(
                        layout,
                        topLeft = Offset(
                            c.x + rr * kotlin.math.cos(a).toFloat() - layout.size.width / 2,
                            c.y + rr * kotlin.math.sin(a).toFloat() - layout.size.height / 2,
                        ),
                    )
                }
                if (from != null) {
                    // An arrow through the middle, pointing the way the wind is blowing.
                    rotate(heading, c) {
                        val tip = Offset(c.x, c.y - r + 18f)
                        val tail = Offset(c.x, c.y + r - 18f)
                        drawLine(Color.White, Offset(c.x, c.y + r * 0.32f), tail, strokeWidth = 4f, cap = StrokeCap.Round)
                        drawLine(Color.White, Offset(c.x, c.y - r * 0.32f), tip, strokeWidth = 4f, cap = StrokeCap.Round)
                        drawPath(
                            Path().apply {
                                moveTo(tip.x, tip.y - 6f)
                                lineTo(tip.x - 12f, tip.y + 14f)
                                lineTo(tip.x + 12f, tip.y + 14f)
                                close()
                            },
                            Color.White,
                        )
                        drawCircle(Color.White, 6f, tail, style = Stroke(3f))
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RollingText(units.speedValue(w.speedMs), style = MaterialTheme.typography.headlineMedium.numeric, color = OnSky)
                Text(units.speedUnit, style = MaterialTheme.typography.labelSmall, color = OnSkyMuted)
            }
        }
        Text(
            listOfNotNull(WIND[w.level] ?: w.level.ifBlank { null }?.let(::cap), w.from?.let { "from the $it" }).joinToString(", "),
            style = MaterialTheme.typography.bodyMedium,
            color = OnSky,
        )
    }
}

@Composable
private fun AirTile(s: Station) {
    val air = s.air
    val aqi = air?.aqi ?: s.today?.aqi
    GlassCard("Air quality", Icons.Rounded.Masks) {
        RollingText(aqi?.toString() ?: "–", style = MaterialTheme.typography.displaySmall.numeric, color = OnSky)
        Text(air?.let { BAND[it.band] ?: cap(it.band) } ?: "No reading", style = MaterialTheme.typography.titleSmall, color = OnSky)
        Spacer(Modifier.weight(1f))
        if (aqi != null) {
            val f by animateFloatAsState((aqi / 300f).coerceIn(0f, 1f), Motion.drift, label = "aqi")
            Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                drawRoundRect(
                    Brush.horizontalGradient(listOf(aqiColor(25), aqiColor(75), aqiColor(125), aqiColor(175), aqiColor(250))),
                    cornerRadius = CornerRadius(size.height / 2),
                )
                val x = f * size.width
                drawCircle(Color.White, size.height * 0.95f, Offset(x, size.height / 2))
                drawCircle(aqiColor(aqi), size.height * 0.55f, Offset(x, size.height / 2))
            }
        }
    }
}

@Composable
private fun RainTile(s: Station) {
    val today = s.today
    val next = s.forecast.firstOrNull { it.daysAhead > (today?.daysAhead ?: 0) && it.rain >= 0.3 }
    GlassCard("Rain", Icons.Rounded.Grain) {
        RollingText("${((today?.rain ?: 0.0) * 100).roundToInt()}%", style = MaterialTheme.typography.displaySmall.numeric, color = OnSky)
        Text("chance today", style = MaterialTheme.typography.titleSmall, color = OnSky)
        Spacer(Modifier.weight(1f))
        Text(
            when {
                today != null && today.thunder >= 0.05 -> "${(today.thunder * 100).roundToInt()}% chance of thunder."
                next != null -> "Next likely ${dayLabel(next.daysAhead).lowercase()}."
                else -> "Dry all week."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = OnSkyMuted,
        )
    }
}

@Composable
private fun LightningTile(s: Station) {
    GlassCard("Lightning", Icons.Rounded.FlashOn) {
        RollingText("${s.lightning}", style = MaterialTheme.typography.displaySmall.numeric, color = OnSky)
        Text(if (s.lightning == 1) "strike" else "strikes", style = MaterialTheme.typography.titleSmall, color = OnSky)
        Spacer(Modifier.weight(1f))
        Text(
            if (s.lightning == 0) "None recorded." else "${plural(s.lightning, "strike")} recorded here.",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSkyMuted,
        )
    }
}
