package com.ezstats.android.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.ezstats.android.EzViewModel
import com.ezstats.android.data.Device
import com.ezstats.android.data.StateData
import com.ezstats.android.data.Station
import androidx.compose.ui.text.style.TextAlign
import com.ezstats.android.ui.common.ATTENTION
import com.ezstats.android.ui.common.WidthClass
import com.ezstats.android.ui.common.widthClass
import com.ezstats.android.ui.common.modeLine
import com.ezstats.android.ui.common.runText
import com.ezstats.android.ui.common.Fact
import com.ezstats.android.ui.common.LocalUnits
import com.ezstats.android.ui.common.LocalWritable
import com.ezstats.android.ui.common.StatusPill
import com.ezstats.android.ui.common.cap
import com.ezstats.android.ui.common.springyPull
import com.ezstats.android.ui.common.deviceName
import com.ezstats.android.ui.common.deviceWhere
import com.ezstats.android.ui.common.skyIcon
import com.ezstats.android.ui.common.skyName
import com.ezstats.android.ui.dashboard.DeviceMenuButton
import com.ezstats.android.ui.dashboard.deviceStatus
import com.ezstats.android.ui.dashboard.deviceTone
import com.ezstats.android.ui.theme.CoolColor
import com.ezstats.android.ui.theme.HeatColor
import com.ezstats.android.ui.theme.Motion
import com.ezstats.android.ui.theme.numeric
import com.ezstats.android.ui.weather.ForecastStrip
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** One thermostat filling the screen, washed with the colour of what it is doing. */
@Composable
internal fun ThermostatScreen(vm: EzViewModel, d: Device, state: StateData, snackbar: SnackbarHostState, onClose: () -> Unit) {
    val tint = when {
        !d.enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        d.mode == "heating" -> HeatColor.copy(alpha = 0.30f)
        d.mode == "cooling" -> CoolColor.copy(alpha = 0.30f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    }
    DetailScaffold(
        tint = tint,
        title = deviceName(d),
        subtitle = deviceWhere(d),
        snackbar = snackbar,
        onClose = onClose,
        actions = { DeviceMenuButton(vm, d, state.homes) },
        // On a wide screen the dial stays on the left and the facts, log and weather take the right.
        aside = {
            ThermostatFacts(vm, d)
            RunLog(d)
            val station = d.weather?.let { state.stations[it.station] }
            OutsideCard(d, station, onOpen = station?.let { s -> { onClose(); vm.openStation(s.key) } })
            Spacer(Modifier.height(24.dp))
        },
    ) {
        StatusPill(deviceStatus(d), deviceTone(d))
        modeLine(d)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        ThermostatControls(vm, d)
    }
}

/** The dial, and −, power and + under it. */
@Composable
private fun ThermostatControls(vm: EzViewModel, d: Device) {
    val units = LocalUnits.current
    val writable = LocalWritable.current
    val haptic = LocalHapticFeedback.current
    val (minF, maxF) = targetRange(d)

    // The target being dialled in. It follows the server again whenever the server's value changes.
    var local by remember(d.key, d.targetF) { mutableDoubleStateOf(d.targetF ?: ((minF + maxF) / 2)) }
    var dirty by remember(d.key, d.targetF) { mutableStateOf(false) }
    LaunchedEffect(local, dirty) {
        if (dirty) {
            delay(450)
            val sent = local
            vm.control(d.key, "target", sent) { it.copy(targetF = sent) }
        }
    }

    fun snap(f: Double): Double {
        val step = if (units.celsius) 0.5 else 1.0
        val shown = (units.show(f) / step).roundToInt() * step
        val back = if (units.celsius) shown * 9 / 5 + 32 else shown
        return ((back * 10).roundToInt() / 10.0).coerceIn(minF, maxF)
    }

    val change: (Double) -> Unit = { f ->
        val snapped = snap(f)
        if (snapped != local) {
            // A tick under the finger for every degree, like a real dial's detents, and a firmer bump at either end.
            haptic.performHapticFeedback(
                if (snapped <= minF || snapped >= maxF) HapticFeedbackType.GestureThresholdActivate
                else HapticFeedbackType.SegmentFrequentTick,
            )
            local = snapped
            dirty = true
        }
    }

    // The dial is as big as it can be, but on a short screen it leaves room for the buttons under it.
    val height = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    // A bigger dial where there is room for it (a tablet).
    val room = if (widthClass() == WidthClass.COMPACT) 340.dp else 420.dp
    ThermostatDial(d, local, writable && d.enabled, change, maxSize = minOf(room, height * 0.62f))

    if (writable) {
        val stepF = if (units.celsius) 0.5 * 9 / 5 else 1.0
        val stepShapes = IconButtonShapes(RoundedCornerShape(24.dp), RoundedCornerShape(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = { change(local - stepF) },
                enabled = d.enabled && local > minF,
                shapes = stepShapes,
                modifier = Modifier.springyPull(limit = 16.dp).size(64.dp),
            ) { Icon(Icons.Rounded.Remove, "Lower the target of ${deviceName(d)}", Modifier.size(28.dp)) }
            PowerButton(d.enabled, deviceName(d)) { on -> vm.control(d.key, "enabled", on) { it.copy(enabled = on) } }
            FilledTonalIconButton(
                onClick = { change(local + stepF) },
                enabled = d.enabled && local < maxF,
                shapes = stepShapes,
                modifier = Modifier.springyPull(limit = 16.dp).size(64.dp),
            ) { Icon(Icons.Rounded.Add, "Raise the target of ${deviceName(d)}", Modifier.size(28.dp)) }
        }
    }
}

/** Humidity, equipment, profile and anything that needs attention. */
@Composable
private fun ThermostatFacts(vm: EzViewModel, d: Device) {
    val writable = LocalWritable.current
    AnimatedVisibility(
        visible = d.attention.isNotEmpty(),
        enter = fadeIn() + expandVertically(Motion.size),
        exit = fadeOut() + shrinkVertically(Motion.size),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            d.attention.forEach { item ->
                AssistChip(
                    onClick = {},
                    label = { Text(ATTENTION[item] ?: cap(item)) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Warning, null, Modifier.size(AssistChipDefaults.IconSize), tint = MaterialTheme.colorScheme.error)
                    },
                )
            }
        }
    }

    DetailCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            d.humidity?.let { Fact("Humidity", "${it.roundToInt()}%") }
            Fact("Equipment", "${d.runningUnits} of ${d.totalUnits} on")
            if (!writable) Fact("Profile", cap(d.profile))
        }
        if (writable) {
            Text("Profile", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ConnectedChoice(listOf("eco", "normal", "turbo"), d.profile) { name ->
                vm.control(d.key, "profile", name) { it.copy(profile = name) }
            }
        }
    }
}

