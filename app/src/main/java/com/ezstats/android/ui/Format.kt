package com.ezstats.android.ui

import androidx.compose.runtime.compositionLocalOf
import com.ezstats.android.Device
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
        return if (celsius) "%.1f".format(v) else if (kotlin.math.abs(v - v.roundToInt()) < 0.05) "${v.roundToInt()}" else "%.1f".format(v)
    }

    fun speed(ms: Double?) = when {
        ms == null -> "–"
        celsius -> "${(ms * 3.6).roundToInt()} km/h"
        else -> "${(ms * 2.236936).roundToInt()} mph"
    }

    /** A stored whole °F moved by one shown degree. */
    fun stepF(f: Int, direction: Int): Int =
        if (!celsius) f + direction else (((show(f.toDouble()).roundToInt() + direction) * 9.0 / 5 + 32).roundToInt())

    /** A whole-°F value as shown. */
    fun wholeF(f: Int) = "${show(f.toDouble()).roundToInt()}°$symbol"
}

val LocalUnits = compositionLocalOf { UnitFmt(false) }
val LocalWritable = compositionLocalOf { false }

fun cap(s: String) = s.replace('_', ' ').replaceFirstChar { it.uppercase() }

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

val SKY = mapOf("clear" to "Clear", "rain" to "Rain", "snow" to "Snow", "thunder" to "Thunder")
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

val FRIDGES = listOf("fridge", "deep_freezer", "refrigerator", "mini_fridge")

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
