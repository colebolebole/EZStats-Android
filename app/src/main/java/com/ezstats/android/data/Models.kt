package com.ezstats.android.data

import androidx.compose.runtime.Immutable

// What the EZStats server describes, as plain immutable values. Parsing lives in Json.kt.

// --- weather ------------------------------------------------------------------------------------------

@Immutable
data class AirQuality(val aqi: Int, val band: String)

@Immutable
data class Wind(val speedMs: Double?, val level: String, val from: String?)

@Immutable
data class ForecastDay(
    val daysAhead: Int,
    val highF: Double?,
    val lowF: Double?,
    val sky: String,
    val rain: Double,
    val thunder: Double,
    val note: String,
    val aqi: Int?,
    val wind: Wind?,
)

@Immutable
data class Station(
    val key: String,
    val label: String,
    val world: String?,
    val x: Int,
    val y: Int,
    val z: Int,
    val enabled: Boolean,
    val intact: Boolean,
    val working: Boolean,
    val lightning: Int,
    val outdoorF: Double?,
    val wind: Wind?,
    val air: AirQuality?,
    val forecast: List<ForecastDay>,
) {
    /** Today's forecast, or the nearest day there is. */
    val today: ForecastDay? get() = forecast.firstOrNull { it.daysAhead == 0 } ?: forecast.firstOrNull()
}

@Immutable
data class WeatherLink(val station: String, val distance: Int?)

@Immutable
data class WeatherData(val units: String, val generatedAt: Long, val stations: List<Station>)

// --- devices ------------------------------------------------------------------------------------------

@Immutable
data class Cold(
    val doorOpen: Boolean,
    val frost: Double?,
    val defrosting: Boolean,
    val autoDefrost: Boolean,
    val fridgeInsideF: Double?,
    val fridgeSetpointF: Int?,
    val chillerF: Double?,
)

/** A thermostat's last finished heating or cooling run (EZvac Pilot 8+): epoch milliseconds. */
@Immutable
data class LastRun(val mode: String, val startedAt: Long, val endedAt: Long)

@Immutable
data class Reading(
    val tempF: Double?,
    val humidity: Double?,
    val weather: String?,
    val air: AirQuality?,
    val windMs: Double?,
    val windLevel: String?,
    val windFrom: String?,
)

/** One EZvac device. Which fields are set depends on [kind]; the rest stay at their defaults. */
@Immutable
data class Device(
    val key: String,
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val kind: String,
    val type: String,
    val label: String,
    val nickname: String?,
    val home: String,
    val system: String?,
    // thermostat
    val roomF: Double? = null,
    val targetF: Double? = null,
    val mode: String = "idle",
    val enabled: Boolean = false,
    val pool: Boolean = false,
    val mini: Boolean = false,
    val humidity: Double? = null,
    val runningUnits: Int = 0,
    val totalUnits: Int = 0,
    val profile: String = "normal",
    val attention: List<String> = emptyList(),
    val weather: WeatherLink? = null,
    /**
     * Whole minutes it has been in its [mode] as of the server's last read (Pilot 8+), worked out from the server's own
     * clock so this phone's clock doesn't matter. In whole minutes so it only changes once a minute and the tile isn't
     * redrawn on every poll for it. Null when EZvac doesn't say.
     */
    val modeMinutes: Long? = null,
    val lastRun: LastRun? = null,
    // equipment
    val running: Boolean = false,
    val rpm: Double? = null,
    val tier: Int = 0,
    // humidity units, purifiers, fridges
    val setpoint: Int? = null,
    val powered: Boolean = false,
    val state: String = "",
    val tank: Double? = null,
    val filter: Double? = null,
    val air: AirQuality? = null,
    val fanMode: String? = null,
    val fanSpeed: String? = null,
    val insideF: Double? = null,
    val cold: Cold? = null,
    // instruments
    val links: Int = 0,
    val reading: Reading? = null,
) {
    val isThermostat get() = kind == "thermostat"
    val isFridge get() = kind == "unit" && type in FRIDGE_TYPES
    val isHumidityUnit get() = kind == "unit" && (type == "dehumidifier" || type == "humidifier")
    val isPurifier get() = kind == "unit" && type == "air_purifier"

    /** Whether tapping the device opens it full screen. */
    val opensDetail get() = isThermostat || isFridge || isHumidityUnit || isPurifier

    /** A thermostat actively heating or cooling. */
    val isHeating get() = isThermostat && enabled && mode == "heating"
    val isCooling get() = isThermostat && enabled && mode == "cooling"

    companion object {
        val FRIDGE_TYPES = setOf("fridge", "deep_freezer", "refrigerator", "mini_fridge")
    }
}

@Immutable
data class Home(val id: String, val name: String)

@Immutable
data class World(
    val name: String,
    val season: String?,
    val seasonDay: Int,
    val seasonDays: Int,
    val seasonPaused: Boolean,
    val airF: Double?,
    val weather: String?,
)

@Immutable
data class StateData(
    val player: String,
    val weatherTab: Boolean,
    val units: String,
    val writable: Boolean,
    val readOnlyReason: String?,
    val homes: List<Home>,
    val devices: List<Device>,
    val worlds: List<World>,
    val stations: Map<String, Station>,
)

// --- sharing unchanged objects between two reads ----------------------------------------------------------------

private fun <T> List<T>.reuse(old: List<T>): List<T> = if (this == old) old else this

/**
 * This state, but reusing the very same objects as [old] wherever nothing changed. A poll that changes one
 * thermostat then hands every other card the same instances as before, which Compose can skip without even looking.
 */
fun StateData.shareWith(old: StateData?): StateData {
    if (old == null) return this
    if (this == old) return old
    val oldDevices = old.devices.associateBy { it.key }
    val oldStations = old.stations
    val sharedStations = stations.mapValues { (key, s) -> oldStations[key]?.takeIf { it == s } ?: s }
    return copy(
        homes = homes.reuse(old.homes),
        devices = devices.map { d -> oldDevices[d.key]?.takeIf { it == d } ?: d }.reuse(old.devices),
        worlds = worlds.reuse(old.worlds),
        stations = if (sharedStations == oldStations) oldStations else sharedStations,
    )
}

/** The same idea for the weather: stations that haven't changed since the last read keep their old objects. */
fun WeatherData.shareWith(old: WeatherData?): WeatherData {
    if (old == null) return this
    if (this == old) return old
    val oldByKey = old.stations.associateBy { it.key }
    return copy(stations = stations.map { s -> oldByKey[s.key]?.takeIf { it == s } ?: s }.reuse(old.stations))
}

/**
 * A summary of every world's season and the day of it. It changes when someone sets the season in game (or a new
 * season day starts), and not when only the temperature outside does, so the app can tell when forecasts will have
 * been recalculated.
 */
fun StateData.seasonSignature(): String = worlds.joinToString("|") { "${it.name}:${it.season}:${it.seasonDay}" }
