package com.maria.homelauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.maria.homelauncher.LocalInk
import com.maria.homelauncher.Item
import com.maria.homelauncher.LayoutStore
import com.maria.homelauncher.syncLayout
import com.maria.homelauncher.AppEntry
import com.maria.homelauncher.AppRepo
import com.maria.homelauncher.BgMode
import com.maria.homelauncher.DrawerMode
import com.maria.homelauncher.GearSide
import com.maria.homelauncher.Gestures
import com.maria.homelauncher.SearchPos
import com.maria.homelauncher.LocalBadges
import com.maria.homelauncher.LocalStyle
import com.maria.homelauncher.NotificationWatch
import com.maria.homelauncher.LauncherWidgets
import com.maria.homelauncher.Prefs
import com.maria.homelauncher.Style
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlin.math.floor
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LauncherRoot(
    repo: AppRepo,
    prefs: Prefs,
    style: Style,
    onStyle: (Style) -> Unit,
    onResetStyle: () -> Unit,
    drawerOpen: Boolean,
    setDrawerOpen: (Boolean) -> Unit,
    resetPanels: Int,
    widgets: LauncherWidgets
) {
    val apps by repo.apps.collectAsState()
    val badges by NotificationWatch.badged.collectAsState()
    var dock by remember { mutableStateOf(prefs.dock) }
    var hidden by remember { mutableStateOf(prefs.hidden) }
    var settingsOpen by remember { mutableStateOf(false) }
    var intro by remember { mutableStateOf(!prefs.onboarded) }
    var welcome by remember { mutableStateOf(false) }
    var single by remember { mutableStateOf(prefs.singleSurface) }
    val context = LocalContext.current

    val layoutStore = remember { LayoutStore(context) }
    var layout by remember { mutableStateOf(layoutStore.load()) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var itemMenu by remember { mutableStateOf<Item?>(null) }
    var editFor by remember { mutableStateOf<AppEntry?>(null) }
    val scope = rememberCoroutineScope()

    // Reconcile the saved layout whenever the installed set or the hidden set changes.
    LaunchedEffect(apps, hidden, single) {
        if (!single || apps.isEmpty()) return@LaunchedEffect
        val synced = syncLayout(layout, apps, hidden)
        if (synced != layout) {
            layout = synced
            layoutStore.save(synced)
        }
    }

    // home press: drop every panel this composable owns, not just the drawer
    LaunchedEffect(resetPanels) {
        if (resetPanels > 0) {
            settingsOpen = false
            openFolder = null
            itemMenu = null
        }
    }

    val byKey = remember(apps) { apps.associateBy { "${it.packageName}/${it.activityName}" } }
    val iconOf: (Item.App) -> AppEntry? = { byKey["${it.pkg}/${it.activity}"] }

    fun commit(next: List<Item>) {
        layout = next
        layoutStore.save(next)
    }

    // read from prefs at the moment the gesture happens, so a change in settings takes
    // effect without going back to the home screen first
    val onGesture: (String) -> Unit = { which ->
        val action = when (which) {
            "down" -> prefs.gSwipeDown
            "double" -> prefs.gDoubleTap
            "left" -> prefs.gSwipeLeft
            else -> prefs.gSwipeRight
        }
        Gestures.run(
            ctx = context,
            action = action,
            onSearch = { setDrawerOpen(true) },
            onApp = { comp -> byKey[comp]?.let(repo::launch) }
        )
    }

    BackHandler(enabled = true) {
        when {
            // the intro owns the screen; back should not drop you behind it
            intro -> Unit
            settingsOpen -> settingsOpen = false
            openFolder != null -> openFolder = null
            drawerOpen -> setDrawerOpen(false)
        }
    }

    // A translucent panel sits in our own window, so without this the home screen shows
    // through it instead of the wallpaper.
    val homeAlpha by animateFloatAsState(
        targetValue = if (drawerOpen || settingsOpen) 0f else 1f,
        animationSpec = tween(180),
        label = "homeAlpha"
    )

    androidx.compose.runtime.CompositionLocalProvider(LocalBadges provides badges) {
    Box(Modifier.fillMaxSize()) {
        if (homeAlpha > 0f) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = homeAlpha }) {
                if (single) {
                    SingleSurface(
                        items = layout,
                        iconOf = iconOf,
                        onLaunch = { a -> iconOf(a)?.let(repo::launch) },
                        onReorder = ::commit,
                        onOpenFolder = { openFolder = it.id },
                        onItemMenu = { itemMenu = it },
                        onOpenSearch = { setDrawerOpen(true) },
                        widgets = widgets,
                        onGesture = onGesture
                    )
                } else {
                    HomeScreen(
                        apps = apps,
                        dock = dock,
                        onLaunch = repo::launch,
                        onOpenDrawer = { setDrawerOpen(true) },
                        widgets = widgets,
                        onGesture = onGesture
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically { it / 4 } + fadeIn(),
            exit = slideOutVertically { it / 4 } + fadeOut()
        ) {
            if (single) {
                SearchOverlay(
                    apps = apps,
                    onLaunch = { setDrawerOpen(false); repo.launch(it) },
                    onSettings = { settingsOpen = true },
                    onClose = { setDrawerOpen(false) }
                )
            } else {
                AppDrawer(
                    apps = apps,
                    dock = dock,
                    hidden = hidden,
                    onLaunch = { setDrawerOpen(false); repo.launch(it) },
                    onInfo = repo::openInfo,
                    onUninstall = repo::uninstall,
                    onToggleDock = { prefs.toggleDock(it.packageName, style.dockMax); dock = prefs.dock },
                    onToggleHidden = { prefs.toggleHidden(it.packageName); hidden = prefs.hidden },
                    onSettings = { settingsOpen = true },
                    onCustomise = { editFor = it },
                    onClose = { setDrawerOpen(false) }
                )
            }
        }

        val folder = openFolder?.let { id ->
            layout.firstOrNull { it is Item.Folder && it.id == id } as? Item.Folder
        }
        if (folder != null) {
            FolderOverlay(
                folder = folder,
                iconOf = iconOf,
                onLaunch = { a -> iconOf(a)?.let(repo::launch) },
                onRename = { newName ->
                    commit(layout.map {
                        if (it is Item.Folder && it.id == folder.id) it.copy(name = newName) else it
                    })
                },
                onMoveOut = { app -> commit(moveOutOfFolder(layout, folder.id, app)) },
                onClose = { openFolder = null }
            )
        }

        if (intro) {
            Onboarding(
                style = style,
                onStyle = onStyle,
                single = single,
                onSingle = { single = it; prefs.singleSurface = it },
                apps = apps,
                wallpaperUris = prefs.wallpapers,
                // skipping means "stop showing me things", so that route goes straight
                // to the home screen with no ceremony
                onSkip = { prefs.onboarded = true; intro = false },
                onDone = { prefs.onboarded = true; intro = false; welcome = true }
            )
        }

        itemMenu?.let { item ->
            ItemMenuSheet(
                item = item,
                iconOf = iconOf,
                hidden = hidden,
                onHide = { a ->
                    prefs.toggleHidden(a.pkg); hidden = prefs.hidden; itemMenu = null
                },
                onInfo = { a -> iconOf(a)?.let(repo::openInfo); itemMenu = null },
                onUninstall = { a -> iconOf(a)?.let(repo::uninstall); itemMenu = null },
                onUngroup = { f -> commit(ungroup(layout, f.id)); itemMenu = null },
                onCustomise = { a -> editFor = iconOf(a); itemMenu = null },
                onDismiss = { itemMenu = null }
            )
        }

        editFor?.let { app ->
            AppEditDialog(
                app = app,
                overrides = repo.overrides,
                onChanged = { scope.launch { repo.refresh() } },
                onDismiss = { editFor = null }
            )
        }

        AnimatedVisibility(
            visible = settingsOpen,
            enter = slideInVertically { it / 4 } + fadeIn(),
            exit = slideOutVertically { it / 4 } + fadeOut()
        ) {
            SettingsScreen(
                prefs = prefs,
                apps = apps,
                widgets = widgets,
                style = style,
                single = single,
                onSingle = { single = it; prefs.singleSurface = it },
                onStyle = onStyle,
                onResetStyle = onResetStyle,
                // straight back to page one, not "next time you open the launcher": the
                // launcher is the home screen, so it is rarely started from cold again
                onReplayIntro = { prefs.onboarded = false; intro = true; settingsOpen = false },
                onClose = { settingsOpen = false; hidden = prefs.hidden }
            )
        }

        if (welcome) WelcomeFade(style) { welcome = false }
    }
    }
}

/** Panel fill for the drawer and settings; Blur and Dim both rely on translucency. */
@Composable
fun panelColor(style: Style): Color = when (style.bgMode) {
    BgMode.Solid -> Color(style.bgColor)
    else -> Color(style.bgColor).copy(alpha = style.bgAlpha)
}

/**
 * White text vanishes on a light wallpaper. Everything drawn directly over the wallpaper
 * carries this so it stays readable whatever the image is.
 */
/** The notification dot. Accent coloured so it reads as part of the theme. */
@Composable
internal fun BadgeDot(modifier: Modifier = Modifier) {
    val style = LocalStyle.current
    Box(
        modifier
            .size((style.iconDp * 0.26f).dp)
            .clip(CircleShape)
            .background(Color(style.accent))
    )
}

internal val overWallpaper = Shadow(
    color = Color.Black.copy(alpha = 0.65f),
    offset = Offset(0f, 2f),
    blurRadius = 8f
)

private enum class Direction { Up, Down }

private const val SWIPE_THRESHOLD_DP = 72

/**
 * Vertical swipe that ignores drags starting inside the system gesture zone, so the
 * nav bar's own swipe-up keeps working. Accumulates distance rather than reacting to a
 * single frame's delta, so a brush of the screen does not trigger it.
 */
@Composable
private fun Modifier.swipe(direction: Direction, onTrigger: () -> Unit): Modifier {
    val density = LocalDensity.current
    val gestureInset = WindowInsets.systemGestures
    val bottomPx = with(density) { gestureInset.getBottom(this).toFloat() }
    val topPx = with(density) { gestureInset.getTop(this).toFloat() }
    val thresholdPx = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }

    return this.pointerInput(direction, bottomPx, topPx, thresholdPx) {
        val height = size.height
        var total = 0f
        var fired = false
        var ignore = false

        detectVerticalDragGestures(
            onDragStart = { offset ->
                total = 0f
                fired = false
                ignore = offset.y > height - bottomPx || offset.y < topPx
            },
            onDragEnd = { total = 0f; fired = false },
            onDragCancel = { total = 0f; fired = false }
        ) { _, dy ->
            if (ignore || fired) return@detectVerticalDragGestures
            total += dy
            val hit = when (direction) {
                Direction.Up -> total < -thresholdPx
                Direction.Down -> total > thresholdPx
            }
            if (hit) {
                fired = true
                onTrigger()
            }
        }
    }
}

