package com.maria.homelauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maria.homelauncher.LocalInk
import com.maria.homelauncher.BgMode
import com.maria.homelauncher.DrawerMode
import com.maria.homelauncher.FontChoice
import com.maria.homelauncher.GearSide
import com.maria.homelauncher.IconPack
import com.maria.homelauncher.IconShape
import com.maria.homelauncher.Prefs
import com.maria.homelauncher.SearchPos
import android.net.Uri
import com.maria.homelauncher.Style
import com.maria.homelauncher.WallpaperColour
import com.maria.homelauncher.TintMode

// Each of these is one page in Settings, not one block of a long scroll.

@Composable
fun GridSection(style: Style, onStyle: (Style) -> Unit) {
    StepperRow("Columns", style.columns, 3..6) { onStyle(style.copy(columns = it)) }

    SliderRow("Icon size", style.iconDp.toFloat(), 36f, 76f) {
        onStyle(style.copy(iconDp = it.toInt()))
    }

    ToggleRow("Show app names", style.showLabels) { onStyle(style.copy(showLabels = it)) }

    if (style.showLabels) {
        SliderRow("Name size", style.labelSp.toFloat(), 8f, 16f) {
            onStyle(style.copy(labelSp = it.toInt()))
        }
    }

    Spacer(Modifier.height(18.dp))
    Label("Rows per page")
    val rowLabels = listOf("Fit") + (2..7).map { it.toString() }
    ChipRow(rowLabels, if (style.rows == 0) 0 else (style.rows - 1).coerceIn(1, 6)) {
        onStyle(style.copy(rows = if (it == 0) 0 else it + 1))
    }
    Hint("Fit uses as many rows as the screen allows. A fixed number can leave space at the bottom, or push apps onto another page.")

    Spacer(Modifier.height(8.dp))
    ToggleRow("Show page dots", style.showPageDots) { onStyle(style.copy(showPageDots = it)) }

    Spacer(Modifier.height(18.dp))
    Label("Folders")
    SliderRow("Tile shade", style.folderAlpha, 0f, 0.45f, decimals = true) {
        onStyle(style.copy(folderAlpha = it))
    }
    SliderRow("Tile corners", style.folderRadius.toFloat(), 0f, 50f) {
        onStyle(style.copy(folderRadius = it.toInt()))
    }
    Hint("How the little four-app folder tile looks on the home pages.")
}

@Composable
fun IconsSection(style: Style, onStyle: (Style) -> Unit) {
    Label("Shape")
    ChipRow(
        IconShape.entries.map { it.label },
        IconShape.entries.indexOf(style.iconShape)
    ) { onStyle(style.copy(iconShape = IconShape.entries[it])) }
    Hint("Adaptive icons are re-masked from their raw layers. Older square icons may lose their corners.")

    Spacer(Modifier.height(20.dp))
    Label("Opacity")
    SliderRow("How solid", style.iconAlpha, 0.2f, 1f, decimals = true) {
        onStyle(style.copy(iconAlpha = it))
    }
    Hint("Turn this down to let the wallpaper show through your icons. The preview above updates as you drag.")

    Spacer(Modifier.height(20.dp))
    Label("Colour")
    ChipRow(
        TintMode.entries.map { it.label },
        TintMode.entries.indexOf(style.iconTint)
    ) { onStyle(style.copy(iconTint = TintMode.entries[it])) }

    if (style.iconTint == TintMode.Mono) {
        // one picker, two targets, so the page does not become two sets of four bars
        var target by remember { mutableStateOf(0) }

        Spacer(Modifier.height(16.dp))
        ChipRow(listOf("Symbol", "Icon background"), target) { target = it }

        Spacer(Modifier.height(12.dp))
        ColorPicker(
            value = if (target == 0) style.iconColor else style.iconBg,
            onCommit = {
                onStyle(
                    if (target == 0) style.copy(iconColor = it) else style.copy(iconBg = it)
                )
            }
        )

        Hint("Uses the app's own monochrome icon where it ships one, otherwise a silhouette from the icon's shape. Busy logos lose interior detail.")
    }
}

