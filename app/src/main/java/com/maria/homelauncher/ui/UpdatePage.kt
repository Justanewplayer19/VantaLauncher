package com.maria.homelauncher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import com.maria.homelauncher.LocalInk
import com.maria.homelauncher.Prefs
import com.maria.homelauncher.Updater
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What version this is, whether a newer one is waiting, and the switch for looking.
 *
 * The install button is deliberately a button. Everything up to it happens on its own, but
 * Android will not let an ordinary app install anything without the user seeing its own
 * dialog, so pretending otherwise would just make the last step feel broken.
 */
@Composable
fun UpdatePage(prefs: Prefs) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ink = LocalInk.current

    var auto by remember { mutableStateOf(prefs.autoUpdate) }
    var ready by remember { mutableStateOf(prefs.readyVersion) }
    var notes by remember { mutableStateOf(prefs.readyNotes) }
    var checking by remember { mutableStateOf(false) }
    var said by remember { mutableStateOf<String?>(null) }
    var allowed by remember { mutableStateOf(Updater.canInstall(ctx)) }

    // granting the install permission happens on a system screen, so the answer only
    // changes while this page is in the background. Without this it still says "not
    // allowed" after the user has just allowed it.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val watch = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = Updater.canInstall(ctx)
        }
        owner.lifecycle.addObserver(watch)
        onDispose { owner.lifecycle.removeObserver(watch) }
    }

    Label("This version")
    Text(
        Updater.installedVersion(ctx),
        color = ink,
        fontSize = 20.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
    )

    if (!Updater.configured) {
        Hint(
            "No update source is set in this build, so nothing is checked. A build from " +
                "the repository knows where its own releases live."
        )
        return
    }

    if (ready.isNotBlank() && Updater.newer(ready, Updater.installedVersion(ctx))) {
        Label("Ready to install")
        Text(ready, color = ink, fontSize = 18.sp, modifier = Modifier.padding(top = 4.dp))
        if (notes.isNotBlank()) {
            Text(
                notes,
                color = ink.copy(alpha = 0.6f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Spacer(Modifier.height(10.dp))

        if (allowed) {
            OutlinedButton(onClick = {
                Updater.fileFor(ctx, ready).takeIf { it.isFile }?.let { Updater.install(ctx, it) }
            }) { Text("Install $ready") }
            Hint("Android asks to confirm. It is already downloaded, so this is quick.")
        } else {
            OutlinedButton(onClick = { ctx.startActivity(Updater.permissionIntent(ctx)) }) {
                Text("Allow installing updates")
            }
            Hint(
                "Android needs permission for this app to install another, granted once on " +
                    "its own screen. The update is already downloaded and waiting."
            )
        }
        Spacer(Modifier.height(26.dp))
    }

    ToggleRow("Check for updates automatically", auto) {
        auto = it
        prefs.autoUpdate = it
        Updater.schedule(ctx)
    }
    Hint("Once a day, on wifi only, and only when the battery is not low.")

    Spacer(Modifier.height(16.dp))
    OutlinedButton(
        enabled = !checking,
        onClick = {
            checking = true
            said = null
            scope.launch {
                val found = Updater.checkAndFetch(ctx)
                ready = prefs.readyVersion
                notes = prefs.readyNotes
                said = when {
                    found != null -> "$found downloaded and ready"
                    else -> "no newer version"
                }
                checking = false
            }
        }
    ) { Text(if (checking) "Checking" else "Check now") }

    said?.let {
        Text(
            it,
            color = ink.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 10.dp)
        )
    }

    val last = prefs.lastUpdateCheck
    if (last > 0) {
        Hint(
            "Last checked " +
                SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(last)) + "."
        )
    }

    Spacer(Modifier.height(20.dp))
    Hint(
        "Updates come from the project's own releases page. An update has to be signed " +
            "with the same key as the copy you have, so a build from somewhere else cannot " +
            "replace this one."
    )
}
