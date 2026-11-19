package com.ezstats.android.data

import android.content.Context
import androidx.compose.runtime.Immutable

/** How the app looks and behaves on this phone. These stay on the phone; they aren't server settings. */
@Immutable
data class AppSettings(
    /** "system", "light" or "dark". */
    val theme: String = "system",
    /** Use the wallpaper's colours (Android 12+) instead of the app's own teal. */
    val dynamicColor: Boolean = true,
    /** "server" (whatever the server uses), "f" or "c". */
    val units: String = "server",
    /** Seconds between refreshes of the devices while the app is open. */
    val refreshSeconds: Int = 2,
    /** Animated skies on the Weather tab: rain, snow, sun, lightning. */
    val animatedWeather: Boolean = true,
    val haptics: Boolean = true,
    /** "light", "medium" or "strong". */
    val hapticStrength: String = "medium",
    /** Look for a newer version of the app on GitHub when it opens (at most every few hours). */
    val autoUpdateCheck: Boolean = true,
) {
    companion object {
        val THEMES = listOf("system", "light", "dark")
        val UNITS = listOf("server", "f", "c")
        val REFRESH = listOf(2, 5, 10, 30)
        val HAPTIC_STRENGTHS = listOf("light", "medium", "strong")
    }
}

/** Reads and writes [AppSettings]. */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = prefs.getString("theme", d.theme)?.takeIf { it in AppSettings.THEMES } ?: d.theme,
            dynamicColor = prefs.getBoolean("dynamic_color", d.dynamicColor),
            units = prefs.getString("units", d.units)?.takeIf { it in AppSettings.UNITS } ?: d.units,
            refreshSeconds = prefs.getInt("refresh_seconds", d.refreshSeconds).takeIf { it in AppSettings.REFRESH } ?: d.refreshSeconds,
            animatedWeather = prefs.getBoolean("animated_weather", d.animatedWeather),
            haptics = prefs.getBoolean("haptics", d.haptics),
            hapticStrength = prefs.getString("haptic_strength", d.hapticStrength)?.takeIf { it in AppSettings.HAPTIC_STRENGTHS } ?: d.hapticStrength,
            autoUpdateCheck = prefs.getBoolean("auto_update_check", d.autoUpdateCheck),
        )
    }

    /** When the app last asked GitHub for a newer version (epoch milliseconds), or 0. */
    fun lastUpdateCheck(): Long = prefs.getLong("last_update_check", 0L)

    fun markUpdateCheck(now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong("last_update_check", now).apply()
    }

    fun save(s: AppSettings) {
        prefs.edit()
            .putString("theme", s.theme)
            .putBoolean("dynamic_color", s.dynamicColor)
            .putString("units", s.units)
            .putInt("refresh_seconds", s.refreshSeconds)
            .putBoolean("animated_weather", s.animatedWeather)
            .putBoolean("haptics", s.haptics)
            .putString("haptic_strength", s.hapticStrength)
            .putBoolean("auto_update_check", s.autoUpdateCheck)
            .apply()
    }
}
