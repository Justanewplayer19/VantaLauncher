package com.maria.homelauncher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** One widget the user has placed, and how tall they made it. */
data class WidgetItem(val appWidgetId: Int, val heightDp: Int)

private const val HOST_ID = 0x4D41
const val REQ_WIDGET_BIND = 9001
const val REQ_WIDGET_CONFIGURE = 9002

/**
 * Everything widget-related in one place: the host, the saved list, and the two-step
 * add flow.
 *
 * Adding is not one call. A normal launcher does not hold BIND_APPWIDGET (it is a
 * privileged permission), so binding an id to a provider has to go through the system
 * consent dialog, and after that the widget may want to run its own configure activity.
 * Both come back through [onResult], and a cancel at either step frees the id again so
 * we do not leak allocations.
 */
class LauncherWidgets(private val act: ComponentActivity) {

    private val sp = act.getSharedPreferences("widgets", Context.MODE_PRIVATE)
    private val density = act.resources.displayMetrics.density

    val manager: AppWidgetManager = AppWidgetManager.getInstance(act)
    val host = AppWidgetHost(act, HOST_ID)

    var items by mutableStateOf(load())
        private set

    /** The id waiting on a bind or configure result; -1 when nothing is in flight. */
    private var pending = -1

    fun start() {
        runCatching { host.startListening() }
        // A restored backup carries widget ids, and a reinstall releases every id the
        // host had handed out, so a restore onto a fresh install can name widgets that
        // no longer exist. Pruning here rather than only on the widgets page means a
        // dead frame never reaches the home screen in the first place.
        runCatching { prune() }
    }
    fun stop() { runCatching { host.stopListening() } }

    fun providers(): List<AppWidgetProviderInfo> {
        val pm = act.packageManager
        return manager.installedProviders.sortedBy {
            it.loadLabel(pm).orEmpty().lowercase()
        }
    }

    @Suppress("DEPRECATION")
    fun add(info: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        if (manager.bindAppWidgetIdIfAllowed(id, info.provider)) {
            configureOrFinish(id)
            return
        }
        pending = id
        val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
        }
        val ok = runCatching { act.startActivityForResult(i, REQ_WIDGET_BIND) }.isSuccess
        if (!ok) { pending = -1; host.deleteAppWidgetId(id) }
    }

    fun onResult(request: Int, resultCode: Int) {
        val id = pending
        if (id < 0) return
        if (resultCode != Activity.RESULT_OK) {
            pending = -1
            host.deleteAppWidgetId(id)
            return
        }
        when (request) {
            REQ_WIDGET_BIND -> configureOrFinish(id)
            REQ_WIDGET_CONFIGURE -> place(id)
        }
    }

    fun remove(appWidgetId: Int) {
        items = items.filterNot { it.appWidgetId == appWidgetId }
        save()
        runCatching { host.deleteAppWidgetId(appWidgetId) }
    }

    fun setHeight(appWidgetId: Int, dp: Int) {
        items = items.map {
            if (it.appWidgetId == appWidgetId) it.copy(heightDp = dp.coerceIn(60, 420)) else it
        }
        save()
    }

    fun move(from: Int, to: Int) {
        if (from == to || from !in items.indices || to !in items.indices) return
        val out = items.toMutableList()
        out.add(to, out.removeAt(from))
        items = out
        save()
    }

    /** Drops ids the system no longer knows about, so a dead frame cannot linger. */
    fun prune() {
        val live = items.filter { manager.getAppWidgetInfo(it.appWidgetId) != null }
        if (live.size != items.size) { items = live; save() }
    }

    private fun configureOrFinish(id: Int) {
        val info = manager.getAppWidgetInfo(id)
        if (info?.configure == null) { place(id); return }
        pending = id
        val ok = runCatching {
            host.startAppWidgetConfigureActivityForResult(act, id, 0, REQ_WIDGET_CONFIGURE, null)
        }.isSuccess
        // some configure activities refuse to start for a third-party host; the widget
        // still works, it just gets whatever defaults it ships with
        if (!ok) place(id)
    }

    private fun place(id: Int) {
        pending = -1
        if (items.any { it.appWidgetId == id }) return
        items = items + WidgetItem(id, defaultHeight(manager.getAppWidgetInfo(id)))
        save()
    }

    private fun defaultHeight(info: AppWidgetProviderInfo?): Int {
        val px = info?.minHeight ?: 0
        if (px <= 0) return 120
        return (px / density).toInt().coerceIn(80, 300)
    }

    private fun load(): List<WidgetItem> =
        sp.getString("list", "")!!.split("|").filter { it.isNotBlank() }.mapNotNull { row ->
            val bits = row.split(":")
            val id = bits.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            WidgetItem(id, bits.getOrNull(1)?.toIntOrNull() ?: 120)
        }

    private fun save() = sp.edit()
        .putString("list", items.joinToString("|") { "${it.appWidgetId}:${it.heightDp}" })
        .apply()

    companion object {
        /** Size hints an AppWidgetHostView wants in dp, not px. */
        fun sizeOptions(widthDp: Int, heightDp: Int) = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        }
    }
}
