package com.maria.homelauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The home surface is one ordered list. Pages are derived by chunking it to whatever fits
 * on screen, so there is no separate page bookkeeping to keep in sync and changing the
 * column count or icon size just reflows.
 */
sealed class Item {
    abstract val key: String

    data class App(val pkg: String, val activity: String) : Item() {
        override val key get() = "a:$pkg/$activity"
    }

    data class Folder(val id: String, val name: String, val apps: List<App>) : Item() {
        override val key get() = "f:$id"
    }
}

class LayoutStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("layout", Context.MODE_PRIVATE)

    fun load(): List<Item> {
        val raw = sp.getString("items", null) ?: return emptyList()
        return runCatching { parse(raw) }.getOrDefault(emptyList())
    }

    fun save(items: List<Item>) {
        sp.edit().putString("items", serialize(items)).apply()
    }

    fun clear() = sp.edit().clear().apply()

    private fun parse(raw: String): List<Item> {
        val arr = JSONArray(raw)
        val out = mutableListOf<Item>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            when (o.getString("t")) {
                "a" -> out += Item.App(o.getString("p"), o.getString("c"))
                "f" -> {
                    val apps = o.getJSONArray("a")
                    val list = (0 until apps.length()).map {
                        val a = apps.getJSONObject(it)
                        Item.App(a.getString("p"), a.getString("c"))
                    }
                    out += Item.Folder(o.getString("id"), o.getString("n"), list)
                }
            }
        }
        return out
    }

    private fun serialize(items: List<Item>): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                when (item) {
                    is Item.App -> JSONObject().put("t", "a").put("p", item.pkg).put("c", item.activity)
                    is Item.Folder -> JSONObject()
                        .put("t", "f")
                        .put("id", item.id)
                        .put("n", item.name)
                        .put("a", JSONArray().apply {
                            item.apps.forEach { put(JSONObject().put("p", it.pkg).put("c", it.activity)) }
                        })
                }
            )
        }
        return arr.toString()
    }
}

/**
 * Reconciles a saved layout against what is actually installed: drops apps that are gone,
 * appends ones that are new, and removes folders left empty. Order of surviving items and
 * folder membership are preserved.
 */
fun syncLayout(saved: List<Item>, installed: List<AppEntry>, hidden: Set<String>): List<Item> {
    val live = installed
        .filter { it.packageName !in hidden }
        .associateBy { "${it.packageName}/${it.activityName}" }

    val seen = mutableSetOf<String>()
    val out = mutableListOf<Item>()

    fun keyOf(a: Item.App) = "${a.pkg}/${a.activity}"

    for (item in saved) {
        when (item) {
            is Item.App -> {
                val k = keyOf(item)
                if (live.containsKey(k) && seen.add(k)) out += item
            }
            is Item.Folder -> {
                val kept = item.apps.filter { live.containsKey(keyOf(it)) && seen.add(keyOf(it)) }
                when (kept.size) {
                    0 -> Unit                       // folder emptied out, drop it
                    1 -> out += kept.first()        // a one-app folder is just the app
                    else -> out += item.copy(apps = kept)
                }
            }
        }
    }

    // anything installed that the layout has never seen goes on the end
    live.forEach { (k, entry) ->
        if (seen.add(k)) out += Item.App(entry.packageName, entry.activityName)
    }

    return out
}

fun newFolderId(): String = "f${System.currentTimeMillis().toString(36)}"
