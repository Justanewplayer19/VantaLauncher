package com.maria.homelauncher

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.Collections

/**
 * Fonts that are not built into the phone.
 *
 * Google Fonts has a route of its own worth preferring: the downloadable-fonts provider
 * that ships with Play services, where the launcher never touches the network and never
 * stores a font file, it names a family and the provider hands back a typeface it has
 * already cached for every app on the device. Everything else, Google included on a phone
 * without that provider, is fetched over HTTP by [FontWeb] and cached here.
 *
 * A font file the user picked themselves is copied into our own storage, because a content
 * URI borrowed from a file manager will not outlive a reboot.
 *
 * Families are cached here by name. Compose resolves a font asynchronously and keeps its
 * own cache, but a fresh FontFamily object on every recomposition would miss that cache
 * and flicker back to the fallback each time.
 */
object Fonts {

    private const val PROVIDER_PKG = "com.google.android.gms"

    private val cache = HashMap<String, FontFamily>()
    private var provider: GoogleFont.Provider? = null
    private var appCtx: Context? = null

    /**
     * Bumped when a downloaded font lands. Reading it inside [google] subscribes whatever
     * composition asked for that font, so the text redraws itself once the file arrives
     * instead of staying on the fallback until something else happens to recompose.
     */
    private val revision = mutableIntStateOf(0)

    private val fetching = Collections.synchronizedSet(HashSet<String>())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The live family lists, one per web provider, once they have been fetched. A provider
     * with nothing here has never managed to answer on this phone.
     */
    var webCatalogues by mutableStateOf<Map<FontSource, List<WebFamily>>>(emptyMap())
        private set

    fun catalogueOf(src: FontSource): List<WebFamily> = webCatalogues[src].orEmpty()

    /**
     * Called once at startup. The provider has to be told which signing certificates count
     * as the real Play services, and the usual way is to paste a block of base64 into the
     * resources. That block is a copy of what is already on the phone, goes stale, and is
     * not something anyone can check by reading it. This asks the package manager for the
     * signatures of the installed provider instead.
     *
     * The honest trade: pinning to a certificate that shipped with the app would also catch
     * a phone whose Play services had been replaced by something else. Here the only thing
     * holding that line is that the package name is pinned and the real one is a system app,
     * so nothing else can claim it without the phone already being lost.
     */
    fun init(ctx: Context) {
        if (appCtx == null) {
            appCtx = ctx.applicationContext
            webCatalogues = FontSource.entries.filter { it.web }
                .associateWith { FontWeb.cachedCatalogue(ctx, it) }
                .filterValues { it.isNotEmpty() }
            // when there was one web provider its cache had its own folder; now that each
            // has one, the old folder is dead weight and nothing will ever read it again
            runCatching { File(ctx.filesDir, "gf").deleteRecursively() }
        }
        if (provider != null) return
        val certs = signatures(ctx) ?: return
        provider = GoogleFont.Provider(
            providerAuthority = "com.google.android.gms.fonts",
            providerPackage = PROVIDER_PKG,
            certificates = listOf(certs)
        )
    }

