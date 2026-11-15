package com.ezstats.android

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

enum class Status { CHECKING, OFFLINE, LOGIN, READY }

enum class Tab { DEVICES, WEATHER }

class EzViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ServerStore(app)

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

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private var api: EzApi? = null
    private val inFlight = HashMap<String, Int>() // devices with a change on its way: polling must not undo it

    init {
        if (active != null) {
            bind()
            check()
        }
    }

    // --- servers ----------------------------------------------------------------------------------

    private fun bind() {
        val server = active
        api = server?.let { s -> EzApi(s.url, store.cookie(s.id)) { store.setCookie(s.id, it) } }
    }

    private fun resetSession() {
        data = null
        weather = null
        banner = null
        loginError = null
        statusError = null
        tab = Tab.DEVICES
        inFlight.clear()
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
                val fresh = a.state()
                if (active != server) return@launch
                data = fresh
                status = Status.READY
            } catch (e: ApiException) {
                if (active != server) return@launch
                if (e.status == 401) {
                    status = Status.LOGIN
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
                val fresh = a.state()
                if (active != server) return@launch
                data = fresh
                banner = null
                status = Status.READY
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
            resetSession()
            status = Status.LOGIN
        }
    }

    private fun sessionEnded() {
        api?.clearCookie()
        resetSession()
        loginError = "Your session ended. Sign in again."
        status = Status.LOGIN
    }

    // --- polling ------------------------------------------------------------------------------------

    /** Keeps the screen current while it is visible: devices every 2 s, the weather tab every 10 s. */
    suspend fun pollLoop() {
        var lastWeather = 0L
        while (true) {
            refreshState()
            val now = System.currentTimeMillis()
            if (tab == Tab.WEATHER && data?.weatherTab == true && now - lastWeather > 10_000) {
                refreshWeather()
                lastWeather = now
            }
            delay(2_000)
        }
    }

    suspend fun refreshState() {
        val a = api ?: return
        val server = active
        try {
            val fresh = a.state()
            if (active != server) return
            val mine = data?.devices?.associateBy { it.key }.orEmpty()
            data = if (inFlight.isEmpty()) fresh else fresh.copy(
                devices = fresh.devices.map { if (inFlight.containsKey(it.key)) mine[it.key] ?: it else it },
            )
            banner = null
        } catch (e: ApiException) {
            if (active != server) return
            if (e.status == 401) sessionEnded() else banner = e.message
        }
    }

    private suspend fun refreshWeather() {
        val a = api ?: return
        val server = active
        try {
            val fresh = a.weather()
            if (active == server) weather = fresh
        } catch (e: ApiException) {
            if (active != server) return
            if (e.status == 401) sessionEnded() else if (e.status != 404) banner = e.message
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            refreshState()
            if (tab == Tab.WEATHER) refreshWeather()
        }
    }

    // --- changes ------------------------------------------------------------------------------------

    private fun updateDevice(key: String, change: (Device) -> Device) {
        val s = data ?: return
        data = s.copy(devices = s.devices.map { if (it.key == key) change(it) else it })
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
            refreshState()
        }
    }

    /** Runs a change that isn't a device control; returns the error to show, or null on success. */
    private suspend fun mutate(call: suspend (EzApi) -> Unit): String? {
        val a = api ?: return "Not connected."
        return try {
            call(a)
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
