package com.maria.homelauncher

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * One family as a catalogue lists it. [key] is whatever that provider needs to fetch the
 * file and is meaningless outside it, so it is never stored in settings: the id keeps the
 * readable name and the key is looked up again from the catalogue.
 */
data class WebFamily(val name: String, val category: String, val key: String = "")

/**
 * The font catalogues that live on the web, fetched over plain HTTP with no account, no key
 * and nothing from the Play Store.
 *
 * Three providers, and each one needed its own handshake:
 *
 *  - **Google Fonts** lists everything in one JSON. Its CSS endpoint hands back a .ttf only
 *    if the thing asking does not look like it understands woff2, so it is asked with an
 *    ancient user agent on purpose.
 *  - **Fontsource** lists its own ids, and the files sit at a predictable path on jsDelivr.
 *    Most of it mirrors Google, but a hundred-odd families are not in Google at all.
 *  - **Fontshare** is a hundred families, none of them in Google. Its file URLs are opaque
 *    hashes, so each one costs a CSS request to resolve. It refuses the ancient user agent
 *    outright, so this is the one that gets a current one.
 *
 * Everything downloaded is ours and lives in the launcher's own storage, so a font keeps
 * working with no connection once it has been fetched a first time.
 */
object FontWeb {

    private const val GOOGLE_META = "https://fonts.google.com/metadata/fonts"
    private const val GOOGLE_CSS = "https://fonts.googleapis.com/css2?family="

    private const val SOURCE_META = "https://api.fontsource.org/v1/fonts"
    private const val SOURCE_CDN = "https://cdn.jsdelivr.net/fontsource/fonts/"

    private const val SHARE_META = "https://api.fontshare.com/v2/fonts?limit=100&offset=0"
    private const val SHARE_CSS = "https://api.fontshare.com/v2/css?f%5B%5D="

    /** Old enough that Google will not offer woff2, which Android cannot load. */
    private const val OLD_UA = "Mozilla/4.0"

    /** Fontshare answers 400 to the ancient one, so it gets a real phone's. */
    private const val NEW_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private const val STALE_MS = 7L * 24 * 60 * 60 * 1000

    /** One folder per provider, kept apart from the fonts the user added on purpose. */
    fun cacheDir(ctx: Context, src: FontSource) =
        File(File(ctx.filesDir, "net"), src.tag).apply { mkdirs() }

    private fun listFile(ctx: Context, src: FontSource) =
        File(cacheDir(ctx, src), "catalogue.tsv")

    fun fileFor(ctx: Context, src: FontSource, family: String) =
        File(cacheDir(ctx, src), family.replace(' ', '_') + ".ttf")

    /** What was fetched last time, if anything. Cheap, so it can be read at startup. */
    fun cachedCatalogue(ctx: Context, src: FontSource): List<WebFamily> = runCatching {
        listFile(ctx, src).takeIf { it.isFile }?.readLines().orEmpty().mapNotNull { line ->
            val p = line.split('\t')
            // the first version of this file had no key column
            if (p.size >= 2 && p[0].isNotBlank()) {
                WebFamily(p[0], p[1], if (p.size >= 3) p[2] else "")
            } else {
                null
            }
        }
    }.getOrDefault(emptyList())

    fun catalogueIsStale(ctx: Context, src: FontSource): Boolean {
        val f = listFile(ctx, src)
        return !f.isFile || System.currentTimeMillis() - f.lastModified() > STALE_MS
    }

