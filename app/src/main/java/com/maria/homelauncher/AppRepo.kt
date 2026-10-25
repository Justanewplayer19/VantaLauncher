package com.maria.homelauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: ImageBitmap,
    /** A Google Fonts family just for this name. Empty means follow the global font. */
    val font: String = ""
)

private const val ICON_PX = 168

class AppRepo(private val ctx: Context) {

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    @Volatile
    private var shape: IconShape = IconShape.Circle

    @Volatile
    private var tint: TintMode = TintMode.Off

    @Volatile
    private var tintFg: Int = 0xFF0B0B0F.toInt()

    @Volatile
    private var tintBg: Int = 0xFFBFC9FF.toInt()

    @Volatile
    private var packName: String = ""

    @Volatile
    private var pack: IconPack? = null

    val overrides = Overrides(ctx)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            reloadBlocking()
        }
    }

    fun register() {
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ctx.registerReceiver(receiver, f)
    }

    fun unregister() {
        runCatching { ctx.unregisterReceiver(receiver) }
    }

    /**
     * Icons are baked at the chosen shape and tint, so changing either means re-rendering
     * every icon. Callers should only do this on discrete changes, not while dragging a slider.
     */
    suspend fun reload(
        newShape: IconShape = shape,
        newTint: TintMode = tint,
        fg: Int = tintFg,
        bg: Int = tintBg,
        newPack: String = packName
    ) = withContext(Dispatchers.IO) {
        shape = newShape
        tint = newTint
        tintFg = fg
        tintBg = bg
        if (newPack != packName || (newPack.isNotEmpty() && pack == null)) {
            packName = newPack
            pack = if (newPack.isEmpty()) null else IconPack.load(ctx, newPack)
        }
        reloadBlocking()
    }

    private fun reloadBlocking() {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val found = pm.queryIntentActivities(intent, 0)
        val s = shape
        val t = tint
        val fg = tintFg
        val bg = tintBg

        val list = found.mapNotNull { ri ->
            val pkg = ri.activityInfo.packageName
            if (pkg == ctx.packageName) return@mapNotNull null
            val key = "$pkg/${ri.activityInfo.name}"
            runCatching {
                AppEntry(
                    label = overrides.label(key) ?: ri.loadLabel(pm).toString(),
                    packageName = pkg,
                    activityName = ri.activityInfo.name,
                    icon = bake(key, { ri.loadIcon(pm) }, s, t, fg, bg),
                    font = overrides.font(key).orEmpty()
                )
            }.getOrNull()
        }.sortedBy { it.label.lowercase() }

        _apps.value = list
    }

    /** Re-renders with no settings change; used after a per-app override is edited. */
    suspend fun refresh() = withContext(Dispatchers.IO) { reloadBlocking() }

    /**
     * Order matters here. A replacement icon the user picked beats everything. One-colour
     * icons beat an icon pack, since the two want opposite things. A pack icon is used
     * exactly as the pack drew it, because the pack is the shape; only apps the pack has
     * no icon for get dressed in its background and mask.
     */
    private fun bake(
        key: String,
        base: () -> Drawable,
        s: IconShape,
        t: TintMode,
        fg: Int,
        bg: Int
    ): ImageBitmap {
        overrides.iconFile(key)?.let { f ->
            BitmapFactory.decodeFile(f.absolutePath)?.let {
                return BitmapDrawable(ctx.resources, it).render(s, t, fg, bg)
            }
        }
        if (t != TintMode.Mono) pack?.let { p ->
            p.iconFor(key)?.let { return it.rasterise() }
            if (p.canDecorate) return p.decorate(base(), key, ICON_PX).asImageBitmap()
        }
        return base().render(s, t, fg, bg)
    }

    fun launch(app: AppEntry) {
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName, app.activityName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { ctx.startActivity(i) }
    }

    fun openInfo(app: AppEntry) {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
    }

    fun uninstall(app: AppEntry) {
        val i = Intent(Intent.ACTION_DELETE)
            .setData(Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
    }
}

/** Straight to a bitmap, no mask: for art that already has its own shape. */
private fun Drawable.rasterise(px: Int = ICON_PX): ImageBitmap {
    val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, px, px)
    draw(Canvas(b))
    return b.asImageBitmap()
}

private fun Drawable.render(
    shape: IconShape,
    tint: TintMode,
    fgColor: Int,
    bgColor: Int,
    px: Int = ICON_PX
): ImageBitmap {
    if (tint == TintMode.Mono) return renderMono(shape, fgColor, bgColor, px)

    val src = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = Canvas(src)

    if (this is AdaptiveIconDrawable && shape != IconShape.Original) {
        // The two layers are authored full-bleed; the system normally crops them with its
        // own mask. Draw them raw so our mask is the only one applied, otherwise a circular
        // system mask would leave gaps inside a square shape.
        background?.apply { setBounds(0, 0, px, px); draw(c) }
        foreground?.apply { setBounds(0, 0, px, px); draw(c) }
    } else {
        setBounds(0, 0, px, px)
        draw(c)
    }

    if (shape == IconShape.Original) return src.asImageBitmap()

    val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val mc = Canvas(out)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = shape.radiusFraction * px
    mc.drawRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), r, r, p)
    p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    mc.drawBitmap(src, 0f, 0f, p)
    return out.asImageBitmap()
}

/**
 * One-colour icons. Prefers the app's own monochrome layer (API 33+) when it ships one,
 * otherwise flattens the foreground to a silhouette via its alpha channel, which works on
 * every icon but loses interior detail on busy artwork.
 */
private fun Drawable.renderMono(
    shape: IconShape,
    fgColor: Int,
    bgColor: Int,
    px: Int
): ImageBitmap {
    val glyphSrc: Drawable = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            this is AdaptiveIconDrawable &&
            monochrome != null -> monochrome!!
        this is AdaptiveIconDrawable -> foreground ?: this
        else -> this
    }

    // silhouette: draw the glyph, then recolour everything it covered
    val glyph = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    Canvas(glyph).also { gc ->
        glyphSrc.setBounds(0, 0, px, px)
        glyphSrc.draw(gc)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fgColor
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        gc.drawRect(0f, 0f, px.toFloat(), px.toFloat(), p)
    }

    val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)

    if (shape != IconShape.Original) {
        val r = shape.radiusFraction * px
        c.drawRoundRect(
            RectF(0f, 0f, px.toFloat(), px.toFloat()),
            r, r,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
        )
    }

    // inset the glyph so it sits inside the shape rather than bleeding to the edges
    val inset = px * 0.19f
    c.drawBitmap(
        glyph,
        null,
        RectF(inset, inset, px - inset, px - inset),
        Paint(Paint.FILTER_BITMAP_FLAG)
    )
    return out.asImageBitmap()
}
