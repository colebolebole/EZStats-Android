package com.ezstats.android.ui.weather

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ezstats.android.data.ForecastDay
import com.ezstats.android.ui.common.LocalUnits
import com.ezstats.android.ui.common.skyIcon
import com.ezstats.android.ui.common.skyName
import com.ezstats.android.ui.theme.numeric
import kotlin.math.roundToInt

fun dayLabel(n: Int) = when (n) { 0 -> "Today"; 1 -> "Tomorrow"; else -> "In $n days" }
fun dayShort(n: Int) = when (n) { 0 -> "Today"; 1 -> "Tmrw"; else -> "+$n" }

private val TEMP_STOPS = listOf(
    10.0 to Color(0xFF93C5FD),
    32.0 to Color(0xFF60A5FA),
    50.0 to Color(0xFF2DD4BF),
    65.0 to Color(0xFFA3E635),
    75.0 to Color(0xFFFACC15),
    85.0 to Color(0xFFFB923C),
    100.0 to Color(0xFFEF4444),
)

/** A colour for a temperature in °F: icy blue through green and yellow to hot red. */
fun tempColor(f: Double): Color {
    val first = TEMP_STOPS.first()
    if (f <= first.first) return first.second
    for (i in 1 until TEMP_STOPS.size) {
        val (t1, c1) = TEMP_STOPS[i]
        val (t0, c0) = TEMP_STOPS[i - 1]
        if (f <= t1) return lerp(c0, c1, ((f - t0) / (t1 - t0)).toFloat())
    }
    return TEMP_STOPS.last().second
}

/** The colour of an air quality index, on the usual green-to-maroon scale. */
fun aqiColor(aqi: Int): Color = when {
    aqi <= 50 -> Color(0xFF22C55E)
    aqi <= 100 -> Color(0xFFEAB308)
    aqi <= 150 -> Color(0xFFF97316)
    aqi <= 200 -> Color(0xFFEF4444)
    aqi <= 300 -> Color(0xFFA855F7)
    else -> Color(0xFF9F1239)
}

/** The range a week of forecasts spans, so every day's bar is drawn on the same scale. */
internal fun forecastRange(days: List<ForecastDay>, now: Double?): Pair<Double, Double>? {
    val values = days.flatMap { listOfNotNull(it.lowF, it.highF) } + listOfNotNull(now)
    if (values.isEmpty()) return null
    val lo = values.min()
    val hi = values.max()
    return if (hi - lo < 1.0) (lo - 1) to (hi + 1) else lo to hi
}

/**
 * The week, a day per row, like a weather app: the day, its sky and chance of rain, then the low and high either
 * side of a bar placed on the week's scale and coloured by temperature. Today's bar has a dot for right now.
 * The bars grow out from their middle the first time the list is shown.
 */
@Composable
fun ForecastRows(days: List<ForecastDay>, nowF: Double?, content: Color) {
    val units = LocalUnits.current
    val range = forecastRange(days, nowF) ?: return
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 120f)) }
    val muted = content.copy(alpha = 0.7f)
    Column {
        days.forEachIndexed { i, d ->
            if (i > 0) androidx.compose.material3.HorizontalDivider(color = content.copy(alpha = 0.12f))
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dayLabel(d.daysAhead), style = MaterialTheme.typography.titleSmall, color = content, modifier = Modifier.width(92.dp))
                Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(skyIcon(d.sky), skyName(d.sky), Modifier.size(22.dp), tint = content)
                    if (d.rain >= 0.1) {
                        Text("${(d.rain * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall.numeric, color = Color(0xFF9FD3FF))
                    }
                }
                Text(
                    units.whole(d.lowF),
                    style = MaterialTheme.typography.titleSmall.numeric,
                    color = muted,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(44.dp),
                )
                Canvas(Modifier.weight(1f).height(8.dp).padding(horizontal = 10.dp)) {
                    val (lo, hi) = range
                    fun x(f: Double) = ((f - lo) / (hi - lo)).toFloat() * size.width
                    drawRoundRect(content.copy(alpha = 0.16f), cornerRadius = CornerRadius(size.height / 2))
                    val l = d.lowF ?: return@Canvas
                    val h = d.highF ?: return@Canvas
                    val mid = (x(l) + x(h)) / 2
                    val half = (x(h) - x(l)) / 2 * grow.value
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(tempColor(l), tempColor(h)), startX = mid - half, endX = mid + half),
                        topLeft = Offset(mid - half, 0f),
                        size = Size((half * 2).coerceAtLeast(size.height), size.height),
                        cornerRadius = CornerRadius(size.height / 2),
                    )
                    if (d.daysAhead == 0 && nowF != null) {
                        drawCircle(Color.White, size.height * 0.85f, Offset(x(nowF), size.height / 2))
                        drawCircle(tempColor(nowF), size.height * 0.5f, Offset(x(nowF), size.height / 2))
                    }
                }
                Text(units.whole(d.highF), style = MaterialTheme.typography.titleSmall.numeric, color = content, modifier = Modifier.width(40.dp))
            }
        }
    }
}

/** The week side by side, small: for the thermostat's outdoor card. */
@Composable
fun ForecastStrip(days: List<ForecastDay>) {
    val units = LocalUnits.current
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEach { d ->
            Column(
                Modifier.width(54.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(dayShort(d.daysAhead), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(skyIcon(d.sky), skyName(d.sky), Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Text(units.whole(d.highF), style = MaterialTheme.typography.titleSmall.numeric)
                Text(units.whole(d.lowF), style = MaterialTheme.typography.bodySmall.numeric, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
