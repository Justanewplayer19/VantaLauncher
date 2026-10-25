package com.maria.homelauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maria.homelauncher.LocalInk
import com.maria.homelauncher.AppEntry
import com.maria.homelauncher.Gestures
import com.maria.homelauncher.LauncherAccess
import com.maria.homelauncher.Prefs
import kotlinx.coroutines.delay

/**
 * One row per gesture. Tapping a row opens its choices in place rather than pushing
 * another page, since each one is a single pick.
 */
@Composable
fun GesturesPage(prefs: Prefs, apps: List<AppEntry>, single: Boolean) {
    val ctx = LocalContext.current

    // prefs are not observable, so writes bump this to pull the new values back in
    var version by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf<String?>(null) }

    val nameOf: (String) -> String? = { comp ->
        apps.firstOrNull { "${it.packageName}/${it.activityName}" == comp }?.label
    }

    @Composable
    fun row(id: String, title: String, value: String, onPick: (String) -> Unit) {
        GestureRow(
            title = title,
            current = Gestures.label(value, nameOf),
            expanded = open == id,
            onToggle = { open = if (open == id) null else id },
            apps = apps,
            selected = value,
            onPick = { onPick(it); version++; open = null }
        )
    }

    key(version) {
        row("down", "Swipe down", prefs.gSwipeDown) { prefs.gSwipeDown = it }
        row("double", "Double tap", prefs.gDoubleTap) { prefs.gDoubleTap = it }

        if (single) {
            Hint(
                "Sideways swipes belong to your home pages in this mode, so they cannot " +
                    "be reassigned. Switch to the drawer layout to use them."
            )
        } else {
            row("left", "Swipe left", prefs.gSwipeLeft) { prefs.gSwipeLeft = it }
            row("right", "Swipe right", prefs.gSwipeRight) { prefs.gSwipeRight = it }
        }
    }

    Spacer(Modifier.height(22.dp))
    Label("Permission")

    // the user turns this on in another app, so watch it while this page is up rather
    // than reading it once and showing a stale answer when they come back
    var connected by remember { mutableStateOf(LauncherAccess.connected) }
    LaunchedEffect(Unit) {
        while (true) {
            connected = LauncherAccess.connected
            delay(1000)
        }
    }
    Text(
        if (connected) {
            "The gesture service is on. Notifications, quick settings, recents and lock " +
                "will work."
        } else {
            "Notifications, quick settings, recents and lock need Android's accessibility " +
                "permission. It is the only way an app that is not the system can do those. " +
                "Nothing on your screen is read; the service exists only to perform those " +
                "four actions."
        },
        color = LocalInk.current.copy(alpha = 0.55f),
        fontSize = 12.sp,
        lineHeight = 18.sp,
        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
    )
    OutlinedButton(onClick = { Gestures.openAccessibilitySettings(ctx) }) {
        Text(if (connected) "Accessibility settings" else "Turn it on")
    }
    Hint("Find \"Home gestures\" in the list, usually under Installed apps or Downloaded apps.")
}

@Composable
private fun GestureRow(
    title: String,
    current: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    apps: List<AppEntry>,
    selected: String,
    onPick: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LocalInk.current.copy(alpha = 0.06f))
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = LocalInk.current, fontSize = 15.sp)
                Text(current, color = LocalInk.current.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            Text(
                if (expanded) "Done" else "Change",
                color = LocalInk.current.copy(alpha = 0.7f),
                fontSize = 13.sp
            )
        }

        if (!expanded) return@Column

        Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
            Gestures.simple.forEach { (id, label) ->
                Choice(label, selected == id) { onPick(id) }
            }

            Spacer(Modifier.height(10.dp))
            Text("Open an app", color = LocalInk.current.copy(alpha = 0.7f), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))

            apps.forEach { app ->
                val id = "${Gestures.APP_PREFIX}${app.packageName}/${app.activityName}"
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected == id) LocalInk.current.copy(alpha = 0.14f)
                            else Color.Transparent
                        )
                        .clickable { onPick(id) }
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        bitmap = app.icon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        app.label,
                        color = LocalInk.current.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) LocalInk.current.copy(alpha = 0.16f) else LocalInk.current.copy(alpha = 0.04f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, color = LocalInk.current, fontSize = 14.sp)
    }
}