@Composable
fun IconPackSection(style: Style, onStyle: (Style) -> Unit) {
    val ctx = LocalContext.current
    val packs = remember { IconPack.installed(ctx) }

    PickRow("System icons", "However each app drew itself", style.iconPack.isEmpty()) {
        onStyle(style.copy(iconPack = ""))
    }
    packs.forEach { p ->
        PickRow(p.label, p.pkg, style.iconPack == p.pkg) {
            onStyle(style.copy(iconPack = p.pkg))
        }
    }

    if (packs.isEmpty()) {
        Hint(
            "No icon packs found. They are ordinary apps: search the Play Store for " +
                "\"icon pack\", install one, and it will appear in this list."
        )
    } else {
        Hint(
            "A pack draws its own shape, so the shape setting does not apply to apps it " +
                "themed. Apps the pack never covered get its background and mask instead, " +
                "where it ships them. One-colour icons override a pack."
        )
    }
}

@Composable
fun ColoursSection(style: Style, onStyle: (Style) -> Unit, prefs: Prefs) {
    val ctx = LocalContext.current
    var target by remember { mutableStateOf(0) }
    ChipRow(listOf("Accent", "Background"), target) { target = it }

    Spacer(Modifier.height(12.dp))
    ColorPicker(
        value = if (target == 0) style.accent else style.bgColor,
        onCommit = {
            onStyle(if (target == 0) style.copy(accent = it) else style.copy(bgColor = it))
        }
    )

    Spacer(Modifier.height(18.dp))
    Label("Panel style")
    ChipRow(
        BgMode.entries.map { it.label },
        BgMode.entries.indexOf(style.bgMode)
    ) { onStyle(style.copy(bgMode = BgMode.entries[it])) }

    if (style.bgMode != BgMode.Solid) {
        SliderRow("Opacity", style.bgAlpha, 0.3f, 1f, decimals = true) {
            onStyle(style.copy(bgAlpha = it))
        }
    }
    if (style.bgMode == BgMode.Blur) {
        SliderRow("Blur strength", style.blurRadius.toFloat(), 8f, 100f) {
            onStyle(style.copy(blurRadius = it.toInt()))
        }
        Hint("Real blur needs Android 12+ and is switched off by battery saver. Elsewhere this behaves like Dim.")
    }

    Spacer(Modifier.height(20.dp))
    Label("From your wallpaper")
    ToggleRow("Follow my wallpaper", style.autoAccent) {
        onStyle(style.copy(autoAccent = it))
    }
    Hint("Every time the wallpaper changes the accent is taken from it, so rotation re-themes the launcher as it goes.")

    var note by remember { mutableStateOf<String?>(null) }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = {
        val last = prefs.lastWallpaper.ifBlank { prefs.wallpapers.firstOrNull().orEmpty() }
        val picked = last.takeIf { it.isNotBlank() }
            ?.let { WallpaperColour.from(ctx, Uri.parse(it)) }
        note = if (picked == null) {
            "set a wallpaper through this launcher first"
        } else {
            onStyle(style.copy(accent = picked))
            "accent taken from your wallpaper"
        }
    }) { Text("Take colours now") }
    note?.let {
        Text(
            it,
            color = LocalInk.current.copy(alpha = 0.6f),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
fun DockSection(style: Style, onStyle: (Style) -> Unit) {
    StepperRow("Apps in the dock", style.dockMax, 3..7) {
        onStyle(style.copy(dockMax = it))
    }
    Hint("Lowering this hides the extra ones; they come back if you raise it again.")

    Spacer(Modifier.height(10.dp))
    ToggleRow("Background behind the dock", style.dockBg) {
        onStyle(style.copy(dockBg = it))
    }
    if (style.dockBg) {
        SliderRow("Corners", style.dockRadius.toFloat(), 0f, 40f) {
            onStyle(style.copy(dockRadius = it.toInt()))
        }
    }
    Hint("The dock only exists in the drawer layout. On one surface every app is already on the pages.")
}

@Composable
fun ClockSection(style: Style, onStyle: (Style) -> Unit) {
    ToggleRow("Show the clock", style.showClock) { onStyle(style.copy(showClock = it)) }
    ToggleRow("24 hour time", style.clock24) { onStyle(style.copy(clock24 = it)) }
    ToggleRow("Show the date", style.showDate) { onStyle(style.copy(showDate = it)) }
    Hint("On one surface these two become the single line above your apps, and turning both off removes it.")
}

@Composable
fun TextSection(style: Style, onStyle: (Style) -> Unit) {
    SliderRow("Clock size", style.clockSp.toFloat(), 28f, 110f) {
        onStyle(style.copy(clockSp = it.toInt()))
    }
    SliderRow("Date size", style.dateSp.toFloat(), 10f, 24f) {
        onStyle(style.copy(dateSp = it.toInt()))
    }
    SliderRow("Search size", style.searchSp.toFloat(), 11f, 22f) {
        onStyle(style.copy(searchSp = it.toInt()))
    }
}

@Composable
fun DrawerSection(style: Style, onStyle: (Style) -> Unit) {
    Label("Browse style")
    ChipRow(
        DrawerMode.entries.map { it.label },
        DrawerMode.entries.indexOf(style.drawerMode)
    ) { onStyle(style.copy(drawerMode = DrawerMode.entries[it])) }
    Hint("Scroll is one long vertical list. Pages splits apps into screens you swipe left and right.")

    Spacer(Modifier.height(18.dp))
    Label("Search bar")
    ChipRow(
        SearchPos.entries.map { it.label },
        SearchPos.entries.indexOf(style.searchPos)
    ) { onStyle(style.copy(searchPos = SearchPos.entries[it])) }
    Hint("The pill stays centred either way. Bottom lifts above the keyboard when you type.")

    Spacer(Modifier.height(18.dp))
    Label("Settings button")
    ChipRow(
        GearSide.entries.map { it.label },
        GearSide.entries.indexOf(style.gearSide)
    ) { onStyle(style.copy(gearSide = GearSide.entries[it])) }
}

// --- shared controls ---

@Composable
internal fun PickRow(title: String, sub: String, selected: Boolean, onPick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) LocalInk.current.copy(alpha = 0.16f) else LocalInk.current.copy(alpha = 0.05f)
            )
            .clickable(onClick = onPick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = LocalInk.current, fontSize = 15.sp)
            Text(sub, color = LocalInk.current.copy(alpha = 0.45f), fontSize = 11.sp)
        }
        if (selected) Text("Using", color = LocalInk.current.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}

