package com.maria.homelauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import java.io.File

private const val OVERRIDE_PX = 192

/**
 * Per-app name and icon changes, keyed by "package/activity".
 *
 * Names live in prefs; replacement icons are copied into the launcher's own files as a
 * square PNG, so the picked image can be deleted or its permission revoked without
 * leaving a hole on the home screen.
 */
class Overrides(private val ctx: Context) {

    private val sp = ctx.getSharedPreferences("overrides", Context.MODE_PRIVATE)
    private val dir = File(ctx.filesDir, "icons")

    fun label(key: String): String? =
        sp.getString("label:$key", null)?.takeIf { it.isNotBlank() }

    fun setLabel(key: String, value: String?) {
        val clean = value?.trim()?.takeIf { it.isNotBlank() }
        sp.edit().apply {
            if (clean == null) remove("label:$key") else putString("label:$key", clean)
        }.apply()
    }

    fun iconFile(key: String): File? = fileFor(key).takeIf { it.exists() }

    /** A Google Fonts family for this one name, or null to follow the global font. */
    fun font(key: String): String? =
        sp.getString("font:$key", null)?.takeIf { it.isNotBlank() }

    fun setFont(key: String, value: String?) {
        val clean = value?.trim()?.takeIf { it.isNotBlank() }
        sp.edit().apply {
            if (clean == null) remove("font:$key") else putString("font:$key", clean)
        }.apply()
    }

    fun hasAny(key: String) =
        label(key) != null || iconFile(key) != null || font(key) != null

    /** False when the image cannot be read; nothing is written in that case. */
    fun setIcon(key: String, uri: Uri): Boolean {
        val bmp = decodeSquare(uri) ?: return false
        dir.mkdirs()
        return runCatching {
            fileFor(key).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        }.getOrDefault(false)
    }

    fun clearIcon(key: String) {
        runCatching { fileFor(key).delete() }
    }

    fun clear(key: String) {
        setLabel(key, null)
        setFont(key, null)
        clearIcon(key)
    }

    /** Bumped on every write so a reload can be keyed off it. */
    fun stamp(): Int = sp.all.size + (dir.listFiles()?.size ?: 0)

    private fun fileFor(key: String) = File(dir, "${key.hashCode().toUInt()}.png")

    private fun decodeSquare(uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val worst = maxOf(bounds.outWidth, bounds.outHeight)
        if (worst <= 0) return null

        var sample = 1
        while (worst / sample > OVERRIDE_PX * 2) sample *= 2

        val full = ctx.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(
                it, null, BitmapFactory.Options().apply { inSampleSize = sample }
            )
        } ?: return null

        // centre-crop to a square so a wide photo does not end up squashed
        val side = minOf(full.width, full.height)
        val src = Rect(
            (full.width - side) / 2, (full.height - side) / 2,
            (full.width + side) / 2, (full.height + side) / 2
        )
        val out = Bitmap.createBitmap(OVERRIDE_PX, OVERRIDE_PX, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            full, src, RectF(0f, 0f, OVERRIDE_PX.toFloat(), OVERRIDE_PX.toFloat()), null
        )
        out
    }.getOrNull()
}
