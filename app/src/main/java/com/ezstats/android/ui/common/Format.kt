package com.ezstats.android.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.HeatPump
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import com.ezstats.android.data.Device
import kotlin.math.abs
import kotlin.math.roundToInt

/** The server stores every temperature in °F; this shows them in the server's units. */
class UnitFmt(val celsius: Boolean) {
    val symbol get() = if (celsius) "C" else "F"

    fun show(f: Double) = if (celsius) (f - 32) * 5 / 9 else f

    fun whole(f: Double?) = if (f == null) "–" else "${show(f).roundToInt()}°"

    fun one(f: Double?) = if (f == null) "–" else "%.1f".format(show(f))

    /** A thermostat target: whole degrees in °F, a decimal in °C. */
    fun target(f: Double): String {
        val v = show(f)
        return if (celsius) "%.1f".format(v) else if (abs(v - v.roundToInt()) < 0.05) "${v.roundToInt()}" else "%.1f".format(v)
    }

    fun speed(ms: Double?) = when {
        ms == null -> "–"
        celsius -> "${(ms * 3.6).roundToInt()} km/h"
        else -> "${(ms * 2.236936).roundToInt()} mph"
    }

    /** Just the number of [speed], and its unit, for layouts that set them in different sizes. */
    fun speedValue(ms: Double?) = when {
        ms == null -> "–"
        celsius -> "${(ms * 3.6).roundToInt()}"
        else -> "${(ms * 2.236936).roundToInt()}"
    }

    val speedUnit get() = if (celsius) "km/h" else "mph"

    /** A stored whole °F moved by one shown degree. */
    fun stepF(f: Int, direction: Int): Int =
        if (!celsius) f + direction else (((show(f.toDouble()).roundToInt() + direction) * 9.0 / 5 + 32).roundToInt())

    /** A whole-°F value as shown. */
    fun wholeF(f: Int) = "${show(f.toDouble()).roundToInt()}°$symbol"
}

val LocalUnits = compositionLocalOf { UnitFmt(false) }

/**
 * A screen with little height to spare: a phone on its side, or a small window. Big headers, heroes and the dial
 * shrink there so the content still fits.
 */
@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
fun isShortScreen(): Boolean = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 520
val LocalWritable = compositionLocalOf { false }

fun cap(s: String) = s.replace('_', ' ').replaceFirstChar { it.uppercase() }

fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"

val TYPE_NAME = mapOf(
    "room" to "Thermostat",
    "pool" to "Pool thermostat",
    "heat_pump" to "Heat pump",
    "air_conditioner" to "Air conditioner",
    "furnace" to "Furnace",
    "boiler" to "Boiler",
    "pool_heat_pump" to "Pool heat pump",
    "dehumidifier" to "Dehumidifier",
    "humidifier" to "Humidifier",
    "air_purifier" to "Air purifier",
    "fridge" to "Fridge",
    "deep_freezer" to "Deep freezer",
    "refrigerator" to "Refrigerator",
    "mini_fridge" to "Mini fridge",
    "air" to "Air vent",
    "water" to "Water vent",
    "thermometer" to "Thermometer",
    "hygrometer" to "Hygrometer",
    "thermo_hygrometer" to "Thermo-hygrometer",
    "temperature_sensor" to "Temperature sensor",
    "humidity_sensor" to "Humidity sensor",
    "temperature_humidity_sensor" to "Temperature and humidity sensor",
    "weather_sensor" to "Weather sensor",
    "sensor_hub" to "Sensor hub",
    "rpm_monitor" to "RPM monitor",
    "settings_panel" to "Settings panel",
    "air_monitor" to "Air monitor",
    "air_sensor" to "Air sensor",
    "wind_sensor" to "Wind sensor",
)

val ATTENTION = mapOf(
    "no_units" to "No equipment linked",
    "no_vents" to "No vents linked",
    "units_off" to "Equipment switched off",
    "cant_keep_up" to "Can't keep up",
    "undersized" to "Undersized",
)

val SKY = mapOf("clear" to "Clear", "rain" to "Rain", "snow" to "Snow", "thunder" to "Thunderstorms")
val WIND = mapOf("calm" to "Calm", "breezy" to "Breezy", "windy" to "Windy", "gale" to "Gale")
val BAND = mapOf(
    "good" to "Good",
    "moderate" to "Moderate",
    "unhealthy_for_sensitive_groups" to "Unhealthy for sensitive groups",
    "unhealthy" to "Unhealthy",
    "very_unhealthy" to "Very unhealthy",
    "hazardous" to "Hazardous",
)
val UNIT_STATE = mapOf(
    "off" to "Off",
    "idle" to "Idle",
    "running" to "Running",
    "emptying" to "Emptying",
    "no_filter" to "No filter",
    "filter_spent" to "Filter spent",
)

