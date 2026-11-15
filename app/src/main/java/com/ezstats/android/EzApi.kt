package com.ezstats.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A failed call. [status] is the HTTP status, or 0 when the server couldn't be reached at all. */
class ApiException(val status: Int, message: String) : Exception(message)

class ControlResult(val adjusted: Boolean, val message: String)

/**
 * Talks to one EZStats server's JSON API (the same one its web page uses) and keeps its session cookie.
 * [onCookie] is told whenever the cookie changes so it can be stored.
 */
class EzApi(
    private val baseUrl: String,
    private var cookie: String?,
    private val onCookie: (String?) -> Unit,
) {
    private class Response(val code: Int, val body: String, val setCookie: String?)

    fun clearCookie() {
        cookie = null
        onCookie(null)
    }

    private suspend fun request(method: String, path: String, body: JSONObject? = null): Response =
        withContext(Dispatchers.IO) {
            val conn = try {
                (URL(baseUrl + path).openConnection() as HttpURLConnection)
            } catch (e: Exception) {
                throw ApiException(0, "That isn't a valid server address.")
            }
            try {
                conn.requestMethod = method
                conn.connectTimeout = 8_000
                conn.readTimeout = 15_000 // a control call can wait up to 5 s on the server
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("X-EZStats", "1") // the server insists on this for anything that changes data
                cookie?.let { conn.setRequestProperty("Cookie", "$COOKIE=$it") }
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val stream = if (code >= 400) conn.errorStream else conn.inputStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                Response(code, text, findSessionCookie(conn))
            } catch (e: IOException) {
                throw ApiException(0, "Can't reach the server (${e.message ?: e.javaClass.simpleName}).")
            } finally {
                conn.disconnect()
            }
        }

    private fun findSessionCookie(conn: HttpURLConnection): String? {
        for ((name, values) in conn.headerFields) {
            if (name == null || !name.equals("Set-Cookie", ignoreCase = true)) continue
            for (header in values) {
                val first = header.substringBefore(';').trim()
                if (first.startsWith("$COOKIE=")) return first.removePrefix("$COOKIE=")
            }
        }
        return null
    }

    private fun failure(r: Response): ApiException {
        val message = try {
            JSONObject(r.body).optString("error").ifBlank { null }
        } catch (e: Exception) {
            null
        }
        return ApiException(r.code, message ?: "The server answered ${r.code}.")
    }

    private fun checked(r: Response): Response {
        if (r.code !in 200..299) throw failure(r)
        return r
    }

    private fun store(value: String?) {
        cookie = value
        onCookie(value)
    }

    // --- session --------------------------------------------------------------------------------

    suspend fun login(username: String, password: String) {
        val r = checked(
            request("POST", "/api/login", JSONObject().put("username", username).put("password", password)),
        )
        store(r.setCookie ?: throw ApiException(500, "The server didn't start a session."))
    }

    /** Spends the one-time link from `/ezstats link`: a full link, or just its token. */
    suspend fun redeemLink(input: String) {
        val trimmed = input.trim()
        val token = Regex("[?&]token=([^&#\\s]+)").find(trimmed)?.groupValues?.get(1)
            ?: trimmed.substringAfterLast('=').trim()
        if (token.isEmpty()) throw ApiException(400, "Paste the link from /ezstats link.")
        val r = request("GET", "/link?token=" + URLEncoder.encode(java.net.URLDecoder.decode(token, "UTF-8"), "UTF-8"))
        if (r.code == 410) throw ApiException(410, "That link has expired. Run /ezstats link in game for a new one.")
        if (r.code !in 200..399) throw failure(r)
        store(r.setCookie ?: throw ApiException(500, "The server didn't start a session."))
    }

    suspend fun logout() {
        try {
            request("POST", "/api/logout", JSONObject())
        } catch (e: ApiException) {
            // signing out locally is enough
        }
        store(null)
    }

    // --- data ---------------------------------------------------------------------------------------

    suspend fun state(): StateData {
        val r = checked(request("GET", "/api/state"))
        return try {
            parseState(r.body)
        } catch (e: Exception) {
            throw ApiException(500, "That doesn't look like an EZStats server.")
        }
    }

    suspend fun weather(): WeatherData {
        val r = checked(request("GET", "/api/weather"))
        return try {
            parseWeather(r.body)
        } catch (e: Exception) {
            throw ApiException(500, "The server sent weather this app can't read.")
        }
    }

    // --- changes ------------------------------------------------------------------------------------

    suspend fun control(key: String, action: String, value: Any?): ControlResult {
        val body = JSONObject().put("key", key).put("action", action)
        if (value != null) body.put("value", value)
        val json = JSONObject(checked(request("POST", "/api/device/control", body)).body.ifBlank { "{}" })
        return ControlResult(json.optBoolean("adjusted"), json.optString("message"))
    }

    suspend fun setNickname(key: String, nickname: String) {
        checked(request("POST", "/api/device/nickname", JSONObject().put("key", key).put("nickname", nickname)))
    }

    suspend fun setHome(key: String, homeId: String) {
        checked(request("POST", "/api/device/home", JSONObject().put("key", key).put("home", homeId)))
    }

    suspend fun createHome(name: String) {
        checked(request("POST", "/api/homes", JSONObject().put("name", name)))
    }

    suspend fun renameHome(id: String, name: String) {
        checked(request("POST", "/api/homes/rename", JSONObject().put("id", id).put("name", name)))
    }

    suspend fun deleteHome(id: String) {
        checked(request("POST", "/api/homes/delete", JSONObject().put("id", id)))
    }

    private companion object {
        const val COOKIE = "ezstats_session"
    }
}