/**
 * The gestures the home surface answers to. Up and down share one detector so they
 * cannot fight over the same drag. Horizontal is separate, and only wired up in the mode
 * that is not already using sideways swipes for pages.
 */
@Composable
private fun Modifier.homeGestures(
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null
): Modifier {
    val density = LocalDensity.current
    val inset = WindowInsets.systemGestures
    val bottomPx = with(density) { inset.getBottom(this).toFloat() }
    val topPx = with(density) { inset.getTop(this).toFloat() }
    val threshold = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }

    val up by rememberUpdatedState(onUp)
    val down by rememberUpdatedState(onDown)
    val left by rememberUpdatedState(onLeft)
    val right by rememberUpdatedState(onRight)
    val twice by rememberUpdatedState(onDoubleTap)

    var m = this.pointerInput(bottomPx, topPx, threshold) {
        val height = size.height
        var total = 0f
        var fired = false
        var ignore = false
        detectVerticalDragGestures(
            onDragStart = { o ->
                total = 0f
                fired = false
                ignore = o.y > height - bottomPx || o.y < topPx
            },
            onDragEnd = { total = 0f; fired = false },
            onDragCancel = { total = 0f; fired = false }
        ) { _, dy ->
            if (ignore || fired) return@detectVerticalDragGestures
            total += dy
            if (total < -threshold) { fired = true; up() }
            else if (total > threshold) { fired = true; down() }
        }
    }

    if (onLeft != null || onRight != null) {
        m = m.pointerInput(threshold) {
            var total = 0f
            var fired = false
            detectHorizontalDragGestures(
                onDragStart = { total = 0f; fired = false },
                onDragEnd = { total = 0f; fired = false },
                onDragCancel = { total = 0f; fired = false }
            ) { _, dx ->
                if (fired) return@detectHorizontalDragGestures
                total += dx
                if (total < -threshold) { fired = true; left?.invoke() }
                else if (total > threshold) { fired = true; right?.invoke() }
            }
        }
    }

    if (onDoubleTap != null) {
        m = m.pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { twice?.invoke() })
        }
    }
    return m
}

