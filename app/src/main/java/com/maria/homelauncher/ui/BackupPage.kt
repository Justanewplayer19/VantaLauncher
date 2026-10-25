package com.maria.homelauncher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Save the setup, or put one back.
 *
 * Deliberately two buttons and no cleverness. There is no automatic backup, no cloud and no
 * schedule, because all three would need somewhere to put a file and a decision about when,
 * and a file the user chose the location of is something they can actually find again.
 */
@Composable
fun BackupPage(onExport: () -> Unit, onImport: () -> Unit) {
    Hint(
        "Writes one file holding your colours, fonts, wallpapers, renames, replaced icons, " +
            "folders, dock, hidden apps, gestures and grid. Your own font files and icon " +
            "images go in it too, so it is everything, not just a list of settings."
    )

    Spacer(Modifier.height(10.dp))
    OutlinedButton(onClick = onExport) { Text("Save setup to a file") }

    Spacer(Modifier.height(26.dp))
    Hint(
        "Loading a backup replaces everything you have set now. It is not merged, and " +
            "there is no undo for it, so save the current setup first if you might want it."
    )

    Spacer(Modifier.height(10.dp))
    OutlinedButton(onClick = onImport) { Text("Load a backup") }

    Spacer(Modifier.height(26.dp))
    Hint(
        "Moving to another phone works, with two exceptions. Widgets are left out, because " +
            "a widget is a slot handed out by the phone it was placed on. An icon pack is " +
            "another app, so the choice comes back but needs that pack installed."
    )
}
