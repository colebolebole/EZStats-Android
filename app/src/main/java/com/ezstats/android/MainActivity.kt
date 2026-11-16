package com.ezstats.android

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.ezstats.android.ui.EzApp

class MainActivity : ComponentActivity() {
    private val vm: EzViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Hold the splash screen until the first real screen is known (the cached dashboard, the sign-in
        // form, or an error), but never for long: the app shouldn't sit on a splash waiting for a slow server.
        val started = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition {
            vm.active != null && vm.status == Status.CHECKING && SystemClock.uptimeMillis() - started < 700
        }

        enableEdgeToEdge()
        setContent { EzApp(vm) }
    }
}
