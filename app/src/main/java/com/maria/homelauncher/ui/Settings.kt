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
import com.maria.homelauncher.Placed
import com.maria.homelauncher.Prefs
import com.maria.homelauncher.Fonts
import com.maria.homelauncher.Backup
import com.maria.homelauncher.StyleStore
import com.maria.homelauncher.Style
import com.maria.homelauncher.TARGET_BOTH
import com.maria.homelauncher.TARGET_HOME
import com.maria.homelauncher.TARGET_LOCK
import com.maria.homelauncher.HomeTrack
import com.maria.homelauncher.Wallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Internal rather than private: the home screen's own edit bar opens some of these
 * directly, so it has to be able to name one.
 */
internal enum class Page(val title: String, val blurb: String) {
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
    SoundPg("Sound", "Play a track on the home screen"),
    Badges("Notification dots", "A dot on apps with something waiting"),
    Hidden("Hidden apps", "Only found by searching"),
    BackupPg("Backup", "Save your whole setup to a file"),
    UpdatePg("Updates", "Check for a newer version"),
    About("Intro and reset", "Replay the tour, reset the look")
}

private val intervals = listOf(
    15 to "15m", 30 to "30m", 60 to "1h", 180 to "3h", 360 to "6h", 720 to "12h", 1440 to "24h"
)

