package com.maria.homelauncher

import android.content.Context

const val TARGET_HOME = 0
const val TARGET_LOCK = 1
const val TARGET_BOTH = 2

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    private fun list(key: String): List<String> =
        sp.getString(key, "")!!.split("|").filter { it.isNotBlank() }

    private fun putList(key: String, v: List<String>) =
        sp.edit().putString(key, v.joinToString("|")).apply()

    var dock: List<String>
        get() = list("dock")
        set(v) = putList("dock", v)

    fun toggleDock(pkg: String, max: Int = 5) {
        val cur = dock.toMutableList()
        if (!cur.remove(pkg) && cur.size < max) cur.add(pkg)
        dock = cur
    }

    /** Watch GitHub for a newer build in the background, on any connection. */
    var autoUpdate: Boolean
        get() = sp.getBoolean("autoUpdate", true)
        set(v) = sp.edit().putBoolean("autoUpdate", v).apply()

    var lastUpdateCheck: Long
        get() = sp.getLong("lastUpdateCheck", 0L)
        set(v) = sp.edit().putLong("lastUpdateCheck", v).apply()

    /** A version sitting downloaded in the cache, waiting to be installed. Blank if none. */
    var readyVersion: String
        get() = sp.getString("readyVersion", "")!!
        set(v) = sp.edit().putString("readyVersion", v).apply()

    var readyNotes: String
        get() = sp.getString("readyNotes", "")!!
        set(v) = sp.edit().putString("readyNotes", v).apply()

    /** A version the user has waved away, so the home screen stops offering it. */
    var dismissedVersion: String
        get() = sp.getString("dismissedVersion", "")!!
        set(v) = sp.edit().putString("dismissedVersion", v).apply()

    /** False until the intro has been finished or skipped. */
    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    /** One surface: every app lives on the home pages, no separate drawer. */
    var singleSurface: Boolean
        get() = sp.getBoolean("singleSurface", false)
        set(v) = sp.edit().putBoolean("singleSurface", v).apply()

    /**
     * False until the home layout has been moved onto the free-placement grid. The
     * version before it kept one dense list of every app, which means nothing once the
     * drawer mode has a home surface of its own.
     */
    var gridMigrated: Boolean
        get() = sp.getBoolean("gridV2", false)
        set(v) = sp.edit().putBoolean("gridV2", v).apply()

    /** Packages kept out of the grid; they only surface when the search box has text. */
    var hidden: Set<String>
        get() = list("hidden").toSet()
        set(v) = putList("hidden", v.toList())

    fun toggleHidden(pkg: String) {
        val cur = hidden.toMutableSet()
        if (!cur.remove(pkg)) cur.add(pkg)
        hidden = cur
    }

    // --- gestures ---

    /**
     * Each holds a [Gestures] action id. Swipe down defaults to the notification shade
     * because that is what the gesture does on every other launcher; it prompts for the
     * accessibility service the first time it is used.
     */
    var gSwipeDown: String
        get() = sp.getString("gSwipeDown", Gestures.NOTIFICATIONS)!!
        set(v) = sp.edit().putString("gSwipeDown", v).apply()

    var gDoubleTap: String
        get() = sp.getString("gDoubleTap", Gestures.NONE)!!
        set(v) = sp.edit().putString("gDoubleTap", v).apply()

    var gSwipeLeft: String
        get() = sp.getString("gSwipeLeft", Gestures.NONE)!!
        set(v) = sp.edit().putString("gSwipeLeft", v).apply()

    var gSwipeRight: String
        get() = sp.getString("gSwipeRight", Gestures.NONE)!!
        set(v) = sp.edit().putString("gSwipeRight", v).apply()

    // --- wallpaper rotation ---

    /** Content URIs the user picked, as strings. Persistable permission is taken on each. */
    var wallpapers: List<String>
        get() = list("wallpapers")
        set(v) = putList("wallpapers", v)

    /**
     * Picked folders, as tree URIs. Stored as folders rather than as the files that were
     * in them at the time, so an image dropped in later is picked up on the next change
     * without the folder being chosen again.
     */
    var wallpaperFolders: List<String>
        get() = list("wallpaperFolders")
        set(v) = putList("wallpaperFolders", v)

    /** A GIF or video drawn behind the launcher. Blank when there is none. */
    var liveBg: String
        get() = sp.getString("liveBg", "")!!
        set(v) = sp.edit().putString("liveBg", v).apply()

    /** False means play once and hold the last frame. */
    var liveLoop: Boolean
        get() = sp.getBoolean("liveLoop", true)
        set(v) = sp.edit().putBoolean("liveLoop", v).apply()

    var rotateOn: Boolean
        get() = sp.getBoolean("rotateOn", false)
        set(v) = sp.edit().putBoolean("rotateOn", v).apply()

    /** WorkManager refuses anything under 15. */
    var rotateMinutes: Int
        get() = sp.getInt("rotateMinutes", 60)
        set(v) = sp.edit().putInt("rotateMinutes", v.coerceAtLeast(15)).apply()

    var rotateTarget: Int
        get() = sp.getInt("rotateTarget", TARGET_BOTH)
        set(v) = sp.edit().putInt("rotateTarget", v).apply()

    var shuffle: Boolean
        get() = sp.getBoolean("shuffle", false)
        set(v) = sp.edit().putBoolean("shuffle", v).apply()

    /** The last image actually applied, so the colour sampler has something to read. */
    var lastWallpaper: String
        get() = sp.getString("lastWallpaper", "")!!
        set(v) = sp.edit().putString("lastWallpaper", v).apply()

    var cursor: Int
        get() = sp.getInt("cursor", 0)
        set(v) = sp.edit().putInt("cursor", v).apply()
}