    /**
     * The old GET_SIGNATURES on purpose. It is deprecated, but it is also exactly what the
     * androidx font code asks the package manager for when it checks us back, and the two
     * lists are compared whole. Reading the newer signing-certificate history instead can
     * hand back a rotated certificate that the check has never heard of.
     */
    @Suppress("DEPRECATION")
    private fun signatures(ctx: Context): List<ByteArray>? = runCatching {
        ctx.packageManager
            .getPackageInfo(PROVIDER_PKG, PackageManager.GET_SIGNATURES)
            .signatures
            ?.map { it.toByteArray() }
            ?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /**
     * Play services first, since it caches one copy for every app on the phone and costs
     * us no storage. Without it the file is fetched from the CDN and kept, so the font
     * works offline from then on. While that first fetch is in flight the text shows the
     * fallback rather than nothing.
     */
    fun google(name: String): FontFamily {
        revision.intValue
        provider?.let { p ->
            return cache.getOrPut("g:$name") {
                val font = GoogleFont(name)
                FontFamily(
                    Font(googleFont = font, fontProvider = p, weight = FontWeight.Normal),
                    Font(googleFont = font, fontProvider = p, weight = FontWeight.Medium),
                    Font(googleFont = font, fontProvider = p, weight = FontWeight.Bold)
                )
            }
        }
        return web(FontSource.Google, name)
    }

    /**
     * A family from one of the web catalogues. If the file is already down it is used; if it
     * is not, the fetch starts and the text shows the fallback until it lands rather than
     * showing nothing.
     */
    fun web(src: FontSource, name: String): FontFamily {
        revision.intValue
        val ctx = appCtx ?: return FontFamily.Default
        val file = FontWeb.fileFor(ctx, src, name)
        if (file.isFile) {
            FontWeb.touch(file)
            return fromFile(file.absolutePath) ?: FontFamily.Default
        }
        fetch(src, name)
        return FontFamily.Default
    }

    private fun fetch(src: FontSource, name: String) {
        val ctx = appCtx ?: return
        if (!fetching.add("${src.tag}:$name")) return
        scope.launch {
            val got = FontWeb.download(ctx, src, name, keyFor(src, name))
            fetching.remove("${src.tag}:$name")
            if (got != null) revision.intValue++
        }
    }

    /**
     * What a provider calls a family internally. Only the readable name is ever stored, so
     * this is looked up again from the catalogue each time. Blank is fine: the download
     * falls back to guessing from the name.
     */
    private fun keyFor(src: FontSource, name: String): String =
        catalogueOf(src).firstOrNull { it.name.equals(name, true) }?.key.orEmpty()

    /** Pulls a provider's family list if it has never been fetched or has gone stale. */
    fun refreshCatalogue(ctx: Context, src: FontSource, force: Boolean = false) {
        if (!src.web) return
        if (!force && !FontWeb.catalogueIsStale(ctx, src)) return
        scope.launch {
            FontWeb.refreshCatalogue(ctx, src)?.let { list ->
                webCatalogues = webCatalogues + (src to list)
            }
        }
    }

    /**
     * Where [keepOffline] puts a family. The picker compares this against the files it
     * already has, so a row can say it has been kept instead of offering to keep it again.
     */
    fun keptPath(ctx: Context, name: String): String =
        File(dir(ctx), "$name.ttf").absolutePath

    /** Downloads a family into the user's own font folder, to keep it without a network. */
    suspend fun keepOffline(ctx: Context, src: FontSource, name: String): String? {
        val into = File(File(ctx.filesDir, "fonts").apply { mkdirs() }, "$name.ttf")
        val got = FontWeb.download(ctx, src, name, keyFor(src, name), into) ?: return null
        return "${FontSource.Files.tag}:${got.absolutePath}"
    }

    /** Null when the file has gone, so the caller can fall back instead of crashing. */
    fun fromFile(path: String): FontFamily? {
        cache["file:$path"]?.let { return it }
        val f = File(path)
        if (!f.isFile) return null
        val family = runCatching { FontFamily(Font(f)) }.getOrNull() ?: return null
        cache["file:$path"] = family
        return family
    }

    /**
     * Downloadable fonts need the provider app. A phone without Play services, or with it
     * disabled, gets the built-in families and is told why rather than silently ignored.
     */
    fun googleAvailable(ctx: Context): Boolean {
        init(ctx)
        return provider != null
    }

    // --- the six places a font can come from ---

    /**
     * Resolves a stored id. The grammar is "<tag>:<value>", and a bare string with no tag
     * is read as a Google family, which is how ids written before there were providers
     * keep working without a migration pass over every app override.
     */
    fun family(id: String, builtIn: FontChoice): FontFamily {
        val ref = parse(id) ?: return builtIn.family
        return when (ref.source) {
            FontSource.BuiltIn -> ref.builtIn().family
            FontSource.Google -> google(ref.value)
            FontSource.Fontsource, FontSource.Fontshare -> web(ref.source, ref.value)
            FontSource.Device, FontSource.Files -> fromFile(ref.value) ?: builtIn.family
        }
    }

    fun parse(id: String): FontRef? {
        if (id.isBlank()) return null
        val tag = id.substringBefore(':', "")
        val value = id.substringAfter(':', id)
        val source = FontSource.entries.firstOrNull { it.tag == tag } ?: FontSource.Google
        return FontRef(source, nameOf(source, value), value)
    }

    private fun nameOf(source: FontSource, value: String) = when (source) {
        FontSource.Device, FontSource.Files -> prettify(File(value).name)
        else -> value
    }

    /** What to show as the current choice. */
    fun label(id: String, builtIn: FontChoice): String =
        parse(id)?.name ?: "${builtIn.label} (built in)"

    /**
     * Every family the catalogue knows, or the list that shipped with the app when it has
     * never been fetched. The bundled one is a few hundred names; the live one is closer to
     * two thousand.
     */
    val googleRefs: List<FontRef> get() = webRefs(FontSource.Google)

    /**
     * Every family a web provider knows. Google falls back to the list that shipped with the
     * app when it has never been fetched, since that one is a useful shortlist on its own.
     * The others have nothing to fall back to and are simply empty until they answer.
     */
    fun webRefs(src: FontSource): List<FontRef> {
        val names = catalogueOf(src).map { it.name }
            .ifEmpty { if (src == FontSource.Google) groups.flatMap { it.second } else emptyList() }
        return names.distinct().map { FontRef(src, it, it) }
    }

    /**
     * How a web tab is split up. Once a catalogue is in, the groups are the categories that
     * provider files its own fonts under. Google keeps the curated shortlist in front,
     * because two thousand names sorted by category is still two thousand names.
     */
    fun webGroups(src: FontSource): List<Pair<String, List<String>>> {
        val catalogue = catalogueOf(src)
        if (catalogue.isEmpty()) {
            return if (src == FontSource.Google) groups else listOf("All" to emptyList())
        }
        // one provider files a font under several categories at once and hands them over as
        // "Sans, Display", which would otherwise become a chip of its own. A font in two
        // categories belongs in both chips.
        val byCategory = catalogue
            .flatMap { fam ->
                fam.category.split(',').map { it.trim() }.filter { it.isNotBlank() }
                    .ifEmpty { listOf("Other") }
                    .map { it to fam.name }
            }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()
            .map { (cat, names) -> cat to names.distinct().sorted() }
        // a hundred families does not need splitting at all
        if (catalogue.size <= 120) {
            return listOf("All" to catalogue.map { it.name }.sorted()) + byCategory
        }
        return if (src == FontSource.Google) listOf(groups.first()) + byCategory else byCategory
    }

    val builtInRefs: List<FontRef> =
        FontChoice.entries.map { FontRef(FontSource.BuiltIn, it.label, it.name) }

    /**
     * Every font file the phone itself ships. There are a couple of hundred, most of them
     * one script each, and several are Google Fonts that happen to already be here. They
     * cost nothing to use: no fetch, no network, and they work on a phone with no Play
     * services at all.
     */
    val deviceRefs: List<FontRef> by lazy {
        val best = LinkedHashMap<String, File>()
        File("/system/fonts").listFiles().orEmpty()
            .filter { it.isFile && (it.name.endsWith(".ttf") || it.name.endsWith(".otf")) }
            .sortedBy { it.name }
            .forEach { f ->
                val name = prettify(f.name)
                // one row per family: the plain file wins over Bold, Italic and the rest
                val existing = best[name]
                if (existing == null || rank(f.name) < rank(existing.name)) best[name] = f
            }
        best.map { (name, f) -> FontRef(FontSource.Device, name, f.absolutePath) }
            .sortedBy { it.name }
    }

    fun fileRefs(ctx: Context): List<FontRef> =
        dir(ctx).listFiles().orEmpty()
            .filter { it.isFile }
            .sortedBy { it.name }
            .map { FontRef(FontSource.Files, prettify(it.name), it.absolutePath) }

    fun refsFor(ctx: Context, source: FontSource): List<FontRef> = when (source) {
        FontSource.BuiltIn -> builtInRefs
        FontSource.Device -> deviceRefs
        FontSource.Files -> fileRefs(ctx)
        else -> webRefs(source)
    }

    /**
     * One row per font name across every provider, with the duplicates folded in. The same
     * family really does turn up twice: the phone ships Roboto, Dancing Script and a dozen
     * others that are also in the Google catalogue, and almost all of Fontsource is a mirror
     * of Google. The local copy wins, because it needs no fetch and works offline, and the
     * row says where else it was found so the choice is visible rather than silently made.
     *
     * Google comes before the two mirrors for the same reason: on a phone with Play services
     * it is the one route that stores nothing.
     */
    fun searchAll(ctx: Context, query: String): List<FontHit> {
        val order = listOf(
            FontSource.Files, FontSource.Device, FontSource.BuiltIn,
            FontSource.Google, FontSource.Fontsource, FontSource.Fontshare
        )
        val byName = LinkedHashMap<String, MutableList<FontRef>>()
        order.forEach { src ->
            refsFor(ctx, src).filter { it.matches(query) }.forEach {
                byName.getOrPut(it.name.lowercase()) { mutableListOf() }.add(it)
            }
        }
        return byName.values.map { FontHit(it.first(), it.drop(1).map { o -> o.source }) }
            .sortedBy { it.ref.name.lowercase() }
    }

    private fun rank(fileName: String) = when {
        !fileName.contains('-') -> 0
        fileName.contains("-Regular") -> 1
        fileName.contains("-VF") -> 2
        else -> 3
    }

    private val WEIGHTS = listOf(
        "Regular", "VF", "Bold", "Italic", "Medium", "Light", "Thin", "Black",
        "SemiBold", "ExtraBold", "ExtraLight", "BoldItalic", "Condensed"
    )

    /** "NotoSansBengaliUI-VF.ttf" reads better as "Noto Sans Bengali UI". */
    private fun prettify(fileName: String): String {
        var base = fileName.substringBeforeLast('.')
        base.substringAfterLast('-', "").let { suffix ->
            if (suffix in WEIGHTS) base = base.substringBeforeLast('-')
        }
        // an imported file keeps the timestamp the import gave it; drop that
        base = base.substringAfter('-', base).ifBlank { base }
        val out = StringBuilder()
        base.forEachIndexed { i, c ->
            if (i > 0 && c.isUpperCase() && base[i - 1].isLowerCase()) out.append(' ')
            out.append(if (c == '-' || c == '_') ' ' else c)
        }
        return out.toString().split(' ').filter { it.isNotBlank() }.joinToString(" ")
    }

    fun forget(path: String) {
        cache.remove("file:$path")
        runCatching { File(path).delete() }
    }

    private fun dir(ctx: Context) = File(ctx.filesDir, "fonts").apply { mkdirs() }

    /**
     * Copies a picked font into our own storage and returns its id. A content URI borrowed
     * from a file manager will not outlive a reboot, so the file has to be ours. It is
     * turned into a typeface once before being kept, so a file that is not a font is
     * refused at the picker instead of leaving a hole later.
     */
    fun importFile(ctx: Context, uri: Uri): String? = runCatching {
        val name = displayName(ctx, uri) ?: "font.ttf"
        val out = File(dir(ctx), "${System.currentTimeMillis()}-${name.take(40)}")
        ctx.contentResolver.openInputStream(uri)!!.use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        if (runCatching { FontFamily(Font(out)) }.isFailure) {
            out.delete()
            return null
        }
        "${FontSource.Files.tag}:${out.absolutePath}"
    }.getOrNull()

    private fun displayName(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }
    }.getOrNull()

