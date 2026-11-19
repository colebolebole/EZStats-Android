package com.ezstats.android.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ezstats.android.EzViewModel
import com.ezstats.android.data.Device
import com.ezstats.android.data.StateData
import com.ezstats.android.ui.common.BAND
import com.ezstats.android.ui.common.Fact
import com.ezstats.android.ui.common.LocalWritable
import com.ezstats.android.ui.common.Meter
import com.ezstats.android.ui.common.RollingText
import com.ezstats.android.ui.common.StatusPill
import com.ezstats.android.ui.common.Stepper
import com.ezstats.android.ui.common.cap
import com.ezstats.android.ui.common.deviceName
import com.ezstats.android.ui.common.deviceWhere
import com.ezstats.android.ui.dashboard.DeviceMenuButton
import com.ezstats.android.ui.dashboard.deviceStatus
import com.ezstats.android.ui.dashboard.deviceTone
import com.ezstats.android.ui.theme.CoolColor
import com.ezstats.android.ui.theme.HeroNumber
import com.ezstats.android.ui.theme.Motion
import com.ezstats.android.ui.weather.aqiColor
import kotlin.math.roundToInt

/** A humidifier, dehumidifier or air purifier filling the screen. */
@Composable
fun UnitScreen(vm: EzViewModel, d: Device, state: StateData, snackbar: SnackbarHostState, onClose: () -> Unit) {
    val writable = LocalWritable.current
    val tint = when {
        !d.powered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        d.isPurifier -> (d.air?.let { aqiColor(it.aqi) } ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.24f)
        else -> CoolColor.copy(alpha = 0.22f)
    }
    DetailScaffold(
        tint = tint,
        title = deviceName(d),
        subtitle = deviceWhere(d),
        snackbar = snackbar,
        onClose = onClose,
        actions = { DeviceMenuButton(vm, d, state.homes) },
        // On a wide screen the gauge and power stay on the left, the target, tank, fan and filter go to the right.
        aside = {
            if (d.isHumidityUnit) {
                DetailCard {
                    val target = d.setpoint
                    if (target != null) {
                        if (writable) {
                            Stepper(
                                "target",
                                target,
                                format = { "$it%" },
                                step = { v, dir -> (v + dir * 5).coerceIn(0, 100) },
                                onSend = { v -> vm.control(d.key, "setpoint", v) { it.copy(setpoint = v) } },
                                big = true,
                            )
                        } else {
                            Fact("Target", "$target%")
                        }
                    }
                    if (d.type == "dehumidifier") d.tank?.let { Meter("Water tank", it, 0.8) }
                }
            }

            if (d.isPurifier) {
                DetailCard("Fan") {
                    if (writable) {
                        ConnectedChoice(listOf("low", "medium", "high", "auto"), d.fanMode) { name ->
                            vm.control(d.key, "fan", name) { it.copy(fanMode = name) }
                        }
                    } else {
                        Text(d.fanMode?.let { cap(it) } ?: "–", style = MaterialTheme.typography.titleMedium)
                    }
                    if (d.fanMode == "auto" && d.fanSpeed != null) {
                        Text("Running at ${d.fanSpeed} right now", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    d.filter?.let { Meter("Filter left", it) }
                }
            }
            Spacer(Modifier.height(24.dp))
        },
    ) {
        StatusPill(deviceStatus(d), deviceTone(d))

        if (d.isPurifier) {
            val aqi = d.air?.aqi
            Gauge(
                fraction = ((aqi ?: 0) / 300f).coerceIn(0f, 1f),
                color = aqi?.let { aqiColor(it) } ?: MaterialTheme.colorScheme.outline,
                value = aqi?.toString() ?: "–",
                label = "Air quality",
                caption = d.air?.let { BAND[it.band] ?: cap(it.band) } ?: "No reading",
            )
        } else {
            val h = d.humidity
            Gauge(
                fraction = ((h ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f),
                color = CoolColor,
                value = h?.let { "${it.roundToInt()}%" } ?: "–",
                label = "Humidity",
                caption = d.setpoint?.let { "Target $it%" } ?: "",
                marker = d.setpoint?.let { it / 100f },
            )
        }

        if (writable) PowerButton(d.powered, deviceName(d)) { on -> vm.control(d.key, "powered", on) { it.copy(powered = on) } }
    }
}

/** A large ring gauge with the value in its middle. [marker] puts a small notch at a target. */
@Composable
private fun Gauge(fraction: Float, color: Color, value: String, label: String, caption: String, marker: Float? = null) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val onSurface = MaterialTheme.colorScheme.onSurface
    val f by androidx.compose.animation.core.animateFloatAsState(fraction, Motion.drift, label = "gauge")
    val c by androidx.compose.animation.animateColorAsState(color, Motion.color, label = "gauge colour")
    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val stroke = 20.dp.toPx()
            val d = size.minDimension - stroke
            val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(track, 135f, 270f, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
            if (f > 0f) drawArc(c, 135f, 270f * f, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
            marker?.let { m ->
                val a = Math.toRadians((135 + 270 * m.coerceIn(0f, 1f)).toDouble())
                val center = Offset(size.width / 2, size.height / 2)
                val r = d / 2
                drawCircle(onSurface, stroke * 0.2f, Offset(center.x + r * kotlin.math.cos(a).toFloat(), center.y + r * kotlin.math.sin(a).toFloat()))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RollingText(value, style = HeroNumber.copy(fontSize = HeroNumber.fontSize * 0.8f))
            if (caption.isNotEmpty()) {
                Text(caption, style = MaterialTheme.typography.titleSmall, color = c, modifier = Modifier.padding(horizontal = 24.dp))
            }
        }
    }
}