@Composable
internal fun SettingsScreen(
    prefs: Prefs,
    apps: List<AppEntry>,
    widgets: LauncherWidgets,
    homeItems: List<Placed>,
    onRemoveWidget: (Placed) -> Unit,
    style: Style,
    single: Boolean,
    onSingle: (Boolean) -> Unit,
    onStyle: (Style) -> Unit,
    onResetStyle: () -> Unit,
    onReplayIntro: () -> Unit,
    styleStore: StyleStore,
    onImported: () -> Unit,
    /** Set when something opened settings at a particular page rather than at the list. */
    startPage: Page? = null,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var page by remember { mutableStateOf(startPage ?: Page.Root) }
    var pics by remember { mutableStateOf(prefs.wallpapers) }
    var picFolders by remember { mutableStateOf(prefs.wallpaperFolders) }
    var lockPics by remember { mutableStateOf(prefs.lockWallpapers) }
    var lockPicFolders by remember { mutableStateOf(prefs.lockWallpaperFolders) }
    var separate by remember { mutableStateOf(prefs.lockSeparate) }
    // which set the picker that is about to open is filling, since the result comes back
    // long after the button was pressed
    var pickInto by remember { mutableStateOf(TARGET_HOME) }
    var singleTarget by remember { mutableStateOf(TARGET_BOTH) }
    var liveBg by remember { mutableStateOf(prefs.liveBg) }
    var audio by remember { mutableStateOf(prefs.homeAudio) }
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
        val lock = pickInto == TARGET_LOCK
        if (lock) {
            lockPics = (lockPics + kept).distinct()
            prefs.lockWallpapers = lockPics
        } else {
            pics = (pics + kept).distinct()
            prefs.wallpapers = pics
        }
        Wallpaper.schedule(ctx)
        note = "${kept.size} added to the ${if (lock) "lock" else "home"} set"
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
            return@rememberLauncherForActivityResult
        }
        // the folder itself is kept, not the files in it, so anything added to it later
        // joins the rotation without being picked again
        if (pickInto == TARGET_LOCK) {
            lockPicFolders = (lockPicFolders + tree.toString()).distinct()
            prefs.lockWallpaperFolders = lockPicFolders
        } else {
            picFolders = (picFolders + tree.toString()).distinct()
            prefs.wallpaperFolders = picFolders
        }
        Wallpaper.schedule(ctx)
        note = "folder added, ${found.size} picture(s) in it now"
    }

    val livePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }.isSuccess
        if (!ok) {
            note = "could not keep access to that file"
            return@rememberLauncherForActivityResult
        }
        liveBg = uri.toString()
        prefs.liveBg = liveBg
        note = "animated background set"
    }

    // copied in rather than played from where it sits: see HomeTrack for why a picked
    // URI is not something to rely on months later
    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        note = "copying that file in..."
        scope.launch {
            val stored = withContext(Dispatchers.IO) { HomeTrack.import(ctx, uri) }
            if (stored == null) {
                note = "that file could not be read"
            } else {
                audio = stored
                prefs.homeAudio = stored
                note = "track set"
            }
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
            note = if (Wallpaper.setOne(ctx, uri, singleTarget)) "wallpaper set"
            else "could not read that one"
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

                Page.WidgetsPg -> WidgetsPage(widgets, homeItems, onRemoveWidget)

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
                    folderCount = picFolders.size,
                    lockPics = lockPics,
                    lockFolderCount = lockPicFolders.size,
                    separate = separate,
                    onSeparate = {
                        separate = it
                        prefs.lockSeparate = it
                        Wallpaper.schedule(ctx)
                    },
                    liveBg = liveBg,
                    onAddImages = { target ->
                        pickInto = target
                        picker.launch(arrayOf("image/*", "video/*"))
                    },
                    onAddFolder = { target ->
                        pickInto = target
                        folderPicker.launch(null)
                    },
                    onPickSingle = { target ->
                        singleTarget = target
                        singlePicker.launch(arrayOf("image/*", "video/*"))
                    },
                    onPickLive = {
                        livePicker.launch(arrayOf("image/gif", "image/webp", "video/*"))
                    },
                    onClearLive = {
                        liveBg = ""
                        prefs.liveBg = ""
                        note = "animated background removed"
                    },
                    onClearSet = { target ->
                        if (target == TARGET_LOCK) {
                            lockPics = emptyList()
                            lockPicFolders = emptyList()
                            prefs.lockWallpapers = lockPics
                            prefs.lockWallpaperFolders = lockPicFolders
                            note = "lock set cleared"
                        } else {
                            pics = emptyList()
                            picFolders = emptyList()
                            prefs.wallpapers = pics
                            prefs.wallpaperFolders = picFolders
                            note = "home set cleared"
                        }
                        Wallpaper.schedule(ctx)
                    },
                    onChangeNow = {
                        scope.launch {
                            note = if (Wallpaper.advance(ctx)) "wallpaper changed"
                            else "could not read any of those pictures"
                        }
                    }
                )

                Page.SoundPg -> SoundPage(
                    prefs = prefs,
                    audio = audio,
                    onPick = { audioPicker.launch(arrayOf("audio/*")) },
                    onNote = { note = it },
                    onClear = {
                        audio = ""
                        prefs.homeAudio = ""
                        HomeTrack.clear(ctx)
                        note = "track removed"
                    }
                )

                Page.Badges -> BadgesPage(style, onStyle)

                Page.Hidden -> HiddenPage(apps, hidden) {
                    prefs.toggleHidden(it)
                    hidden = prefs.hidden
                }

                Page.UpdatePg -> UpdatePage(prefs)

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
    folderCount: Int,
    lockPics: List<String>,
    lockFolderCount: Int,
    separate: Boolean,
    onSeparate: (Boolean) -> Unit,
    liveBg: String,
    onAddImages: (Int) -> Unit,
    onAddFolder: (Int) -> Unit,
    onPickSingle: (Int) -> Unit,
    onPickLive: () -> Unit,
    onClearLive: () -> Unit,
    onClearSet: (Int) -> Unit,
    onChangeNow: () -> Unit
) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(prefs.rotateOn) }
    var loop by remember { mutableStateOf(prefs.liveLoop) }
    var lockOn by remember { mutableStateOf(prefs.lockRotate) }
    var lockRandom by remember { mutableStateOf(prefs.lockRandom) }
    var single by remember { mutableStateOf(TARGET_BOTH) }

    // read off the disk rather than from what was saved, so a watched folder reports what
    // is in it now and not what was in it when it was picked
    var homeNow by remember { mutableStateOf(-1) }
    var lockNow by remember { mutableStateOf(-1) }
    LaunchedEffect(pics, folderCount, lockPics, lockFolderCount, separate) {
        val counts = withContext(Dispatchers.IO) {
            Wallpaper.sources(ctx, TARGET_HOME).size to Wallpaper.sources(ctx, TARGET_LOCK).size
        }
        homeNow = counts.first
        lockNow = counts.second
    }
    var minutes by remember { mutableStateOf(prefs.rotateMinutes) }
    var target by remember { mutableStateOf(prefs.rotateTarget) }
    var shuffle by remember { mutableStateOf(prefs.shuffle) }

    Label("Just one")
    Text(
        "Set a single picture and leave rotation off.",
        color = LocalInk.current.copy(alpha = 0.45f),
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
    )
    val whichOne = listOf(TARGET_HOME to "Home", TARGET_LOCK to "Lock", TARGET_BOTH to "Both")
    ChipRow(whichOne.map { it.second }, whichOne.indexOfFirst { it.first == single }) { i ->
        single = whichOne[i].first
    }
    Spacer(Modifier.height(10.dp))
    Button(onClick = { onPickSingle(single) }) { Text("Choose a wallpaper") }

    Spacer(Modifier.height(26.dp))
    Label("Two sets or one")
    Spacer(Modifier.height(6.dp))
    ToggleRow("Give the lock screen its own pictures", separate, onSeparate)
    Hint(
        if (separate) {
            "The lock screen draws from its own set below and the home screen from its " +
                "own, so the two never show each other's pictures. Each keeps its own " +
                "place in its own list."
        } else {
            "Both screens draw from one set. Turn this on to keep them apart."
        }
    )

    Spacer(Modifier.height(22.dp))
    PictureSet(
        title = if (separate) "Home pictures" else "Pictures",
        files = pics.size,
        folders = folderCount,
        available = homeNow,
        onAdd = { onAddImages(TARGET_HOME) },
        onAddFolder = { onAddFolder(TARGET_HOME) },
        onClear = { onClearSet(TARGET_HOME) }
    )
    Hint(
        "Pick single files, or a whole folder from anywhere on the device. A folder is " +
            "re-read every time the wallpaper changes, so pictures you add to it later " +
            "join in on their own. It reads what is directly inside, not sub-folders."
    )

    if (separate) {
        Spacer(Modifier.height(22.dp))
        PictureSet(
            title = "Lock screen pictures",
            files = lockPics.size,
            folders = lockFolderCount,
            available = lockNow,
            onAdd = { onAddImages(TARGET_LOCK) },
            onAddFolder = { onAddFolder(TARGET_LOCK) },
            onClear = { onClearSet(TARGET_LOCK) }
        )
        if (lockPics.isEmpty() && lockFolderCount == 0) {
            Hint("Nothing here yet, so the lock screen is left alone until you add something.")
        }
    }

    Spacer(Modifier.height(26.dp))
    Label("Rotate through several")
    Spacer(Modifier.height(6.dp))
    ToggleRow("Rotate automatically", on) {
        on = it
        prefs.rotateOn = it
        Wallpaper.schedule(ctx)
    }

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
        Wallpaper.schedule(ctx)
    }
    if (separate && target == TARGET_BOTH) {
        Hint(
            "With two sets, Both means one picture from each: the home set goes on the " +
                "home screen and the lock set on the lock screen, at the same moment."
        )
    }
    Hint("Some phone skins block apps from setting the lock screen. Stock Android is fine.")

    Spacer(Modifier.height(12.dp))
    ToggleRow("Shuffle", shuffle) {
        shuffle = it
        prefs.shuffle = it
    }

    Spacer(Modifier.height(12.dp))
    Button(enabled = homeNow > 0 || lockNow > 0, onClick = onChangeNow) {
        Text("Change now")
    }

    Spacer(Modifier.height(26.dp))
    Label("Lock screen, on every screen off")
    Spacer(Modifier.height(6.dp))
    ToggleRow("New picture each time the screen goes off", lockOn) {
        lockOn = it
        prefs.lockRotate = it
    }
    if (lockOn) {
        Spacer(Modifier.height(10.dp))
        val orders = listOf(false to "In order", true to "Random")
        ChipRow(orders.map { it.second }, orders.indexOfFirst { it.first == lockRandom }) { i ->
            lockRandom = orders[i].first
            prefs.lockRandom = lockRandom
        }
    }
    Hint(
        (if (separate) "Uses the lock screen set above. "
        else "Uses the one set above, since both screens are sharing it. ") +
            "It touches the lock screen only, so your home wallpaper stays where it is. " +
            "The change happens on the way off rather than on the way on, so it is " +
            "already there when the screen lights up, and it keeps its own place in the " +
            "list, separate from the timed rotation."
    )

    Spacer(Modifier.height(26.dp))
    Label("Animated background")
    Text(
        if (liveBg.isBlank()) "None" else "Playing behind the home screen",
        color = LocalInk.current.copy(alpha = 0.65f),
        fontSize = 13.sp,
        modifier = Modifier.padding(vertical = 6.dp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onPickLive) {
            Text(if (liveBg.isBlank()) "Choose a GIF or video" else "Change")
        }
        if (liveBg.isNotBlank()) OutlinedButton(onClick = onClearLive) { Text("Remove") }
    }

    if (liveBg.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        ToggleRow("Repeat", loop) {
            loop = it
            prefs.liveLoop = it
        }
        Hint(
            if (loop) "Plays over and over."
            else "Plays once and holds on the last frame."
        )
    }

    Hint(
        "This one really moves, and it belongs to the home screen alone: the launcher " +
            "draws it in its own window, which is the only surface it is allowed to " +
            "draw on. It pauses whenever another app is in front."
    )
    Hint(
        "The lock screen cannot have a moving one from an app that is not a live " +
            "wallpaper, because Android only ever takes a still picture there. A GIF or " +
            "a video dropped into either set above is still welcome though: a frame is " +
            "taken out of it, and that frame is what goes on."
    )
}

