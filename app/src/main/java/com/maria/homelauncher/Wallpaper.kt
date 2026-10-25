package com.maria.homelauncher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "wallpaper-rotate"

object Wallpaper {

    /**
     * Applies the next image in the list. Returns false if there was nothing usable --
     * empty list, or every URI failed (revoked permission, deleted file).
     */
    suspend fun advance(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = Prefs(ctx)
        val uris = sources(ctx)
        if (uris.isEmpty()) return@withContext false

        // Walk the list from the cursor so a dead URI falls through to the next one
        // instead of stalling the rotation forever.
        val start = if (prefs.shuffle) uris.indices.random() else prefs.cursor % uris.size
        for (step in uris.indices) {
            val i = (start + step) % uris.size
            if (apply(ctx, Uri.parse(uris[i]), prefs.rotateTarget)) {
                prefs.cursor = (i + 1) % uris.size
                return@withContext true
            }
        }
        false
    }

    /**
     * Everything the rotation can currently choose from: the individually picked images,
     * plus whatever is in the picked folders *right now*. Reading the folders on every
     * change is the point: a picture added to one afterwards joins the rotation on its
     * own, where a list captured at pick time would have gone stale.
     */
    fun sources(ctx: Context): List<String> {
        val prefs = Prefs(ctx)
        val fromFolders = prefs.wallpaperFolders.flatMap { imagesIn(ctx, Uri.parse(it)) }
        return (prefs.wallpapers + fromFolders).distinct()
    }

    /** Applies one image immediately, outside the rotation. */
    suspend fun setOne(ctx: Context, uri: Uri, target: Int): Boolean =
        withContext(Dispatchers.IO) { apply(ctx, uri, target) }

    /**
     * Every image directly inside a picked folder. The tree permission covers the children,
     * so these URIs keep working after a reboot like individually picked files do.
     */
    fun imagesIn(ctx: Context, tree: Uri): List<String> {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
            ?: return emptyList()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val out = mutableListOf<String>()

        runCatching {
            ctx.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val mime = c.getString(1) ?: continue
                    if (!mime.startsWith("image/")) continue
                    out += DocumentsContract.buildDocumentUriUsingTree(tree, id).toString()
                }
            }
        }
        return out.sorted()
    }

    private fun apply(ctx: Context, uri: Uri, target: Int): Boolean {
        val wm = WallpaperManager.getInstance(ctx)
        val flags = when (target) {
            TARGET_HOME -> listOf(WallpaperManager.FLAG_SYSTEM)
            TARGET_LOCK -> listOf(WallpaperManager.FLAG_LOCK)
            else -> listOf(WallpaperManager.FLAG_SYSTEM, WallpaperManager.FLAG_LOCK)
        }

        // A live wallpaper is drawn *over* the static one. Setting a picture underneath
        // one reports success and changes nothing you can see, which is exactly what it
        // looks like when a phone that ships with a moving wallpaper ignores you. Samsung
        // is the usual one. Clearing it first is the only way to be seen.
        if (runCatching { wm.wallpaperInfo }.getOrNull() != null) {
            flags.forEach { runCatching { wm.clear(it) } }
        }

        // Decoded here rather than handed over as a stream: some skins quietly refuse a
        // stream they cannot crop themselves, and a bitmap gives them nothing to refuse.
        val bitmap = decode(ctx, uri)

        var ok = false
        for (f in flags) {
            val done = runCatching {
                if (bitmap != null) {
                    wm.setBitmap(bitmap, null, true, f)
                    true
                } else {
                    // setStream consumes the stream, so each target needs its own
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        wm.setStream(input, null, true, f)
                        true
                    } ?: false
                }
            }.getOrDefault(false)
            ok = ok || done
        }
        if (ok) {
            Prefs(ctx).lastWallpaper = uri.toString()
            WallpaperColour.applyIfWanted(ctx, uri)
        }
        return ok
    }

    /**
     * The picture at roughly the size of the screen. A full-resolution photo is both a
     * waste and a way to be killed for using too much memory, and the wallpaper service
     * scales whatever it is given anyway.
     */
    private fun decode(ctx: Context, uri: Uri): Bitmap? = runCatching {
        val metrics = ctx.resources.displayMetrics
        val want = maxOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > want * 2) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }.getOrNull()

    fun schedule(ctx: Context) {
        val prefs = Prefs(ctx)
        val wm = WorkManager.getInstance(ctx)

        if (!prefs.rotateOn || sources(ctx).isEmpty()) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }

        val req = PeriodicWorkRequestBuilder<RotateWorker>(
            prefs.rotateMinutes.toLong(), TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder().setRequiresBatteryNotLow(true).build()
        ).build()

        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}

class RotateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return if (Wallpaper.advance(applicationContext)) Result.success() else Result.retry()
    }
}
