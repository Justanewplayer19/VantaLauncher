package com.maria.homelauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Updates, without going to the website.
 *
 * The launcher watches its own GitHub releases, downloads a newer APK on wifi, and then
 * offers to install it. What it cannot do is install it silently: Android only allows that
 * for an app that owns the device or is part of the system image, and this is neither. So
 * the last step is always a tap on Android's own installer dialog. Everything before that
 * step is automatic, which is the part that was actually tedious.
 *
 * Three things have to line up or an update will fail in a way that is confusing rather
 * than obvious, so they are worth stating:
 *
 *  - **The new APK must be signed with the same key as the installed one.** Android refuses
 *    the install otherwise, with a message that does not explain why. A debug build cannot
 *    update a release build, or the other way round.
 *  - **The release tag is the version.** `v0.3` means versionName `0.3`. [newer] compares
 *    those numbers, so a tag that is not a version means no update is ever offered.
 *  - **The APK has to be an asset on the release**, not just source code, since that is
 *    what gets downloaded.
 *
 * `dev.ps1 release` does all three, so the convention is kept by the tool rather than by
 * anyone remembering it.
 */
object Updater {

    /** Where releases are published. Blank would mean no update checking at all. */
    const val OWNER = "Justanewplayer19"
    const val REPO = "VantaLauncher"

    private const val UA = "VantaLauncher"
    private const val WORK_NAME = "update-check"

    val configured: Boolean get() = OWNER.isNotBlank()

    data class Release(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val bytes: Long
    )

    fun installedVersion(ctx: Context): String = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
    }.getOrNull() ?: "0"

    /**
     * Compares dotted numbers, so 0.10 is correctly newer than 0.9 where a string compare
     * would say the opposite. A part that is not a number counts as zero rather than
     * throwing, since a tag is typed by a human.
     */
    fun newer(candidate: String, installed: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v")
            .split('.', '-', '+')
            .map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Null when there is no network, no release, or no APK attached to it. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        if (!configured) return@withContext null
        runCatching {
            val body = get("https://api.github.com/repos/$OWNER/$REPO/releases/latest")
                ?: return@runCatching null
            val json = JSONObject(body)
            val tag = json.optString("tag_name").ifBlank { return@runCatching null }
            val assets = json.optJSONArray("assets") ?: return@runCatching null
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val name = a.optString("name")
                if (!name.endsWith(".apk", true)) continue
                return@runCatching Release(
                    version = tag.removePrefix("v"),
                    notes = json.optString("body").trim(),
                    apkUrl = a.optString("browser_download_url"),
                    bytes = a.optLong("size")
                )
            }
            null
        }.getOrNull()
    }

    /**
     * Downloaded into the cache, because a half-finished update is not worth keeping and
     * the system may clear it whenever it needs the space. Named after the version so an
     * older download is never mistaken for the current one.
     */
    fun fileFor(ctx: Context, version: String) =
        File(File(ctx.cacheDir, "updates").apply { mkdirs() }, "vanta-$version.apk")

    suspend fun download(ctx: Context, release: Release): File? = withContext(Dispatchers.IO) {
        runCatching {
            val target = fileFor(ctx, release.version)
            if (target.isFile && target.length() == release.bytes) return@runCatching target

            // anything left from an abandoned attempt, or from a version that has already
            // been installed, is just taking up room
            target.parentFile?.listFiles().orEmpty().forEach { if (it != target) it.delete() }

            val tmp = File(target.parentFile, target.name + ".part")
            open(release.apkUrl)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            } ?: return@runCatching null

            // a truncated APK installs as nothing but an error, so size is checked before
            // it is ever offered
            if (release.bytes > 0 && tmp.length() != release.bytes) {
                tmp.delete()
                return@runCatching null
            }
            tmp.renameTo(target)
            target.takeIf { it.isFile }
        }.getOrNull()
    }

    /** True once the user has allowed this app to install others. */
    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /** The system screen where that permission lives, since it is not a runtime prompt. */
    fun permissionIntent(ctx: Context) =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Hands the file to Android's installer. A content URI, because a file:// one has been
     * refused since Android 7 and the installer is another app that has to be able to read
     * it.
     */
    fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        if (!configured || !Prefs(ctx).autoUpdate) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        // once a day is plenty for a launcher, and unmetered because this ends in a
        // download of several megabytes that nobody asked to pay for
        val req = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    /**
     * The whole check, shared by the background worker and the button on the settings page.
     * Returns the version that is ready to install, or null if there is nothing to do.
     */
    suspend fun checkAndFetch(ctx: Context, autoDownload: Boolean = true): String? {
        val prefs = Prefs(ctx)
        prefs.lastUpdateCheck = System.currentTimeMillis()
        val release = latest() ?: return null
        if (!newer(release.version, installedVersion(ctx))) {
            prefs.readyVersion = ""
            return null
        }
        if (!autoDownload) return release.version
        val file = download(ctx, release) ?: return null
        prefs.readyVersion = release.version
        prefs.readyNotes = release.notes.take(500)
        return file.let { release.version }
    }

    private fun get(url: String): String? = open(url)?.use { it.bufferedReader().readText() }

    /** GitHub answers 403 to a request with no user agent, so there is always one. */
    private fun open(url: String) = runCatching {
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/vnd.github+json")
            instanceFollowRedirects = true
        }.takeIf { it.responseCode in 200..299 }?.inputStream
    }.getOrNull()
}

class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        // a failed check is not worth retrying hard: there will be another one tomorrow,
        // and the user can always press the button
        Updater.checkAndFetch(applicationContext)
        return Result.success()
    }
}