    /**
     * Pulls one provider's family list. Returns null on any failure, which the caller treats
     * as "carry on with what is already cached" rather than as something to report.
     */
    suspend fun refreshCatalogue(ctx: Context, src: FontSource): List<WebFamily>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val out = when (src) {
                    FontSource.Google -> googleCatalogue()
                    FontSource.Fontsource -> fontsourceCatalogue()
                    FontSource.Fontshare -> fontshareCatalogue()
                    else -> null
                } ?: return@runCatching null
                if (out.isEmpty()) return@runCatching null
                listFile(ctx, src).writeText(
                    out.joinToString("\n") { "${it.name}\t${it.category}\t${it.key}" }
                )
                out
            }.getOrNull()
        }

    private fun googleCatalogue(): List<WebFamily>? {
        val body = get(GOOGLE_META, OLD_UA) ?: return null
        // a plain JSON object, but guard against the XSSI prefix some Google endpoints
        // put in front of one
        val list = JSONObject(body.substringAfter('{').let { "{$it" })
            .getJSONArray("familyMetadataList")
        return list.objects().mapNotNull { o ->
            o.optString("family").takeIf { it.isNotBlank() }
                ?.let { WebFamily(it, o.optString("category")) }
        }
    }

    /**
     * The id and the default subset are both needed to build a file path, and the weight
     * matters because plenty of display faces ship only one and it is not always 400.
     */
    private fun fontsourceCatalogue(): List<WebFamily>? {
        val body = get(SOURCE_META, NEW_UA) ?: return null
        return JSONArray(body).objects().mapNotNull { o ->
            val family = o.optString("family").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val subset = o.optString("defSubset").ifBlank { "latin" }
            val weights = (0 until (o.optJSONArray("weights")?.length() ?: 0))
                .map { o.getJSONArray("weights").optInt(it) }
                .filter { it > 0 }
            val weight = if (weights.contains(400)) 400 else weights.minByOrNull {
                kotlin.math.abs(it - 400)
            } ?: 400
            WebFamily(family, o.optString("category"), "$id|$subset|$weight")
        }
    }

    private fun fontshareCatalogue(): List<WebFamily>? {
        val body = get(SHARE_META, NEW_UA) ?: return null
        return JSONObject(body).getJSONArray("fonts").objects().mapNotNull { o ->
            val name = o.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val slug = o.optString("slug").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            WebFamily(name, o.optString("category"), slug)
        }
    }

    /**
     * Fetches one family as a font file. [into] lets the caller send it to the user's own
     * font folder instead of the cache, which is what "keep offline" does.
     *
     * [key] is the provider's own identifier for the family, from the catalogue. Without one
     * the family name is used, which is right for Google and a guess everywhere else.
     */
    suspend fun download(
        ctx: Context,
        src: FontSource,
        family: String,
        key: String = "",
        into: File? = null
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val target = into ?: fileFor(ctx, src, family)
            if (target.isFile && target.length() > 0) return@runCatching target

            val url = when (src) {
                FontSource.Google -> googleUrl(family)
                FontSource.Fontsource -> fontsourceUrl(key.ifBlank { slug(family) })
                FontSource.Fontshare -> fontshareUrl(key.ifBlank { slug(family) })
                else -> null
            } ?: return@runCatching null

            // straight to a temporary name, so a half-written file is never mistaken
            // for a usable font if the connection drops
            val tmp = File(target.parentFile, target.name + ".part")
            openStream(url, uaFor(src))?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            } ?: return@runCatching null

            if (tmp.length() == 0L) {
                tmp.delete()
                return@runCatching null
            }
            target.parentFile?.mkdirs()
            tmp.renameTo(target)
            if (into == null) prune(cacheDir(ctx, src))
            target.takeIf { it.isFile }
        }.getOrNull()
    }

    /**
     * Every row on a provider tab draws its own name in its own font, so scrolling one is
     * a download per row. That is the point of the preview, but it also means a few minutes
     * of browsing would quietly fill the cache with fonts nobody chose, and some faces are
     * well over a megabyte. The newest [KEEP] survive, which is enough to scroll back up
     * without refetching, and anything the user actually wanted is in their own folder
     * where this never looks.
     */
    private val touched = HashMap<String, Long>()

    /**
     * Marks a cached font as still wanted. [prune] goes by how recently a file was touched,
     * and a font that is actually in use gets resolved every time the thing wearing it is
     * drawn, so touching it here keeps it at the front of the queue and out of reach of a
     * scroll through some other provider. Rate limited, because the home screen asks for
     * its own font far more often than a filesystem write is worth.
     */
    fun touch(file: File) {
        val now = System.currentTimeMillis()
        synchronized(touched) {
            if (now - (touched[file.path] ?: 0L) < 60_000) return
            touched[file.path] = now
        }
        runCatching { file.setLastModified(now) }
    }

    private fun prune(dir: File, keep: Int = KEEP) {
        val fonts = dir.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".ttf") }
        if (fonts.size <= keep) return
        fonts.sortedByDescending { it.lastModified() }.drop(keep).forEach { it.delete() }
    }

    private const val KEEP = 48

    private fun googleUrl(family: String): String? {
        val css = get(GOOGLE_CSS + enc(family), OLD_UA) ?: return null
        return ttfIn(css)
    }

    /**
     * A plain path, so no request is needed to find the file. The subset in the key is the
     * family's own default; latin is tried after it for the handful of catalogue rows that
     * name a subset the CDN does not have under that spelling.
     */
    private fun fontsourceUrl(key: String): String? {
        val parts = key.split('|')
        val id = parts[0]
        val subset = parts.getOrNull(1)?.ifBlank { null } ?: "latin"
        val weight = parts.getOrNull(2)?.ifBlank { null } ?: "400"
        val tries = listOf(subset, "latin").distinct()
        return tries.map { "$SOURCE_CDN$id@latest/$it-$weight-normal.ttf" }.firstOrNull { reachable(it) }
    }

    /** The file URLs are opaque hashes, so the stylesheet is the only way to find one. */
    private fun fontshareUrl(slug: String): String? {
        val css = get("$SHARE_CSS$slug@400", NEW_UA) ?: return null
        return ttfIn(css)
    }

    /** The .ttf is listed last, after woff2 and woff, and is the only one Android can read. */
    private fun ttfIn(css: String): String? =
        Regex("url\\('?(//[^')]+?\\.ttf)'?\\)").find(css)?.groupValues?.get(1)
            ?.let { "https:$it" }
            ?: Regex("url\\('?(https://[^')]+?\\.ttf)'?\\)").find(css)?.groupValues?.get(1)

    private fun uaFor(src: FontSource) = if (src == FontSource.Google) OLD_UA else NEW_UA

    /** A last resort when a family was chosen before its catalogue had ever been fetched. */
    private fun slug(family: String) = family.lowercase().replace(' ', '-')

    private fun enc(family: String) =
        URLEncoder.encode(family, "UTF-8").replace("%20", "+")

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }

    private fun reachable(url: String): Boolean = runCatching {
        (URL(url).openConnection() as HttpURLConnection).run {
            requestMethod = "HEAD"
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("User-Agent", NEW_UA)
            val ok = responseCode in 200..299
            disconnect()
            ok
        }
    }.getOrDefault(false)

    private fun get(url: String, ua: String): String? = openConnection(url, ua)?.let { conn ->
        runCatching { conn.inputStream.bufferedReader().use { it.readText() } }
            .also { conn.disconnect() }
            .getOrNull()
    }

    private fun openStream(url: String, ua: String) =
        openConnection(url, ua)?.let { runCatching { it.inputStream }.getOrNull() }

    private fun openConnection(url: String, ua: String): HttpURLConnection? = runCatching {
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("User-Agent", ua)
            instanceFollowRedirects = true
        }.takeIf { it.responseCode in 200..299 }
    }.getOrNull()
}
