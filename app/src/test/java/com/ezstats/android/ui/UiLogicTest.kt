package com.ezstats.android.ui

import com.ezstats.android.data.Device
import com.ezstats.android.data.ForecastDay
import com.ezstats.android.data.Station
import com.ezstats.android.data.Wind
import com.ezstats.android.ui.common.UnitFmt
import com.ezstats.android.ui.common.rubberBand
import com.ezstats.android.ui.weather.Sky
import com.ezstats.android.ui.weather.compassDegrees
import com.ezstats.android.ui.weather.forecastRange
import com.ezstats.android.ui.weather.orderStations
import com.ezstats.android.ui.weather.skyOf
import com.ezstats.android.ui.weather.todaySentence
import com.ezstats.android.ui.weather.weatherPages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The logic behind the screens that doesn't need a screen: ordering, scales, sentences, springs. */
class UiLogicTest {

    private fun station(key: String, world: String = "world", working: Boolean = true, sky: String = "clear", label: String = key) = Station(
        key = key, label = label, world = world, x = 0, y = 64, z = 0,
        enabled = true, intact = true, working = working, lightning = 0,
        outdoorF = 60.0, wind = Wind(4.0, "breezy", "NW"), air = null,
        forecast = if (working) listOf(
            ForecastDay(0, 70.0, 50.0, sky, 0.6, 0.0, "Fog early", 30, null),
            ForecastDay(1, 80.0, 55.0, "rain", 0.8, 0.0, "", 30, null),
        ) else emptyList(),
    )

    @Test
    fun stationsNearYourThermostatsComeFirstAndIdleOnesLast() {
        val all = listOf(
            station("idle", working = false),
            station("b", world = "zeta"),
            station("a", world = "alpha"),
            station("mine", world = "zeta"),
        )
        val order = orderStations(all, mapOf("mine" to listOf("Living room")))
        assertEquals(listOf("mine", "a", "b", "idle"), order.map { it.key })
    }

    @Test
    fun weatherShowsEveryStationUntilYouStarSome() {
        val all = listOf(station("a"), station("b"), station("c"))
        assertEquals(listOf("a", "b", "c"), weatherPages(all, emptySet(), null, loaded = true).map { it.key })
        // Browsing means nothing while there are no favorites: you already see everything.
        assertEquals(listOf("a", "b", "c"), weatherPages(all, emptySet(), "b", loaded = true).map { it.key })
    }

    @Test
    fun onceYouHaveFavoritesTheWeatherPageShowsOnlyThose() {
        val all = listOf(station("a"), station("b"), station("c"))
        assertEquals(listOf("b", "c"), weatherPages(all, setOf("b", "c"), null, loaded = true).map { it.key })
    }

    @Test
    fun aStationYouBrowseIsAnExtraPageAndAFavoriteIsNotDuplicated() {
        val all = listOf(station("a"), station("b"), station("c"))
        assertEquals(listOf("b", "a"), weatherPages(all, setOf("b"), "a", loaded = true).map { it.key })
        assertEquals(listOf("b"), weatherPages(all, setOf("b"), "b", loaded = true).map { it.key })
        // A station that isn't on the server can't be browsed.
        assertEquals(listOf("b"), weatherPages(all, setOf("b"), "zzz", loaded = true).map { it.key })
    }

    @Test
    fun favoritesThatNoLongerExistFallBackToEverythingOnceLoaded() {
        val all = listOf(station("a"), station("b"))
        assertEquals(listOf("a", "b"), weatherPages(all, setOf("gone"), null, loaded = true).map { it.key })
        // Still loading: show nothing rather than flash the wrong stations, then switch.
        assertTrue(weatherPages(all, setOf("gone"), null, loaded = false).isEmpty())
    }

    @Test
    fun theWeekIsDrawnOnOneScaleThatIncludesRightNow() {
        val s = station("s")
        assertEquals(50.0 to 80.0, forecastRange(s.forecast, 60.0))
        assertEquals(40.0 to 80.0, forecastRange(s.forecast, 40.0))
        assertNull(forecastRange(emptyList(), null))
        // A flat week still gets a bar to draw on.
        val flat = listOf(ForecastDay(0, 60.0, 60.0, "clear", 0.0, 0.0, "", null, null))
        assertEquals(59.0 to 61.0, forecastRange(flat, null))
    }

    @Test
    fun compassPointsBecomeDegrees() {
        assertEquals(0f, compassDegrees("N"))
        assertEquals(315f, compassDegrees("nw"))
        assertEquals(22.5f, compassDegrees("NNE"))
        assertNull(compassDegrees("sideways"))
        assertNull(compassDegrees(null))
    }

    @Test
    fun todayIsSummedUpInASentence() {
        val s = todaySentence(station("s"), UnitFmt(false))!!
        assertTrue(s, s.startsWith("Clear today. Fog early."))
        assertTrue(s, s.contains("wind NW at 9 mph, breezy", ignoreCase = true))
        assertFalse("no rain talk on a clear day", s.contains("rain"))
        assertNull(todaySentence(station("idle", working = false), UnitFmt(false)))
    }

    @Test
    fun theSkyFollowsTodaysForecast() {
        assertEquals(Sky.CLEAR, skyOf(station("s")))
        assertEquals(Sky.THUNDER, skyOf(station("s", sky = "thunder")))
        assertEquals(Sky.OFFLINE, skyOf(station("s", working = false)))
        assertEquals(Sky.CLOUDY, skyOf("overcast"))
    }

    @Test
    fun pullingGivesLessAndLessAndNeverPastTheLimit() {
        val limit = 50f
        assertEquals(0f, rubberBand(0f, limit))
        val small = rubberBand(10f, limit)
        val big = rubberBand(1000f, limit)
        assertTrue("it follows the finger, with resistance", small > 0f && small < 10f)
        assertTrue("it never passes the limit", big < limit)
        assertEquals("both ways alike", -small, rubberBand(-10f, limit), 0.0001f)
        assertTrue("more pull is always more stretch", rubberBand(200f, limit) > rubberBand(100f, limit))
    }

    @Test
    fun celsiusStepsMoveOneShownDegree() {
        val c = UnitFmt(true)
        // 50°F is 10°C; one step up is 11°C, stored as the nearest whole °F (52).
        assertEquals(52, c.stepF(50, 1))
        assertEquals("10°C", c.wholeF(50))
        assertTrue(abs(c.show(212.0) - 100.0) < 1e-9)
    }

    @Test
    fun onlyDevicesWithMoreToShowOpenFullScreen() {
        fun dev(kind: String, type: String) = Device(key = type, world = "w", x = 0, y = 0, z = 0, kind = kind, type = type, label = "", nickname = null, home = "", system = null)
        assertTrue(dev("thermostat", "room").opensDetail)
        assertTrue(dev("unit", "refrigerator").opensDetail)
        assertTrue(dev("unit", "air_purifier").opensDetail)
        assertTrue(dev("unit", "dehumidifier").opensDetail)
        assertFalse(dev("equipment", "furnace").opensDetail)
        assertFalse(dev("instrument", "thermometer").opensDetail)
    }
}
