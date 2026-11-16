package com.ezstats.android

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ezstats.android.data.ApiException
import com.ezstats.android.data.AppSettings
import com.ezstats.android.data.SettingsStore
import com.ezstats.android.data.Device
import com.ezstats.android.data.EzApi
import com.ezstats.android.data.Server
import com.ezstats.android.data.ServerStore
import com.ezstats.android.data.StateData
import com.ezstats.android.data.WeatherData
import com.ezstats.android.data.parseState
import com.ezstats.android.data.seasonSignature
import com.ezstats.android.data.parseWeather
import com.ezstats.android.data.shareWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Status { CHECKING, OFFLINE, LOGIN, READY }

enum class Tab { DEVICES, WEATHER }

class EzViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ServerStore(app)
    private val settingsStore = SettingsStore(app)

    /** This phone's preferences: theme, units, refresh rate and so on. */
    var settings by mutableStateOf(settingsStore.load()); private set
    var showSettings by mutableStateOf(false)

    fun updateSettings(change: (AppSettings) -> AppSettings) {
        settings = change(settings)
        settingsStore.save(settings)
    }

    var servers by mutableStateOf(store.list()); private set
    var active by mutableStateOf(store.active()); private set
    var showServers by mutableStateOf(false)

    var status by mutableStateOf(Status.CHECKING); private set
    var statusError by mutableStateOf<String?>(null); private set

    var data by mutableStateOf<StateData?>(null); private set
    var weather by mutableStateOf<WeatherData?>(null); private set
    /** A connection problem while there is still data on screen. */
    var banner by mutableStateOf<String?>(null); private set

    var loginBusy by mutableStateOf(false); private set
    var loginError by mutableStateOf<String?>(null); private set

    var tab by mutableStateOf(Tab.DEVICES)

    /** The station the Weather tab shows (its pager follows this), or null for the first one. */
    var weatherStation by mutableStateOf<String?>(null)

    /**
     * The weather stations starred on the active server. The Weather tab pages through these once there are any;
     * every station stays one tap away in its list. (Declared above `init`, which loads them through bind().)
     */
    var favoriteStations by mutableStateOf<Set<String>>(emptySet()); private set

    /** The device shown full screen, by key, or null for the dashboard. */
    var openDetail by mutableStateOf<String?>(null)

    /** Where each device tile is on screen, so the full-screen view can grow from and shrink back to it. */
    val tileBounds = HashMap<String, Rect>()

    /** The dashboard sets this: scrolls (and unfolds its home) so a device's tile is on screen. */
    var revealTile: (suspend (String) -> Unit)? = null

    /** How much the dashboard is scaled down behind the full-screen view, and the size of the screen. */
    var dashboardScale: () -> Float = { 1f }
    var rootSize = Size.Zero

    /** Homes folded shut on the dashboard; remembered between launches. */
    var collapsed by mutableStateOf(store.collapsedHomes()); private set

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private var api: EzApi? = null
    private val inFlight = HashMap<String, Int>() // devices with a change on its way: polling must not undo it

    /** The JSON behind [data] and [weather]: a poll that brings back exactly the same text is skipped. */
    private var lastStateRaw: String? = null
    private var lastWeatherRaw: String? = null

    /** A summary of every world's season and day, to notice when it changes. */
    private var seasonSignature: String? = null

    /** After a season (or season day) changes, the weather is re-read often until this time. */
    private var weatherBurstUntil = 0L

    init {
        if (active != null) {
            bind()
            check()
            showCached()
        }
    }

    // --- the last state we saw -----------------------------------------------------------------------------

    private var lastSnapshot = 0L

    /**
     * Shows the dashboard as it was when the app was last open, straight away, while the real state is fetched.
     * Whichever arrives first wins; the fetched state replaces it a moment later.
     */
    private fun showCached() {
        val server = active ?: return
        if (store.cookie(server.id) == null) return // never signed in here: nothing worth showing
        viewModelScope.launch(Dispatchers.Default) {
            val raw = store.readSnapshot(server.id) ?: return@launch
            val cached = runCatching { parseState(raw) }.getOrNull() ?: return@launch
            val cachedWeather = store.readWeatherSnapshot(server.id)?.let { runCatching { parseWeather(it) }.getOrNull() }
            withContext(Dispatchers.Main) {
                if (active == server && data == null && status == Status.CHECKING) {
                    data = cached
                    status = Status.READY
                }
                if (active == server && weather == null && cachedWeather != null) weather = cachedWeather
            }
        }
    }

    /** Remembers the state for the next launch, in the background and at most every ten seconds. */
    private fun saveSnapshot(server: Server?, raw: String, force: Boolean = false) {
        if (server == null) return
        val now = System.currentTimeMillis()
        if (!force && now - lastSnapshot < 10_000) return
        lastSnapshot = now
        viewModelScope.launch(Dispatchers.IO) { store.writeSnapshot(server.id, raw) }
    }

    private var lastWeatherSnapshot = 0L

    /** The same for the weather, at most every thirty seconds. */
    private fun saveWeatherSnapshot(server: Server?, raw: String) {
        if (server == null) return
        val now = System.currentTimeMillis()
        if (now - lastWeatherSnapshot < 30_000) return
        lastWeatherSnapshot = now
        viewModelScope.launch(Dispatchers.IO) { store.writeWeatherSnapshot(server.id, raw) }
    }

    private fun forgetSnapshot() {
        active?.let { s -> viewModelScope.launch(Dispatchers.IO) { store.deleteSnapshot(s.id) } }
    }

    // --- servers ----------------------------------------------------------------------------------

    private fun bind() {
        val server = active
        api = server?.let { s -> EzApi(s.url, store.cookie(s.id)) { store.setCookie(s.id, it) } }
        favoriteStations = server?.let { store.favoriteStations(it.id) }.orEmpty()
    }

    fun toggleFavoriteStation(key: String) {
        val server = active ?: return
        val first = favoriteStations.isEmpty()
        favoriteStations = if (key in favoriteStations) favoriteStations - key else favoriteStations + key
        store.setFavoriteStations(server.id, favoriteStations)
        if (first && favoriteStations.isNotEmpty()) {
            messages.tryEmit("The Weather tab now shows your favorites. Every station is still in the list.")
        }
    }

    private fun resetSession() {
        data = null
        weather = null
        lastStateRaw = null
        lastWeatherRaw = null
        seasonSignature = null
        weatherBurstUntil = 0L
        banner = null
        loginError = null
        statusError = null
        tab = Tab.DEVICES
        weatherStation = null
        openDetail = null
        showSettings = false
        inFlight.clear()
    }

    fun toggleHome(id: String) {
        collapsed = if (id in collapsed) collapsed - id else collapsed + id
        store.setCollapsedHomes(collapsed)
    }

    fun setAllCollapsed(ids: Collection<String>, collapse: Boolean) {
        collapsed = if (collapse) collapsed + ids else collapsed - ids.toSet()
        store.setCollapsedHomes(collapsed)
    }

    private fun reloadServers() {
        servers = store.list()
        active = store.active()
    }

    fun selectServer(id: String) {
        store.setActive(id)
        reloadServers()
        showServers = false
        resetSession()
        bind()
        check()
    }

    fun addServer(name: String, url: String) {
        val first = store.list().isEmpty()
        store.add(name, url)
        reloadServers()
        if (first) selectServer(store.active()!!.id)
    }

    fun editServer(id: String, name: String, url: String) {
        val old = store.list().firstOrNull { it.id == id } ?: return
        store.update(id, name, url)
        reloadServers()
        if (id == active?.id && old.url != url) {
            store.setCookie(id, null) // a different address is a different server: sign in again
            resetSession()
            bind()
            check()
        }
    }

    fun deleteServer(id: String) {
        val wasActive = id == active?.id
        store.delete(id)
        reloadServers()
        if (wasActive) {
            resetSession()
            bind()
            if (active != null) check()
        }
    }

    // --- connecting ---------------------------------------------------------------------------------

    fun check() {
        val a = api ?: return
        val server = active
        status = Status.CHECKING
        statusError = null
        viewModelScope.launch {
            try {
                val fresh = a.stateResult()
                if (active != server) return@launch
                data = fresh.data.shareWith(data)
                lastStateRaw = fresh.raw
                status = Status.READY
                saveSnapshot(server, fresh.raw, force = true)
            } catch (e: ApiException) {
                if (active != server) return@launch
                if (e.status == 401) {
                    forgetSnapshot()
                    data = null
                    status = Status.LOGIN
                } else if (data != null) {
                    banner = e.message // the last known state is on screen: say it may be out of date
                    status = Status.READY
                } else {
                    statusError = e.message
                    status = Status.OFFLINE
                }
            }
        }
    }

    fun signIn(username: String, password: String) = authenticate { it.login(username.trim(), password) }

    fun signInWithLink(link: String) = authenticate { it.redeemLink(link) }

    private fun authenticate(call: suspend (EzApi) -> Unit) {
        val a = api ?: return
        val server = active
        loginBusy = true
        loginError = null
        viewModelScope.launch {
            try {
                call(a)
                val fresh = a.stateResult()
                if (active != server) return@launch
                data = fresh.data
                lastStateRaw = fresh.raw
                banner = null
                status = Status.READY
                saveSnapshot(server, fresh.raw, force = true)
            } catch (e: ApiException) {
                loginError = e.message
            } finally {
                loginBusy = false
            }
        }
    }

    fun signOut() {
        val a = api ?: return
        viewModelScope.launch {
            a.logout()
            forgetSnapshot()
            resetSession()
            status = Status.LOGIN
        }
    }

    private fun sessionEnded() {
        api?.clearCookie()
        forgetSnapshot()
        resetSession()
        loginError = "Your session ended. Sign in again."
        status = Status.LOGIN
    }

    // --- polling ------------------------------------------------------------------------------------

    /** Keeps the devices current while the screen is visible: every 2 seconds, or as often as set in Settings. */
    suspend fun pollLoop() {
        while (true) {
            refreshState()
            val bursting = data?.weatherTab == true && System.currentTimeMillis() < weatherBurstUntil
            if (bursting) refreshWeather()
            delay(if (bursting) minOf(settings.refreshSeconds, 2) * 1_000L else settings.refreshSeconds * 1_000L)
        }
    }

    /**
     * The season is in the dashboard state, which is read every couple of seconds, but the forecast comes from the
     * weather, which would otherwise only be read every few seconds on its own tab. When the season or its day
     * changes the forecasts change with it, so read the weather right away and keep reading it for a while: it
     * shows up as soon as the server has it, instead of whenever the next scheduled read happens to land.
     */
    private fun noteSeason(next: StateData) {
        val signature = next.seasonSignature()
        if (seasonSignature != null && signature != seasonSignature && next.weatherTab) {
            weatherBurstUntil = System.currentTimeMillis() + 40_000
        }
        seasonSignature = signature
    }

    /** Keeps the weather current while the Weather tab is showing: it starts at once, then every 5 seconds. */
    suspend fun weatherLoop() {
        while (true) {
            refreshWeather()
            delay(5_000)
        }
    }

    /** Loads the weather once, as soon as the dashboard is up, so the Weather tab opens with everything ready. */
    suspend fun prefetchWeather() {
        if (lastWeatherRaw == null && data?.weatherTab == true) refreshWeather()
    }

    suspend fun refreshState() {
        val a = api ?: return
        val server = active
        try {
            val result = a.stateResultIfChanged(lastStateRaw)
            if (active != server) return
            banner = null
            if (result == null) return // exactly what is on screen already
            var next = result.data
            if (inFlight.isNotEmpty()) {
                val mine = data?.devices?.associateBy { it.key }.orEmpty()
                next = next.copy(devices = next.devices.map { if (inFlight.containsKey(it.key)) mine[it.key] ?: it else it })
                lastStateRaw = null // what is shown isn't what the server sent: read it properly next time
            } else {
                lastStateRaw = result.raw
            }
            // Same objects wherever nothing changed, so the screen only redraws what actually did.
            data = next.shareWith(data)
            noteSeason(next)
            saveSnapshot(server, result.raw)
        } catch (e: ApiException) {
            if (active != server) return
            if (e.status == 401) sessionEnded() else banner = e.message
        }
    }

    private suspend fun refreshWeather() {
        val a = api ?: return
        val server = active
        try {
            val fresh = a.weatherResultIfChanged(lastWeatherRaw) ?: return
            if (active != server) return
            weather = fresh.data.shareWith(weather)
            lastWeatherRaw = fresh.raw
            saveWeatherSnapshot(server, fresh.raw)
        } catch (e: ApiException) {
            if (active != server) return
            if (e.status == 401) sessionEnded() else if (e.status != 404) banner = e.message
        }
    }

    /** True while a refresh the user asked for (pull to refresh) is running. */
    var refreshing by mutableStateOf(false); private set

    fun refreshNow() {
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            try {
                lastStateRaw = null
                refreshState()
                if (tab == Tab.WEATHER) {
                    lastWeatherRaw = null
                    refreshWeather()
                }
                // A refresh that is over in a blink looks like nothing happened: let the indicator be seen.
                delay((600 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            } finally {
                refreshing = false
            }
        }
    }

    /** Shows a weather station on the Weather tab. */
    fun openStation(key: String) {
        weatherStation = key
        tab = Tab.WEATHER
    }

    // --- changes ------------------------------------------------------------------------------------

    private fun updateDevice(key: String, change: (Device) -> Device) {
        val s = data ?: return
        data = s.copy(devices = s.devices.map { if (it.key == key) change(it) else it })
        lastStateRaw = null // the screen no longer matches the last answer
    }

    /**
     * Changes a device. The screen shows [optimistic] at once; polling leaves that device alone until
     * the server has answered and been read again, so the value doesn't jump back.
     */
    fun control(key: String, action: String, value: Any?, optimistic: (Device) -> Device) {
        val a = api ?: return
        inFlight[key] = (inFlight[key] ?: 0) + 1
        updateDevice(key, optimistic)
        viewModelScope.launch {
            try {
                val result = a.control(key, action, value)
                if (result.adjusted && result.message.isNotBlank()) messages.tryEmit(result.message)
            } catch (e: ApiException) {
                if (e.status == 401) sessionEnded() else messages.tryEmit(e.message ?: "Something went wrong")
            }
            delay(350)
            val left = (inFlight[key] ?: 1) - 1
            if (left <= 0) inFlight.remove(key) else inFlight[key] = left
            lastStateRaw = null
            refreshState()
        }
    }

    /** Runs a change that isn't a device control; returns the error to show, or null on success. */
    private suspend fun mutate(call: suspend (EzApi) -> Unit): String? {
        val a = api ?: return "Not connected."
        return try {
            call(a)
            lastStateRaw = null
            refreshState()
            null
        } catch (e: ApiException) {
            if (e.status == 401) {
                sessionEnded()
                "Signed out."
            } else {
                e.message
            }
        }
    }

    suspend fun renameDevice(key: String, nickname: String) = mutate { it.setNickname(key, nickname.trim()) }

    suspend fun moveToHome(key: String, homeId: String) = mutate { it.setHome(key, homeId) }

    suspend fun createHome(name: String) = mutate { it.createHome(name.trim()) }

    suspend fun renameHome(id: String, name: String) = mutate { it.renameHome(id, name.trim()) }

    suspend fun deleteHome(id: String) = mutate { it.deleteHome(id) }
}