/**
 * The thermostat's log: its last finished heating and cooling runs, newest first ("Heated for 3 min · 11:44 PM").
 * Not shown when the server is too old (before EZStats 0.7.3) to keep one.
 */
@Composable
private fun RunLog(d: Device) {
    val runs = d.runs ?: return
    DetailCard {
        Text("Log", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (runs.isEmpty()) {
            Text("No heating or cooling runs recorded yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            runs.forEach { r ->
                val heating = r.mode == "heating"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (heating) Icons.Rounded.LocalFireDepartment else Icons.Rounded.AcUnit,
                        null,
                        Modifier.size(18.dp),
                        tint = if (heating) HeatColor else CoolColor,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(runText(r), style = MaterialTheme.typography.bodyLarge.numeric)
                }
            }
        }
    }
}

/** The conditions at the nearest weather station, and the week ahead. */
@Composable
private fun OutsideCard(d: Device, station: Station?, onOpen: (() -> Unit)?) {
    val units = LocalUnits.current
    if (station == null) {
        Text("No weather station in range", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val today = station.today
    DetailCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (today != null) Icon(skyIcon(today.sky, station.phase), null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${units.whole(station.outdoorF)} outside", style = MaterialTheme.typography.titleLarge.numeric)
                Text(
                    listOfNotNull(
                        today?.let { skyName(it.sky, station.phase) },
                        today?.let { "H ${units.whole(it.highF)}  L ${units.whole(it.lowF)}" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (station.forecast.size > 1) ForecastStrip(station.forecast)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${station.label}" + (d.weather?.distance?.let { " · $it blocks away" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            if (onOpen != null) {
                TextButton(onClick = onOpen) {
                    Text("Weather")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp))
                }
            }
        }
    }
}
