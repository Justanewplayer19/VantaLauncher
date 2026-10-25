package com.maria.homelauncher.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import com.maria.homelauncher.AppEntry
import com.maria.homelauncher.LocalStyle
import com.maria.homelauncher.LauncherWidgets
import com.maria.homelauncher.Prefs
import com.maria.homelauncher.Fonts
import com.maria.homelauncher.Backup
import com.maria.homelauncher.StyleStore
import com.maria.homelauncher.Style
import com.maria.homelauncher.TARGET_BOTH
import com.maria.homelauncher.TARGET_HOME
import com.maria.homelauncher.TARGET_LOCK
import com.maria.homelauncher.Wallpaper
import kotlinx.coroutines.launch

private enum class Page(val title: String, val blurb: String) {
    Root("Settings", ""),
    ThemesPg("Themes", "Whole looks, in one tap"),
    HomeLayout("Home layout", "Drawer or one surface"),
    WidgetsPg("Widgets", "Place widgets on your home screen"),
    GesturesPg("Gestures", "Swipe and double tap actions"),
    Grid("Grid", "Columns, icon size, names"),
    Icons("Icons", "Shape and colour"),
    IconPackPg("Icon pack", "Use a downloaded icon theme"),
    Colours("Colours", "Accent, background, panel"),
    DockPg("Dock", "Favourites row at the bottom"),
    ClockPg("Clock", "Format, date, visibility"),
    TextPage("Text", "Sizes for the clock and search"),
    FontPg("Font", "Six providers, searched together"),
    DrawerPage("Drawer", "Browsing, search bar, button side"),
    WallpaperPg("Wallpaper", "Rotate a folder, or set one"),
    Badges("Notification dots", "A dot on apps with something waiting"),
    Hidden("Hidden apps", "Only found by searching"),
    BackupPg("Backup", "Save your whole setup to a file"),
    About("Intro and reset", "Replay the tour, reset the look")
}

private val intervals = listOf(
    15 to "15m", 30 to "30m", 60 to "1h", 180 to "3h", 360 to "6h", 720 to "12h", 1440 to "24h"
)

