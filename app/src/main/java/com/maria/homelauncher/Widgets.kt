package com.maria.homelauncher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

private const val HOST_ID = 0x4D41
const val REQ_WIDGET_BIND = 9001
const val REQ_WIDGET_CONFIGURE = 9002

/**
 * The widget host and the two-step add flow. Where a widget sits and how big it is
 * belongs to the home layout, like everything else on the surface, so this class only
 * hands out ids and says when one is ready to be placed.
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

    /** Called with a bound, configured id. The home layout decides where it lands. */
    var onPlaced: ((Int) -> Unit)? = null

    /** The id waiting on a bind or configure result; -1 when nothing is in flight. */
    private var pending = -1

    fun start() { runCatching { host.startListening() } }
    fun stop() { runCatching { host.stopListening() } }

    fun providers(): List<AppWidgetProviderInfo> {
        val pm = act.packageManager
        return manager.installedProviders.sortedBy {
            it.loadLabel(pm).orEmpty().lowercase()
        }
    }

    fun alive(appWidgetId: Int) = manager.getAppWidgetInfo(appWidgetId) != null

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
            REQ_WIDGET_CONFIGURE -> finish(id)
        }
    }

    /** Hands the id back to the system. Called when a widget is taken off the surface. */
    fun release(appWidgetId: Int) {
        runCatching { host.deleteAppWidgetId(appWidgetId) }
    }

    /**
     * How many cells wide and tall a provider asks to be, so a freshly added widget
     * arrives at roughly its intended size instead of a single square.
     */
    fun defaultSpan(info: AppWidgetProviderInfo?, cellWidthDp: Float, cellHeightDp: Float): Pair<Int, Int> {
        if (info == null || cellWidthDp <= 0f || cellHeightDp <= 0f) return 2 to 1
        val wDp = info.minWidth / density
        val hDp = info.minHeight / density
        val w = Math.ceil((wDp / cellWidthDp).toDouble()).toInt().coerceIn(1, 8)
        val h = Math.ceil((hDp / cellHeightDp).toDouble()).toInt().coerceIn(1, 8)
        return w to h
    }

    /**
     * Widgets placed by the version that stacked them vertically, read once so they can
     * be moved onto the grid. [forgetStacked] drops the old store afterwards.
     */
    fun stacked(): List<Pair<Int, Int>> =
        sp.getString("list", "")!!.split("|").filter { it.isNotBlank() }.mapNotNull { row ->
            val bits = row.split(":")
            val id = bits.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            id to (bits.getOrNull(1)?.toIntOrNull() ?: 120)
        }

    fun forgetStacked() = sp.edit().remove("list").apply()

    private fun configureOrFinish(id: Int) {
        val info = manager.getAppWidgetInfo(id)
        if (info?.configure == null) { finish(id); return }
        pending = id
        val ok = runCatching {
            host.startAppWidgetConfigureActivityForResult(act, id, 0, REQ_WIDGET_CONFIGURE, null)
        }.isSuccess
        // some configure activities refuse to start for a third-party host; the widget
        // still works, it just gets whatever defaults it ships with
        if (!ok) finish(id)
    }

    private fun finish(id: Int) {
        pending = -1
        onPlaced?.invoke(id)
    }

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
