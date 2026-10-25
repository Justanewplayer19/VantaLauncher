package com.maria.homelauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Which apps currently have a notification waiting, for the dots on their icons.
 *
 * This is the read half of the notification shade. The shade itself belongs to SystemUI
 * and cannot be restyled by a launcher, but a listener can see what is in it. Only the
 * package names are kept: no titles, no text, nothing is stored or sent anywhere.
 */
class NotificationWatch : NotificationListenerService() {

    override fun onListenerConnected() {
        connected.value = true
        refresh()
    }

    override fun onListenerDisconnected() {
        connected.value = false
        packages.value = emptySet()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        packages.value = runCatching {
            activeNotifications
                // ongoing means a persistent thing like a download or a player, not news
                ?.filterNot { it.isOngoing }
                ?.map { it.packageName }
                ?.toSet()
                .orEmpty()
        }.getOrDefault(emptySet())
    }

    companion object {
        val packages = MutableStateFlow<Set<String>>(emptySet())
        val connected = MutableStateFlow(false)

        val badged: StateFlow<Set<String>> get() = packages

        /** Whether the user has granted notification access, which only they can do. */
        fun allowed(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(
                ctx.contentResolver, "enabled_notification_listeners"
            ).orEmpty()
            val me = ComponentName(ctx, NotificationWatch::class.java)
            return flat.split(":").any {
                val c = ComponentName.unflattenFromString(it)
                c != null && c.packageName == me.packageName
            }
        }

        fun openSettings(ctx: Context) {
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}
