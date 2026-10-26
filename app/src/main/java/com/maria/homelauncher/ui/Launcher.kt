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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.maria.homelauncher.LocalInk
import com.maria.homelauncher.Item
import com.maria.homelauncher.LayoutStore
import com.maria.homelauncher.Placed
import com.maria.homelauncher.DrawerCell
import com.maria.homelauncher.DrawerFolder
import com.maria.homelauncher.DrawerStore
import com.maria.homelauncher.addToFolder
import com.maria.homelauncher.dropPage
import com.maria.homelauncher.drawerCells
import com.maria.homelauncher.newDrawerFolderId
import com.maria.homelauncher.removeFromFolders
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
import com.maria.homelauncher.StyleStore
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
    widgets: LauncherWidgets,
    styleStore: StyleStore,
    onImported: () -> Unit
) {
    val apps by repo.apps.collectAsState()
    val badges by NotificationWatch.badged.collectAsState()
    var dock by remember { mutableStateOf(prefs.dock) }
    var hidden by remember { mutableStateOf(prefs.hidden) }
    var settingsOpen by remember { mutableStateOf(false) }
    var settingsAt by remember { mutableStateOf<Page?>(null) }
    var intro by remember { mutableStateOf(!prefs.onboarded) }
    var welcome by remember { mutableStateOf(false) }
    var single by remember { mutableStateOf(prefs.singleSurface) }
    val context = LocalContext.current

    val layoutStore = remember { LayoutStore(context) }
    val drawerStore = remember { DrawerStore(context) }
    var drawerFolders by remember { mutableStateOf(drawerStore.load()) }
    var drawerFolder by remember { mutableStateOf<String?>(null) }
    var layout by remember {
        mutableStateOf(openLayout(prefs, layoutStore, widgets, single, style.columns))
    }
    var openFolder by remember { mutableStateOf<String?>(null) }

    var itemMenu by remember { mutableStateOf<Item?>(null) }
    var editFor by remember { mutableStateOf<AppEntry?>(null) }
    var editingHome by remember { mutableStateOf(false) }
    var cellDp by remember { mutableStateOf(72f to 96f) }
    var homePages by remember { mutableStateOf(prefs.homePages) }
    var liveBg by remember { mutableStateOf(prefs.liveBg) }
    var liveLoop by remember { mutableStateOf(prefs.liveLoop) }
    var audio by remember { mutableStateOf(prefs.homeAudio) }
    var audioLoop by remember { mutableStateOf(prefs.audioLoop) }
    var audioVolume by remember { mutableStateOf(prefs.audioVolume) }
    // whether a gesture has anywhere to go, so a detector is only put in the way of the
    // grid's own touches when there is a reason to
    var doubleSet by remember { mutableStateOf(prefs.gDoubleTap != Gestures.NONE) }
    var sideSet by remember {
        mutableStateOf(prefs.gSwipeLeft != Gestures.NONE || prefs.gSwipeRight != Gestures.NONE)
    }
    var pendingSpot by remember { mutableStateOf<Placed?>(null) }
    val scope = rememberCoroutineScope()

    fun commit(next: List<Placed>) {
        layout = next
        layoutStore.save(next)
        // dropping something on the spare page at the end is how a page gets added
        // without asking for one, so the count has to follow
        val needed = (next.maxOfOrNull { if (it.placed) it.page + 1 else 0 } ?: 1).coerceAtLeast(1)
        if (needed > prefs.homePages) {
            prefs.homePages = needed
            homePages = needed
        }
    }

    /**
     * Back and the close button both land here. They used to differ: back set the flag
     * and nothing else, so anything settings had written straight to prefs was only
     * noticed if you left through the button.
     */
    fun reread() {
        hidden = prefs.hidden
        liveBg = prefs.liveBg
        liveLoop = prefs.liveLoop
        audio = prefs.homeAudio
        audioLoop = prefs.audioLoop
        audioVolume = prefs.audioVolume
        doubleSet = prefs.gDoubleTap != Gestures.NONE
        sideSet = prefs.gSwipeLeft != Gestures.NONE || prefs.gSwipeRight != Gestures.NONE
    }

    fun closeSettings() {
        settingsOpen = false
        settingsAt = null
        reread()
    }

    fun commitFolders(next: List<DrawerFolder>) {
        drawerFolders = next
        drawerStore.save(next)
    }

    // Reconcile the saved layout whenever the installed set or the hidden set changes.
    // With one surface every app belongs on the pages; with a drawer the home screen
    // only holds what was put there by hand.
    LaunchedEffect(apps, hidden, single) {
        if (apps.isEmpty()) return@LaunchedEffect
        val alive = layout.filter {
            val w = it.item as? Item.Widget ?: return@filter true
            widgets.alive(w.appWidgetId)
        }
        val synced = syncLayout(alive, apps, hidden, addNew = single)
        if (synced != layout) commit(synced)
    }

    // A widget is only ours once the system consent dialog and the widget's own setup
    // screen are both done with, which is long after the tap that asked for it.
    SideEffect {
        widgets.onPlaced = { id ->
            val spot = pendingSpot
            pendingSpot = null
            val landed = if (spot != null) {
                spot.copy(item = Item.Widget(id))
            } else {
                val (w, h) = widgets.defaultSpan(
                    widgets.manager.getAppWidgetInfo(id), cellDp.first, cellDp.second
                )
                Placed(Item.Widget(id), w = w, h = h)
            }
            commit(layout + landed)
            editingHome = true
        }
    }

    // Settings writes some of these straight to prefs, and there is more than one way
    // out of it: the close button, back, and the home key, which drops the panel from
    // somewhere else entirely. Re-reading on every resume covers all three and costs a
    // handful of preference reads.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reread()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    // home press: drop every panel this composable owns, not just the drawer
    LaunchedEffect(resetPanels) {
        if (resetPanels > 0) {
            reread()
            settingsOpen = false
            openFolder = null
            drawerFolder = null
            itemMenu = null
            editingHome = false
        }
    }

    val byKey = remember(apps) { apps.associateBy { "${it.packageName}/${it.activityName}" } }
    val iconOf: (Item.App) -> AppEntry? = { byKey["${it.pkg}/${it.activity}"] }

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
            settingsOpen -> closeSettings()
            openFolder != null -> openFolder = null
            drawerFolder != null -> drawerFolder = null
            drawerOpen -> setDrawerOpen(false)
            editingHome -> editingHome = false
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
        // behaves like the wallpaper: underneath everything, including the panels, which
        // are translucent over it the same way they are over a still image
        LiveBackground(liveBg, liveLoop, Modifier.fillMaxSize())

        // draws nothing; it is here because this is what is alive exactly as long as the
        // home screen is the thing in front
        HomeAudio(audio, audioLoop, audioVolume)

        if (homeAlpha > 0f) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = homeAlpha }) {
                val grid = @Composable { modifier: Modifier ->
                    HomeGrid(
                        placed = layout,
                        iconOf = iconOf,
                        widgets = widgets,
                        editing = editingHome,
                        onEditing = { editingHome = it },
                        onLaunch = { a -> iconOf(a)?.let(repo::launch) },
                        onChange = { commit(it) },
                        onOpenFolder = { openFolder = it.id },
                        onItemMenu = { itemMenu = it },
                        onWallpaper = { settingsAt = Page.WallpaperPg; settingsOpen = true },
                        onStyle = { settingsAt = Page.ThemesPg; settingsOpen = true },
                        pages = homePages,
                        onPages = { count, drop ->
                            if (drop >= 0) commit(dropPage(layout, drop))
                            homePages = count
                            prefs.homePages = count
                        },
                        onPlaceWidget = { info, page, col, row, w, h ->
                            pendingSpot = Placed(Item.Widget(-1), page, col, row, w, h)
                            widgets.add(info)
                        },
                        onCellSize = { w, h -> cellDp = w to h },
                        onDoubleTap = if (doubleSet) ({ onGesture("double") }) else null,
                        onSideSwipe = if (sideSet) ({ dir: Int ->
                            onGesture(if (dir < 0) "left" else "right")
                        }) else null,
                        // with a drawer the home screen is yours to arrange, so an icon
                        // can be taken off it; on one surface every app lives here and
                        // removing one would only mean hiding it
                        canRemoveApps = !single,
                        modifier = modifier
                    )
                }

                if (single) {
                    SingleSurface(
                        grid = grid,
                        onOpenSearch = { setDrawerOpen(true) },
                        onGesture = onGesture
                    )
                } else {
                    HomeScreen(
                        apps = apps,
                        dock = dock,
                        grid = grid,
                        onLaunch = repo::launch,
                        onOpenDrawer = { setDrawerOpen(true) },
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
                    folders = drawerFolders,
                    iconOf = iconOf,
                    onOpenFolder = { drawerFolder = it.id },
                    onNewFolder = { a ->
                        val id = newDrawerFolderId()
                        commitFolders(
                            drawerFolders + DrawerFolder(
                                id = id,
                                name = "Folder",
                                members = listOf("${a.packageName}/${a.activityName}")
                            )
                        )
                        drawerFolder = id
                    },
                    onPutInFolder = { a, id ->
                        commitFolders(
                            addToFolder(drawerFolders, id, "${a.packageName}/${a.activityName}")
                        )
                    },
                    onUngroupFolder = { f -> commitFolders(drawerFolders.filterNot { it.id == f.id }) },
                    onAddToHome = { a ->
                        val fresh = Item.App(a.packageName, a.activityName)
                        if (layout.none { it.item.key == fresh.key }) commit(layout + Placed(fresh))
                        setDrawerOpen(false)
                    },
                    onSettings = { settingsOpen = true },
                    onCustomise = { editFor = it },
                    onClose = { setDrawerOpen(false) }
                )
            }
        }

        val folder = openFolder?.let { id ->
            layout.firstOrNull { (it.item as? Item.Folder)?.id == id }?.item as? Item.Folder
        }
        if (folder != null) {
            FolderOverlay(
                folder = folder,
                iconOf = iconOf,
                onLaunch = { a -> iconOf(a)?.let(repo::launch) },
                onRename = { newName ->
                    commit(layout.map {
                        val f = it.item as? Item.Folder
                        if (f != null && f.id == folder.id) it.copy(item = f.copy(name = newName))
                        else it
                    })
                },
                onMoveOut = { app -> commit(moveOutOfFolder(layout, folder.id, app)) },
                onClose = { openFolder = null }
            )
        }

        // the same overlay the home screen uses, over the drawer instead
        val fromDrawer = drawerFolder?.let { id ->
            drawerFolders.firstOrNull { it.id == id }?.let { f ->
                Item.Folder(
                    id = f.id,
                    name = f.name,
                    apps = f.members.filter { byKey.containsKey(it) }
                        .map { Item.App(it.substringBefore('/'), it.substringAfter('/')) }
                )
            }
        }
        if (fromDrawer != null) {
            FolderOverlay(
                folder = fromDrawer,
                iconOf = iconOf,
                onLaunch = { a ->
                    iconOf(a)?.let(repo::launch)
                    drawerFolder = null
                    setDrawerOpen(false)
                },
                onRename = { newName ->
                    commitFolders(drawerFolders.map {
                        if (it.id == fromDrawer.id) it.copy(name = newName) else it
                    })
                },
                onMoveOut = { app ->
                    commitFolders(
                        removeFromFolders(drawerFolders, "${app.pkg}/${app.activity}")
                    )
                },
                onClose = { drawerFolder = null }
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
                canRemove = !single,
                onRemoveFromHome = { i ->
                    commit(layout.filterNot { it.item.key == i.key }); itemMenu = null
                },
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
                homeItems = layout,
                onRemoveWidget = { slot ->
                    (slot.item as? Item.Widget)?.let { widgets.release(it.appWidgetId) }
                    commit(layout.filterNot { it.item.key == slot.item.key })
                },
                style = style,
                single = single,
                onSingle = { single = it; prefs.singleSurface = it },
                onStyle = onStyle,
                onResetStyle = onResetStyle,
                // straight back to page one, not "next time you open the launcher": the
                // launcher is the home screen, so it is rarely started from cold again
                onReplayIntro = { prefs.onboarded = false; intro = true; settingsOpen = false },
                styleStore = styleStore,
                onImported = onImported,
                startPage = settingsAt,
                onClose = { closeSettings() }
            )
        }

        // above the gesture bar, and only when nothing is covering the home screen
        if (!drawerOpen && !settingsOpen && !intro && !welcome) {
            Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.BottomCenter) {
                UpdateBanner(prefs, style) { settingsAt = Page.UpdatePg; settingsOpen = true }
            }
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
    grid: @Composable (Modifier) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onOpenDrawer: () -> Unit,
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
            .displayCutoutPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Apps, widgets and the clock, wherever they were put. The clock used to be a
        // fixed header here, which meant it owned the top of the screen whether that was
        // wanted or not; it is an item on the grid now, like everything else.
        grid(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp, bottom = 4.dp))

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
    folders: List<DrawerFolder>,
    iconOf: (Item.App) -> AppEntry?,
    onOpenFolder: (Item.Folder) -> Unit,
    onNewFolder: (AppEntry) -> Unit,
    onPutInFolder: (AppEntry, String) -> Unit,
    onUngroupFolder: (Item.Folder) -> Unit,
    onAddToHome: (AppEntry) -> Unit,
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

    // Folders group the list, so a search flattens them again: looking for an app by name
    // and being told it is inside something would be a worse answer than just showing it.
    val cells = remember(shown, folders, query) {
        if (query.isBlank()) drawerCells(shown, folders)
        else shown.map { DrawerCell.Single(it) }
    }

    var folderSheet by remember { mutableStateOf<Item.Folder?>(null) }
    var pickFolderFor by remember { mutableStateOf<AppEntry?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(panelColor(style))
            .systemBarsPadding()
            .displayCutoutPadding()
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
                items(cells, key = { cellKey(it) }) { cell ->
                    DrawerTile(
                        cell = cell,
                        iconOf = iconOf,
                        onLaunch = onLaunch,
                        onOpenFolder = onOpenFolder,
                        onAppMenu = { sheetFor = it },
                        onFolderMenu = { folderSheet = it }
                    )
                }
            }

            DrawerMode.Pages -> PagedAppGrid(
                cells = cells,
                iconOf = iconOf,
                onLaunch = onLaunch,
                onOpenFolder = onOpenFolder,
                onAppMenu = { sheetFor = it },
                onFolderMenu = { folderSheet = it }
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
                SheetRow("Add to home screen") { onAddToHome(app); sheetFor = null }
                SheetRow("Put in a folder") { pickFolderFor = app; sheetFor = null }
                SheetRow(if (app.packageName in hidden) "Unhide" else "Hide app") {
                    onToggleHidden(app); sheetFor = null
                }
                SheetRow("Rename or change icon") { onCustomise(app); sheetFor = null }
                SheetRow("App info") { onInfo(app); sheetFor = null }
                SheetRow("Uninstall") { onUninstall(app); sheetFor = null }
            }
        }
    }

    folderSheet?.let { folder ->
        ModalBottomSheet(
            onDismissRequest = { folderSheet = null },
            containerColor = Color(style.bgColor)
        ) {
            Column(Modifier.padding(bottom = 28.dp)) {
                Text(
                    folder.name,
                    color = LocalInk.current,
                    fontFamily = style.fontFamily,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    fontSize = 18.sp
                )
                // the name is editable inside the folder, so renaming is just opening it
                SheetRow("Open and rename") { onOpenFolder(folder); folderSheet = null }
                SheetRow("Ungroup folder") { onUngroupFolder(folder); folderSheet = null }
            }
        }
    }

    pickFolderFor?.let { app ->
        ModalBottomSheet(
            onDismissRequest = { pickFolderFor = null },
            containerColor = Color(style.bgColor)
        ) {
            Column(Modifier.padding(bottom = 28.dp)) {
                Text(
                    "Put ${app.label} in",
                    color = LocalInk.current,
                    fontFamily = style.fontFamily,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    fontSize = 18.sp
                )
                SheetRow("New folder") { onNewFolder(app); pickFolderFor = null }
                folders.forEach { f ->
                    SheetRow(f.name) { onPutInFolder(app, f.id); pickFolderFor = null }
                }
            }
        }
    }
}