/** One set of pictures: how many there are, and the three things you can do to it. */
@Composable
private fun PictureSet(
    title: String,
    files: Int,
    folders: Int,
    available: Int,
    onAdd: () -> Unit,
    onAddFolder: () -> Unit,
    onClear: () -> Unit
) {
    Label(title)
    Text(
        buildString {
            append(if (files == 0) "No files chosen" else "$files file(s) chosen")
            if (folders > 0) append(", $folders folder(s) watched")
            if (available >= 0) append(", $available in the rotation right now")
        },
        color = LocalInk.current.copy(alpha = 0.65f),
        fontSize = 13.sp,
        modifier = Modifier.padding(vertical = 6.dp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onAdd) { Text("Add pictures") }
        OutlinedButton(onClick = onAddFolder) { Text("Add folder") }
        // a watched folder is removable too, and it is the only thing in the list when
        // no single files were ever picked
        if (files > 0 || folders > 0) {
            OutlinedButton(onClick = onClear) { Text("Clear") }
        }
    }
}

@Composable
private fun SoundPage(
    prefs: Prefs,
    audio: String,
    onPick: () -> Unit,
    onNote: (String) -> Unit,
    onClear: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var loop by remember { mutableStateOf(prefs.audioLoop) }
    var volume by remember { mutableStateOf(prefs.audioVolume) }
    var testing by remember { mutableStateOf(false) }

    val name = remember(audio) { HomeTrack.label(ctx, audio) }

    Text(
        "A track that plays while the home screen is in front, and stops the moment you " +
            "open something else. MP3, OGG, WAV, M4A, anything the phone can play.",
        color = LocalInk.current.copy(alpha = 0.55f),
        fontSize = 12.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )

    Label("Track")
    Text(
        when {
            audio.isBlank() -> "None"
            name.isNotBlank() -> name
            else -> "One file chosen"
        },
        color = LocalInk.current.copy(alpha = 0.65f),
        fontSize = 13.sp,
        modifier = Modifier.padding(vertical = 6.dp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onPick) {
            Text(if (audio.isBlank()) "Choose a file" else "Change")
        }
        if (audio.isNotBlank()) {
            OutlinedButton(
                enabled = !testing,
                onClick = {
                    testing = true
                    onNote("playing it now...")
                    scope.launch {
                        val wrong = HomeTrack.probe(ctx, audio, volume)
                        testing = false
                        onNote(wrong?.let { "it will not play: $it" } ?: "that played fine")
                    }
                }
            ) { Text("Test") }
            OutlinedButton(onClick = onClear) { Text("Remove") }
        }
    }
    if (audio.isNotBlank()) {
        Hint(
            "Test plays it for a few seconds right here. If you hear it here but not on " +
                "the home screen, say so and I will know where to look."
        )
    }

    if (audio.isNotBlank()) {
        Spacer(Modifier.height(18.dp))
        ToggleRow("Repeat", loop) {
            loop = it
            prefs.audioLoop = it
        }
        Hint(
            if (loop) "Starts again every time it reaches the end."
            else "Plays once each time you come back to the home screen."
        )

        Spacer(Modifier.height(16.dp))
        Label("Volume")
        val levels = listOf(10, 25, 50, 75, 100)
        ChipRow(levels.map { "$it%" }, levels.indexOfFirst { it == volume }) { i ->
            volume = levels[i]
            prefs.audioVolume = volume
        }
        Hint(
            "Set against whatever the media volume is, and mixed under anything already " +
                "playing rather than pausing it. Nothing here interrupts your music."
        )
    }
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
