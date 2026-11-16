package com.ezstats.android

import android.app.Application
import android.content.Context

class EzApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Start reading the saved servers and logins now, off the main thread, so the first screen doesn't
        // have to wait for the disk when it asks for them.
        Thread { getSharedPreferences("servers", Context.MODE_PRIVATE).all }.start()
    }
}
