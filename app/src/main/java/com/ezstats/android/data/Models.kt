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

/** Where the sun is in a Minecraft day. */
enum class DayPhase { SUNRISE, DAY, SUNSET, NIGHT }

/**
 * The phase for an in-game time of day in ticks (0 = sunrise/6:00, 6000 = noon, 12000 = sunset/18:00, 18000 = midnight).
 * Sunrise is the last 1500 ticks of the night and the first 1000 of the day, sunset the 2000 around dusk, and the sky
 * is dark in between, so each of the three lasts a minute or two of real time.
 */
fun dayPhaseOf(ticks: Int): DayPhase {
    val t = ((ticks % 24000) + 24000) % 24000
    return when {
        t >= 22500 || t < 1000 -> DayPhase.SUNRISE
        t < 11500 -> DayPhase.DAY
        t < 13500 -> DayPhase.SUNSET
        else -> DayPhase.NIGHT
    }
}

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
    /**
     * The time of day in the station's world (EZStats 0.7.4+). Kept as a phase, not as ticks, so it only changes when the
     * sun passes a threshold and a station isn't redrawn on every weather read. Null when the server doesn't say.
     */
    val phase: DayPhase? = null,
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

/** One finished heating or cooling run of a thermostat, in epoch milliseconds. */
@Immutable
data class Run(val mode: String, val startedAt: Long, val endedAt: Long)

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
    /**
     * The thermostat's log (EZStats 0.7.3+): its last 5 finished runs, newest first. EZStats keeps them itself and
     * sends an empty list when it has seen none; null when the server is older and has no log at all.
     */
    val runs: List<Run>? = null,
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
    // a vehicle's climate control (kind "car", from MTVehicles). Its [roomF] is the temperature inside, [mode] is
    // heating, cooling or idle like a thermostat's, [running] says it is doing that right now, and [fanMode] is
    // auto, 1, 2 or 3.
    val car: CarInfo? = null,
) {
    val isThermostat get() = kind == "thermostat"
    val isCar get() = kind == "car"
    val isFridge get() = kind == "unit" && type in FRIDGE_TYPES
    val isHumidityUnit get() = kind == "unit" && (type == "dehumidifier" || type == "humidifier")
    val isPurifier get() = kind == "unit" && type == "air_purifier"

    /** Whether tapping the device opens it full screen. */
    val opensDetail get() = isThermostat || isCar || isFridge || isHumidityUnit || isPurifier

    /** A thermostat (or a vehicle's climate control) actively heating or cooling. */
    val isHeating get() = (isThermostat || isCar) && enabled && mode == "heating"
    val isCooling get() = (isThermostat || isCar) && enabled && mode == "cooling"

    companion object {
        val FRIDGE_TYPES = setOf("fridge", "deep_freezer", "refrigerator", "mini_fridge")
    }
}

/**
 * What a vehicle's climate control adds to a [Device] (EZStats with MTVehicles). Temperatures are °F like everywhere
 * else; ones the server doesn't know are null.
 */
@Immutable
data class CarInfo(
    val license: String,
    /** The vehicle's own name; the device's label is the climate control's name when it has one. */
    val vehicleName: String,
    /** Whether the vehicle is spawned in a loaded chunk: otherwise it has no place ([Device.world] is empty). */
    val loaded: Boolean = true,
    val occupied: Boolean = false,
    /** "auto", "cool" or "heat". */
    val climateMode: String = "auto",
    val ambientF: Double? = null,
    val minTargetF: Double? = null,
    val maxTargetF: Double? = null,
    /** It is running without a driver (a remote start), for [remoteSecondsLeft] more seconds. */
    val remote: Boolean = false,
    val remoteSecondsLeft: Int? = null,
    /** How long a remote start lasts; 0 or null for none. */
    val remoteStartMinutes: Int? = null,
)

@Immutable
data class Home(val id: String, val name: String, val parent: String = "")

/** The three kinds of group (EZStats 0.8+): an organization holds folders and homes, a folder holds more, a home holds devices. */
enum class GroupKind(val route: String, val label: String) {
    ORG("orgs", "organization"),
    FOLDER("folders", "folder"),
    HOME("homes", "home"),
}

/** An organization, folder or home, as one thing: [parent] is the id of what it is in, or "" at the top. */
@Immutable
data class Group(val id: String, val name: String, val parent: String, val kind: GroupKind)

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
    /** Organizations and folders (EZStats 0.8+); both empty on an older server. Each in the order the player arranged it. */
    val orgs: List<Group> = emptyList(),
    val folders: List<Group> = emptyList(),
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
        orgs = orgs.reuse(old.orgs),
        folders = folders.reuse(old.folders),
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