@Composable
fun SettingsScreen(
    prefs: Prefs,
    apps: List<AppEntry>,
    widgets: LauncherWidgets,
    style: Style,
    single: Boolean,
    onSingle: (Boolean) -> Unit,
    onStyle: (Style) -> Unit,
    onResetStyle: () -> Unit,
    onReplayIntro: () -> Unit,
    styleStore: StyleStore,
    onImported: () -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var page by remember { mutableStateOf(Page.Root) }
    var pics by remember { mutableStateOf(prefs.wallpapers) }
    var hidden by remember { mutableStateOf(prefs.hidden) }
    var note by remember { mutableStateOf<String?>(null) }

    // inside a sub-page, back returns to the list rather than leaving settings
    BackHandler(enabled = page != Page.Root) { page = Page.Root; note = null }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val kept = uris.mapNotNull { u ->
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    u, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                u.toString()
            }.getOrNull()
        }
        pics = (pics + kept).distinct()
        prefs.wallpapers = pics
        Wallpaper.schedule(ctx)
        note = "${kept.size} added"
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { tree ->
        if (tree == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                tree, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }.isSuccess
        if (!ok) {
            note = "could not keep access to that folder"
            return@rememberLauncherForActivityResult
        }
        val found = Wallpaper.imagesIn(ctx, tree)
        if (found.isEmpty()) {
            note = "no images directly in that folder"
        } else {
            pics = (pics + found).distinct()
            prefs.wallpapers = pics
            Wallpaper.schedule(ctx)
            note = "${found.size} added from folder"
        }
    }

    // a font is not a well-known mime type on every device, so the filter is wide and the
    // file is validated after it is copied instead
    val fontTypes = remember {
        arrayOf("font/*", "application/x-font-ttf", "application/octet-stream", "*/*")
    }
    val fontPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val id = Fonts.importFile(ctx, uri)
        if (id == null) {
            note = "that file is not a font the phone can read"
        } else {
            onStyle(style.copy(fontId = id))
            note = "font added and set"
        }
    }

    val backupSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        note = if (Backup.write(ctx, uri)) "setup saved to that file"
        else "could not write to that file"
    }

    // a backup written by a file manager or sent through a chat app can come back with
    // almost any mime type, so the filter is wide and the contents are checked instead
    val backupOpener = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = Backup.read(ctx, uri)
        // short on purpose: a toast gets two lines and silently cuts the rest, and the
        // long version of this is already on the page itself
        val message = when {
            !result.ok -> result.message
            result.widgetsDropped -> "Setup restored. Widgets need placing again."
            else -> "Setup restored."
        }
        if (!result.ok) {
            note = message
            return@rememberLauncherForActivityResult
        }
        // a restore starts the activity again, which takes this screen and its note with
        // it, so the one thing the user needs to read has to outlive the restart
        android.widget.Toast.makeText(ctx, message, android.widget.Toast.LENGTH_LONG).show()
        onImported()
    }

    val singlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        scope.launch {
            note = if (Wallpaper.setOne(ctx, uri, prefs.rotateTarget)) "wallpaper set"
            else "could not read that image"
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(style.bgColor))
            .systemBarsPadding()
            .displayCutoutPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (page == Page.Root) onClose() else { page = Page.Root; note = null }
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = LocalInk.current)
            }
            Text(page.title, color = LocalInk.current, fontSize = 20.sp)
        }

        // one scroll state for every page, so stepping into a page and back would
        // otherwise leave the list scrolled to where that page was
        val scroll = rememberScrollState()
        LaunchedEffect(page) { scroll.scrollTo(0) }

        Column(
            Modifier
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
        ) {
            // the preview belongs with the visual pages, not on every screen
            val wantsPreview = page in setOf(
                Page.Grid, Page.Icons, Page.IconPackPg, Page.Colours,
                Page.ThemesPg, Page.DockPg, Page.ClockPg, Page.TextPage, Page.FontPg,
                Page.DrawerPage
            )
            if (wantsPreview) {
                StylePreview(
                    style = style,
                    apps = apps,
                    wallpaperUris = pics,
                    single = single,
                    modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                )
            }

            when (page) {
                Page.Root -> RootList { page = it }

                Page.HomeLayout -> {
                    ToggleRow("One surface (no app drawer)", single, onSingle)
                    Hint(
                        "All apps sit on the home pages. Long-press to drag, drop one icon " +
                            "on another to make a folder, swipe up to search. Off, you get a " +
                            "classic home screen plus a drawer."
                    )
                }

                Page.ThemesPg -> {
                    // recomputed on every recomposition of this page, so applying a theme
                    // makes the undo row appear and using it makes the row go away
                    var undoable by remember { mutableStateOf(styleStore.undoLabel()) }
                    ThemesPage(
                        style = style,
                        undoLabel = undoable,
                        onApply = { preset ->
                            styleStore.snapshot(preset.name)
                            onStyle(preset.applyTo(style))
                            undoable = preset.name
                            note = null
                        },
                        onUndo = {
                            styleStore.undo()?.let(onStyle)
                            undoable = null
                            note = "put back"
                        }
                    )
                }

                Page.WidgetsPg -> WidgetsPage(widgets)

                Page.GesturesPg -> GesturesPage(prefs, apps, single)

                Page.Grid -> GridSection(style, onStyle)
                Page.Icons -> IconsSection(style, onStyle)
                Page.IconPackPg -> IconPackSection(style, onStyle)
                Page.Colours -> ColoursSection(style, onStyle, prefs)
                Page.DockPg -> DockSection(style, onStyle)
                Page.ClockPg -> ClockSection(style, onStyle)
                Page.TextPage -> TextSection(style, onStyle)

                Page.FontPg -> FontPage(
                    style = style,
                    onStyle = onStyle,
                    onPickFile = { fontPicker.launch(fontTypes) },
                    googleOk = remember { Fonts.googleAvailable(ctx) }
                )
                Page.DrawerPage -> DrawerSection(style, onStyle)

                Page.WallpaperPg -> WallpaperPage(
                    prefs = prefs,
                    pics = pics,
                    onAddImages = { picker.launch(arrayOf("image/*")) },
                    onAddFolder = { folderPicker.launch(null) },
                    onPickSingle = { singlePicker.launch(arrayOf("image/*")) },
                    onClear = {
                        pics = emptyList()
                        prefs.wallpapers = pics
                        Wallpaper.schedule(ctx)
                        note = "cleared"
                    },
                    onChangeNow = {
                        scope.launch {
                            note = if (Wallpaper.advance(ctx)) "wallpaper changed"
                            else "could not read any of those images"
                        }
                    }
                )

                Page.Badges -> BadgesPage(style, onStyle)

                Page.Hidden -> HiddenPage(apps, hidden) {
                    prefs.toggleHidden(it)
                    hidden = prefs.hidden
                }

                Page.BackupPg -> BackupPage(
                    onExport = { backupSaver.launch(Backup.suggestedName()) },
                    onImport = { backupOpener.launch(arrayOf("application/json", "text/*", "*/*")) }
                )

                Page.About -> {
                    Text(
                        "Start the welcome tour again from the beginning.",
                        color = LocalInk.current.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedButton(onClick = onReplayIntro) { Text("Replay intro") }

                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Put every look setting back to how it started. Your apps, folders " +
                            "and wallpapers are untouched.",
                        color = LocalInk.current.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedButton(onClick = {
                        onResetStyle()
                        note = "appearance reset"
                    }) { Text("Reset appearance") }
                }
            }

            note?.let {
                Text(
                    it,
                    color = LocalInk.current.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun RootList(onPick: (Page) -> Unit) {
    Spacer(Modifier.height(4.dp))
    Page.entries.filter { it != Page.Root }.forEach { p ->
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onPick(p) }
                .padding(vertical = 15.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(p.title, color = LocalInk.current, fontSize = 16.sp)
                Text(p.blurb, color = LocalInk.current.copy(alpha = 0.45f), fontSize = 12.sp)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = LocalInk.current.copy(alpha = 0.35f)
            )
        }
    }
}

@Composable
private fun WallpaperPage(
    prefs: Prefs,
    pics: List<String>,
    onAddImages: () -> Unit,
    onAddFolder: () -> Unit,
    onPickSingle: () -> Unit,
    onClear: () -> Unit,
    onChangeNow: () -> Unit
) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(prefs.rotateOn) }
    var minutes by remember { mutableStateOf(prefs.rotateMinutes) }
    var target by remember { mutableStateOf(prefs.rotateTarget) }
    var shuffle by remember { mutableStateOf(prefs.shuffle) }

    Label("Just one")
    Text(
        "Set a single image and leave rotation off.",
        color = LocalInk.current.copy(alpha = 0.45f),
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
    )
    Button(onClick = onPickSingle) { Text("Choose a wallpaper") }

    Spacer(Modifier.height(26.dp))
    Label("Rotate through several")
    Spacer(Modifier.height(6.dp))
    ToggleRow("Rotate automatically", on) {
        on = it
        prefs.rotateOn = it
        Wallpaper.schedule(ctx)
    }

    Text(
        if (pics.isEmpty()) "No images chosen" else "${pics.size} image(s) chosen",
        color = LocalInk.current.copy(alpha = 0.65f),
        fontSize = 13.sp,
        modifier = Modifier.padding(vertical = 6.dp)
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onAddImages) { Text("Add images") }
        OutlinedButton(onClick = onAddFolder) { Text("Add folder") }
        if (pics.isNotEmpty()) OutlinedButton(onClick = onClear) { Text("Clear") }
    }
    Hint("Pick single images, or a whole folder from anywhere on the device. A folder adds the images directly inside it, not sub-folders.")

    Spacer(Modifier.height(16.dp))
    Label("Every")
    ChipRow(intervals.map { it.second }, intervals.indexOfFirst { it.first == minutes }) { i ->
        minutes = intervals[i].first
        prefs.rotateMinutes = minutes
        Wallpaper.schedule(ctx)
    }
    if (minutes == 15) {
        Hint("15 minutes is Android's floor for background work. Anything shorter needs a foreground service.")
    }

    Spacer(Modifier.height(16.dp))
    Label("Apply to")
    val targets = listOf(TARGET_HOME to "Home", TARGET_LOCK to "Lock", TARGET_BOTH to "Both")
    ChipRow(targets.map { it.second }, targets.indexOfFirst { it.first == target }) { i ->
        target = targets[i].first
        prefs.rotateTarget = target
    }
    Hint("Some phone skins block apps from setting the lock screen. Stock Android is fine.")

    Spacer(Modifier.height(12.dp))
    ToggleRow("Shuffle", shuffle) {
        shuffle = it
        prefs.shuffle = it
    }

    Spacer(Modifier.height(12.dp))
    Button(enabled = pics.isNotEmpty(), onClick = onChangeNow) { Text("Change now") }
}

@Composable
private fun HiddenPage(apps: List<AppEntry>, hidden: Set<String>, onUnhide: (String) -> Unit) {
    val style = LocalStyle.current
    Text(
        "Hidden apps stay out of the grid and only show up when you type in search.",
        color = LocalInk.current.copy(alpha = 0.55f),
        fontSize = 12.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )

    if (hidden.isEmpty()) {
        Text(
            "Nothing hidden. Long-press an app in the drawer to hide it.",
            color = LocalInk.current.copy(alpha = 0.45f),
            fontSize = 12.sp
        )
        return
    }

    val byPkg = apps.associateBy { it.packageName }
    hidden.sortedBy { byPkg[it]?.label ?: it }.forEach { pkg ->
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onUnhide(pkg) }
                .padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(byPkg[pkg]?.label ?: pkg, color = LocalInk.current, fontSize = 14.sp)
            Text("Unhide", color = Color(style.accent), fontSize = 13.sp)
        }
    }
}
