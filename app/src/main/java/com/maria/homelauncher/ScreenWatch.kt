package com.maria.homelauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Changes the lock screen wallpaper when the screen goes off.
 *
 * On screen *off* rather than on, on purpose. Setting a wallpaper takes long enough to
 * decode a photo, and doing it the moment the screen lights up would mean watching the
 * old picture get replaced. Doing it on the way out means the new one is already there.
 *
 * ACTION_SCREEN_OFF cannot be declared in the manifest. Android has only ever delivered
 * it to receivers registered by running code, which is why this is wired up from the
 * Application and lives as long as the process does. A launcher is the one app that can
 * reasonably rely on that: the home process is the last thing Android gives up. If it is
 * killed anyway, the next home press brings it back.
 */
object ScreenWatch {

    /** A screen press can produce more than one broadcast; this keeps it to one picture. */
    private const val GAP_MS = 4000L

    private var receiver: BroadcastReceiver? = null

    fun start(ctx: Context) {
        if (receiver != null) return
        val app = ctx.applicationContext
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_SCREEN_OFF) return
                val prefs = Prefs(app)
                if (!prefs.lockRotate) return

                val now = System.currentTimeMillis()
                if (now - prefs.lockLast < GAP_MS) return
                prefs.lockLast = now

                // the broadcast gets ten seconds of held-open time, and decoding a photo
                // and handing it to the wallpaper service fits inside that comfortably
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        Wallpaper.advanceLock(app)
                    } finally {
                        runCatching { pending.finish() }
                    }
                }
            }
        }
        runCatching { app.registerReceiver(r, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
        receiver = r
    }
}
