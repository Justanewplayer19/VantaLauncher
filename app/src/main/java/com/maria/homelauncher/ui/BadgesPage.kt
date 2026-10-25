package com.maria.homelauncher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maria.homelauncher.LocalInk
import com.maria.homelauncher.NotificationWatch
import com.maria.homelauncher.Style
import kotlinx.coroutines.delay

@Composable
fun BadgesPage(style: Style, onStyle: (Style) -> Unit) {
    val ctx = LocalContext.current

    // granted in another app, so watch it rather than reading once
    var allowed by remember { mutableStateOf(NotificationWatch.allowed(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            allowed = NotificationWatch.allowed(ctx)
            delay(1000)
        }
    }

    ToggleRow("Show notification dots", style.badges) { onStyle(style.copy(badges = it)) }
    Hint("A small accent dot on any app with something waiting. Ongoing things like a download or a music player do not count.")

    Spacer(Modifier.height(22.dp))
    Label("Permission")
    Text(
        if (allowed) {
            "Notification access is on. Dots will appear as notifications arrive."
        } else {
            "Dots need notification access. The launcher only reads which app a " +
                "notification came from, never its contents, and nothing is stored or sent."
        },
        color = LocalInk.current.copy(alpha = 0.55f),
        fontSize = 12.sp,
        lineHeight = 18.sp,
        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
    )
    OutlinedButton(onClick = { NotificationWatch.openSettings(ctx) }) {
        Text(if (allowed) "Notification access" else "Turn it on")
    }
    Hint("Find \"Home notification dots\" in that list and switch it on.")
}
