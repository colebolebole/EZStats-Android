package com.ezstats.android.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/** Finding, comparing and downloading the app's own releases from GitHub, against a stand-in for GitHub. */
class UpdaterTest {

    private class Reply(val code: Int, val body: ByteArray = ByteArray(0), val location: String? = null)

    private lateinit var socket: ServerSocket
    private val routes = ConcurrentHashMap<String, Reply>()
    @Volatile private var userAgent: String? = null

    private val base get() = "http://127.0.0.1:${socket.localPort}"

    @Before
    fun start() {
        socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (e: Exception) { return@thread }
                thread(isDaemon = true) {
                    client.use { c ->
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
                        val path = line().split(" ").getOrNull(1) ?: return@use
                        while (true) {
                            val h = line()
                            if (h.isEmpty()) break
                            if (h.startsWith("User-Agent:", ignoreCase = true)) userAgent = h.substringAfter(':').trim()
                        }
                        val r = routes[path] ?: Reply(404)
                        val head = StringBuilder("HTTP/1.1 ${r.code} X\r\nContent-Length: ${r.body.size}\r\nConnection: close\r\n")
                        r.location?.let { head.append("Location: $it\r\n") }
                        head.append("\r\n")
                        c.getOutputStream().apply { write(head.toString().toByteArray()); write(r.body); flush() }
                    }
                }
            }
        }
    }

    @After
    fun stop() = socket.close()

    private val release = """
        {"tag_name":"v2.5.2","name":"EZStats 2.5.2","draft":false,"prerelease":false,
         "html_url":"https://github.com/colebolebole/EZStats-Android/releases/tag/v2.5.2",
         "body":"## What's new in 2.5.2\n\n- **Faster** weather, see [the docs](https://example.com) and `config`.\n- Smaller APK.\n",
         "assets":[
           {"name":"source.zip","size":10,"browser_download_url":"https://example.com/source.zip"},
           {"name":"EZStats-2.5.2.apk","size":1725515,"browser_download_url":"https://github.com/x/EZStats-2.5.2.apk",
            "digest":"sha256:ABCDEF0123"}]}
    """.trimIndent()

    // --- versions ---------------------------------------------------------------------------------------

    @Test
    fun versionsCompareNumberByNumber() {
        assertTrue(isNewerVersion("v2.5.2", "2.5.1"))
        assertTrue(isNewerVersion("2.6.0", "2.5.9"))
        assertTrue(isNewerVersion("v2.10.0", "2.9.9")) // not as text
        assertTrue(isNewerVersion("3", "2.9.9"))
        assertFalse(isNewerVersion("2.5.1", "2.5.1"))
        assertFalse(isNewerVersion("v2.5", "2.5.0")) // the same, with a part left off
        assertFalse(isNewerVersion("2.5.0", "2.5.1"))
        assertFalse(isNewerVersion("latest", "2.5.1")) // not a version: never an update
        assertFalse(isNewerVersion("2.5.2", "dev"))
        assertEquals(listOf(2, 5, 1), versionParts("V2.5.1"))
        assertNull(versionParts("2.5.x"))
        assertNull(versionParts(""))
    }

    // --- reading GitHub's JSON --------------------------------------------------------------------------

    @Test
    fun theLatestReleaseIsReadWithItsApk() {
        val r = parseRelease(release)!!
        assertEquals("2.5.2", r.version)
        assertEquals("EZStats-2.5.2.apk", r.assetName) // the .apk, not the zip before it
        assertEquals("https://github.com/x/EZStats-2.5.2.apk", r.assetUrl)
        assertEquals(1725515L, r.size)
        assertEquals("abcdef0123", r.sha256)
        assertTrue(r.pageUrl.endsWith("/v2.5.2"))
    }

    @Test
    fun draftsPrereleasesAndReleasesWithoutAnApkAreIgnored() {
        assertNull(parseRelease(release.replace("\"draft\":false", "\"draft\":true")))
        assertNull(parseRelease(release.replace("\"prerelease\":false", "\"prerelease\":true")))
        assertNull(parseRelease(release.replace(".apk", ".txt")))
        assertNull(parseRelease(release.replace("\"v2.5.2\"", "\"nightly\"")))
        // GitHub only adds a digest to newer assets: without one the size alone is checked.
        assertNull(parseRelease(release.replace("\"digest\":\"sha256:ABCDEF0123\"", "\"digest\":null"))!!.sha256)
    }

    @Test
    fun releaseNotesBecomePlainText() {
        val text = plainNotes(parseRelease(release)!!.notes)
        assertEquals("Faster weather, see the docs and config.\nSmaller APK.", text)
        assertEquals("a".repeat(10) + "…", plainNotes("a".repeat(50), maxChars = 10))
        assertEquals("1.6 MB", megabytes(1_725_515))
        assertEquals("", megabytes(0))
    }

    // --- talking to GitHub ------------------------------------------------------------------------------

    @Test
    fun theClientAsksForTheLatestReleaseAsAGenericApp() = runBlocking {
        routes["/latest"] = Reply(200, release.toByteArray())
        val r = ReleaseClient("$base/latest").latest()
        assertEquals("2.5.2", r!!.version)
        // GitHub wants a User-Agent; this one names the app and nothing about the user or the phone.
        assertEquals("EZStats-Android", userAgent)
    }

    @Test
    fun noReleaseYetIsNotAnError() = runBlocking {
        routes["/latest"] = Reply(404)
        assertNull(ReleaseClient("$base/latest").latest())
    }

    @Test
    fun githubFailuresSayTheyAreIOProblems() = runBlocking {
        routes["/latest"] = Reply(403)
        try {
            ReleaseClient("$base/latest").latest(); fail("expected a rate limit")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("try again later"))
        }
        routes["/latest"] = Reply(500)
        try {
            ReleaseClient("$base/latest").latest(); fail("expected a server error")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("500"))
        }
        val dead = ReleaseClient("$base/latest")
        socket.close() // nobody home: an IOException the app turns into "Couldn't reach GitHub"
        try {
            dead.latest(); fail("expected no connection")
        } catch (e: IOException) {
            assertNotNull(e.message)
        }
    }

    // --- downloading ------------------------------------------------------------------------------------

    @Test
    fun anApkIsDownloadedThroughARedirectWithProgress() = runBlocking {
        val bytes = ByteArray(300_000) { (it * 31).toByte() }
        routes["/asset"] = Reply(302, location = "$base/real") // release assets live on another host
        routes["/real"] = Reply(200, bytes)
        val dest = File(File(System.getProperty("java.io.tmpdir"), "dl-${System.nanoTime()}"), "EZStats.apk")
        val progress = mutableListOf<Float>()
        val got = ReleaseClient().download("$base/asset", dest, bytes.size.toLong()) { progress += it }
        assertArrayEquals(bytes, got.readBytes())
        assertFalse(File(dest.parentFile, dest.name + ".part").exists()) // no half-finished file left behind
        assertEquals(1f, progress.last(), 0f)
        assertTrue("progress only goes forward", progress.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(progress.size > 3) // reported along the way, not just at the end
        dest.parentFile.deleteRecursively()
        Unit
    }

    @Test
    fun aFailedDownloadLeavesNothingBehind() = runBlocking {
        routes["/asset"] = Reply(500)
        val dest = File(File(System.getProperty("java.io.tmpdir"), "dl-${System.nanoTime()}"), "EZStats.apk")
        try {
            ReleaseClient().download("$base/asset", dest, 100) { }; fail("expected a failure")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("500"))
        }
        assertFalse(dest.exists())
        assertFalse(File(dest.parentFile, dest.name + ".part").exists())
        dest.parentFile.deleteRecursively()
        Unit
    }
}
