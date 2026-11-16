package com.ezstats.android.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

private class Req(val method: String, val path: String, val rawQuery: String?, val headers: Map<String, String>, val body: String)

private class Res(val code: Int, val body: String, val cookie: String? = null, val location: String? = null)

/** A bare-bones HTTP/1.1 server: the JDK's own HttpServer isn't visible from Android unit tests. */
private class MockHttp(private val handler: (Req) -> Res) {
    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val port get() = socket.localPort

    fun start() {
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (e: Exception) { return@thread }
                thread(isDaemon = true) { client.use { serve(it) } }
            }
        }
    }

    fun stop() = socket.close()

    private fun serve(c: Socket) {
        val input = c.getInputStream().buffered()
        fun line(): String {
            val sb = StringBuilder()
            while (true) {
                val b = input.read()
                if (b < 0 || b == '\n'.code) break
                if (b != '\r'.code) sb.append(b.toChar())
            }
            return sb.toString()
        }
        val first = line().split(" ")
        if (first.size < 2) return
        val headers = HashMap<String, String>()
        while (true) {
            val h = line()
            if (h.isEmpty()) break
            headers[h.substringBefore(':').lowercase()] = h.substringAfter(':').trim()
        }
        val len = headers["content-length"]?.toInt() ?: 0
        val raw = ByteArray(len)
        var n = 0
        while (n < len) n += input.read(raw, n, len - n)
        val target = first[1]
        val res = handler(
            Req(first[0], target.substringBefore('?'), target.substringAfter('?', "").ifEmpty { null }, headers, raw.decodeToString()),
        )
        val bytes = res.body.toByteArray()
        val out = StringBuilder("HTTP/1.1 ${res.code} X\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n")
        res.cookie?.let { out.append("Set-Cookie: ezstats_session=$it; Path=/; HttpOnly; SameSite=Strict\r\n") }
        res.location?.let { out.append("Location: $it\r\n") }
        out.append("\r\n")
        c.getOutputStream().apply {
            write(out.toString().toByteArray())
            write(bytes)
            flush()
        }
    }
}

/** Runs [EzApi] against a stand-in for the EZStats web server, with the same rules (header, cookie, JSON shapes). */
class EzApiTest {
    private lateinit var server: MockHttp
    private lateinit var base: String
    private val sessions = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val seen = java.util.Collections.synchronizedList(mutableListOf<String>()) // "METHOD path body"
    private var savedCookie: String? = null
    @Volatile private var olderServer = false

    @Before
    fun start() {
        server = MockHttp { handle(it) }
        server.start()
        base = "http://127.0.0.1:${server.port}"
    }

    @After
    fun stop() = server.stop()

    private fun api(cookie: String? = null) = EzApi(base, cookie) { savedCookie = it }

    private fun signedIn(r: Req): Boolean {
        val c = r.headers["cookie"] ?: return false
        return c.removePrefix("ezstats_session=") in sessions
    }

