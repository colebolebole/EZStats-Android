package com.ezstats.android.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HeatPump
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ezstats.android.EzViewModel
import com.ezstats.android.data.Device
import com.ezstats.android.data.Station
import com.ezstats.android.ui.common.ATTENTION
import com.ezstats.android.ui.common.BAND
import com.ezstats.android.ui.common.IconBadge
import com.ezstats.android.ui.common.LocalUnits
import com.ezstats.android.ui.common.LocalWritable
import com.ezstats.android.ui.common.PressCard
import com.ezstats.android.ui.common.RollingText
import com.ezstats.android.ui.common.StatusPill
import com.ezstats.android.ui.common.UNIT_STATE
import com.ezstats.android.ui.common.UnitFmt
import com.ezstats.android.ui.common.WIND
import com.ezstats.android.ui.common.cap
import com.ezstats.android.ui.common.deviceIcon
import com.ezstats.android.ui.common.deviceName
import com.ezstats.android.ui.common.deviceWhere
import com.ezstats.android.ui.common.skyIcon
import com.ezstats.android.ui.common.typeName
import com.ezstats.android.ui.detail.MiniDial
import com.ezstats.android.ui.detail.trackTile
import com.ezstats.android.ui.theme.Motion
import com.ezstats.android.ui.theme.Tone
import com.ezstats.android.ui.theme.numeric
import com.ezstats.android.ui.theme.toneColors
import kotlin.math.roundToInt

// --- what a device is doing, as a tone and a word ------------------------------------------------------------

fun deviceTone(d: Device): Tone = when {
    d.isThermostat -> when {
        !d.enabled -> Tone.NEUTRAL
        d.mode == "heating" -> Tone.HEAT
        d.mode == "cooling" -> Tone.COOL
        else -> Tone.NEUTRAL
    }
    d.kind == "equipment" -> when {
        !d.enabled || !d.running -> Tone.NEUTRAL
        d.mode == "heating" -> Tone.HEAT
        d.mode == "cooling" -> Tone.COOL
        else -> Tone.GOOD
    }
    d.kind == "unit" -> when {
        !d.powered -> Tone.NEUTRAL
        d.cold?.doorOpen == true || d.cold?.defrosting == true -> Tone.WARN
        d.state == "no_filter" || d.state == "filter_spent" || d.state == "emptying" -> Tone.WARN
        d.isFridge -> Tone.COOL
        d.state == "running" -> Tone.GOOD
        else -> Tone.NEUTRAL
    }
    else -> Tone.NEUTRAL
}

fun deviceStatus(d: Device): String = when {
    d.isThermostat -> if (d.enabled) cap(d.mode) else "Off"
    d.kind == "equipment" -> when {
        !d.enabled -> "Off"
        d.running -> when (d.mode) { "heating" -> "Heating"; "cooling" -> "Cooling"; else -> "Running" }
        else -> "Idle"
    }
    d.kind == "unit" -> when {
        !d.powered -> "Off"
        d.cold?.defrosting == true -> "Defrosting"
        d.cold?.doorOpen == true -> "Door open"
        else -> UNIT_STATE[d.state] ?: cap(d.state)
    }
    d.kind == "vent" -> "Vent"
    else -> "Sensor"
}

/** A card's colour: its surface, washed with the colour of what it is doing. */
@Composable
private fun tileColor(tone: Tone, strength: Float): Color {
    val base = MaterialTheme.colorScheme.surfaceContainerLow
    val target = if (tone == Tone.NEUTRAL) base else toneColors(tone).container.copy(alpha = strength).compositeOver(base)
    val c by animateColorAsState(target, Motion.color, label = "tile")
    return c
}

// --- picking the tile -----------------------------------------------------------------------------------------

@Composable
fun DeviceTile(vm: EzViewModel, d: Device, station: Station?, wide: Boolean, onLongPress: () -> Unit) {
    val open: (() -> Unit)? = if (d.opensDetail) ({ vm.openDetail = d.key }) else null
    val modifier = if (d.opensDetail) Modifier.trackTile(vm, d.key) else Modifier
    when {
        d.isThermostat -> ThermostatTile(d, station, open, onLongPress, modifier)
        wide && d.isFridge -> RefrigeratorTile(d, open, onLongPress, modifier)
        else -> CompactTile(vm, d, open, onLongPress, modifier)
    }
}

// --- thermostats ------------------------------------------------------------------------------------------------

