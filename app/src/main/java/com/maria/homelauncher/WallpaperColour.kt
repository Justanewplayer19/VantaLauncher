package com.maria.homelauncher

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color as AColor
import android.net.Uri

/**
 * Picks an accent out of a wallpaper.
 *
 * Deliberately not androidx.palette: that pulls in a library and a clustering pass for a
 * job that is one average over a thumbnail. The image is sampled down hard first, then
 * only colourful pixels are averaged, so a photo that is mostly sky does not hand back
 * grey. Finally the result is pushed to a readable saturation and brightness, since an
 * accent is drawn on a dark panel and a muddy one disappears.
 */
object WallpaperColour {

    private const val SAMPLE_PX = 48

    fun from(ctx: Context, uri: Uri): Long? {
        val bmp = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri).use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val worst = maxOf(bounds.outWidth, bounds.outHeight)
            if (worst <= 0) return null

            var sample = 1
            while (worst / sample > SAMPLE_PX * 2) sample *= 2

            ctx.contentResolver.openInputStream(uri).use {
                BitmapFactory.decodeStream(
                    it, null, BitmapFactory.Options().apply { inSampleSize = sample }
                )
            }
        }.getOrNull() ?: return null

        val hsv = FloatArray(3)
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0L

        // walk a grid rather than every pixel; at this size that is already plenty
        val stepX = maxOf(1, bmp.width / SAMPLE_PX)
        val stepY = maxOf(1, bmp.height / SAMPLE_PX)
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val p = bmp.getPixel(x, y)
                AColor.colorToHSV(p, hsv)
                if (hsv[1] > 0.25f && hsv[2] > 0.25f) {
                    r += AColor.red(p); g += AColor.green(p); b += AColor.blue(p); n++
                }
                x += stepX
            }
            y += stepY
        }
        bmp.recycle()

        if (n == 0L) return null

        AColor.colorToHSV(
            AColor.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt()), hsv
        )
        hsv[1] = hsv[1].coerceIn(0.35f, 0.72f)
        hsv[2] = hsv[2].coerceIn(0.72f, 1f)
        return 0xFF000000L or (AColor.HSVToColor(hsv).toLong() and 0xFFFFFF)
    }

    /** No-op unless the user asked for it. Returns the accent written, if any. */
    fun applyIfWanted(ctx: Context, uri: Uri): Long? {
        val store = StyleStore(ctx)
        val style = store.load()
        if (!style.autoAccent) return null
        val accent = from(ctx, uri) ?: return null
        store.save(style.copy(accent = accent))
        return accent
    }
}