    private fun handle(r: Req): Res {
        seen += "${r.method} ${r.path} ${r.body}"
        val custom = r.headers.containsKey("x-ezstats")
        val notSignedIn = Res(401, """{"error":"Not signed in."}""")
        val noHeader = Res(403, """{"error":"Missing X-EZStats header"}""")
        return when {
            r.method == "POST" && r.path == "/api/login" -> {
                if (!custom) return noHeader
                val o = JSONObject(r.body)
                if (o.getString("username") == "Steve" && o.getString("password") == "hunter2") {
                    sessions += "abc123"
                    Res(200, """{"ok":true}""", cookie = "abc123")
                } else Res(401, """{"error":"Wrong username or password."}""")
            }
            olderServer && r.path in listOf("/api/quicklogin", "/api/reset-password") -> Res(404, """{"error":"Not found"}""")
            r.method == "POST" && r.path == "/api/quicklogin" -> {
                if (!custom) return noHeader
                val o = JSONObject(r.body)
                if (o.getString("username") == "Locked") return Res(429, "")
                if (o.getString("username") == "Steve" && o.getString("code") == "12345678") {
                    sessions += "quick1"
                    Res(200, """{"ok":true}""", cookie = "quick1")
                } else Res(401, """{"error":"That code didn't work. Run /ezstats quicklogin in game for a new one."}""")
            }
            r.method == "POST" && r.path == "/api/reset-password" -> {
                if (!custom) return noHeader
                val o = JSONObject(r.body)
                if (o.getString("username") == "Steve" && o.getString("code") == "87654321") {
                    sessions += "reset1"
                    Res(200, """{"ok":true}""", cookie = "reset1")
                } else Res(401, """{"error":"That code didn't work. Run /ezstats resetpassword in game for a new one."}""")
            }
            r.method == "GET" && r.path == "/api/state" -> if (signedIn(r)) Res(200, STATE) else notSignedIn
            r.method == "GET" && r.path == "/api/weather" -> if (signedIn(r)) Res(200, WEATHER) else notSignedIn
            r.method == "POST" && r.path == "/api/device/control" -> {
                if (!signedIn(r)) return notSignedIn
                if (!custom) return noHeader
                val o = JSONObject(r.body)
                when {
                    o.getString("action") == "target" && o.getDouble("value") > 85 ->
                        Res(200, """{"ok":true,"adjusted":true,"message":"Limited to 85 °F."}""")
                    o.getString("action") == "profile" && o.getString("value") == "bogus" ->
                        Res(400, """{"error":"Bad request"}""")
                    else -> Res(200, """{"ok":true,"adjusted":false,"message":""}""")
                }
            }
            r.method == "POST" && r.path == "/api/logout" -> {
                sessions.clear()
                Res(200, """{"ok":true}""")
            }
            else -> Res(404, """{"error":"Not found"}""")
        }
    }

    @Test
    fun loginStoresCookieAndStateParses() = runBlocking {
        val a = api()
        try {
            a.state(); fail("expected 401")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
        }
        a.login("Steve", "hunter2")
        assertEquals("abc123", savedCookie)

        val s = a.state()
        assertEquals("Steve", s.player)
        assertTrue(s.weatherTab)
        assertTrue(s.writable)
        assertEquals("F", s.units)
        assertEquals(listOf("Cottage"), s.homes.map { it.name })
        assertEquals(1, s.worlds.size)
        assertEquals("summer", s.worlds[0].season)
        assertEquals(4, s.devices.size)

        val t = s.devices.first { it.isThermostat }
        assertEquals("Living room", t.label)
        assertEquals("Lounge", t.nickname)
        assertEquals(72.5, t.targetF!!, 0.0)
        assertEquals(70.1, t.roomF!!, 0.0)
        assertEquals("heating", t.mode)
        assertEquals("eco", t.profile)
        assertEquals(listOf("cant_keep_up"), t.attention)
        assertEquals("wx1", t.weather!!.station)
        assertEquals(40, t.weather!!.distance)
        assertEquals("home1", t.home)

        val h = s.devices.first { it.type == "humidifier" }
        assertEquals(50, h.setpoint)
        assertTrue(h.powered)
        assertEquals(0.4, h.tank!!, 0.0)
        assertNull(h.nickname)

        val f = s.devices.first { it.type == "refrigerator" }
        assertEquals(36, f.cold!!.fridgeSetpointF)
        assertTrue(f.cold!!.doorOpen)
        assertNull(f.cold!!.chillerF) // left out by the server because it was NaN

        val i = s.devices.first { it.kind == "instrument" }
        assertEquals(68.0, i.reading!!.tempF!!, 0.0)
        assertEquals(2, s.stations.getValue("wx1").forecast.size)
        assertEquals("W", s.stations.getValue("wx1").wind!!.from)
    }

    @Test
    fun controlSendsHeaderCookieAndBody() = runBlocking {
        val a = api("abc123").also { sessions += "abc123" }
        val ok = a.control("w:1:2:3", "profile", "turbo")
        assertFalse(ok.adjusted)
        val adj = a.control("w:1:2:3", "target", 90.0)
        assertTrue(adj.adjusted)
        assertEquals("Limited to 85 °F.", adj.message)
        val sent = JSONObject(seen.last().substringAfter("/api/device/control "))
        assertEquals("w:1:2:3", sent.getString("key"))
        assertEquals(90.0, sent.getDouble("value"), 0.0)
        try {
            a.control("w:1:2:3", "profile", "bogus"); fail("expected error")
        } catch (e: ApiException) {
            assertEquals(400, e.status)
            assertEquals("Bad request", e.message)
        }
    }

