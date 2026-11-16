package com.ezstats.android.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID

data class Server(val id: String, val name: String, val url: String)

/** Persists the list of EZStats servers and which one is currently selected. */
class ServerStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("servers", Context.MODE_PRIVATE)

    /** The parsed list, kept so it isn't re-read from JSON every time something asks which server is active. */
    @Volatile
    private var cache: List<Server>? = null

    fun list(): List<Server> {
        cache?.let { return it }
        val raw = prefs.getString(KEY_SERVERS, null)
        val parsed = if (raw == null) emptyList() else try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Server(o.getString("id"), o.getString("name"), o.getString("url"))
            }
        } catch (e: Exception) {
            emptyList()
        }
        cache = parsed
        return parsed
    }

    private fun save(servers: List<Server>) {
        val arr = JSONArray()
        servers.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url))
        }
        cache = servers
        prefs.edit().putString(KEY_SERVERS, arr.toString()).apply()
    }

    // --- the last state we saw, so the next launch can show something at once ------------------------------

    private fun snapshotFile(id: String) = java.io.File(appContext.filesDir, "state-$id.json")

    /** The last state JSON received from this server, or null. Reads a file: call it off the main thread. */
    fun readSnapshot(id: String): String? = try {
        snapshotFile(id).takeIf { it.isFile }?.readText()
    } catch (e: Exception) {
        null
    }

    fun writeSnapshot(id: String, json: String) {
        try {
            val file = snapshotFile(id)
            val tmp = java.io.File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json)
            tmp.renameTo(file) // never leaves a half-written file behind
        } catch (e: Exception) {
            // a cache that can't be written is just a cache that isn't there
        }
    }

    fun deleteSnapshot(id: String) {
        try {
            snapshotFile(id).delete()
            weatherFile(id).delete()
        } catch (e: Exception) {
        }
    }

    private fun weatherFile(id: String) = java.io.File(appContext.filesDir, "weather-$id.json")

    fun readWeatherSnapshot(id: String): String? = try {
        weatherFile(id).takeIf { it.isFile }?.readText()
    } catch (e: Exception) {
        null
    }

    fun writeWeatherSnapshot(id: String, json: String) {
        try {
            val file = weatherFile(id)
            val tmp = java.io.File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json)
            tmp.renameTo(file)
        } catch (e: Exception) {
        }
    }

    fun active(): Server? {
        val servers = list()
        val id = prefs.getString(KEY_ACTIVE, null)
        return servers.firstOrNull { it.id == id }
    }

    fun setActive(id: String) {
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    /** Adds a server; the first one added becomes the active server. */
    fun add(name: String, url: String): Server {
        val server = Server(UUID.randomUUID().toString(), name, url)
        val servers = list() + server
        save(servers)
        if (active() == null) setActive(server.id)
        return server
    }

    fun update(id: String, name: String, url: String) {
        save(list().map { if (it.id == id) it.copy(name = name, url = url) else it })
    }

    /** Ids of the homes the user has folded shut on the dashboard ("none" is the "No home" group). */
    fun collapsedHomes(): Set<String> = prefs.getStringSet("collapsed_homes", emptySet())?.toSet().orEmpty()

    fun setCollapsedHomes(ids: Set<String>) {
        prefs.edit().putStringSet("collapsed_homes", ids.toSet()).apply()
    }

    /** The session cookie EZStats gave us for this server, kept so a login survives restarts. */
    fun cookie(id: String): String? = prefs.getString("cookie_$id", null)

    fun setCookie(id: String, value: String?) {
        prefs.edit().apply {
            if (value == null) remove("cookie_$id") else putString("cookie_$id", value)
        }.apply()
    }

    fun delete(id: String) {
        setCookie(id, null)
        deleteSnapshot(id)
        val remaining = list().filterNot { it.id == id }
        save(remaining)
        if (prefs.getString(KEY_ACTIVE, null) == id) {
            val next = remaining.firstOrNull()
            prefs.edit().apply {
                if (next != null) putString(KEY_ACTIVE, next.id) else remove(KEY_ACTIVE)
            }.apply()
        }
    }

    companion object {
        private const val KEY_SERVERS = "servers"
        private const val KEY_ACTIVE = "active"

        /**
         * Turns user input such as "192.168.1.10:8765" or "https://stats.example.com/"
         * into a clean URL, or returns null when it isn't a usable http(s) address.
         * A missing scheme defaults to http://, because that is how EZStats serves by default.
         */
        fun normalizeUrl(input: String): String? {
            var s = input.trim()
            if (s.isEmpty()) return null
            if (!s.contains("://")) s = "http://$s"
            val uri = try {
                URI(s)
            } catch (e: Exception) {
                return null
            }
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "http" && scheme != "https") return null
            if (uri.host.isNullOrEmpty()) return null
            return s.trimEnd('/')
        }

        fun hostOf(url: String): String =
            try {
                URI(url).host ?: url
            } catch (e: Exception) {
                url
            }
    }
}
