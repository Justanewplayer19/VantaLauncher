package com.maria.homelauncher

import java.text.Normalizer

/**
 * App search that ranks instead of filtering.
 *
 * A plain `label.contains(query)` is the version everybody writes first and it is wrong in
 * two ways at once. It misses things that obviously should match -- "gmaps" is not inside
 * "Google Maps", "instgram" is one slipped finger away from an app you own, "Pokemon" is
 * not inside "Pokémon GO" as far as a string comparison is concerned -- and when it does
 * match it hands back whatever order the app list happened to be in, so typing "ch" can
 * put "Device Health Services" above "Chrome".
 *
 * So matching is done in tiers, best first, and the tier decides the order. Inside a tier
 * the app you actually open wins, then the shorter name, then alphabet. The tiers are
 * deliberately coarse: an exact name always beats a prefix, a prefix always beats a
 * fuzzy guess, and nothing fuzzy can ever climb above something solid no matter how often
 * it has been opened.
 */
object Search {

    private const val EXACT = 10
    private const val LEADS = 9
    private const val WORD = 8
    private const val INITIALS = 7
    private const val CHUNKS = 6
    private const val INSIDE = 5
    private const val TYPO = 4
    private const val LOOSE = 3
    private const val PACKAGE = 2

    /**
     * The matching apps, best first. [uses] is how many times a package has been opened
     * from the launcher, used only to break ties.
     */
    fun rank(
        apps: List<AppEntry>,
        raw: String,
        uses: (String) -> Int = { 0 }
    ): List<AppEntry> {
        val q = fold(raw).trim()
        if (q.isBlank()) return emptyList()
        val qt = tighten(q)
        if (qt.isEmpty()) return emptyList()

        return apps
            .mapNotNull { app ->
                val tier = tier(app, q, qt)
                if (tier == 0) null else app to total(tier, app, uses(app.packageName))
            }
            .sortedWith(
                compareByDescending<Pair<AppEntry, Int>> { it.second }
                    .thenBy { it.first.label.length }
                    .thenBy { fold(it.first.label) }
            )
            .map { it.first }
    }

    /**
     * Tier first, then the tie-breakers, so they can never cross a tier boundary: the
     * usage bonus tops out well below one tier's worth.
     */
    private fun total(tier: Int, app: AppEntry, uses: Int): Int {
        val opened = uses.coerceAtMost(30) * 3
        val short = (40 - app.label.length).coerceIn(0, 40)
        return tier * 1000 + opened * 10 + short
    }

    private fun tier(app: AppEntry, q: String, qt: String): Int {
        val label = fold(app.label)
        val tight = tighten(label)
        val words = words(app.label)

        if (label == q || tight == qt) return EXACT
        if (label.startsWith(q) || tight.startsWith(qt)) return LEADS
        if (words.any { it.startsWith(q) }) return WORD

        // "gm" for Google Maps, "pg" for Pokemon GO. Two letters minimum: a single letter
        // would make every app with that initial an acronym hit and bury the real prefixes.
        if (qt.length >= 2) {
            val initials = words.mapNotNull { it.firstOrNull() }.joinToString("")
            if (initials.startsWith(qt)) return INITIALS
            if (chunks(words, qt)) return CHUNKS
        }

        if (tight.contains(qt)) return INSIDE

        // one wrong, missing, extra or swapped letter, and only once the query is long
        // enough that a single edit is not most of it
        if (qt.length >= 4 && (near(tight, qt) || words.any { near(tighten(it), qt) })) {
            return TYPO
        }

        if (qt.length >= 2 && words.any { it.startsWith(qt.first()) } &&
            subsequence(tight, qt)
        ) {
            return LOOSE
        }

        // last, and only as a safety net: the thing you remember is sometimes the package
        if (qt.length >= 3 && tighten(fold(app.packageName)).contains(qt)) return PACKAGE

        return 0
    }

    /** Lower case with accents taken off, so "pokemon" finds "Pokémon". */
    private fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()

    private fun tighten(s: String): String = s.filter { it.isLetterOrDigit() }

    /**
     * The name broken into the pieces a person would say. Splits on punctuation and
     * spaces, and on a lower-to-upper step so "WhatsApp" and "YouTube" are two words.
     */
    private fun words(label: String): List<String> =
        label.split(Regex("(?<=[a-z0-9])(?=[A-Z])|[^\\p{L}\\p{N}]+"))
            .map { fold(it) }
            .filter { it.isNotBlank() }

    /**
     * The query cut up so each piece is the start of the next word: "gmaps" across
     * "Google Maps", "ytmusic" across "YouTube Music". Starts at the first word, because
     * a match that begins in the middle of the name is the one [WORD] already covers.
     */
    private fun chunks(words: List<String>, q: String): Boolean {
        var qi = 0
        for (w in words) {
            if (qi >= q.length) break
            var n = 0
            while (qi + n < q.length && n < w.length && q[qi + n] == w[n]) n++
            if (n == 0) return false
            qi += n
        }
        return qi == q.length
    }

    /** Every letter of the query somewhere in the name, in order. */
    private fun subsequence(hay: String, q: String): Boolean {
        var i = 0
        for (c in hay) {
            if (c == q[i]) i++
            if (i == q.length) return true
        }
        return false
    }

    /**
     * True when [q] is [hay], or the start of it, give or take one edit. One pass, no
     * matrix: with a budget of a single edit there are only ever three ways to spend it.
     */
    private fun near(hay: String, q: String): Boolean {
        if (hay.length < q.length - 1) return false
        // compare against only as much of the name as the query could reach, so a typo in
        // "instgram" still finds "Instagram Lite"
        val cut = hay.take((q.length + 1).coerceAtMost(hay.length))
        return edit1(cut, q) || (cut.length > q.length && edit1(cut.dropLast(1), q))
    }

    private fun edit1(a: String, b: String): Boolean {
        if (a == b) return true
        var i = 0
        while (i < a.length && i < b.length && a[i] == b[i]) i++
        if (i == a.length && i == b.length) return true

        return when {
            // a swap: the next two letters the other way round
            a.length == b.length && i + 1 < a.length &&
                a[i] == b[i + 1] && a[i + 1] == b[i] &&
                a.substring(i + 2) == b.substring(i + 2) -> true
            // one letter different
            a.length == b.length -> a.substring(i + 1) == b.substring(i + 1)
            // one letter too many in a
            a.length == b.length + 1 -> a.substring(i + 1) == b.substring(i)
            // one letter missing from a
            b.length == a.length + 1 -> b.substring(i + 1) == a.substring(i)
            else -> false
        }
    }
}
