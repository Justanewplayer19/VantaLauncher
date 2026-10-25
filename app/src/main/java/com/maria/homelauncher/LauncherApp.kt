package com.maria.homelauncher

import android.app.Application

/**
 * Exists for the things that have to outlive the home screen itself.
 *
 * The activity goes away every time another app comes forward; the process does not. The
 * screen-off watcher belongs here for that reason, where it is set up once per process
 * instead of once per visit to the home screen.
 */
class LauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ScreenWatch.start(this)
    }
}
