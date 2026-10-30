package com.maria.homelauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Baked icons, kept on disk.
 *
 * Baking one icon costs about five milliseconds: an IPC to load the drawable, a draw into
 * a bitmap, a second bitmap for the mask. That is nothing once and everything a hundred
 * and twenty times, which is what every cold start used to pay. A launcher on a phone
 * with little memory is killed and started again all day, so "every cold start" means
 * "most times you press home".
 *
 * Reading the same icon back off disk is well under a millisecond, so the work moves to
 * the first run after anything that changes how icons look, and never happens again.
 *
 * Nothing in here is precious. It lives in the cache directory, which Android is free to
 * empty whenever it wants the space; a miss just bakes the icon again.
 */
class IconCache(ctx: Context, signature: String) {

    /**
     * One directory per look. Changing the shape, the tint or the icon pack means every
     * icon is different, so the old ones are a directory to delete rather than a hundred
     * files to invalidate one at a time.
     */
    private val root = File(ctx.cacheDir, "icons")
    private val dir = File(root, signature.hash())

    fun read(name: String): Bitmap? = runCatching {
        val f = File(dir, name)
        if (!f.isFile) null else BitmapFactory.decodeFile(f.absolutePath)
    }.getOrNull()

    fun write(name: String, bmp: Bitmap) {
        runCatching {
            dir.mkdirs()
            // written beside and renamed, so a kill halfway through leaves no half file
            val tmp = File(dir, "$name.part")
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!tmp.renameTo(File(dir, name))) tmp.delete()
        }
    }

    /** Everything baked for some other look. Only the current one is worth keeping. */
    fun dropOtherLooks() {
        runCatching {
            root.listFiles()?.forEach { if (it.isDirectory && it != dir) it.deleteRecursively() }
        }
    }
}

/**
 * A short, file-name-safe digest. Not a security question: it only has to be stable
 * across runs and not collide across a few hundred strings, and a package name with a
 * slash and a dot in it is not a file name.
 */
internal fun String.hash(): String = runCatching {
    java.security.MessageDigest.getInstance("SHA-1")
        .digest(toByteArray())
        .take(10)
        .joinToString("") { "%02x".format(it) }
}.getOrElse { hashCode().toString(16) }
