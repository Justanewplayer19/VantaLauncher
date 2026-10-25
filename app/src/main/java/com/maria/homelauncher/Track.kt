package com.maria.homelauncher

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The home screen's audio file, kept as a copy inside the app.
 *
 * Playing straight from the picked URI looks simpler and is not. A content URI is a
 * question asked of another app, and plenty of answers are not a file at all: a cloud
 * provider hands back a stream it wants to download first, some providers hand back a
 * descriptor MediaPlayer cannot seek, and a persisted permission can be withdrawn by a
 * reboot, an update, or the user tidying up. Every one of those ends the same way, with
 * silence and nothing to show for it.
 *
 * A copy sidesteps all of it. Audio files are small next to what the launcher already
 * carries in fonts and icons, and the file is deleted the moment the track is removed.
 */
object HomeTrack {

    private fun dir(ctx: Context) = File(ctx.filesDir, "audio").apply { mkdirs() }

    /** Legacy, and what the picker hands over: anything that is not our own copy. */
    fun isUri(stored: String) = stored.startsWith("content://") || stored.startsWith("file://")

    /**
     * Copies the picked file in and returns what to store, or null if it could not be
     * read at all. Only one track is kept, so the previous copy goes with it.
     */
    fun import(ctx: Context, uri: Uri): String? = runCatching {
        val name = displayName(ctx, uri) ?: "track"
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60).ifBlank { "track" }

        dir(ctx).listFiles()?.forEach { it.delete() }
        val out = File(dir(ctx), safe)
        ctx.contentResolver.openInputStream(uri)!!.use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        if (out.length() == 0L) {
            out.delete()
            return null
        }
        safe
    }.getOrNull()

    /** The playable file for a stored name, or null if it has gone missing. */
    fun file(ctx: Context, stored: String): File? {
        if (stored.isBlank() || isUri(stored)) return null
        return File(dir(ctx), stored).takeIf { it.isFile }
    }

    /** What to show on the settings page. */
    fun label(ctx: Context, stored: String): String = when {
        stored.isBlank() -> ""
        isUri(stored) -> displayName(ctx, Uri.parse(stored)) ?: "One file chosen"
        else -> stored
    }

    fun clear(ctx: Context) {
        runCatching { dir(ctx).listFiles()?.forEach { it.delete() } }
    }

    /**
     * Plays the track for a few seconds right now and says what went wrong if it will not
     * play, which is the whole point: the home screen version has nowhere to report to,
     * so without this a track that cannot be decoded is indistinguishable from a track
     * playing at a volume you cannot hear.
     *
     * Returns null when it played, or something to show the user when it did not.
     */
    suspend fun probe(ctx: Context, stored: String, volume: Int): String? =
        withContext(Dispatchers.IO) {
            // by far the most likely answer, and the one no error code would ever mention
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val level = runCatching { am?.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrNull()
            if (level == 0) return@withContext "the media volume on this phone is at zero"

            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                val local = file(ctx, stored)
                if (local != null) mp.setDataSource(local.absolutePath)
                else mp.setDataSource(ctx, Uri.parse(stored))
                // prepare rather than prepareAsync: a failure here is the answer we came for
                mp.prepare()
                mp.setVolume(volume / 100f, volume / 100f)
                mp.start()
                delay(4000)
                null
            } catch (t: Throwable) {
                t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
            } finally {
                runCatching { mp.release() }
            }
        }

    private fun displayName(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }
    }.getOrNull()
}
