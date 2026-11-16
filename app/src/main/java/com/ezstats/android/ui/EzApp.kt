package com.ezstats.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import com.ezstats.android.ui.common.CrispHaptics
import com.ezstats.android.ui.common.NoHaptics
import com.ezstats.android.ui.settings.SettingsScreen
import androidx.compose.ui.Modifier
import com.ezstats.android.EzViewModel
import com.ezstats.android.Status
import com.ezstats.android.ui.dashboard.DashboardSkeleton
import com.ezstats.android.ui.dashboard.MainScreen
import com.ezstats.android.ui.onboarding.LoginScreen
import com.ezstats.android.ui.onboarding.OfflineScreen
import com.ezstats.android.ui.onboarding.ServersScreen
import com.ezstats.android.ui.theme.EzTheme
import com.ezstats.android.ui.theme.Motion

/** Which top-level screen the app is on. */
private enum class Screen { NONE, CHECKING, OFFLINE, LOGIN, MAIN }

/** Picks the screen for where the app is: no server, connecting, offline, sign-in, or the dashboard. */
@Composable
fun EzApp(vm: EzViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    // Every haptic in the app goes through one feedback: crisp vibration primitives at the strength chosen in
    // Settings, or nothing at all when they are switched off there.
    val view = LocalView.current
    val context = LocalContext.current
    val strength = CrispHaptics.strengthOf(vm.settings.hapticStrength)
    val crisp = remember(view, context, strength) { CrispHaptics(view, context, strength) }

    EzTheme(vm.settings.theme, vm.settings.dynamicColor) {
      CompositionLocalProvider(LocalHapticFeedback provides if (vm.settings.haptics) crisp else NoHaptics) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                val screen = when {
                    vm.active == null -> Screen.NONE
                    vm.status == Status.CHECKING -> Screen.CHECKING
                    vm.status == Status.OFFLINE -> Screen.OFFLINE
                    vm.status == Status.LOGIN -> Screen.LOGIN
                    else -> Screen.MAIN
                }
                // The main screen stays composed while Servers slides over it, so coming back changes nothing.
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        (fadeIn(Motion.fadeIn(60)) + scaleIn(Motion.open, initialScale = 0.96f)) togetherWith fadeOut(Motion.fadeOut())
                    },
                    label = "screen",
                ) { s ->
                    when (s) {
                        Screen.NONE -> Unit
                        Screen.CHECKING -> DashboardSkeleton()
                        Screen.OFFLINE -> OfflineScreen(vm)
                        Screen.LOGIN -> LoginScreen(vm)
                        Screen.MAIN -> MainScreen(vm, snackbar)
                    }
                }
                AnimatedVisibility(
                    visible = vm.showSettings,
                    enter = slideInHorizontally(Motion.slide) { it } + fadeIn(Motion.fadeIn()),
                    exit = slideOutHorizontally(Motion.slide) { it } + fadeOut(Motion.fadeOut()),
                ) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { SettingsScreen(vm) }
                }
                AnimatedVisibility(
                    visible = vm.active == null || vm.showServers,
                    enter = slideInHorizontally(Motion.slide) { it } + fadeIn(Motion.fadeIn()),
                    exit = slideOutHorizontally(Motion.slide) { it } + fadeOut(Motion.fadeOut()),
                ) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { ServersScreen(vm) }
                }
            }
        }
      }
    }
}
