package com.maria.homelauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maria.homelauncher.LocalInk
import com.maria.homelauncher.Prefs
import com.maria.homelauncher.Style
import com.maria.homelauncher.Updater

/**
 * A downloaded update, mentioned once on the home screen.
 *
 * Android 13 made posting a notification a permission to be asked for, and asking for one
 * on first run to say "there might be an update one day" is a poor trade. This launcher is
 * the home screen, so it already has somewhere to put a sentence. Dismissing it sets the
 * version aside rather than hiding the row forever, so the next version says so again.
 */
@Composable
fun UpdateBanner(prefs: Prefs, style: Style, onOpenUpdates: () -> Unit) {
    val ctx = LocalContext.current
    var version by remember { mutableStateOf(prefs.readyVersion) }

    val show = version.isNotBlank() &&
        version != prefs.dismissedVersion &&
        Updater.newer(version, Updater.installedVersion(ctx))
    if (!show) return

    val accent = Color(style.accent)
    val ink = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            // clear of the dock and the "swipe up" hint, both of which live at the bottom
            .padding(start = 16.dp, end = 16.dp, bottom = 104.dp)
            .clip(RoundedCornerShape(18.dp))
            // the panel's own background rather than a tinted sheet: this sits on the
            // user's wallpaper, which can be any colour, and a translucent accent on a
            // light photo is unreadable
            .background(Color(style.bgColor).copy(alpha = 0.94f))
            .clickable(onClick = onOpenUpdates)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Version $version is ready", color = accent, fontSize = 14.sp)
            Text(
                "Downloaded already. Tap to install.",
                color = ink.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        }
        Text(
            "Later",
            color = ink.copy(alpha = 0.75f),
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    prefs.dismissedVersion = version
                    version = ""
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
