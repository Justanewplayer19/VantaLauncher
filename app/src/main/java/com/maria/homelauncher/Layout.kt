package com.maria.homelauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * One thing sitting on the home surface. Apps and folders take a single cell, widgets
 * take as many as they were stretched to.
 */
sealed class Item {
    abstract val key: String

    data class App(val pkg: String, val activity: String) : Item() {
        override val key get() = "a:$pkg/$activity"
    }

    data class Folder(val id: String, val name: String, val apps: List<App>) : Item() {
        override val key get() = "f:$id"
    }

    data class Widget(val appWidgetId: Int) : Item() {
        override val key get() = "w:$appWidgetId"
    }
}

/**
 * An item and the cells it occupies. [page] of -1 means "not placed yet": the grid finds
 * it the first free spot that fits and writes the real coordinates back. That is how both
 * newly installed apps and layouts saved by older versions get positions without the
 * store needing to know how wide the grid is.
 */
data class Placed(
    val item: Item,
    val page: Int = -1,
    val col: Int = 0,
    val row: Int = 0,
    val w: Int = 1,
    val h: Int = 1
) {
    val placed get() = page >= 0
    fun covers(c: Int, r: Int) = c >= col && c < col + w && r >= row && r < row + h
    fun overlaps(o: Placed) = page == o.page &&
        col < o.col + o.w && o.col < col + w &&
        row < o.row + o.h && o.row < row + h
}

class LayoutStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("layout", Context.MODE_PRIVATE)

    fun load(): List<Placed> {
        val raw = sp.getString("items", null) ?: return emptyList()
        return runCatching { parse(raw) }.getOrDefault(emptyList())
    }

    fun save(items: List<Placed>) {
        sp.edit().putString("items", serialize(items)).apply()
    }

    fun clear() = sp.edit().clear().apply()

    private fun parse(raw: String): List<Placed> {
        val arr = JSONArray(raw)
        val out = mutableListOf<Placed>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val item: Item? = when (o.getString("t")) {
                "a" -> Item.App(o.getString("p"), o.getString("c"))
                "f" -> {
                    val apps = o.getJSONArray("a")
                    val list = (0 until apps.length()).map {
                        val a = apps.getJSONObject(it)
                        Item.App(a.getString("p"), a.getString("c"))
                    }
                    Item.Folder(o.getString("id"), o.getString("n"), list)
                }
                "w" -> Item.Widget(o.getInt("id"))
                else -> null
            }
            if (item == null) continue
            // rows saved before free placement carry no coordinates, so they come back
            // unplaced and keep their order, which the grid then lays out left to right
            out += Placed(
                item = item,
                page = o.optInt("pg", -1),
                col = o.optInt("cl", 0),
                row = o.optInt("rw", 0),
                w = o.optInt("w", 1).coerceAtLeast(1),
                h = o.optInt("h", 1).coerceAtLeast(1)
            )
        }
        return out
    }

    private fun serialize(items: List<Placed>): String {
        val arr = JSONArray()
        items.forEach { p ->
            val o = when (val item = p.item) {
                is Item.App -> JSONObject().put("t", "a").put("p", item.pkg).put("c", item.activity)
                is Item.Widget -> JSONObject().put("t", "w").put("id", item.appWidgetId)
                is Item.Folder -> JSONObject()
                    .put("t", "f")
                    .put("id", item.id)
                    .put("n", item.name)
                    .put("a", JSONArray().apply {
                        item.apps.forEach { put(JSONObject().put("p", it.pkg).put("c", it.activity)) }
                    })
            }
            arr.put(o.put("pg", p.page).put("cl", p.col).put("rw", p.row).put("w", p.w).put("h", p.h))
        }
        return arr.toString()
    }
}

/**
 * Reconciles a saved layout against what is actually installed: drops apps that are gone,
 * empties out folders that lost everything, and leaves positions alone. [addNew] is on
 * for the one-surface mode, where every installed app belongs on the pages; with a drawer
 * the home screen only holds what was put there by hand.
 *
 * Widgets pass through untouched. Whether their ids are still alive is the widget host's
 * question, not this one's.
 */
fun syncLayout(
    saved: List<Placed>,
    installed: List<AppEntry>,
    hidden: Set<String>,
    addNew: Boolean
): List<Placed> {
    val live = installed
        .filter { it.packageName !in hidden }
        .associateBy { "${it.packageName}/${it.activityName}" }

    val seen = mutableSetOf<String>()
    val out = mutableListOf<Placed>()

    fun keyOf(a: Item.App) = "${a.pkg}/${a.activity}"

    for (p in saved) {
        when (val item = p.item) {
            is Item.Widget -> out += p
            is Item.App -> {
                val k = keyOf(item)
                if (live.containsKey(k) && seen.add(k)) out += p
            }
            is Item.Folder -> {
                val kept = item.apps.filter { live.containsKey(keyOf(it)) && seen.add(keyOf(it)) }
                when (kept.size) {
                    0 -> Unit                                    // folder emptied out, drop it
                    1 -> out += p.copy(item = kept.first())       // a one-app folder is just the app
                    else -> out += p.copy(item = item.copy(apps = kept))
                }
            }
        }
    }

    if (addNew) {
        live.forEach { (k, entry) ->
            if (seen.add(k)) out += Placed(Item.App(entry.packageName, entry.activityName))
        }
    }

    return out
}

/** True when [p] is inside the grid and lands on cells nothing else is using. */
fun fitsHere(all: List<Placed>, p: Placed, cols: Int, rows: Int, ignore: Item? = null): Boolean {
    if (p.col < 0 || p.row < 0 || p.col + p.w > cols || p.row + p.h > rows) return false
    return all.none { it.item.key != ignore?.key && it.placed && it.overlaps(p) }
}

/**
 * Gives every unplaced item the first spot it fits in, scanning each page left to right
 * before trying the next one. Returns the same list when there was nothing to do, so a
 * caller can use identity to decide whether anything needs saving.
 */
fun packUnplaced(items: List<Placed>, cols: Int, rows: Int): List<Placed> {
    if (cols < 1 || rows < 1 || items.none { !it.placed }) return items

    val out = items.toMutableList()
    val settled = out.filter { it.placed }.toMutableList()

    for (i in out.indices) {
        val p = out[i]
        if (p.placed) continue
        val w = p.w.coerceAtMost(cols)
        val h = p.h.coerceAtMost(rows)
        var page = 0
        var spot: Placed? = null
        while (spot == null && page < 64) {
            outer@ for (r in 0..rows - h) {
                for (c in 0..cols - w) {
                    val try0 = p.copy(page = page, col = c, row = r, w = w, h = h)
                    if (fitsHere(settled, try0, cols, rows)) { spot = try0; break@outer }
                }
            }
            page++
        }
        val landed = spot ?: p.copy(page = 0, col = 0, row = 0, w = w, h = h)
        out[i] = landed
        settled += landed
    }
    return out
}

fun newFolderId(): String = "f${System.currentTimeMillis().toString(36)}"