    @Test
    fun wrongPasswordReportsServerMessage() = runBlocking {
        try {
            api().login("Steve", "nope"); fail("expected error")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
            assertEquals("Wrong username or password.", e.message)
        }
        assertNull(savedCookie)
    }

    @Test
    fun quickLoginSendsTheDigitsAndStoresTheCookie() = runBlocking {
        // Spaces and dashes in the code are fine; the server gets plain digits.
        api().quickLogin("Steve", "1234 5678")
        assertEquals("quick1", savedCookie)
        assertTrue(seen.last().contains("\"code\":\"12345678\""))
        savedCookie = null
        api().quickLogin("Steve", "1234-5678")
        assertEquals("quick1", savedCookie)
        try {
            api().quickLogin("Steve", "00000000"); fail("expected a wrong code")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
            assertTrue(e.message!!.contains("/ezstats quicklogin"))
        }
        // A code that isn't 8 digits never reaches the server.
        val before = seen.size
        try {
            api().quickLogin("Steve", "1234"); fail("expected a short code")
        } catch (e: ApiException) {
            assertEquals(400, e.status)
        }
        assertEquals(before, seen.size)
    }

    @Test
    fun resetPasswordSendsTheNewPasswordAndSignsIn() = runBlocking {
        api().resetPassword("Steve", "8765 4321", "a-new-password")
        assertEquals("reset1", savedCookie)
        val sent = JSONObject(seen.last().substringAfter("/api/reset-password "))
        assertEquals("87654321", sent.getString("code"))
        assertEquals("a-new-password", sent.getString("password"))
        savedCookie = null
        try {
            api().resetPassword("Steve", "11111111", "a-new-password"); fail("expected a wrong code")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
            assertTrue(e.message!!.contains("/ezstats resetpassword"))
        }
        assertNull(savedCookie)
        // A bad password is refused before the code is spent (the server would reject it too).
        val before = seen.size
        for (bad in listOf("short", "has a space in it", "x".repeat(129))) {
            try {
                api().resetPassword("Steve", "87654321", bad); fail("expected '$bad' to be refused")
            } catch (e: ApiException) {
                assertEquals(400, e.status)
            }
        }
        assertEquals(before, seen.size)
    }

    @Test
    fun pilot8FieldsAreReadAndAbsentOnesStayNull() = runBlocking {
        sessions += "abc123"
        val s = api("abc123").state()
        val t = s.devices.first { it.isThermostat }
        // Heating since 750 s before the server's read: 12 whole minutes, worked out on the server's own clock.
        assertEquals(12L, t.modeMinutes)
        assertEquals(LastRun("cooling", 1799990000000, 1799991800000), t.lastRun)
        // An older EZvac leaves all of it out.
        val f = s.devices.first { it.type == "refrigerator" }
        assertNull(f.modeMinutes); assertNull(f.lastRun)
    }

    @Test
    fun codesAndPasswordsAreChecked() {
        assertEquals("12345678", codeDigits(" 1234 5678 "))
        assertEquals("12345678", codeDigits("1234-5678"))
        assertNull(codeDigits("1234567"))
        assertNull(codeDigits("123456789"))
        assertNull(codeDigits("1234567a"))
        assertNull(passwordProblem("eightchr"))
        assertNull(passwordProblem("x".repeat(128)))
        assertTrue(passwordProblem("seven77") != null)
    }

    @Test
    fun anOlderServerSaysWhatItNeeds() = runBlocking {
        olderServer = true // answers 404 to /api/quicklogin and /api/reset-password, as a server before 0.7.0 does
        try {
            api().quickLogin("Steve", "12345678"); fail("expected 404")
        } catch (e: ApiException) {
            assertEquals(404, e.status)
            assertTrue(e.message!!.contains("0.7.0"))
        }
        try {
            api().resetPassword("Steve", "87654321", "a-new-password"); fail("expected 404")
        } catch (e: ApiException) {
            assertEquals(404, e.status)
            assertTrue(e.message!!.contains("0.7.1"))
        }
    }

