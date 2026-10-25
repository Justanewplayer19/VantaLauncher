package com.maria.homelauncher

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The whole setup, in one file you can keep.
 *
 * Everything the launcher knows lives in five preference stores and two folders of files,
 * and none of it could be written out anywhere. Rebuilding a wallpaper, a set of renames,
 * replaced icons, per-app fonts, the dock, the hidden list, the grid and the widget
 * placement by hand is hours of work, and it only takes one cleared app to need it.
 *
 * The file is JSON on purpose: it can be read, and a value can be corrected in a text
 * editor if it ever comes to that.
 *
 * Two things are deliberately not portable to a different phone, and the import says so
 * rather than failing quietly:
 *
 *  - **Widget placements** are host ids handed out by this phone's widget service. The ids
 *    mean nothing on another device, so widgets have to be placed again there.
 *  - **Icon packs** are other apps. The name is restored, but it only takes effect if that
 *    pack is installed.
 *
 * Font and icon files travel with the backup, and a font id pointing at a file is rewritten
 * to wherever that file lands on the new phone, since the path is not the same twice.
 */
object Backup {

    private const val APP = "com.maria.homelauncher"
    private const val FORMAT = 1

    /** Every store the launcher writes. Adding one here is all it takes to include it. */
    private val STORES = listOf("launcher", "style", "overrides", "layout", "widgets")

    /** Folders carried whole, relative to filesDir. */
    private val FOLDERS = listOf("fonts", "icons")

    /** Not a setting: the undo snapshot is this phone's scratch space, not part of a setup. */
    private val SKIP_STORES = listOf("style_undo")

    fun suggestedName(): String {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "launcher-backup-$day.json"
    }

    // --- writing ---

    fun toJson(ctx: Context): String {
        val root = JSONObject()
        root.put("app", APP)
        root.put("format", FORMAT)
        root.put("created", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
        root.put("device", "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")

        val prefs = JSONObject()
        STORES.filterNot { it in SKIP_STORES }.forEach { name ->
            prefs.put(name, storeToJson(ctx, name))
        }
        root.put("prefs", prefs)

        val files = JSONObject()
        FOLDERS.forEach { folder ->
            File(ctx.filesDir, folder).listFiles().orEmpty()
                .filter { it.isFile }
                .forEach { f ->
                    runCatching {
                        files.put(
                            "$folder/${f.name}",
                            Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
                        )
                    }
                }
        }
        root.put("files", files)
        return root.toString(2)
    }

    /**
     * Each value carries its type. SharedPreferences is typed, and asking for a key as the
     * wrong type throws, so putting an Int back where a Long was would break the next read.
     */
    private fun storeToJson(ctx: Context, name: String): JSONObject {
        val out = JSONObject()
        ctx.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (k, v) ->
            val entry = JSONObject()
            when (v) {
                is Boolean -> entry.put("t", "b").put("v", v)
                is Int -> entry.put("t", "i").put("v", v)
                is Long -> entry.put("t", "l").put("v", v)
                is Float -> entry.put("t", "f").put("v", v.toDouble())
                is String -> entry.put("t", "s").put("v", v)
                is Set<*> -> entry.put("t", "ss")
                    .put("v", org.json.JSONArray(v.map { it.toString() }))
                else -> return@forEach
            }
            out.put(k, entry)
        }
        return out
    }

    fun write(ctx: Context, uri: Uri): Boolean = runCatching {
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(toJson(ctx).toByteArray())
        } ?: return false
        true
    }.getOrDefault(false)

    // --- reading ---

    /** What an import did, so the screen can say something true rather than "done". */
    data class Result(
        val ok: Boolean,
        val message: String,
        val widgetsDropped: Boolean = false
    )

    fun read(ctx: Context, uri: Uri): Result {
        val text = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        }.getOrNull() ?: return Result(false, "That file could not be opened.")

        val root = runCatching { JSONObject(text) }.getOrNull()
            ?: return Result(false, "That is not a backup file.")

        if (root.optString("app") != APP) {
            return Result(false, "That backup is not from this launcher.")
        }
        if (root.optInt("format", 0) > FORMAT) {
            return Result(false, "That backup was written by a newer version of the app.")
        }
        val prefs = root.optJSONObject("prefs")
            ?: return Result(false, "That backup has no settings in it.")

        // files first: a font id can only be pointed at a file that is already there
        val landed = restoreFiles(ctx, root.optJSONObject("files"))

        val sameDevice = root.optString("device") ==
            "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}"

        var restored = 0
        STORES.forEach { name ->
            val store = prefs.optJSONObject(name) ?: return@forEach
            restoreStore(ctx, name, store, landed)
            restored++
        }
        if (restored == 0) return Result(false, "That backup has no settings in it.")

        return Result(
            ok = true,
            message = "Restored from ${root.optString("created", "an earlier backup")}.",
            // widget ids belong to the phone that issued them, so the rows naming them
            // come back dead on another device and the grid drops them on sight
            widgetsDropped = !sameDevice && (
                prefs.optJSONObject("layout")
                    ?.optJSONObject("items")
                    ?.optString("v")
                    .orEmpty()
                    .contains("\"t\":\"w\"")
                )
        )
    }

    /** Returns file name to new absolute path, for rewriting the ids that point at them. */
    private fun restoreFiles(ctx: Context, files: JSONObject?): Map<String, String> {
        val landed = HashMap<String, String>()
        if (files == null) return landed
        files.keys().forEach { key ->
            val folder = key.substringBefore('/', "")
            val name = key.substringAfter('/', "")
            if (folder !in FOLDERS || name.isBlank() || name.contains('/')) return@forEach
            runCatching {
                val bytes = Base64.decode(files.getString(key), Base64.NO_WRAP)
                val dir = File(ctx.filesDir, folder).apply { mkdirs() }
                val out = File(dir, name)
                out.writeBytes(bytes)
                landed[name] = out.absolutePath
            }
        }
        return landed
    }

    private fun restoreStore(
        ctx: Context,
        name: String,
        store: JSONObject,
        landed: Map<String, String>
    ) {
        val e = ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
        store.keys().forEach { k ->
            val entry = store.optJSONObject(k) ?: return@forEach
            when (entry.optString("t")) {
                "b" -> e.putBoolean(k, entry.optBoolean("v"))
                "i" -> e.putInt(k, entry.optInt("v"))
                "l" -> e.putLong(k, entry.optLong("v"))
                "f" -> e.putFloat(k, entry.optDouble("v").toFloat())
                "s" -> e.putString(k, repath(entry.optString("v"), landed))
                "ss" -> {
                    val arr = entry.optJSONArray("v")
                    val set = LinkedHashSet<String>()
                    for (i in 0 until (arr?.length() ?: 0)) arr?.optString(i)?.let(set::add)
                    e.putStringSet(k, set)
                }
            }
        }
        e.apply()
    }

    /**
     * A font id like `f:/data/user/0/<app>/files/fonts/Satoshi.ttf` names a path, and the
     * path on the new phone is not the one in the backup. The file travelled with us, so
     * the id is pointed at wherever it landed. Anything else is left alone.
     */
    private fun repath(value: String, landed: Map<String, String>): String {
        if (!value.startsWith("${FontSource.Files.tag}:")) return value
        val old = value.substringAfter(':')
        val fresh = landed[File(old).name] ?: return value
        return "${FontSource.Files.tag}:$fresh"
    }
}
