package com.ezstats.android.ui.dashboard

import com.ezstats.android.ui.theme.LocalIsDark
import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.toSize
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ezstats.android.EzViewModel
import com.ezstats.android.Tab
import com.ezstats.android.ui.common.EntranceTracker
import com.ezstats.android.ui.common.LocalEntrance
import com.ezstats.android.ui.common.LocalUnits
import com.ezstats.android.ui.common.LocalWritable
import com.ezstats.android.ui.common.UnitFmt
import com.ezstats.android.ui.detail.DetailOverlay
import com.ezstats.android.ui.theme.Motion
import com.ezstats.android.ui.weather.WeatherTab
import com.ezstats.android.ui.weather.rememberWeatherUiState

/** How far the dashboard sinks (as a fraction of its size) while a device is open on top of it. */
private const val DEPTH = 0.06f

/**
 * The signed-in app: the Devices and Weather tabs, the bar to switch between them, and the full-screen device
 * view that grows out of a tile on top of everything.
 */
@Composable
fun MainScreen(vm: EzViewModel, snackbar: SnackbarHostState) {
    val state = vm.data ?: return DashboardSkeleton()
    val owner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

    // Keep the screen current, but only while the app is in view.
    LaunchedEffect(vm.active?.id) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.pollLoop() }
    }
    val showWeather = state.weatherTab
    if (!showWeather && vm.tab == Tab.WEATHER) vm.tab = Tab.DEVICES

    // The weather: load it once as soon as the dashboard is up, so the tab opens ready, and keep it fresh while it shows.
    LaunchedEffect(vm.active?.id, showWeather) {
        if (showWeather) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.prefetchWeather() }
    }
    LaunchedEffect(vm.active?.id, showWeather, vm.tab) {
        if (showWeather && vm.tab == Tab.WEATHER) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.weatherLoop() }
    }

    // How far the device view has opened (0 = closed). The dashboard behind it reads this to sink back.
    val expand = remember { Animatable(0f) }
    SideEffect { vm.dashboardScale = { 1f - DEPTH * expand.value.coerceIn(0f, 1f) } }
    LaunchedEffect(vm.openDetail) {
        // If the full-screen view vanished without closing (its device went away), settle the dashboard back.
        if (vm.openDetail == null && expand.value != 0f) expand.animateTo(0f, Motion.close)
    }

    // Kept here, above the tabs, so each tab is exactly where you left it when you come back to it.
    val gridState = rememberLazyGridState()
    val weatherUi = rememberWeatherUiState()
    val entrance = remember(vm.active?.id) { EntranceTracker() }

    // The weather's sky is dark: light status bar icons there, the theme's own everywhere else.
    val onSky = vm.tab == Tab.WEATHER && vm.openDetail == null && !weatherUi.showPicker && !vm.showSettings && !vm.showServers
    val dark = LocalIsDark.current
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark && !onSky
                isAppearanceLightNavigationBars = !dark && !onSky
            }
        }
    }

    CompositionLocalProvider(
        LocalUnits provides remember(state.units, vm.settings.units) {
            UnitFmt(
                when (vm.settings.units) {
                    "c" -> true
                    "f" -> false
                    else -> state.units == "C"
                },
            )
        },
        LocalWritable provides state.writable,
        LocalEntrance provides entrance,
    ) {
        // The dashboard stays composed under the device view, so it is exactly where you left it.
        Box(Modifier.fillMaxSize().onSizeChanged { vm.rootSize = it.toSize() }) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val e = expand.value.coerceIn(0f, 1f)
                        scaleX = 1f - DEPTH * e
                        scaleY = 1f - DEPTH * e
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(Color.Black.copy(alpha = 0.32f * expand.value.coerceIn(0f, 1f)))
                    },
            ) {
                val tabs = remember {
                    listOf(
                        TabItem(Tab.DEVICES, "Devices", Icons.Outlined.Thermostat, Icons.Rounded.Thermostat),
                        TabItem(Tab.WEATHER, "Weather", Icons.Outlined.WbSunny, Icons.Rounded.WbSunny),
                    )
                }
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        if (showWeather) FloatingTabBar(tabs, vm.tab, onSelect = { vm.tab = it }, onSky = onSky)
                    },
                    snackbarHost = { SnackbarHost(snackbar) },
                ) { padding ->
                    // Material's fade-through: the old tab gets out of the way fast, the new one grows in.
                    AnimatedContent(
                        targetState = vm.tab,
                        transitionSpec = {
                            (fadeIn(Motion.fadeIn(70)) + scaleIn(Motion.open, initialScale = 0.94f)) togetherWith fadeOut(Motion.fadeOut())
                        },
                        label = "tab",
                    ) { tab ->
                        when (tab) {
                            Tab.DEVICES -> DevicesTab(vm, state, padding, gridState)
                            Tab.WEATHER -> WeatherTab(vm, padding, weatherUi)
                        }
                    }
                }
            }
            vm.openDetail?.let { key -> DetailOverlay(vm, key, state, snackbar, expand) }
        }
    }
}
