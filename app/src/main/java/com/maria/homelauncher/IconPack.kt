package com.maria.homelauncher

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

data class IconPackInfo(val pkg: String, val label: String)

/**
 * A third-party icon theme, read straight out of the pack's own APK.
 *
 * Every pack on the Play Store follows the same ADW-era convention: an `appfilter.xml`
 * mapping component names to drawable names, plus optional `iconback` / `iconmask` /
 * `iconupon` artwork and a scale factor used to dress apps the pack has no icon for.
 * There is no library for this and no formal spec; this parses what packs actually ship.
 */
class IconPack private constructor(
    private val res: Resources,
    private val pkg: String,
    private val map: Map<String, String>,
    private val byPackage: Map<String, String>,
    private val backs: List<String>,
    private val mask: String?,
    private val upon: String?,
    private val scale: Float
) {

    /** True when the pack can dress apps it has no specific icon for. */
    val canDecorate: Boolean get() = backs.isNotEmpty() || mask != null || upon != null

    /** The pack's own icon for this component, if it ships one. */
    fun iconFor(component: String): Drawable? {
        val name = map[component] ?: byPackage[component.substringBefore('/')] ?: return null
        return drawable(name)
    }

    /**
     * Dresses an app's normal icon in the pack's background, mask and overlay, so apps the
     * pack never heard of still look like they belong to it.
     */
    fun decorate(base: Drawable, component: String, px: Int): Bitmap {
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val full = Rect(0, 0, px, px)

        // packs ship several backs and expect them spread across the grid; keying off the
        // component keeps a given app on the same one between reloads
        if (backs.isNotEmpty()) {
            val pick = backs[(component.hashCode().toUInt() % backs.size.toUInt()).toInt()]
            drawable(pick)?.apply { bounds = full; draw(c) }
        }

        val inset = (px * (1f - scale.coerceIn(0.3f, 1f)) / 2f).toInt()
        val layer = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        Canvas(layer).also { lc ->
            base.bounds = Rect(inset, inset, px - inset, px - inset)
            base.draw(lc)
            // an iconmask is opaque where the icon should be cut away, so it punches out
            mask?.let { m ->
                drawable(m)?.let { d ->
                    val mb = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                    Canvas(mb).also { mc -> d.bounds = full; d.draw(mc) }
                    lc.drawBitmap(
                        mb, 0f, 0f,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                        }
                    )
                }
            }
        }
        c.drawBitmap(layer, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))

        upon?.let { drawable(it)?.apply { bounds = full; draw(c) } }
        return out
    }

    private fun drawable(name: String): Drawable? {
        val id = res.getIdentifier(name, "drawable", pkg)
        if (id == 0) return null
        return runCatching { res.getDrawable(id, null) }.getOrNull()
    }

    companion object {
        /** The intent filters packs declare so launchers can find them. */
        private val THEME_ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.gau.go.launcherex.theme",
            "com.novalauncher.THEME",
            "com.anddoes.launcher.THEME",
            "ch.deletescape.lawnchair.ICONPACK"
        )

        fun installed(ctx: Context): List<IconPackInfo> {
            val pm = ctx.packageManager
            val seen = LinkedHashMap<String, String>()
            THEME_ACTIONS.forEach { action ->
                runCatching {
                    pm.queryIntentActivities(Intent(action), 0).forEach { ri ->
                        val p = ri.activityInfo.packageName
                        if (p !in seen) seen[p] = ri.loadLabel(pm).toString()
                    }
                }
            }
            return seen.map { (p, label) -> IconPackInfo(p, label) }
                .sortedBy { it.label.lowercase() }
        }

        /** Null when the pack is gone, unreadable, or has no appfilter at all. */
        fun load(ctx: Context, pkg: String): IconPack? = runCatching {
            val packCtx = ctx.createPackageContext(pkg, Context.CONTEXT_IGNORE_SECURITY)
            val res = packCtx.resources
            val parser = openAppFilter(res, pkg) ?: return null

            val map = HashMap<String, String>()
            val byPackage = HashMap<String, String>()
            val backs = ArrayList<String>()
            var mask: String? = null
            var upon: String? = null
            var scale = 1f

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "item" -> {
                            val comp = parser.getAttributeValue(null, "component")
                            val drw = parser.getAttributeValue(null, "drawable")
                            if (comp != null && drw != null) {
                                val key = normalise(comp)
                                if (key != null) {
                                    map[key] = drw
                                    byPackage.putIfAbsent(key.substringBefore('/'), drw)
                                }
                            }
                        }
                        "iconback" -> backs += imgs(parser)
                        "iconmask" -> mask = imgs(parser).firstOrNull() ?: mask
                        "iconupon" -> upon = imgs(parser).firstOrNull() ?: upon
                        "scale" -> parser.getAttributeValue(null, "factor")
                            ?.toFloatOrNull()?.let { scale = it }
                    }
                }
                event = parser.next()
            }

            if (map.isEmpty() && backs.isEmpty() && mask == null) return null
            IconPack(res, pkg, map, byPackage, backs, mask, upon, scale)
        }.getOrNull()

        /** Packs put appfilter in res/xml, in assets, or occasionally in res/raw. */
        private fun openAppFilter(res: Resources, pkg: String): XmlPullParser? {
            res.getIdentifier("appfilter", "xml", pkg).let { id ->
                if (id != 0) return runCatching { res.getXml(id) }.getOrNull()
            }
            runCatching {
                return Xml.newPullParser().apply {
                    setInput(res.assets.open("appfilter.xml"), "utf-8")
                }
            }
            res.getIdentifier("appfilter", "raw", pkg).let { id ->
                if (id != 0) return runCatching {
                    Xml.newPullParser().apply { setInput(res.openRawResource(id), "utf-8") }
                }.getOrNull()
            }
            return null
        }

        private fun imgs(parser: XmlPullParser): List<String> =
            (0 until parser.attributeCount)
                .filter { parser.getAttributeName(it).startsWith("img") }
                .mapNotNull { parser.getAttributeValue(it)?.takeIf { v -> v.isNotBlank() } }

        private fun normalise(raw: String): String? {
            val t = raw.removePrefix("ComponentInfo{").removeSuffix("}")
            return if (t.contains('/')) t else null
        }
    }
}