@Composable
private fun HomeScreen(
    apps: List<AppEntry>,
    dock: List<String>,
    onLaunch: (AppEntry) -> Unit,
    onOpenDrawer: () -> Unit,
    widgets: LauncherWidgets,
    onGesture: (String) -> Unit
) {
    val style = LocalStyle.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .homeGestures(
                onUp = onOpenDrawer,
                onDown = { onGesture("down") },
                onLeft = { onGesture("left") },
                onRight = { onGesture("right") },
                onDoubleTap = { onGesture("double") }
            )
            .systemBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(72.dp))
        Clock()

        Box(
            Modifier.weight(1f).fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            WidgetStack(widgets, Modifier.fillMaxWidth())
        }

        val byPkg = apps.associateBy { it.packageName }
        val dockApps = dock.mapNotNull { byPkg[it] }.take(style.dockMax)

        if (dockApps.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .then(
                        if (style.dockBg) Modifier
                            .clip(RoundedCornerShape(style.dockRadius.dp))
                            .background(LocalInk.current.copy(alpha = 0.10f))
                        else Modifier
                    )
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                dockApps.forEach { app ->
                    AppTile(app = app, showLabel = false, onClick = { onLaunch(app) })
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "swipe up for apps",
            color = LocalInk.current.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontFamily = style.fontFamily,
            style = TextStyle(shadow = overWallpaper)
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun Clock() {
    val style = LocalStyle.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(10_000)
        }
    }
    val pattern = if (style.clock24) "H:mm" else "h:mm"
    val time = remember(now, pattern) {
        SimpleDateFormat(pattern, Locale.getDefault()).format(now)
    }
    val date = remember(now) { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(now) }

    if (style.showClock) {
        Text(
            time,
            color = LocalInk.current,
            fontSize = style.clockSp.sp,
            fontFamily = style.fontFamily,
            style = TextStyle(shadow = overWallpaper)
        )
    }
    if (style.showDate) {
        Text(
            date,
            color = LocalInk.current.copy(alpha = 0.85f),
            fontSize = style.dateSp.sp,
            fontFamily = style.fontFamily,
            style = TextStyle(shadow = overWallpaper)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDrawer(
    apps: List<AppEntry>,
    dock: List<String>,
    hidden: Set<String>,
    onLaunch: (AppEntry) -> Unit,
    onInfo: (AppEntry) -> Unit,
    onUninstall: (AppEntry) -> Unit,
    onToggleDock: (AppEntry) -> Unit,
    onToggleHidden: (AppEntry) -> Unit,
    onSettings: () -> Unit,
    onCustomise: (AppEntry) -> Unit,
    onClose: () -> Unit
) {
    val style = LocalStyle.current
    val density = LocalDensity.current
    var query by remember { mutableStateOf("") }
    var sheetFor by remember { mutableStateOf<AppEntry?>(null) }

    // The grid eats vertical drags, so a plain swipe-down only worked on the margins.
    // Watch for over-scroll past the top of the list instead.
    val gridState = rememberLazyGridState()
    val close by rememberUpdatedState(onClose)
    val pullPx = with(density) { 110.dp.toPx() }
    val pullToClose = remember(pullPx) {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                when {
                    available.y > 0f && !gridState.canScrollBackward -> {
                        pulled += available.y
                        if (pulled > pullPx) {
                            pulled = 0f
                            close()
                        }
                    }
                    available.y < 0f -> pulled = 0f
                }
                return Offset.Zero
            }
        }
    }

    // Hidden apps stay out of the grid, but a search still finds them.
    val shown = remember(apps, query, hidden) {
        if (query.isBlank()) apps.filter { it.packageName !in hidden }
        else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(panelColor(style))
            .systemBarsPadding()
            .imePadding()
            .swipe(Direction.Down, onClose)
            .nestedScroll(pullToClose)
    ) {
        if (style.searchPos == SearchPos.Top) {
            SearchBar(query = query, onQuery = { query = it }, onSettings = onSettings)
        }

        Box(Modifier.weight(1f)) {
        when (style.drawerMode) {
            DrawerMode.Scroll -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(style.columns),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(if (style.showLabels) 16.dp else 20.dp)
            ) {
                items(shown, key = { it.packageName + it.activityName }) { app ->
                    AppTile(
                        app = app,
                        showLabel = style.showLabels,
                        onClick = { onLaunch(app) },
                        onLongClick = { sheetFor = app }
                    )
                }
            }

            DrawerMode.Pages -> PagedAppGrid(
                apps = shown,
                onLaunch = onLaunch,
                onLongClick = { sheetFor = it }
            )
        }
        }

        if (style.searchPos == SearchPos.Bottom) {
            SearchBar(query = query, onQuery = { query = it }, onSettings = onSettings)
        }
    }

    sheetFor?.let { app ->
        ModalBottomSheet(
            onDismissRequest = { sheetFor = null },
            containerColor = Color(style.bgColor)
        ) {
            Column(Modifier.padding(bottom = 28.dp)) {
                Text(
                    app.label,
                    color = LocalInk.current,
                    fontFamily = style.fontFamily,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    fontSize = 18.sp
                )
                SheetRow(if (app.packageName in dock) "Remove from dock" else "Add to dock") {
                    onToggleDock(app); sheetFor = null
                }
                SheetRow(if (app.packageName in hidden) "Unhide" else "Hide app") {
                    onToggleHidden(app); sheetFor = null
                }
                SheetRow("Rename or change icon") { onCustomise(app); sheetFor = null }
                SheetRow("App info") { onInfo(app); sheetFor = null }
                SheetRow("Uninstall") { onUninstall(app); sheetFor = null }
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onSettings: () -> Unit) {
    val style = LocalStyle.current
    val accent = Color(style.accent)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mirror the gear's footprint on the opposite side so the pill stays centred.
        if (style.gearSide == GearSide.Left) {
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, "Settings", tint = LocalInk.current.copy(alpha = 0.75f))
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }

        Row(
            Modifier
                .weight(1f)
                .height(50.dp)
                .clip(CircleShape)
                .background(LocalInk.current.copy(alpha = 0.10f))
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = LocalInk.current.copy(alpha = 0.55f),
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        "Search",
                        color = LocalInk.current.copy(alpha = 0.45f),
                        fontSize = style.searchSp.sp,
                        fontFamily = style.fontFamily
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    cursorBrush = SolidColor(accent),
                    textStyle = TextStyle(
                        color = LocalInk.current,
                        fontSize = style.searchSp.sp,
                        fontFamily = style.fontFamily
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (style.gearSide == GearSide.Right) {
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, "Settings", tint = LocalInk.current.copy(alpha = 0.75f))
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetRow(text: String, onClick: () -> Unit) {
    val style = LocalStyle.current
    Text(
        text,
        color = LocalInk.current,
        fontFamily = style.fontFamily,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        fontSize = 16.sp
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    app: AppEntry,
    showLabel: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val style = LocalStyle.current

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(style.iconDp.dp).alpha(style.iconAlpha)
            )
            if (style.badges && app.packageName in LocalBadges.current) {
                BadgeDot(Modifier.align(Alignment.TopEnd))
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(7.dp))
            Text(
                app.label,
                color = LocalInk.current.copy(alpha = 0.95f),
                fontSize = style.labelSp.sp,
                fontFamily = style.familyFor(app),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = TextStyle(shadow = overWallpaper)
            )
        }
    }
}

@Composable
private fun SingleSurface(
    items: List<Item>,
    iconOf: (Item.App) -> AppEntry?,
    onLaunch: (Item.App) -> Unit,
    onReorder: (List<Item>) -> Unit,
    onOpenFolder: (Item.Folder) -> Unit,
    onItemMenu: (Item) -> Unit,
    onOpenSearch: () -> Unit,
    widgets: LauncherWidgets,
    onGesture: (String) -> Unit
) {
    val style = LocalStyle.current

    Column(
        Modifier
            .fillMaxSize()
            .homeGestures(
                onUp = onOpenSearch,
                onDown = { onGesture("down") },
                onDoubleTap = { onGesture("double") }
            )
            .systemBarsPadding()
    ) {
        CompactClock()
        WidgetStack(
            widgets,
            Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )
        HomeGrid(
            items = items,
            iconOf = iconOf,
            onLaunch = onLaunch,
            onReorder = onReorder,
            onOpenFolder = onOpenFolder,
            onItemMenu = onItemMenu,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun CompactClock() {
    val style = LocalStyle.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(10_000)
        }
    }
    val pattern = remember(style.clock24, style.showClock, style.showDate) {
        val t = if (style.clock24) "H:mm" else "h:mm"
        when {
            style.showClock && style.showDate -> "$t  ·  EEE, MMM d"
            style.showClock -> t
            style.showDate -> "EEE, MMM d"
            else -> ""
        }
    }
    if (pattern.isEmpty()) return
    val text = remember(now, pattern) {
        SimpleDateFormat(pattern, Locale.getDefault()).format(now)
    }
    Text(
        text,
        color = LocalInk.current.copy(alpha = 0.85f),
        fontSize = style.dateSp.sp,
        fontFamily = style.fontFamily,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
        textAlign = TextAlign.Center,
        style = TextStyle(shadow = overWallpaper)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemMenuSheet(
    item: Item,
    iconOf: (Item.App) -> AppEntry?,
    hidden: Set<String>,
    onHide: (Item.App) -> Unit,
    onInfo: (Item.App) -> Unit,
    onUninstall: (Item.App) -> Unit,
    onUngroup: (Item.Folder) -> Unit,
    onCustomise: (Item.App) -> Unit,
    onDismiss: () -> Unit
) {
    val style = LocalStyle.current

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(style.bgColor)) {
        Column(Modifier.padding(bottom = 28.dp)) {
            Text(
                when (item) {
                    is Item.App -> iconOf(item)?.label ?: item.pkg
                    is Item.Folder -> item.name
                },
                color = LocalInk.current,
                fontFamily = style.familyFor((item as? Item.App)?.let(iconOf)),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                fontSize = 18.sp
            )
            when (item) {
                is Item.App -> {
                    SheetRow(if (item.pkg in hidden) "Unhide" else "Hide app") { onHide(item) }
                    SheetRow("Rename or change icon") { onCustomise(item) }
                    SheetRow("App info") { onInfo(item) }
                    SheetRow("Uninstall") { onUninstall(item) }
                }
                is Item.Folder -> {
                    SheetRow("Ungroup folder") { onUngroup(item) }
                }
            }
        }
    }
}

/** Pulls one app back out of a folder and drops it next to that folder. */
private fun moveOutOfFolder(items: List<Item>, folderId: String, app: Item.App): List<Item> {
    val idx = items.indexOfFirst { it is Item.Folder && it.id == folderId }
    if (idx < 0) return items
    val f = items[idx] as Item.Folder
    val remaining = f.apps.filterNot { it.pkg == app.pkg && it.activity == app.activity }

    val out = items.toMutableList()
    when (remaining.size) {
        0 -> out[idx] = app
        1 -> {
            out[idx] = remaining.first()
            out.add(idx + 1, app)
        }
        else -> {
            out[idx] = f.copy(apps = remaining)
            out.add(idx + 1, app)
        }
    }
    return out
}

/** Dissolves a folder, spilling its apps back onto the surface in place. */
private fun ungroup(items: List<Item>, folderId: String): List<Item> {
    val idx = items.indexOfFirst { it is Item.Folder && it.id == folderId }
    if (idx < 0) return items
    val f = items[idx] as Item.Folder
    val out = items.toMutableList()
    out.removeAt(idx)
    out.addAll(idx, f.apps)
    return out
}

/** Drawer laid out as swipeable pages instead of one long scroll. */
@Composable
private fun PagedAppGrid(
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onLongClick: (AppEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalStyle.current
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val cols = style.columns
        val cellH = with(density) {
            (style.iconDp.dp + (if (style.showLabels) 26.dp else 10.dp) + 18.dp).toPx()
        }
        val rows = maxOf(1, floor(with(density) { maxHeight.toPx() } / cellH).toInt())
        val capacity = cols * rows
        val pages = remember(apps, capacity) {
            apps.chunked(capacity).ifEmpty { listOf(emptyList()) }
        }
        val pagerState = rememberPagerState(pageCount = { pages.size })

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val pageApps = pages.getOrElse(page) { emptyList() }
            Column(Modifier.fillMaxSize()) {
                repeat(rows) { r ->
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        repeat(cols) { c ->
                            Box(
                                Modifier.weight(1f).fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                pageApps.getOrNull(r * cols + c)?.let { app ->
                                    AppTile(
                                        app = app,
                                        showLabel = style.showLabels,
                                        onClick = { onLaunch(app) },
                                        onLongClick = { onLongClick(app) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (pages.size > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                repeat(pages.size) { i ->
                    val on = i == pagerState.currentPage
                    Box(
                        Modifier
                            .size(if (on) 7.dp else 6.dp)
                            .clip(CircleShape)
                            .background(LocalInk.current.copy(alpha = if (on) 0.85f else 0.3f))
                    )
                }
            }
        }
    }
}
