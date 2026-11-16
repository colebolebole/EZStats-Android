package com.ezstats.android

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

// --- tolerant JSON readers: the server leaves a field out rather than sending null/NaN -----------------

internal fun JSONObject.dbl(n: String): Double? =
    if (has(n) && !isNull(n)) optDouble(n, Double.NaN).takeUnless { it.isNaN() } else null

internal fun JSONObject.intOrNull(n: String): Int? = if (has(n) && !isNull(n)) optInt(n) else null

internal fun JSONObject.strOrNull(n: String): String? = if (has(n) && !isNull(n)) optString(n) else null

internal fun JSONObject.flag(n: String, def: Boolean = false): Boolean =
    if (has(n) && !isNull(n)) optBoolean(n, def) else def

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }

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
)

@Immutable
data class WeatherLink(val station: String, val distance: Int?)

private fun parseAir(o: JSONObject?): AirQuality? =
    o?.let { AirQuality(it.optInt("aqi"), it.optString("band")) }

private fun parseWind(o: JSONObject?): Wind? =
    o?.let { Wind(it.dbl("speedMs"), it.optString("level"), it.strOrNull("from")) }

private fun parseStation(o: JSONObject): Station = Station(
    key = o.optString("key"),
    label = o.optString("label"),
    world = o.strOrNull("world"),
    x = o.optInt("x"), y = o.optInt("y"), z = o.optInt("z"),
    enabled = o.flag("enabled"),
    intact = o.flag("intact"),
    working = o.flag("working"),
    lightning = o.optInt("lightningStrikes"),
    outdoorF = o.dbl("outdoorF"),
    wind = parseWind(o.optJSONObject("wind")),
    air = parseAir(o.optJSONObject("air")),
    forecast = o.optJSONArray("forecast").objects().map { d ->
        ForecastDay(
            daysAhead = d.optInt("daysAhead"),
            highF = d.dbl("highF"),
            lowF = d.dbl("lowF"),
            sky = d.optString("sky"),
            rain = d.dbl("rain") ?: 0.0,
            thunder = d.dbl("thunder") ?: 0.0,
            note = d.optString("note"),
            aqi = d.intOrNull("aqi"),
            wind = parseWind(d.optJSONObject("wind")),
        )
    },
)

@Immutable
data class WeatherData(val units: String, val generatedAt: Long, val stations: List<Station>)

fun parseWeather(json: String): WeatherData {
    val root = JSONObject(json)
    return WeatherData(
        units = root.optString("units", "F"),
        generatedAt = root.optLong("generatedAt"),
        stations = root.optJSONArray("stations").objects().map(::parseStation),
    )
}

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
}

private fun parseDevice(o: JSONObject): Device {
    val cold = o.optJSONObject("cold")?.let {
        Cold(
            doorOpen = it.flag("doorOpen"),
            frost = it.dbl("frost"),
            defrosting = it.flag("defrosting"),
            autoDefrost = it.flag("autoDefrost"),
            fridgeInsideF = it.dbl("fridgeInsideF"),
            fridgeSetpointF = it.intOrNull("fridgeSetpointF"),
            chillerF = it.dbl("chillerF"),
        )
    }
    val reading = o.optJSONObject("reading")?.let {
        Reading(
            tempF = it.dbl("tempF"),
            humidity = it.dbl("humidity"),
            weather = it.strOrNull("weather"),
            air = parseAir(it.optJSONObject("air")),
            windMs = it.dbl("windMs"),
            windLevel = it.strOrNull("windLevel"),
            windFrom = it.strOrNull("windFrom"),
        )
    }
    val link = o.optJSONObject("weather")?.let {
        WeatherLink(it.optString("station"), it.intOrNull("distance"))
    }
    return Device(
        key = o.optString("key"),
        world = o.optString("world"),
        x = o.optInt("x"), y = o.optInt("y"), z = o.optInt("z"),
        kind = o.optString("kind"),
        type = o.optString("type"),
        label = o.optString("label"),
        nickname = o.strOrNull("nickname")?.takeIf { it.isNotBlank() },
        home = o.optString("home"),
        system = o.strOrNull("system")?.takeIf { it.isNotBlank() },
        roomF = o.dbl("roomF"),
        targetF = o.dbl("targetF"),
        mode = o.optString("mode", "idle"),
        enabled = o.flag("enabled"),
        pool = o.flag("pool"),
        mini = o.flag("mini"),
        humidity = o.dbl("humidity"),
        runningUnits = o.optInt("runningUnits"),
        totalUnits = o.optInt("totalUnits"),
        profile = o.optString("profile", "normal"),
        attention = o.optJSONArray("attention").strings(),
        weather = link,
        running = o.flag("running"),
        rpm = o.dbl("rpm"),
        tier = o.optInt("tier"),
        setpoint = o.intOrNull("setpoint"),
        powered = o.flag("powered"),
        state = o.optString("state"),
        tank = o.dbl("tank"),
        filter = o.dbl("filter"),
        air = parseAir(o.optJSONObject("air")),
        fanMode = o.strOrNull("fanMode"),
        fanSpeed = o.strOrNull("fanSpeed"),
        insideF = o.dbl("insideF"),
        cold = cold,
        links = o.optInt("links"),
        reading = reading,
    )
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

fun parseState(json: String): StateData {
    val root = JSONObject(json)
    val s = root.getJSONObject("state")
    val stations = LinkedHashMap<String, Station>()
    s.optJSONObject("stations")?.let { obj ->
        obj.keys().forEach { k -> obj.optJSONObject(k)?.let { stations[k] = parseStation(it) } }
    }
    return StateData(
        player = root.optString("player"),
        weatherTab = root.flag("weatherTab"),
        units = s.optString("units", "F"),
        writable = s.flag("writable"),
        readOnlyReason = s.strOrNull("readOnlyReason"),
        homes = s.optJSONArray("homes").objects().map { Home(it.optString("id"), it.optString("name")) },
        devices = s.optJSONArray("devices").objects().map(::parseDevice),
        worlds = s.optJSONArray("worlds").objects().map { w ->
            val season = w.optJSONObject("season")
            val outdoor = w.optJSONObject("outdoor")
            World(
                name = w.optString("name"),
                season = season?.strOrNull("name"),
                seasonDay = season?.optInt("day") ?: 0,
                seasonDays = season?.optInt("days") ?: 0,
                seasonPaused = season?.flag("paused") ?: false,
                airF = outdoor?.dbl("airF"),
                weather = outdoor?.strOrNull("weather")?.takeIf { it.isNotBlank() },
            )
        },
        stations = stations,
    )
}

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