@Composable
internal fun Label(text: String) {
    Text(text, color = LocalInk.current.copy(alpha = 0.7f), fontSize = 13.sp)
}

@Composable
internal fun Hint(text: String) {
    Text(
        text,
        color = LocalInk.current.copy(alpha = 0.45f),
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
internal fun ToggleRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = LocalInk.current, fontSize = 15.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun StepperRow(text: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = LocalInk.current, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            range.forEach { n ->
                val on = n == value
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (on) LocalInk.current.copy(alpha = 0.22f)
                            else LocalInk.current.copy(alpha = 0.06f)
                        )
                        .clickable { onChange(n) },
                    contentAlignment = Alignment.Center
                ) { Text("$n", color = LocalInk.current, fontSize = 14.sp) }
            }
        }
    }
}

@Composable
internal fun SliderRow(
    text: String,
    value: Float,
    min: Float,
    max: Float,
    decimals: Boolean = false,
    onChange: (Float) -> Unit
) {
    Column(Modifier.padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text, color = LocalInk.current.copy(alpha = 0.85f), fontSize = 14.sp)
            Text(
                if (decimals) String.format(java.util.Locale.US, "%.2f", value) else value.toInt().toString(),
                color = LocalInk.current.copy(alpha = 0.55f),
                fontSize = 13.sp
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = min..max)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChipRow(labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { i, l ->
            FilterChip(selected = i == selected, onClick = { onPick(i) }, label = { Text(l) })
        }
    }
}

