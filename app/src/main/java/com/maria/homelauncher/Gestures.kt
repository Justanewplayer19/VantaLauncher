package com.maria.homelauncher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * The only way a normal app can pull down the notification shade or lock the screen.
 *
 * Both are global actions reserved to accessibility services; there is no permission a
 * launcher can request instead. The service does nothing else: it listens for no events
 * and reads no screen content, it exists purely so [Gestures] has something to call.
 */
class LauncherAccess : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        var instance: LauncherAccess? = null
            private set

        val connected: Boolean get() = instance != null

        fun perform(action: Int): Boolean =
            runCatching { instance?.performGlobalAction(action) }.getOrNull() ?: false
    }
}

/** What a gesture can be set to. Stored as a string so "app:pkg/activity" fits too. */
object Gestures {

    const val NONE = "none"
    const val NOTIFICATIONS = "notifications"
    const val QUICK_SETTINGS = "quicksettings"
    const val LOCK = "lock"
    const val RECENTS = "recents"
    const val SEARCH = "search"
    const val APP_PREFIX = "app:"

    /** The fixed choices, in the order the settings page shows them. */
    val simple = listOf(
        NONE to "Nothing",
        NOTIFICATIONS to "Notifications",
        QUICK_SETTINGS to "Quick settings",
        LOCK to "Lock screen",
        RECENTS to "Recent apps",
        SEARCH to "Search"
    )

    fun needsService(action: String) =
        action == NOTIFICATIONS || action == QUICK_SETTINGS ||
            action == LOCK || action == RECENTS

    fun label(action: String, nameOf: (String) -> String?): String = when {
        action.startsWith(APP_PREFIX) ->
            nameOf(action.removePrefix(APP_PREFIX)) ?: "An app"
        else -> simple.firstOrNull { it.first == action }?.second ?: "Nothing"
    }

    /**
     * Runs the gesture. When it needs the accessibility service and that is switched off,
     * says so once and drops the user on the screen where they can turn it on, rather
     * than failing silently.
     */
    fun run(
        ctx: Context,
        action: String,
        onSearch: () -> Unit,
        onApp: (String) -> Unit
    ) {
        if (action.isEmpty() || action == NONE) return

        if (action.startsWith(APP_PREFIX)) {
            onApp(action.removePrefix(APP_PREFIX))
            return
        }
        if (action == SEARCH) {
            onSearch()
            return
        }

        if (!LauncherAccess.connected) {
            Toast.makeText(ctx, "Turn on Home in accessibility settings first", Toast.LENGTH_LONG)
                .show()
            openAccessibilitySettings(ctx)
            return
        }

        val ok = when (action) {
            NOTIFICATIONS -> LauncherAccess.perform(
                AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            )
            QUICK_SETTINGS -> LauncherAccess.perform(
                AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            )
            RECENTS -> LauncherAccess.perform(AccessibilityService.GLOBAL_ACTION_RECENTS)
            LOCK -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                LauncherAccess.perform(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            } else false
            else -> false
        }

        if (!ok && action == LOCK && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Toast.makeText(ctx, "Locking needs Android 9 or newer", Toast.LENGTH_SHORT).show()
        }
    }

    fun openAccessibilitySettings(ctx: Context) {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
