package com.maria.homelauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maria.homelauncher.LocalInk
import com.maria.homelauncher.PRESETS
import com.maria.homelauncher.Preset
import com.maria.homelauncher.Style

/**
 * One row per preset, each one wearing its own colours. The row is the preview: a swatch
 * of the background with the accent and the ink sitting on it, which is most of what
 * separates one of these from another at a glance.
 */
@Composable
fun ThemesPage(
    style: Style,
    undoLabel: String?,
    onApply: (Preset) -> Unit,
    onUndo: () -> Unit
) {
    Hint(
        "A theme sets colours, icon shape, how much glass there is, and a font. It leaves " +
            "your columns, dock, gestures, widgets and hidden apps alone, so nothing has " +
            "to be set up again afterwards."
    )

    // a theme overwrites a dozen colours at once, and nobody remembers what they were
    undoLabel?.let { name ->
        Spacer(Modifier.height(12.dp))
        UndoRow(name, onUndo)
    }

    Spacer(Modifier.height(12.dp))
    PRESETS.forEach { p ->
        PresetRow(p, current = style.bgColor == p.bg && style.accent == p.accent) {
            onApply(p)
        }
    }

    Hint(
        "Every one of these can be taken apart afterwards on the Colours, Icons and Font " +
            "pages. The light themes assume a light wallpaper, since app names on the home " +
            "screen sit on your own picture rather than on the theme."
    )
}

/**
 * Offered until another theme is applied. One step back, not a history: the colours before
 * the last theme are worth keeping, every look anyone ever tried is not.
 */
@Composable
private fun UndoRow(name: String, onUndo: () -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ink.copy(alpha = 0.09f))
            .clickable(onClick = onUndo)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Undo $name", color = ink, fontSize = 15.sp)
            Text(
                "Put the colours back to how they were before it",
                color = ink.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun PresetRow(p: Preset, current: Boolean, onClick: () -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ink.copy(alpha = if (current) 0.14f else 0.05f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Swatch(p)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, color = ink, fontSize = 16.sp)
            Text(
                p.blurb,
                color = ink.copy(alpha = 0.5f),
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
        if (current) {
            Text("In use", color = ink.copy(alpha = 0.5f), fontSize = 12.sp)
        }
    }
}

/** The theme's own background, accent and ink, in the icon shape it would use. */
@Composable
private fun Swatch(p: Preset) {
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape((p.folderRadius / 2).dp))
            .background(Color(p.bg))
            .border(
                1.dp,
                Color(p.ink).copy(alpha = 0.22f),
                RoundedCornerShape((p.folderRadius / 2).dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(shapeOf(p))
                .background(Color(p.accent))
        )
    }
}

@Composable
private fun shapeOf(p: Preset) = when (p.shape.radiusFraction) {
    0.5f -> CircleShape
    else -> RoundedCornerShape((26 * p.shape.radiusFraction).dp)
}