@Composable
private fun ThermostatTile(d: Device, station: Station?, onClick: (() -> Unit)?, onLongPress: () -> Unit, modifier: Modifier) {
    val units = LocalUnits.current
    val tone = deviceTone(d)
    val colors = toneColors(tone)
    PressCard(
        onClick = onClick,
        onLongClick = onLongPress,
        onLongClickLabel = "More options",
        modifier = modifier.fillMaxWidth(),
        color = tileColor(tone, 0.55f),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TileHeader(d, colors.container, colors.accent) { StatusPill(deviceStatus(d), tone) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Top) {
                        RollingText(units.one(d.roomF), style = MaterialTheme.typography.displayMedium.numeric)
                        Text("°${units.symbol}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                    }
                    Text(
                        if (d.enabled && d.targetF != null) "Inside · set to ${units.target(d.targetF)}°" else "Inside",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MiniDial(d, Modifier.size(92.dp))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                d.humidity?.let { Chip(Icons.Rounded.WaterDrop, "${it.roundToInt()}%") }
                Chip(Icons.Rounded.HeatPump, "${d.runningUnits}/${d.totalUnits} running")
                station?.outdoorF?.let { t ->
                    Chip(station.today?.let { skyIcon(it.sky) } ?: skyIcon(""), "${units.whole(t)} outside")
                }
            }
            AnimatedVisibility(
                visible = d.attention.isNotEmpty(),
                enter = fadeIn() + expandVertically(Motion.size),
                exit = fadeOut() + shrinkVertically(Motion.size),
            ) {
                val first = d.attention.firstOrNull().orEmpty()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Warning, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        (ATTENTION[first] ?: cap(first)) + if (d.attention.size > 1) " +${d.attention.size - 1} more" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** A small icon-and-text fact. */
@Composable
private fun Chip(icon: ImageVector, text: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge.numeric)
        }
    }
}

/** The icon, name and place of a device, and something on the right (its status). */
@Composable
private fun TileHeader(d: Device, badge: Color, onBadge: Color, trailing: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(deviceIcon(d), badge, onBadge, size = 44.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(deviceName(d), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                deviceWhere(d),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

// --- a refrigerator: fridge and freezer side by side --------------------------------------------------------

@Composable
private fun RefrigeratorTile(d: Device, onClick: (() -> Unit)?, onLongPress: () -> Unit, modifier: Modifier) {
    val units = LocalUnits.current
    val tone = deviceTone(d)
    val colors = toneColors(tone)
    PressCard(onClick, modifier.fillMaxWidth(), onLongPress, "More options", color = tileColor(tone, 0.35f)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TileHeader(d, colors.container, colors.accent) { StatusPill(deviceStatus(d), tone) }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                listOf("Fridge" to d.cold?.fridgeInsideF, "Freezer" to d.insideF).forEach { (name, f) ->
                    Column(Modifier.weight(1f)) {
                        RollingText(units.whole(f), style = MaterialTheme.typography.displaySmall.numeric)
                        Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// --- everything else: a compact square-ish tile --------------------------------------------------------------

/** The big value, and a line under it, for a compact tile. */
private fun compactValue(d: Device, units: UnitFmt): Pair<String, String> {
    val r = d.reading
    return when {
        d.kind == "equipment" -> deviceStatus(d) to listOfNotNull(
            d.rpm?.let { "${it.roundToInt()} rpm" },
            if (d.tier > 0) "stage ${d.tier}" else null,
        ).joinToString(" · ").ifEmpty { typeName(d) }
        d.isHumidityUnit -> (d.humidity?.let { "${it.roundToInt()}%" } ?: "–") to listOfNotNull(
            d.setpoint?.let { "target $it%" },
            d.tank?.takeIf { d.type == "dehumidifier" }?.let { "tank ${(it * 100).roundToInt()}%" },
        ).joinToString(" · ").replaceFirstChar { it.uppercase() }
        d.isPurifier -> (d.air?.let { "AQI ${it.aqi}" } ?: "–") to listOfNotNull(
            d.air?.let { BAND[it.band] ?: it.band },
            d.fanMode?.let { "fan $it" },
        ).joinToString(" · ")
        d.isFridge -> units.whole(d.insideF) to (
            if (d.cold?.doorOpen == true) "Door open" else d.setpoint?.let { "Target ${units.wholeF(it)}" } ?: "Inside"
            )
        d.kind == "instrument" && r != null -> when {
            r.tempF != null -> "${units.one(r.tempF)}°" to listOfNotNull(r.humidity?.let { "${it.roundToInt()}% humidity" }, r.weather).joinToString(" · ")
            r.humidity != null -> "${r.humidity.roundToInt()}%" to "Humidity"
            r.air != null -> "AQI ${r.air.aqi}" to (BAND[r.air.band] ?: r.air.band)
            r.windMs != null -> units.speed(r.windMs) to listOfNotNull(r.windFrom?.let { "from $it" }, r.windLevel?.let { WIND[it] ?: it }).joinToString(" · ")
            else -> "–" to typeName(d)
        }
        d.kind == "instrument" -> (if (d.links > 0) "${d.links}" else "–") to (if (d.links > 0) "linked" else "No reading")
        d.kind == "vent" -> (if (d.type == "water") "Water" else "Air") to "Vent"
        else -> "–" to typeName(d)
    }
}

@Composable
private fun CompactTile(vm: EzViewModel, d: Device, onClick: (() -> Unit)?, onLongPress: () -> Unit, modifier: Modifier) {
    val units = LocalUnits.current
    val writable = LocalWritable.current
    val haptic = LocalHapticFeedback.current
    val tone = deviceTone(d)
    val colors = toneColors(tone)
    val (value, detail) = compactValue(d, units)
    PressCard(onClick, modifier.fillMaxWidth(), onLongPress, "More options", color = tileColor(tone, 0.45f)) {
        Column(Modifier.padding(16.dp).heightIn(min = 132.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(deviceIcon(d), colors.container, colors.accent, size = 40.dp)
                Spacer(Modifier.weight(1f))
                // Equipment switches right on its tile: there is nothing more to it.
                if (d.kind == "equipment" && writable) {
                    Switch(
                        checked = d.enabled,
                        onCheckedChange = { on ->
                            haptic.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                            vm.control(d.key, "equipment-enabled", on) { it.copy(enabled = on) }
                        },
                        thumbContent = if (d.enabled) ({ Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }) else null,
                    )
                } else if (d.kind == "unit" || d.kind == "equipment") {
                    StatusDot(tone)
                }
            }
            Spacer(Modifier.height(14.dp))
            RollingText(value, style = MaterialTheme.typography.headlineMedium.numeric)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (tone == Tone.WARN) colors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(deviceName(d), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (d.nickname != null || d.label.isNotBlank()) typeName(d) else "${d.x}, ${d.y}, ${d.z}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatusDot(tone: Tone) {
    val c by animateColorAsState(toneColors(tone).accent, Motion.color, label = "dot")
    Box(Modifier.size(10.dp)) {
        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = c, modifier = Modifier.size(10.dp)) {}
    }
}
