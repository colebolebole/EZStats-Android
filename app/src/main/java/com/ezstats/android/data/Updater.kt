package com.ezstats.android.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

// Checking GitHub for a newer release of this app, downloading it, and handing it to Android to install.
// Nothing about the user or the phone is sent: it is two plain requests for public files, with a generic User-Agent.

/** A release of the app on GitHub, with the APK to download. */
data class AppRelease(
    /** "2.5.2", without the leading "v". */
    val version: String,
    /** The release notes, as the author wrote them (Markdown). */
    val notes: String,
    val pageUrl: String,
    val assetName: String,
    val assetUrl: String,
    /** The APK's size in bytes (0 when GitHub doesn't say). */
    val size: Long,
    /** The APK's SHA-256 as hex, when GitHub gives one. */
    val sha256: String?,
)

/** Where the update flow is, for the Settings screen. */
sealed interface UpdateState {
    /** Nothing checked yet (or a quiet check on launch found nothing to say). */
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState
    data class Ready(val release: AppRelease, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** "v2.5.1" or "2.5.1" as [2, 5, 1]; null when it isn't dotted numbers. */
fun versionParts(version: String): List<Int>? {
    val parts = version.trim().removePrefix("v").removePrefix("V").split('.')
    if (parts.isEmpty() || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
    return parts.map { it.toIntOrNull() ?: return null }
}

/** Whether [candidate] is a later version than [current]. A version that isn't dotted numbers is never newer. */
fun isNewerVersion(candidate: String, current: String): Boolean {
    val a = versionParts(candidate) ?: return false
    val b = versionParts(current) ?: return false
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

/**
 * The release described by GitHub's `releases/latest` JSON, or null for a draft, a pre-release, or one without an APK.
 * The first `.apk` asset is the one to install.
 */
fun parseRelease(json: String): AppRelease? {
    val o = JSONObject(json)
    if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
    val version = o.optString("tag_name").trim().removePrefix("v").removePrefix("V")
    if (versionParts(version) == null) return null
    val assets = o.optJSONArray("assets") ?: return null
    for (i in 0 until assets.length()) {
        val a = assets.optJSONObject(i) ?: continue
        val name = a.optString("name")
        val url = a.optString("browser_download_url")
        if (!name.endsWith(".apk", ignoreCase = true) || url.isEmpty()) continue
        return AppRelease(
            version = version,
            notes = o.optString("body"),
            pageUrl = o.optString("html_url"),
            assetName = name,
            assetUrl = url,
            size = a.optLong("size"),
            sha256 = a.optString("digest").takeIf { it.startsWith("sha256:", ignoreCase = true) }?.substringAfter(':')?.lowercase(),
        )
    }
    return null
}

/** Release notes as plain text for a small card: no Markdown marks, no heading line, and not too long. */
fun plainNotes(markdown: String, maxChars: Int = 700): String {
    val lines = markdown.lines()
        .map { it.trim() }
        .filterNot { it.startsWith("#") }
        .map { it.replace("**", "").replace("`", "").replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1").removePrefix("- ").removePrefix("* ") }
    val text = lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
    return if (text.length <= maxChars) text else text.take(maxChars).trimEnd() + "…"
}

/** A byte count as people read it: "1.6 MB". */
fun megabytes(bytes: Long): String = if (bytes <= 0) "" else "%.1f MB".format(bytes / 1_048_576.0)

/** Talks to GitHub: finds the latest release and downloads its APK. No Android in it, so it can be tested on the JVM. */
class ReleaseClient(private val apiUrl: String = LATEST_RELEASE_URL, private val userAgent: String = "EZStats-Android") {

    /** The latest published release with an APK, or null when there is none. Throws [IOException] when GitHub can't be reached. */
    suspend fun latest(): AppRelease? = withContext(Dispatchers.IO) {
        val conn = open(apiUrl, 10_000, 15_000)
        try {
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            when (val code = conn.responseCode) {
                200 -> parseRelease(conn.inputStream.bufferedReader().use { it.readText() })
                404 -> null // no release yet
                403, 429 -> throw IOException("GitHub says to try again later.")
                else -> throw IOException("GitHub answered $code.")
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Downloads [url] to [dest] (through a temporary file, so a half-finished download is never mistaken for the APK),
     * telling [onProgress] how far it is, 0 to 1. [expectedSize] is used for the progress when the server doesn't say.
     */
    suspend fun download(url: String, dest: File, expectedSize: Long, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, dest.name + ".part")
        val conn = open(url, 15_000, 30_000)
        try {
            if (conn.responseCode != 200) throw IOException("The download answered ${conn.responseCode}.")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            var done = 0L
            var shown = -1
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt().coerceAtMost(100)
                            if (percent != shown) {
                                shown = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                }
            }
            if (!part.renameTo(dest)) {
                part.copyTo(dest, overwrite = true)
                part.delete()
            }
            onProgress(1f)
            dest
        } catch (e: Exception) {
            part.delete()
            throw if (e is IOException) e else IOException(e.message ?: "The download failed.", e)
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, connectMs: Int, readMs: Int): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = connectMs
        conn.readTimeout = readMs
        conn.instanceFollowRedirects = true // release assets are served from another host
        conn.setRequestProperty("User-Agent", userAgent) // GitHub insists on one; it names the app and nothing else
        return conn
    }

    companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/colebolebole/EZStats-Android/releases/latest"
    }
}

/** The part that needs Android: checking a downloaded file really is this app's update, and installing it. */
class AppInstaller(context: Context) {
    private val context = context.applicationContext

    /** Where updates are downloaded: inside the app's cache, which the FileProvider shares with the installer. */
    fun updateFile(release: AppRelease): File = File(File(context.cacheDir, "updates"), release.assetName.replace(Regex("[^A-Za-z0-9._-]"), "_"))

    /** Deletes the updates already downloaded, so old ones don't pile up. */
    fun clear() {
        File(context.cacheDir, "updates").listFiles()?.forEach { it.delete() }
    }

    /**
     * Whether [file] can be installed as an update of this app. Returns the reason it can't, or null. It must be the
     * size and SHA-256 GitHub reported, an APK of this same app, a newer version, and signed with the same key (which
     * Android would insist on anyway, but better to say so here than to fail in the installer).
     */
    fun problemWith(file: File, release: AppRelease): String? {
        if (release.size > 0 && file.length() != release.size) return "The download is incomplete. Try again."
        release.sha256?.let { expected ->
            if (!sha256(file).equals(expected, ignoreCase = true)) return "The download doesn't match what GitHub published. Try again."
        }
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(file.path, signingFlags()) ?: return "That file isn't an app package."
        if (info.packageName != context.packageName) return "That file is a different app."
        val mine = pm.getPackageInfo(context.packageName, signingFlags())
        if (versionCode(info) <= versionCode(mine)) return "That version is already installed."
        if (signers(info).intersect(signers(mine)).isEmpty()) return "That version is signed with a different key, so it can't update this app."
        return null
    }

    /** Android 8+ makes the user allow each app to install apps; until they do, the installer refuses. */
    fun canInstall(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** The settings page where the user allows this app to install apps. */
    fun allowInstallsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens Android's installer on [file]. */
    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Suppress("DEPRECATION")
    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    /** The SHA-256 of each certificate the package is signed with. */
    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val certs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptySet()
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else {
            info.signatures
        }
        return certs?.map { sha256(it.toByteArray()) }?.toSet().orEmpty()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
