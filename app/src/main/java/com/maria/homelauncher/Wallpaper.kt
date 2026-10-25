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
        step(ctx, prefs.rotateTarget, prefs.shuffle, prefs.cursor, true) { prefs.cursor = it }
    }

    /**
     * The lock screen only, for the screen-off change.
     *
     * It keeps a cursor of its own so it does not take turns with the timed rotation, and
     * it does not sample an accent colour: the picture is gone again in a few seconds, and
     * a theme that moves with it would be its own kind of annoying.
     */
    suspend fun advanceLock(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = Prefs(ctx)
        step(ctx, TARGET_LOCK, prefs.lockRandom, prefs.lockCursor, false) { prefs.lockCursor = it }
    }

    /**
     * Walks the list from [from] so a dead URI falls through to the next one instead of
     * stalling the rotation forever, and reports where it stopped.
     */
    private fun step(
        ctx: Context,
        target: Int,
        random: Boolean,
        from: Int,
        record: Boolean,
        save: (Int) -> Unit
    ): Boolean {
        val uris = sources(ctx)
        if (uris.isEmpty()) return false

        val start = if (random) uris.indices.random() else from.coerceAtLeast(0) % uris.size
        for (n in uris.indices) {
            val i = (start + n) % uris.size
            if (apply(ctx, Uri.parse(uris[i]), target, record)) {
                save((i + 1) % uris.size)
                return true
            }
        }

        // Nothing in the list would go on. Put the last picture that did back, rather
        // than leaving the phone showing the stock default: clearing a live wallpaper is
        // what exposes that default, and a half-done change is worse than no change.
        val last = Prefs(ctx).lastWallpaper
        if (last.isNotBlank()) runCatching { apply(ctx, Uri.parse(last), target, false) }
        return false
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

    private fun apply(ctx: Context, uri: Uri, target: Int, record: Boolean = true): Boolean {
        val wm = WallpaperManager.getInstance(ctx)
        val flags = when (target) {
            TARGET_HOME -> listOf(WallpaperManager.FLAG_SYSTEM)
            TARGET_LOCK -> listOf(WallpaperManager.FLAG_LOCK)
            else -> listOf(WallpaperManager.FLAG_SYSTEM, WallpaperManager.FLAG_LOCK)
        }

        // Decoded before anything is touched, and the whole thing is called off if it
        // cannot be. This is the one that matters.
        //
        // Handing the file over as a stream instead looks like a kind fallback and is a
        // trap: the wallpaper service takes the bytes, says yes, throws the old picture
        // away and then has nothing it can draw, so the phone falls back to the stock
        // default. A file we cannot read is a file to skip, not a file to try harder
        // with. The caller moves on to the next picture and the current one stays up.
        //
        // The clear below is destructive for the same reason, so it waits until the
        // replacement is in hand.
        val bitmap = decode(ctx, uri) ?: return false

        // A live wallpaper is drawn *over* the static one. Setting a picture underneath
        // one reports success and changes nothing you can see, which is exactly what it
        // looks like when a phone that ships with a moving wallpaper ignores you. Samsung
        // is the usual one. Clearing it first is the only way to be seen.
        if (runCatching { wm.wallpaperInfo }.getOrNull() != null) {
            flags.forEach { runCatching { wm.clear(it) } }
        }

        var ok = false
        for (f in flags) {
            // one re-arm: the first attempt can come back having done nothing at all on
            // a skin that was busy with its own wallpaper work, and trying once more
            // costs a fraction of a second against a phone left on its default
            val done = put(wm, bitmap, f) || put(wm, bitmap, f)
            ok = ok || done
        }
        if (ok && record) {
            Prefs(ctx).lastWallpaper = uri.toString()
            WallpaperColour.applyIfWanted(ctx, uri)
        }
        return ok
    }

    /**
     * One target, set and then checked. Only ever a bitmap we decoded ourselves.
     *
     * `setBitmap` returning without throwing is not proof of anything: the id the system
     * hands out for the current wallpaper is. If it did not move, the call was swallowed
     * and this reports the failure honestly so the caller can try again or put the old
     * picture back.
     */
    private fun put(wm: WallpaperManager, bitmap: Bitmap, flag: Int): Boolean {
        val before = runCatching { wm.getWallpaperId(flag) }.getOrDefault(-1)
        val tried = runCatching {
            wm.setBitmap(bitmap, null, true, flag)
            true
        }.getOrDefault(false)
        if (!tried) return false

        val after = runCatching { wm.getWallpaperId(flag) }.getOrDefault(-1)
        // an id of -1 means the phone would not tell us, which is not the same as failing
        return before < 0 || after < 0 || after != before
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
        val out = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        // a decoder can hand back something with no pixels in it rather than failing
        out?.takeIf { it.width > 0 && it.height > 0 }
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
