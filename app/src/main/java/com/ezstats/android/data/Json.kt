package com.ezstats.android.data

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

fun parseWeather(json: String): WeatherData {
    val root = JSONObject(json)
    return WeatherData(
        units = root.optString("units", "F"),
        generatedAt = root.optLong("generatedAt"),
        stations = root.optJSONArray("stations").objects().map(::parseStation),
    )
}

// --- devices ------------------------------------------------------------------------------------------

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