    /**
     * The provider has no way to list what it holds, so a list has to ship with the app.
     * Grouped, because four hundred names in one column is not a choice, it is a wall.
     * Searching is not limited to these: any family name from fonts.google.com can be
     * typed in by hand and it will be fetched.
     */
    val groups: List<Pair<String, List<String>>> = listOf(
        "Popular" to listOf(
            "Inter", "Roboto", "Open Sans", "Lato", "Montserrat", "Poppins", "Nunito",
            "Work Sans", "DM Sans", "Rubik", "Quicksand", "Outfit", "Figtree", "Manrope",
            "Playfair Display", "Merriweather", "Bebas Neue", "Oswald", "Caveat", "Lobster"
        ),
        "Sans" to listOf(
            "Source Sans 3", "Raleway", "Ubuntu", "Mulish", "Karla", "Jost", "Lexend",
            "Plus Jakarta Sans", "Space Grotesk", "Sora", "Urbanist", "Josefin Sans",
            "Barlow", "Barlow Condensed", "Cabin", "Catamaran", "Exo 2", "Heebo", "Hind",
            "Libre Franklin", "Nunito Sans", "Overpass", "Oxygen", "PT Sans", "Prompt",
            "Red Hat Display", "Red Hat Text", "Titillium Web", "Asap", "Chivo",
            "Epilogue", "Archivo", "Archivo Narrow", "Albert Sans", "Be Vietnam Pro",
            "Public Sans", "Schibsted Grotesk", "Instrument Sans", "Geist", "Onest",
            "Rethink Sans", "Wix Madefor Display", "Bricolage Grotesque", "Syne",
            "Noto Sans", "IBM Plex Sans", "Fira Sans", "Assistant", "Commissioner",
            "Readex Pro", "Gabarito", "Anek Latin", "Golos Text", "Darker Grotesque",
            "Familjen Grotesk", "General Sans", "Kanit", "Mukta", "Dosis", "Signika",
            "Comfortaa", "Fredoka", "Baloo 2", "Varela Round", "M PLUS Rounded 1c",
            "Roboto Condensed", "Roboto Flex", "Saira", "Saira Condensed", "Teko",
            "Rajdhani", "Encode Sans", "Maven Pro", "Sarabun", "Tajawal", "Cairo"
        ),
        "Serif" to listOf(
            "Lora", "Bitter", "Crimson Text", "Crimson Pro", "EB Garamond",
            "Libre Baskerville", "Cormorant Garamond", "Cormorant", "Spectral",
            "Source Serif 4", "Noto Serif", "PT Serif", "Zilla Slab", "Arvo",
            "Roboto Slab", "Josefin Slab", "Bodoni Moda", "DM Serif Display",
            "DM Serif Text", "Instrument Serif", "Fraunces", "Newsreader",
            "Literata", "Petrona", "Vollkorn", "Alegreya", "Cardo", "Domine",
            "Gelasio", "Rosarivo", "Marcellus", "Cinzel", "Gloock", "Lusitana",
            "Young Serif", "Playfair Display SC", "Abril Fatface", "Yeseva One",
            "Prata", "Italiana", "Tenor Sans", "IBM Plex Serif", "Faustina"
        ),
        "Mono" to listOf(
            "JetBrains Mono", "Fira Code", "IBM Plex Mono", "Space Mono",
            "Source Code Pro", "Roboto Mono", "Inconsolata", "Courier Prime",
            "DM Mono", "Red Hat Mono", "Noto Sans Mono", "Ubuntu Mono",
            "Overpass Mono", "Azeret Mono", "Martian Mono", "Spline Sans Mono",
            "Geist Mono", "Chivo Mono", "Anonymous Pro", "Cutive Mono",
            "Nova Mono", "Share Tech Mono", "Syne Mono", "Xanh Mono"
        ),
        "Display" to listOf(
            "Anton", "Righteous", "Audiowide", "Orbitron", "Russo One",
            "Archivo Black", "Alfa Slab One", "Black Ops One", "Bungee",
            "Bungee Shade", "Monoton", "Faster One", "Wallpoet", "Megrim",
            "Michroma", "Syncopate", "Unica One", "Goldman", "Rammetto One",
            "Titan One", "Luckiest Guy", "Bowlby One SC", "Passion One",
            "Staatliches", "Shrikhand", "Chewy", "Sigmar One", "Climate Crisis",
            "Rubik Mono One", "Rubik Glitch", "Rubik Puddles", "Rubik Wet Paint",
            "Nabla", "Honk", "Sixtyfour", "Workbench", "Tourney", "Lacquer",
            "Creepster", "Eater", "Nosifer", "Metal Mania", "Pirata One",
            "UnifrakturMaguntia", "Cinzel Decorative", "Playball", "Poetsen One"
        ),
        "Handwriting" to listOf(
            "Pacifico", "Dancing Script", "Satisfy", "Shadows Into Light",
            "Permanent Marker", "Patrick Hand", "Indie Flower", "Gloria Hallelujah",
            "Amatic SC", "Kalam", "Architects Daughter", "Covered By Your Grace",
            "Rock Salt", "Reenie Beanie", "Nothing You Could Do", "Homemade Apple",
            "Great Vibes", "Allura", "Parisienne", "Sacramento", "Yellowtail",
            "Marck Script", "Cookie", "Courgette", "Kaushan Script", "Lobster Two",
            "Neucha", "Caveat Brush", "Just Another Hand", "Schoolbell", "Sue Ellen Francisco"
        ),
        "Pixel and retro" to listOf(
            "Press Start 2P", "VT323", "Silkscreen", "Pixelify Sans", "Micro 5",
            "Jersey 10", "Jersey 25", "Handjet", "DotGothic16", "Share Tech Mono",
            "Major Mono Display", "Nova Square", "Jacquard 12", "Jacquarda Bastarda 9",
            "Tiny5", "Workbench", "Sixtyfour Convergence"
        )
    )

