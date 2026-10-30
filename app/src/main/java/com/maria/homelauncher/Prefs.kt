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

    /**
     * How many home pages to keep, including empty ones. A page with nothing on it is
     * something to ask for rather than a mistake to tidy away: it is how you get a look
     * at the wallpaper with nothing in front of it.
     */
    var homePages: Int
        get() = sp.getInt("homePages", 1).coerceIn(1, 16)
        set(v) = sp.edit().putInt("homePages", v.coerceIn(1, 16)).apply()

    /** False until the fixed clock header was turned into an item on the grid. */
    var clockOnGrid: Boolean
        get() = sp.getBoolean("clockOnGrid2", false)
        set(v) = sp.edit().putBoolean("clockOnGrid2", v).apply()

    /** Packages kept out of the grid; they only surface when the search box has text. */
    var hidden: Set<String>
        get() = list("hidden").toSet()
        set(v) = putList("hidden", v.toList())

    fun toggleHidden(pkg: String) {
        val cur = hidden.toMutableSet()
        if (!cur.remove(pkg)) cur.add(pkg)
        hidden = cur
    }

    // --- search ---

    /**
     * How many times a package has been opened from the launcher. Search uses it to break
     * ties, so the Chrome you open daily sits above the browser you have never touched.
     */
    fun uses(pkg: String): Int = sp.getInt("use:$pkg", 0)

    fun noteLaunch(pkg: String) {
        sp.edit().putInt("use:$pkg", (uses(pkg) + 1).coerceAtMost(999)).apply()
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

    /**
     * Content URIs the user picked, as strings. Persistable permission is taken on each.
     *
     * This is the home set. It kept its old key on purpose: it was the only set there
     * used to be, so an existing setup becomes the home set without being touched.
     */
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

    /**
     * The lock screen's own images and folders, used only when [lockSeparate] is on.
     *
     * Two sets rather than one list with a flag on each entry: picking is the thing you
     * actually do, and "add these to the lock screen" is one gesture against a set where
     * it would be an edit of every row against a tagged list.
     */
    var lockWallpapers: List<String>
        get() = list("lockWallpapers")
        set(v) = putList("lockWallpapers", v)

    var lockWallpaperFolders: List<String>
        get() = list("lockWallpaperFolders")
        set(v) = putList("lockWallpaperFolders", v)

    /**
     * Off means both screens draw from the home set, which is how it worked when there
     * was only one. On gives the lock screen the set above, and the two never collide.
     */
    var lockSeparate: Boolean
        get() = sp.getBoolean("lockSeparate", false)
        set(v) = sp.edit().putBoolean("lockSeparate", v).apply()

    /** A GIF or video drawn behind the home screen. Blank when there is none. */
    var liveBg: String
        get() = sp.getString("liveBg", "")!!
        set(v) = sp.edit().putString("liveBg", v).apply()

    /** False means play once and hold the last frame. */
    var liveLoop: Boolean
        get() = sp.getBoolean("liveLoop", true)
        set(v) = sp.edit().putBoolean("liveLoop", v).apply()

    // --- lock screen, on screen off ---

    /**
     * Change the lock screen wallpaper every time the screen goes off, so the next time
     * it comes on there is a different picture waiting.
     *
     * The home wallpaper is deliberately left alone: this is the one that is only ever
     * seen for a few seconds at a time, which is what makes changing it that often fun
     * rather than disorienting.
     */
    var lockRotate: Boolean
        get() = sp.getBoolean("lockRotate", false)
        set(v) = sp.edit().putBoolean("lockRotate", v).apply()

    /** True picks any image, false walks the list in order. */
    var lockRandom: Boolean
        get() = sp.getBoolean("lockRandom", false)
        set(v) = sp.edit().putBoolean("lockRandom", v).apply()

    /** Its own place in the list, so it does not share with the timed rotation. */
    var lockCursor: Int
        get() = sp.getInt("lockCursor", 0)
        set(v) = sp.edit().putInt("lockCursor", v).apply()

    /** When the screen-off change last ran, to ignore a second broadcast for one press. */
    var lockLast: Long
        get() = sp.getLong("lockLast", 0L)
        set(v) = sp.edit().putLong("lockLast", v).apply()

    // --- home screen sound ---

    /** An audio file played while the home screen is in front. Blank when there is none. */
    var homeAudio: String
        get() = sp.getString("homeAudio", "")!!
        set(v) = sp.edit().putString("homeAudio", v).apply()

    var audioLoop: Boolean
        get() = sp.getBoolean("audioLoop", true)
        set(v) = sp.edit().putBoolean("audioLoop", v).apply()

    /** Percent. Ambience at the volume of a song is not ambience. */
    var audioVolume: Int
        get() = sp.getInt("audioVolume", 50).coerceIn(5, 100)
        set(v) = sp.edit().putInt("audioVolume", v.coerceIn(5, 100)).apply()

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

    /**
     * The last picture that went on the lock screen. With two sets, the home one is no
     * longer a sensible thing to put back when a lock change fails.
     */
    var lastLock: String
        get() = sp.getString("lastLock", "")!!
        set(v) = sp.edit().putString("lastLock", v).apply()

    var cursor: Int
        get() = sp.getInt("cursor", 0)
        set(v) = sp.edit().putInt("cursor", v).apply()

    /**
     * The timed rotation's place in the lock set. Three cursors looks like a lot until
     * you notice all three are different jobs: the home set, the lock set on a timer, and
     * the lock set on every screen off. Sharing any two of them means one driver skipping
     * pictures the other already used.
     */
    var rotateLockCursor: Int
        get() = sp.getInt("rotateLockCursor", 0)
        set(v) = sp.edit().putInt("rotateLockCursor", v).apply()
}
