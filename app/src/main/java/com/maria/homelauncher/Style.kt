package com.maria.homelauncher

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

enum class IconShape(val label: String, val radiusFraction: Float) {
    Circle("Circle", 0.5f),
    Squircle("Squircle", 0.40f),
    Rounded("Rounded", 0.25f),
    Square("Square", 0f),
    Original("Original", -1f)
}

enum class BgMode(val label: String) {
    Blur("Blur"),
    Dim("Dim"),
    Solid("Solid")
}

enum class DrawerMode(val label: String) {
    Scroll("Scroll"),
    Pages("Pages")
}

enum class TintMode(val label: String) {
    Off("Original"),
    Mono("One colour")
}

enum class SearchPos(val label: String) {
    Top("Top"),
    Bottom("Bottom")
}

enum class GearSide(val label: String) {
    Left("Left"),
    Right("Right")
}

enum class FontChoice(val label: String) {
    Default("Default"),
    Sans("Sans"),
    Serif("Serif"),
    Mono("Mono"),
    Cursive("Cursive")
}

data class Style(
    val columns: Int = 4,
    val iconDp: Int = 52,
    val showLabels: Boolean = true,
    val labelSp: Int = 11,
    val rows: Int = 0,
    val showPageDots: Boolean = true,
    val iconShape: IconShape = IconShape.Circle,
    val iconTint: TintMode = TintMode.Off,
    val iconColor: Long = 0xFF0B0B0F,
    val iconBg: Long = 0xFFBFC9FF,
    val iconAlpha: Float = 1f,
    val iconPack: String = "",
    val drawerMode: DrawerMode = DrawerMode.Scroll,
    val searchPos: SearchPos = SearchPos.Top,
    val gearSide: GearSide = GearSide.Left,

    val dockMax: Int = 5,
    val dockBg: Boolean = true,
    val dockRadius: Int = 30,

    val showClock: Boolean = true,
    val clock24: Boolean = false,
    val showDate: Boolean = true,

    val folderAlpha: Float = 0.16f,
    val folderRadius: Int = 26,
    val badges: Boolean = true,

    val accent: Long = 0xFFBFC9FF,
    val autoAccent: Boolean = false,
    val bgColor: Long = 0xFF0B0B0F,
    /** Text and icons. White on the dark themes, near-black on the light ones. */
    val ink: Long = 0xFFFFFFFF,
    val bgMode: BgMode = BgMode.Blur,
    val bgAlpha: Float = 0.80f,
    val blurRadius: Int = 48,

    val font: FontChoice = FontChoice.Default,
    /** A font id: "g:Inter", "d:/system/fonts/...", "f:...". Empty means use [font]. */
    val fontId: String = "",
    val clockSp: Int = 68,
    val dateSp: Int = 15,
    val searchSp: Int = 15
)

/**
 * Fonts used to be two fields, a Google family and a file path. Both are read once here and
 * folded into the single id, so a phone that had one set before keeps it.
 */
private fun migrateFont(sp: android.content.SharedPreferences): String {
    sp.getString("fontId", "")!!.let { if (it.isNotBlank()) return it }
    sp.getString("fontFile", "")!!.let { if (it.isNotBlank()) return "f:$it" }
    sp.getString("fontName", "")!!.let { if (it.isNotBlank()) return "g:$it" }
    return ""
}

val LocalStyle = staticCompositionLocalOf { Style() }

/**
 * The colour text and icons are drawn in. Everything used to say Color.White, which quietly
 * ruled out every light theme, so it reads this instead.
 */
val LocalInk = staticCompositionLocalOf { androidx.compose.ui.graphics.Color.White }

/** Packages with a notification waiting. Changes often, so not a static local. */
val LocalBadges = androidx.compose.runtime.compositionLocalOf { emptySet<String>() }

class StyleStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("style", Context.MODE_PRIVATE)
    private val undo = ctx.getSharedPreferences("style_undo", Context.MODE_PRIVATE)

    fun load() = Style(
        columns = sp.getInt("columns", 4),
        iconDp = sp.getInt("iconDp", 52),
        showLabels = sp.getBoolean("showLabels", true),
        labelSp = sp.getInt("labelSp", 11),
        rows = sp.getInt("rows", 0),
        showPageDots = sp.getBoolean("showPageDots", true),
        iconShape = enumOr("iconShape", IconShape.Circle),
        iconTint = enumOr("iconTint", TintMode.Off),
        iconColor = sp.getLong("iconColor", 0xFF0B0B0F),
        iconBg = sp.getLong("iconBg", 0xFFBFC9FF),
        iconAlpha = sp.getFloat("iconAlpha", 1f),
        iconPack = sp.getString("iconPack", "")!!,
        drawerMode = enumOr("drawerMode", DrawerMode.Scroll),
        searchPos = enumOr("searchPos", SearchPos.Top),
        gearSide = enumOr("gearSide", GearSide.Left),
        dockMax = sp.getInt("dockMax", 5),
        dockBg = sp.getBoolean("dockBg", true),
        dockRadius = sp.getInt("dockRadius", 30),
        showClock = sp.getBoolean("showClock", true),
        clock24 = sp.getBoolean("clock24", false),
        showDate = sp.getBoolean("showDate", true),
        folderAlpha = sp.getFloat("folderAlpha", 0.16f),
        folderRadius = sp.getInt("folderRadius", 26),
        badges = sp.getBoolean("badges", true),

        accent = sp.getLong("accent", 0xFFBFC9FF),
        autoAccent = sp.getBoolean("autoAccent", false),
        bgColor = sp.getLong("bgColor", 0xFF0B0B0F),
        ink = sp.getLong("ink", 0xFFFFFFFF),
        bgMode = enumOr("bgMode", BgMode.Blur),
        bgAlpha = sp.getFloat("bgAlpha", 0.80f),
        blurRadius = sp.getInt("blurRadius", 48),

        font = enumOr("font", FontChoice.Default),
        fontId = migrateFont(sp),
        clockSp = sp.getInt("clockSp", 68),
        dateSp = sp.getInt("dateSp", 15),
        searchSp = sp.getInt("searchSp", 15)
    )

    fun save(s: Style) {
        sp.edit()
            .putInt("columns", s.columns)
            .putInt("iconDp", s.iconDp)
            .putBoolean("showLabels", s.showLabels)
            .putInt("labelSp", s.labelSp)
            .putInt("rows", s.rows)
            .putBoolean("showPageDots", s.showPageDots)
            .putString("iconShape", s.iconShape.name)
            .putString("iconTint", s.iconTint.name)
            .putLong("iconColor", s.iconColor)
            .putLong("iconBg", s.iconBg)
            .putFloat("iconAlpha", s.iconAlpha)
            .putString("iconPack", s.iconPack)
            .putString("drawerMode", s.drawerMode.name)
            .putString("searchPos", s.searchPos.name)
            .putString("gearSide", s.gearSide.name)
            .putInt("dockMax", s.dockMax)
            .putBoolean("dockBg", s.dockBg)
            .putInt("dockRadius", s.dockRadius)
            .putBoolean("showClock", s.showClock)
            .putBoolean("clock24", s.clock24)
            .putBoolean("showDate", s.showDate)
            .putFloat("folderAlpha", s.folderAlpha)
            .putInt("folderRadius", s.folderRadius)
            .putBoolean("badges", s.badges)
            .putLong("accent", s.accent)
            .putBoolean("autoAccent", s.autoAccent)
            .putLong("bgColor", s.bgColor)
            .putLong("ink", s.ink)
            .putString("bgMode", s.bgMode.name)
            .putFloat("bgAlpha", s.bgAlpha)
            .putInt("blurRadius", s.blurRadius)
            .putString("font", s.font.name)
            .putString("fontId", s.fontId)
            .putInt("clockSp", s.clockSp)
            .putInt("dateSp", s.dateSp)
            .putInt("searchSp", s.searchSp)
            .apply()
    }

    fun reset() = sp.edit().clear().apply()

    /**
     * Keeps a copy of the whole look so it can be put back.
     *
     * A theme overwrites a dozen colour fields at once, and getting those back by hand
     * means knowing what they were. This copies every entry rather than a named list of
     * fields, so a setting added later is covered without anyone remembering to add it
     * here. Only one snapshot is kept: this is an undo, not a history.
     */
    fun snapshot(label: String) {
        val e = undo.edit().clear().putString(LABEL, label)
        sp.all.forEach { (k, v) ->
            when (v) {
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Float -> e.putFloat(k, v)
                is String -> e.putString(k, v)
            }
        }
        e.apply()
    }

    /** What the snapshot was taken before, for the button to name it. Null if there is none. */
    fun undoLabel(): String? = undo.getString(LABEL, null)

    /**
     * Puts the snapshot back and clears it, so undo cannot be pressed twice and land
     * somewhere nobody asked for.
     */
    fun undo(): Style? {
        if (undo.getString(LABEL, null) == null) return null
        val e = sp.edit().clear()
        undo.all.forEach { (k, v) ->
            if (k == LABEL) return@forEach
            when (v) {
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Float -> e.putFloat(k, v)
                is String -> e.putString(k, v)
            }
        }
        e.apply()
        undo.edit().clear().apply()
        return load()
    }

    private inline fun <reified T : Enum<T>> enumOr(key: String, fallback: T): T {
        val raw = sp.getString(key, null) ?: return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }

    private companion object {
        /** Doubles as the "is there a snapshot" flag, so it is never a real setting. */
        const val LABEL = "__undo_label"
    }
}