    @Test
    fun tooManyTriesGetsAFriendlyAnswer() = runBlocking {
        try {
            api().quickLogin("Locked", "12345678"); fail("expected 429")
        } catch (e: ApiException) {
            assertEquals(429, e.status)
            assertTrue(e.message!!.contains("Wait"))
        }
    }

    @Test
    fun weatherParsesAndLogoutClearsCookie() = runBlocking {
        sessions += "abc123"
        val a = api("abc123")
        val w = a.weather()
        assertEquals(1, w.stations.size)
        assertTrue(w.stations[0].working)
        assertEquals("rain", w.stations[0].forecast[1].sky)
        assertEquals(0.6, w.stations[0].forecast[1].rain, 0.0)
        a.logout()
        assertNull(savedCookie)
        assertNotNull(seen.firstOrNull { it.startsWith("POST /api/logout") })
    }

    @Test
    fun unreachableServerIsStatusZero() = runBlocking {
        val a = EzApi("http://127.0.0.1:1", null) {}
        try {
            a.state(); fail("expected failure")
        } catch (e: ApiException) {
            assertEquals(0, e.status)
        }
    }

    @Test
    fun unchangedObjectsAreSharedBetweenReads() = runBlocking {
        sessions += "abc123"
        val a = api("abc123")
        val first = a.state()
        val again = a.state()
        assertEquals(first, again)
        assertTrue("an identical read reuses the old state", again.shareWith(first) === first)

        // One thermostat changes: every other device keeps its object, the changed one is new.
        val changed = first.copy(devices = first.devices.map { if (it.isThermostat) it.copy(targetF = 70.0) else it })
        val shared = changed.shareWith(first)
        assertTrue(shared !== first)
        assertEquals(70.0, shared.devices.first { it.isThermostat }.targetF!!, 0.0)
        first.devices.filterNot { it.isThermostat }.forEach { old ->
            assertTrue(shared.devices.first { it.key == old.key } === old)
        }
        assertTrue(shared.homes === first.homes)
        assertTrue(shared.worlds === first.worlds)
        assertTrue(shared.stations === first.stations)
    }

    @Test
    fun anUnchangedPollIsSkipped() = runBlocking {
        sessions += "abc123"
        val a = api("abc123")
        val first = a.stateResult()
        assertNull("the same answer again isn't parsed", a.stateResultIfChanged(first.raw))
        assertNotNull("anything else is", a.stateResultIfChanged(first.raw + " "))
        assertNotNull("and so is the first read", a.stateResultIfChanged(null))
        val weather = a.weatherResult()
        assertNull(a.weatherResultIfChanged(weather.raw))
    }

    @Test
    fun weatherKeepsUnchangedStationsBetweenReads() = runBlocking {
        sessions += "abc123"
        val a = api("abc123")
        val first = a.weatherResult()
        assertTrue(first.raw.contains("\"stations\""))
        val again = a.weather()
        assertTrue("an identical read reuses the old data", again.shareWith(first.data) === first.data)
        val warmer = first.data.copy(stations = first.data.stations.map { it.copy(outdoorF = 90.0) })
        val shared = warmer.shareWith(first.data)
        assertEquals(90.0, shared.stations[0].outdoorF!!, 0.0)
        assertTrue(shared.stations !== first.data.stations)
    }

    @Test
    fun settingTheSeasonChangesTheSignatureButTheTemperatureDoesNot() = runBlocking {
        sessions += "abc123"
        val s = api("abc123").state()
        val world = s.worlds.single()
        assertEquals("world:summer:3", s.seasonSignature())
        // It warms up outside: not a season change.
        assertEquals(s.seasonSignature(), s.copy(worlds = listOf(world.copy(airF = 99.0, weather = "Rain"))).seasonSignature())
        // Someone sets the season in game, or a new season day starts.
        assertTrue(s.seasonSignature() != s.copy(worlds = listOf(world.copy(season = "winter"))).seasonSignature())
        assertTrue(s.seasonSignature() != s.copy(worlds = listOf(world.copy(seasonDay = 4))).seasonSignature())
    }