    val popular: List<String> get() = groups.first().second

    val catalog: List<String> = groups.flatMap { it.second }.distinct()
}

/**
 * Where a font comes from. The tag is what gets stored, so it must not change.
 *
 * [web] marks the ones that are a catalogue on the internet rather than something already
 * on the phone. They all behave the same way from here: list, search, fetch, keep.
 */
enum class FontSource(val label: String, val tag: String, val web: Boolean = false) {
    Files("Your files", "f"),
    Device("On this phone", "d"),
    BuiltIn("Built in", "b"),
    Google("Google Fonts", "g", web = true),
    Fontsource("Fontsource", "s", web = true),
    Fontshare("Fontshare", "h", web = true);

    /** What the tab says under the list, once it knows how much is in there. */
    val blurb: String
        get() = when (this) {
            Google -> "Everything on fonts.google.com."
            Fontsource -> "Mirrors Google and adds a hundred-odd that are not in it."
            Fontshare -> "A hundred families from Indian Type Foundry. None are in Google."
            else -> ""
        }
}

data class FontRef(val source: FontSource, val name: String, val value: String) {
    val id: String get() = "${source.tag}:$value"
    fun matches(query: String) = query.isBlank() || name.contains(query.trim(), true)
    fun builtIn(): FontChoice =
        FontChoice.entries.firstOrNull { it.name == value } ?: FontChoice.Default
}

/** One font name, and the other providers that also had it. */
data class FontHit(val ref: FontRef, val alsoIn: List<FontSource>)

val FontChoice.family: FontFamily
    get() = when (this) {
        FontChoice.Default -> FontFamily.Default
        FontChoice.Sans -> FontFamily.SansSerif
        FontChoice.Serif -> FontFamily.Serif
        FontChoice.Mono -> FontFamily.Monospace
        FontChoice.Cursive -> FontFamily.Cursive
    }