/** A stable key per drawer row, so the grid does not rebuild everything on a change. */
private fun cellKey(cell: DrawerCell): String = when (cell) {
    is DrawerCell.Group -> "f:${cell.folder.id}"
    is DrawerCell.Single -> "a:${cell.app.packageName}/${cell.app.activityName}"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerTile(
    cell: DrawerCell,
    iconOf: (Item.App) -> AppEntry?,
    onLaunch: (AppEntry) -> Unit,
    onOpenFolder: (Item.Folder) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
    onFolderMenu: (Item.Folder) -> Unit
) {
    val style = LocalStyle.current
    when (cell) {
        is DrawerCell.Single -> AppTile(
            app = cell.app,
            showLabel = style.showLabels,
            onClick = { onLaunch(cell.app) },
            onLongClick = { onAppMenu(cell.app) }
        )

        is DrawerCell.Group -> Box(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .combinedClickable(
                    onClick = { onOpenFolder(cell.folder) },
                    onLongClick = { onFolderMenu(cell.folder) }
                ),
            contentAlignment = Alignment.Center
        ) {
            ItemTile(item = cell.folder, iconOf = iconOf)
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
    grid: @Composable (Modifier) -> Unit,
    onOpenSearch: () -> Unit,
    onGesture: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .homeGestures(
                onUp = onOpenSearch,
                onDown = { onGesture("down") },
                onDoubleTap = { onGesture("double") }
            )
            .systemBarsPadding()
            .displayCutoutPadding()
    ) {
        grid(Modifier.weight(1f))
        Spacer(Modifier.height(14.dp))
    }
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
    canRemove: Boolean,
    onRemoveFromHome: (Item) -> Unit,
    onDismiss: () -> Unit
) {
    val style = LocalStyle.current

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(style.bgColor)) {
        Column(Modifier.padding(bottom = 28.dp)) {
            Text(
                when (item) {
                    is Item.App -> iconOf(item)?.label ?: item.pkg
                    is Item.Folder -> item.name
                    is Item.Widget -> "Widget"
                    is Item.Clock -> "Clock"
                },
                color = LocalInk.current,
                fontFamily = style.familyFor((item as? Item.App)?.let(iconOf)),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                fontSize = 18.sp
            )
            when (item) {
                is Item.App -> {
                    if (canRemove) SheetRow("Remove from home") { onRemoveFromHome(item) }
                    SheetRow(if (item.pkg in hidden) "Unhide" else "Hide app") { onHide(item) }
                    SheetRow("Rename or change icon") { onCustomise(item) }
                    SheetRow("App info") { onInfo(item) }
                    SheetRow("Uninstall") { onUninstall(item) }
                }
                is Item.Folder -> {
                    SheetRow("Ungroup folder") { onUngroup(item) }
                    if (canRemove) SheetRow("Remove from home") { onRemoveFromHome(item) }
                }
                is Item.Widget -> Unit
                is Item.Clock -> SheetRow("Remove from home") { onRemoveFromHome(item) }
            }
        }
    }
}

/**
 * Reads the home layout, bringing it forward from the version that had no free
 * placement if that has not been done yet.
 *
 * Back then the layout was one dense list that only the one-surface mode looked at, and
 * widgets were a stack of their own. With a drawer that list is every app on the phone,
 * which is not what anyone wants appearing on a home screen they never arranged, so it
 * goes. The widgets come across and get spots on the grid.
 *
 * This happens before the first composition on purpose: the grid gives coordinates to
 * anything that has none, and it would happily do that to a list on its way out.
 */
private fun openLayout(
    prefs: Prefs,
    store: LayoutStore,
    widgets: LauncherWidgets,
    single: Boolean,
    columns: Int
): List<Placed> {
    val saved = store.load()
    if (prefs.gridMigrated) {
        val withIt = withClock(saved, prefs, columns)
        if (withIt !== saved) store.save(withIt)
        return withIt
    }

    var next = if (single) saved else saved.filter { it.item is Item.Widget }
    val carried = widgets.stacked()
    if (carried.isNotEmpty()) {
        val have = next.mapNotNull { (it.item as? Item.Widget)?.appWidgetId }.toSet()
        next = next + carried.filterNot { it.first in have }.map { (id, heightDp) ->
            Placed(item = Item.Widget(id), w = columns, h = (heightDp / 96).coerceIn(1, 4))
        }
    }
    widgets.forgetStacked()
    next = withClock(next, prefs, columns)
    store.save(next)
    prefs.gridMigrated = true
    return next
}

/**
 * Puts the clock on the grid the first time this version runs. It was a fixed header
 * before, so a layout from then has no item for it and the screen would come up without
 * one. The top row is where it used to sit, and is the spot it gets if nothing else has
 * claimed it; otherwise it goes in unplaced and the grid finds it room.
 */
private fun withClock(items: List<Placed>, prefs: Prefs, columns: Int): List<Placed> {
    if (prefs.clockOnGrid || items.any { it.item is Item.Clock }) return items
    prefs.clockOnGrid = true
    // two rows, because a clock at any reasonable size is taller than one cell and the
    // date would end up as a sliver underneath it
    fun rowFree(r: Int) = items.none { it.placed && it.page == 0 && it.covers(0, r) }
    val clock = when {
        rowFree(0) && rowFree(1) -> Placed(Item.Clock, page = 0, col = 0, row = 0, w = columns, h = 2)
        rowFree(0) -> Placed(Item.Clock, page = 0, col = 0, row = 0, w = columns, h = 1)
        else -> Placed(Item.Clock, w = columns, h = 2)
    }
    return listOf(clock) + items
}

/**
 * Pulls one app back out of a folder. It comes out unplaced, so the grid gives it the
 * first free cell rather than landing it on top of something.
 */
private fun moveOutOfFolder(items: List<Placed>, folderId: String, app: Item.App): List<Placed> {
    val idx = items.indexOfFirst { (it.item as? Item.Folder)?.id == folderId }
    if (idx < 0) return items
    val slot = items[idx]
    val f = slot.item as Item.Folder
    val remaining = f.apps.filterNot { it.pkg == app.pkg && it.activity == app.activity }

    val out = items.toMutableList()
    when (remaining.size) {
        // the folder is gone either way, so the app that is left keeps the folder's cell
        0 -> out[idx] = slot.copy(item = app)
        1 -> {
            out[idx] = slot.copy(item = remaining.first())
            out.add(idx + 1, Placed(app))
        }
        else -> {
            out[idx] = slot.copy(item = f.copy(apps = remaining))
            out.add(idx + 1, Placed(app))
        }
    }
    return out
}

/** Dissolves a folder. The first app keeps its cell, the rest get spots of their own. */
private fun ungroup(items: List<Placed>, folderId: String): List<Placed> {
    val idx = items.indexOfFirst { (it.item as? Item.Folder)?.id == folderId }
    if (idx < 0) return items
    val slot = items[idx]
    val f = slot.item as Item.Folder
    val out = items.toMutableList()
    out.removeAt(idx)
    f.apps.forEachIndexed { i, app ->
        out.add(idx + i, if (i == 0) slot.copy(item = app) else Placed(app))
    }
    return out
}

/** Drawer laid out as swipeable pages instead of one long scroll. */
@Composable
private fun PagedAppGrid(
    cells: List<DrawerCell>,
    iconOf: (Item.App) -> AppEntry?,
    onLaunch: (AppEntry) -> Unit,
    onOpenFolder: (Item.Folder) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
    onFolderMenu: (Item.Folder) -> Unit,
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
        val pages = remember(cells, capacity) {
            cells.chunked(capacity).ifEmpty { listOf(emptyList()) }
        }
        val pagerState = rememberPagerState(pageCount = { pages.size })

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val pageCells = pages.getOrElse(page) { emptyList() }
            Column(Modifier.fillMaxSize()) {
                repeat(rows) { r ->
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        repeat(cols) { c ->
                            Box(
                                Modifier.weight(1f).fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                pageCells.getOrNull(r * cols + c)?.let { cell ->
                                    DrawerTile(
                                        cell = cell,
                                        iconOf = iconOf,
                                        onLaunch = onLaunch,
                                        onOpenFolder = onOpenFolder,
                                        onAppMenu = onAppMenu,
                                        onFolderMenu = onFolderMenu
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
