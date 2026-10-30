package com.maria.homelauncher.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.maria.homelauncher.AppEntry
import com.maria.homelauncher.Item

/**
 * One app being carried off the drawer and onto the home screen.
 *
 * The drawer and the grid are two different composables with a panel between them, so a
 * drag that starts in one and ends in the other needs something that outlives both ends
 * of the gesture. This is it: the drawer writes where the finger is, the grid reads it
 * and shows where the icon would land, and whoever lets go decides what happens.
 *
 * Positions are **window** coordinates, because that is the only frame of reference the
 * two surfaces share. The grid converts them against its own position on screen.
 */
@Stable
class CarryOut {

    /** What is in the air, or null when nothing is. */
    var app by mutableStateOf<AppEntry?>(null)

    /** Where the finger is, in window coordinates. */
    var at by mutableStateOf(Offset.Zero)

    /**
     * Filled in by the grid while it is on screen, and cleared when it leaves. The drawer
     * never calls them directly; it moves the finger and the grid keeps up.
     */
    var aim: ((Offset) -> Unit)? = null
    var put: ((Item.App, Offset) -> Unit)? = null
    var drop: (() -> Unit)? = null

    fun begin(a: AppEntry, where: Offset) {
        app = a
        at = where
        aim?.invoke(where)
    }

    fun moveTo(where: Offset) {
        if (app == null) return
        at = where
        aim?.invoke(where)
    }

    /** Lets go without placing anything. */
    fun forget() {
        app = null
        drop?.invoke()
    }
}
