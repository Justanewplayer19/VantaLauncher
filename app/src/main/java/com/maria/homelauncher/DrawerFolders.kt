package com.maria.homelauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Folders inside the app drawer.
 *
 * Kept apart from the home layout on purpose. The home screen holds items at
 * coordinates; the drawer is a sorted list of everything installed, and a folder there is
 * a grouping of that list rather than a thing sitting in a cell. Sharing one store would
 * have meant one of the two pretending to be the other.
 *
 * Membership is stored as "package/activity" strings, the same key the rest of the app
 * uses, so an entry survives an icon pack change, a rename or a reinstall and simply
 * stops being shown if the app goes away.
 */
class DrawerStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("drawer", Context.MODE_PRIVATE)

    fun load(): List<DrawerFolder> {
        val raw = sp.getString("folders", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val members = o.getJSONArray("m")
                DrawerFolder(
                    id = o.getString("id"),
                    name = o.getString("n"),
                    members = (0 until members.length()).map { members.getString(it) }
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(folders: List<DrawerFolder>) {
        val arr = JSONArray()
        folders.forEach { f ->
            arr.put(
                JSONObject()
                    .put("id", f.id)
                    .put("n", f.name)
                    .put("m", JSONArray().apply { f.members.forEach { put(it) } })
            )
        }
        sp.edit().putString("folders", arr.toString()).apply()
    }
}

data class DrawerFolder(val id: String, val name: String, val members: List<String>)

/** One row of the drawer: either a folder or a loose app. */
sealed class DrawerCell {
    abstract val label: String

    data class Group(val folder: Item.Folder) : DrawerCell() {
        override val label get() = folder.name
    }

    data class Single(val app: AppEntry) : DrawerCell() {
        override val label get() = app.label
    }
}

/**
 * Turns the installed list plus the saved folders into what the drawer actually shows:
 * folders and loose apps together, sorted by name the way a drawer always is.
 *
 * A folder made by hand stays a folder even with one app in it, because someone put it
 * there on purpose. Only an empty one disappears. Members that are no longer installed
 * simply stop being listed.
 */
fun drawerCells(apps: List<AppEntry>, folders: List<DrawerFolder>): List<DrawerCell> {
    val byKey = apps.associateBy { "${it.packageName}/${it.activityName}" }
    val claimed = mutableSetOf<String>()
    val cells = mutableListOf<DrawerCell>()

    folders.forEach { f ->
        val live = f.members.filter { byKey.containsKey(it) && it !in claimed }
        if (live.isEmpty()) return@forEach
        claimed += live
        cells += DrawerCell.Group(
            Item.Folder(
                id = f.id,
                name = f.name,
                apps = live.map { key ->
                    Item.App(key.substringBefore('/'), key.substringAfter('/'))
                }
            )
        )
    }

    apps.forEach { if ("${it.packageName}/${it.activityName}" !in claimed) cells += DrawerCell.Single(it) }
    return cells.sortedBy { it.label.lowercase() }
}

fun newDrawerFolderId(): String = "d${System.currentTimeMillis().toString(36)}"

/** Puts an app in a folder, taking it out of any other one first. */
fun addToFolder(folders: List<DrawerFolder>, folderId: String, key: String): List<DrawerFolder> =
    folders.map { f ->
        when {
            f.id == folderId -> if (key in f.members) f else f.copy(members = f.members + key)
            key in f.members -> f.copy(members = f.members - key)
            else -> f
        }
    }

fun removeFromFolders(folders: List<DrawerFolder>, key: String): List<DrawerFolder> =
    folders.map { if (key in it.members) it.copy(members = it.members - key) else it }
        .filter { it.members.isNotEmpty() }