fun skyName(sky: String) = SKY[sky] ?: cap(sky.ifBlank { "cloudy" })

fun skyIcon(sky: String): ImageVector = when (sky) {
    "rain" -> Icons.Rounded.WaterDrop
    "snow" -> Icons.Rounded.AcUnit
    "thunder" -> Icons.Rounded.FlashOn
    "clear" -> Icons.Rounded.WbSunny
    else -> Icons.Rounded.Cloud
}

fun typeName(d: Device) = TYPE_NAME[d.type] ?: cap(d.type.ifBlank { "device" })

/** What a device is called: its owner's nickname, its own label, or what kind of thing it is. */
fun deviceName(d: Device) = d.nickname ?: d.label.ifBlank { null } ?: typeName(d)

/** Where a device is, as short phrases for the line under its name. */
fun deviceWhere(d: Device): String {
    val parts = mutableListOf<String>()
    if (d.nickname != null && d.label.isNotBlank()) parts += d.label
    if (!d.isThermostat && d.label.isBlank() && d.nickname != null) parts += typeName(d)
    parts += "${d.world} ${d.x}, ${d.y}, ${d.z}"
    if (d.pool) parts += "pool"
    if (d.mini) parts += "mini"
    d.system?.let { parts += "system $it" }
    return parts.joinToString(" · ")
}

/** A picture of what a device is. */
fun deviceIcon(d: Device): ImageVector = when (d.type) {
    "room" -> Icons.Rounded.Thermostat
    "pool", "pool_heat_pump" -> Icons.Rounded.Pool
    "heat_pump" -> Icons.Rounded.HeatPump
    "air_conditioner" -> Icons.Rounded.AcUnit
    "furnace" -> Icons.Rounded.LocalFireDepartment
    "boiler" -> Icons.Rounded.Whatshot
    "dehumidifier", "humidifier", "hygrometer", "humidity_sensor" -> Icons.Rounded.WaterDrop
    "air_purifier", "air_monitor", "air_sensor", "air", "wind_sensor" -> Icons.Rounded.Air
    "fridge", "refrigerator", "mini_fridge" -> Icons.Rounded.Kitchen
    "deep_freezer" -> Icons.Rounded.AcUnit
    "water" -> Icons.Rounded.Waves
    "sensor_hub" -> Icons.Rounded.Hub
    "rpm_monitor" -> Icons.Rounded.Speed
    "settings_panel" -> Icons.Rounded.Settings
    "thermometer", "temperature_sensor", "thermo_hygrometer", "temperature_humidity_sensor" -> Icons.Rounded.DeviceThermostat
    else -> if (d.isThermostat) Icons.Rounded.Thermostat else Icons.Rounded.Sensors
}

/** "Good evening": the dashboard's greeting. */
fun greeting(hour: Int) = when (hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

// --- how long a thermostat has been heating or cooling (EZvac Pilot 8+) -------------------------------------------

/** "under a minute", "12 min", "2 h 5 min", "1 d 3 h". */
fun spanText(minutes: Long): String {
    if (minutes < 1) return "under a minute"
    if (minutes < 60) return "$minutes min"
    val hours = minutes / 60
    if (hours < 24) return "$hours h ${minutes % 60} min"
    return "${hours / 24} d ${hours % 24} h"
}

/** A time of day in this phone's time zone, with the date when it wasn't today. */
fun clockText(
    epochMs: Long,
    now: java.time.Instant = java.time.Instant.now(),
    zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
): String {
    val at = java.time.Instant.ofEpochMilli(epochMs).atZone(zone)
    val time = java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
    return if (at.toLocalDate() == now.atZone(zone).toLocalDate()) at.format(time)
    else at.format(java.time.format.DateTimeFormatter.ofPattern("MMM d")) + " " + at.format(time)
}

/**
 * "Cooling for 12 min · Last heated 14:02 to 14:20": how long it has been in its mode and its last finished run.
 * Null when EZvac doesn't say (before Pilot 8).
 */
fun modeLine(d: Device, now: java.time.Instant = java.time.Instant.now(), zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String? {
    val parts = mutableListOf<String>()
    val minutes = d.modeMinutes
    if (d.enabled && minutes != null) {
        val word = when (d.mode) { "heating" -> "Heating"; "cooling" -> "Cooling"; "idle" -> "Idle"; else -> cap(d.mode) }
        parts += "$word for ${spanText(minutes)}"
    }
    d.lastRun?.let { r ->
        parts += "Last ${if (r.mode == "heating") "heated" else "cooled"} ${clockText(r.startedAt, now, zone)} to ${clockText(r.endedAt, now, zone)}"
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
