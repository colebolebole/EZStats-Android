package com.ezstats.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A failed call. [status] is the HTTP status, or 0 when the server couldn't be reached at all. */
class ApiException(val status: Int, message: String) : Exception(message)

class ControlResult(val adjusted: Boolean, val message: String)

/** The 8 digits of a QuickLogin or reset code (the server ignores spaces and dashes too), or null if it isn't one. */
fun codeDigits(input: String): String? =
    input.filter { !it.isWhitespace() && it != '-' }.takeIf { it.length == 8 && it.all(Char::isDigit) }

/** Why [password] won't do as a new password (the server's rule: 8 to 128 characters, no whitespace), or null. */
fun passwordProblem(password: String): String? = when {
    password.length < 8 -> "The new password needs at least 8 characters."
    password.length > 128 -> "The new password can be at most 128 characters."
    password.any { it.isWhitespace() } -> "The new password can't contain spaces."
    else -> null
}

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
        try {
            send(method, path, body)
        } catch (e: ApiException) {
            // A read-only call that failed to connect, stalled or was cut (often a pooled connection the network quietly
            // dropped) is tried once more on a fresh connection, instead of failing the whole poll.
            if (method == "GET" && e.status == 0) send(method, path, body) else throw e
        }

    private suspend fun send(method: String, path: String, body: JSONObject?): Response =
        withContext(Dispatchers.IO) {
            val conn = try {
                (URL(baseUrl + path).openConnection() as HttpURLConnection)
            } catch (e: Exception) {
                throw ApiException(0, "That isn't a valid server address.")
            }
            try {
                conn.requestMethod = method
                conn.connectTimeout = 6_000
                conn.readTimeout = if (method == "GET") 8_000 else 15_000 // a control call can wait up to 5 s on the server
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
                // Not disconnect()ed on purpose: the body is read in full and closed, so the connection goes back
                // to the pool and the next poll skips the TCP (and TLS) handshake.
                Response(code, text, findSessionCookie(conn))
            } catch (e: IOException) {
                conn.disconnect()
                throw ApiException(0, "Can't reach the server (${e.message ?: e.javaClass.simpleName}).")
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
        val fallback = if (r.code == 429) "Too many wrong tries. Wait a few minutes, then try again." else "The server answered ${r.code}."
        return ApiException(r.code, message ?: fallback)
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

    /** QuickLogin (EZStats 0.7.0+): the Minecraft name and the 8 digit code `/ezstats quicklogin` sent in chat. */
    suspend fun quickLogin(username: String, code: String) {
        val digits = codeDigits(code) ?: throw ApiException(400, "The code is 8 digits.")
        val r = request("POST", "/api/quicklogin", JSONObject().put("username", username).put("code", digits))
        if (r.code == 404) throw ApiException(404, "This server doesn't have QuickLogin. It needs EZStats 0.7.0 or newer.")
        checked(r)
        store(r.setCookie ?: throw ApiException(500, "The server didn't start a session."))
    }

    /**
     * Password reset (EZStats 0.7.1+): the name, the 8 digit code `/ezstats resetpassword` sent in chat, and the new
     * password. It signs this device in, and the server ends the player's other sessions.
     */
    suspend fun resetPassword(username: String, code: String, newPassword: String) {
        val digits = codeDigits(code) ?: throw ApiException(400, "The code is 8 digits.")
        passwordProblem(newPassword)?.let { throw ApiException(400, it) }
        val r = request(
            "POST", "/api/reset-password",
            JSONObject().put("username", username).put("code", digits).put("password", newPassword),
        )
        if (r.code == 404) throw ApiException(404, "This server can't reset passwords from the app. It needs EZStats 0.7.1 or newer.")
        checked(r)
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

    /** The parsed state, and the JSON it came from (kept to show instantly on the next launch). */
    class StateResult(val data: StateData, val raw: String)

    // Parsing happens on a background thread: a big state parsed on the main thread every two seconds is a
    // dropped frame every two seconds.
    suspend fun stateResult(): StateResult = stateResultIfChanged(null)!!

    /**
     * The state, or null when the server sent exactly [previousRaw] again: most polls change nothing, and those
     * then skip parsing, comparing and redrawing altogether.
     */
    suspend fun stateResultIfChanged(previousRaw: String?): StateResult? {
        val r = checked(request("GET", "/api/state"))
        if (previousRaw != null && r.body == previousRaw) return null
        return withContext(Dispatchers.Default) {
            try {
                StateResult(parseState(r.body), r.body)
            } catch (e: Exception) {
                throw ApiException(500, "That doesn't look like an EZStats server.")
            }
        }
    }

    suspend fun state(): StateData = stateResult().data

    class WeatherResult(val data: WeatherData, val raw: String)

    suspend fun weatherResult(): WeatherResult = weatherResultIfChanged(null)!!

    /** The weather, or null when it is exactly [previousRaw] again (see [stateResultIfChanged]). */
    suspend fun weatherResultIfChanged(previousRaw: String?): WeatherResult? {
        val r = checked(request("GET", "/api/weather"))
        if (previousRaw != null && r.body == previousRaw) return null
        return withContext(Dispatchers.Default) {
            try {
                WeatherResult(parseWeather(r.body), r.body)
            } catch (e: Exception) {
                throw ApiException(500, "The server sent weather this app can't read.")
            }
        }
    }

    suspend fun weather(): WeatherData = weatherResult().data

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

    // --- organizations, folders and homes (EZStats 0.8+; an older server only has homes, at the top) ------------

    /** Makes an organization, folder or home. [parent] is the id of the organization or folder it goes in, "" for the top. */
    suspend fun createGroup(kind: GroupKind, name: String, parent: String = "") {
        val body = JSONObject().put("name", name)
        if (kind != GroupKind.ORG && parent.isNotEmpty()) body.put("parent", parent)
        checked(groupRequest(request("POST", "/api/${kind.route}", body), kind != GroupKind.HOME || parent.isNotEmpty()))
    }

    suspend fun renameGroup(kind: GroupKind, id: String, name: String) {
        checked(groupRequest(request("POST", "/api/${kind.route}/rename", JSONObject().put("id", id).put("name", name)), kind != GroupKind.HOME))
    }

    /** Deletes only that group: what was in it moves up a level. */
    suspend fun deleteGroup(kind: GroupKind, id: String) {
        checked(groupRequest(request("POST", "/api/${kind.route}/delete", JSONObject().put("id", id)), kind != GroupKind.HOME))
    }

    /** Moves a folder or home, with everything in it, into [parent] ("" is the top). */
    suspend fun moveGroup(id: String, parent: String) {
        checked(groupRequest(request("POST", "/api/groups/move", JSONObject().put("id", id).put("parent", parent)), true))
    }

    /**
     * Moves a folder or home into [parent] ("" is the top) and puts it just before [before] there ("" is the end), in one
     * call (EZStats 0.8.2). A server that has organizations but not that route (0.8.0, 0.8.1) gets the same result as two
     * calls, a move (when [parent] isn't [currentParent]) and then the order.
     */
    suspend fun placeGroup(id: String, parent: String, before: String, currentParent: String) {
        val r = request("POST", "/api/groups/place", JSONObject().put("id", id).put("parent", parent).put("before", before))
        if (r.code == 404 && failureMessage(r)?.startsWith("No such") != true) {
            if (parent != currentParent) moveGroup(id, parent)
            orderGroup(id, before)
        } else {
            checked(r)
        }
    }

    /** Puts a group just before [before] among the groups beside it; "" is the end. */
    suspend fun orderGroup(id: String, before: String) {
        checked(groupRequest(request("POST", "/api/groups/order", JSONObject().put("id", id).put("before", before)), true))
    }

    /** Puts a device just before [before] among the devices of its home; "" is the end of the home. */
    suspend fun orderDevice(key: String, before: String) {
        checked(groupRequest(request("POST", "/api/device/order", JSONObject().put("key", key).put("before", before)), true))
    }

    /**
     * A server older than 0.8 answers 404 to the routes that came with folders; say what it needs instead of "Not found".
     * A 0.8 server's own 404s say "No such home." (or folder, organization, group), and those are kept.
     */
    private fun groupRequest(r: Response, needsNewServer: Boolean): Response =
        if (r.code == 404 && needsNewServer && failureMessage(r)?.startsWith("No such") != true) {
            Response(404, """{"error":"This server doesn't have organizations and folders. It needs EZStats 0.8.0 or newer."}""", null)
        } else r

    private fun failureMessage(r: Response): String? = try {
        JSONObject(r.body).optString("error").ifBlank { null }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val COOKIE = "ezstats_session"
    }
}