    @Test
    fun urlNormalising() {
        assertEquals("http://192.168.1.10:8765", ServerStore.normalizeUrl(" 192.168.1.10:8765/ "))
        assertEquals("https://stats.example.com", ServerStore.normalizeUrl("https://stats.example.com/"))
        assertNull(ServerStore.normalizeUrl("ftp://x"))
        assertNull(ServerStore.normalizeUrl(""))
    }

    companion object {
        private const val STATE = """
        {"player":"Steve","weatherTab":true,"state":{
          "generatedAt":1800000000000,"units":"F","writable":true,"ezvacVersion":"x","apiVersion":"1.13",
          "homes":[{"id":"home1","name":"Cottage"}],
          "devices":[
            {"key":"w:1:2:3","world":"world","x":1,"y":2,"z":3,"kind":"thermostat","type":"room","label":"Living room",
             "roomF":70.1,"targetF":72.5,"mode":"heating","enabled":true,"pool":false,"mini":false,"humidity":41.0,
             "runningUnits":1,"totalUnits":2,"unitsOff":0,"profile":"eco","blocked":false,"attention":["cant_keep_up"],
             "nickname":"Lounge","home":"home1","weather":{"station":"wx1","distance":40},
             "loaded":true,"modeSince":1799999250000,
             "lastRun":{"mode":"cooling","startedAt":1799990000000,"endedAt":1799991800000}},
            {"key":"w:4:5:6","world":"world","x":4,"y":5,"z":6,"kind":"unit","type":"humidifier","label":"Bedroom",
             "humidity":38.0,"setpoint":50,"powered":true,"state":"running","tank":0.4,"home":"","loaded":false},
            {"key":"w:7:8:9","world":"world","x":7,"y":8,"z":9,"kind":"unit","type":"refrigerator","label":"Kitchen",
             "humidity":40.0,"setpoint":0,"powered":true,"state":"idle","tank":0.0,"insideF":-2.0,
             "cold":{"doorOpen":true,"defrosting":false,"autoDefrost":true,"fridgeInsideF":38.0,"fridgeSetpointF":36,"barrels":0},"home":"home1"},
            {"key":"w:9:9:9","world":"world","x":9,"y":9,"z":9,"kind":"instrument","type":"thermometer","label":"Porch",
             "links":0,"reading":{"tempF":68.0},"home":""}
          ],
          "worlds":[{"name":"world","season":{"name":"summer","day":3,"days":10,"paused":false},
                     "outdoor":{"airF":75.0,"feelsLikeF":77.0,"humidity":50.0,"weather":"Clear"}}],
          "stations":{"wx1":{"key":"wx1","label":"Tower","world":"world","x":0,"y":70,"z":0,"enabled":true,"intact":true,
                      "working":true,"lightningStrikes":0,"areaRadius":128,"outdoorF":71.0,
                      "wind":{"speedMs":5.0,"level":"breezy","from":"W"},
                      "forecast":[{"daysAhead":0,"highF":80.0,"lowF":60.0,"sky":"clear","rain":0.0,"thunder":0.0,"note":""},
                                  {"daysAhead":1,"highF":70.0,"lowF":55.0,"sky":"rain","rain":0.6,"thunder":0.1,"note":"Wet"}]}}
        }}
        """

        private const val WEATHER = """
        {"generatedAt":5,"units":"F","stations":[{"key":"wx1","label":"Tower","world":"world","x":0,"y":70,"z":0,
          "enabled":true,"intact":true,"working":true,"lightningStrikes":0,"areaRadius":128,"outdoorF":71.0,
          "forecast":[{"daysAhead":0,"highF":80.0,"lowF":60.0,"sky":"clear","rain":0.0,"thunder":0.0,"note":""},
                      {"daysAhead":1,"highF":70.0,"lowF":55.0,"sky":"rain","rain":0.6,"thunder":0.1,"note":"Wet"}]}]}
        """
    }
}
