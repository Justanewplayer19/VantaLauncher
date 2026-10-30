package com.maria.homelauncher.ui

import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.Text
import com.maria.homelauncher.AppEntry
import com.maria.homelauncher.Item
import com.maria.homelauncher.LauncherWidgets
import com.maria.homelauncher.LocalBadges
import com.maria.homelauncher.LocalInk
import com.maria.homelauncher.LocalStyle
import com.maria.homelauncher.Placed
import com.maria.homelauncher.fitsHere
import com.maria.homelauncher.newFolderId
import com.maria.homelauncher.packUnplaced
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Pointer must land inside this fraction of a cell half-extent to mean "make a folder". */
private const val FOLDER_HIT = 0.38f

/** How far sideways counts as a swipe, matching the one the rest of the home screen uses. */
private const val SIDE_SWIPE_DP = 72

/**
 * The home surface: a grid of cells that anything can be put anywhere in. Items keep the
 * spot they were dropped on, gaps are allowed, and widgets sit in the same grid as the
 * icons rather than in a stack of their own.
 *
 * Long pressing an icon picks it up straight away. Long pressing bare wallpaper turns on
 * edit mode, which is what makes widgets movable: outside it their own views own every
 * touch, which is the whole point of a widget.
 */
@Composable
fun HomeGrid(
    placed: List<Placed>,
    iconOf: (Item.App) -> AppEntry?,
    widgets: LauncherWidgets,
    editing: Boolean,
    onEditing: (Boolean) -> Unit,
    onLaunch: (Item.App) -> Unit,
    onChange: (List<Placed>) -> Unit,
    onOpenFolder: (Item.Folder) -> Unit,
    onItemMenu: (Item) -> Unit,
    onWallpaper: () -> Unit,
    onStyle: () -> Unit,
    pages: Int,
    /** New page count, and the page to take away first, or -1 when none goes. */
    onPages: (Int, Int) -> Unit,
    onPlaceWidget: (AppWidgetProviderInfo, Int, Int, Int, Int, Int) -> Unit,
    onCellSize: (Float, Float) -> Unit,
    canRemoveApps: Boolean,
    /**
     * Set when a double tap has somewhere to go. The home screen's gesture modifier sits
     * on the parent and never sees one: the bare-wallpaper box below is a child, it holds
     * the detector that answers a long press, and it consumes the touch before the parent
     * is asked. So a double tap has to be answered here or not at all.
     */
    onDoubleTap: (() -> Unit)? = null,
    /** -1 for a swipe left, 1 for a swipe right. Only consulted on a single page. */
    onSideSwipe: ((Int) -> Unit)? = null,
    /** An app on its way over from the drawer. The grid aims it and puts it down. */
    carry: CarryOut? = null,
    /** Where the dock is, in window coordinates. Empty when there is no dock. */
    dockRect: Rect = Rect.Zero,
    /** Hands an icon to the dock at that x. False when the dock would not take it. */
    onToDock: (Item.App, Float) -> Boolean = { _, _ -> false },
    modifier: Modifier = Modifier
) {
    val style = LocalStyle.current
    val density = LocalDensity.current

    var rootPos by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { rootPos = it.positionInWindow() }
    ) {
        val cols = style.columns
        val guessH = with(density) {
            (style.iconDp.dp + (if (style.showLabels) 26.dp else 10.dp) + 18.dp).toPx()
        }
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val fit = maxOf(1, floor(hPx / guessH).toInt())
        val rows = if (style.rows > 0) minOf(style.rows, 12) else fit
        val cellW = wPx / cols
        val cellH = hPx / rows

        // the widget picker needs this to work out how many cells a provider wants
        LaunchedEffect(cellW, cellH) {
            with(density) { onCellSize(cellW.toDp().value, cellH.toDp().value) }
        }

        // anything without coordinates (a fresh install, or a layout saved by the version
        // that kept one flat list) gets a spot the first time the grid knows its own size
        LaunchedEffect(placed, cols, rows) {
            val packed = packUnplaced(placed, cols, rows)
            if (packed !== placed) onChange(packed)
        }

        val live = remember(placed) { placed.filter { it.placed } }
        val lastPage = remember(live) { live.maxOfOrNull { it.page } ?: 0 }

        var dragKey by remember { mutableStateOf<String?>(null) }
        var pointer by remember { mutableStateOf(Offset.Zero) }
        var grab by remember { mutableStateOf(Offset.Zero) }
        var dropCol by remember { mutableStateOf(0) }
        var dropRow by remember { mutableStateOf(0) }
        var dropOk by remember { mutableStateOf(true) }
        var folderOnto by remember { mutableStateOf<String?>(null) }
        var edgeDir by remember { mutableStateOf(0) }
        // the finger has left the grid and is over the dock, which is a different
        // question from which cell it is nearest
        var overDock by remember { mutableStateOf(false) }

        var tray by remember { mutableStateOf(false) }
        var trayInfo by remember { mutableStateOf<AppWidgetProviderInfo?>(null) }
        var traySpan by remember { mutableStateOf(2 to 1) }

        var sizeKey by remember { mutableStateOf<String?>(null) }
        var sizeW by remember { mutableStateOf(1) }
        var sizeH by remember { mutableStateOf(1) }
        var sizeCorner by remember { mutableStateOf(Offset.Zero) }

        val dragged = live.firstOrNull { it.item.key == dragKey }
        // an app being carried in counts as busy too: the lattice belongs up, the pager
        // has to stay still, and the spare page at the end has to exist
        val coming = carry?.app
        val busy = dragKey != null || sizeKey != null || trayInfo != null || coming != null

        // Pages are kept on purpose now, empty or not, so the count is whichever is
        // larger: what was asked for, or what is needed to hold the furthest item. The
        // spare one only appears mid-drag, so something can be carried to a page that
        // does not exist yet.
        val kept = maxOf(pages, lastPage + 1)
        val pageCount = kept + if (busy) 1 else 0
        val pagerState = rememberPagerState(pageCount = { pageCount })

        LaunchedEffect(edgeDir, pagerState.currentPage) {
            if (edgeDir == 0) return@LaunchedEffect
            delay(550)
            val next = pagerState.currentPage + edgeDir
            if (next in 0 until pageCount) pagerState.animateScrollToPage(next)
        }

        fun commit(next: List<Placed>) = onChange(next)

        fun moveTo(key: String, page: Int, col: Int, row: Int) {
            commit(placed.map {
                if (it.item.key == key) it.copy(page = page, col = col, row = row) else it
            })
        }

        fun resize(key: String, w: Int, h: Int) {
            commit(placed.map { if (it.item.key == key) it.copy(w = w, h = h) else it })
        }

        fun remove(p: Placed) {
            (p.item as? Item.Widget)?.let { widgets.release(it.appWidgetId) }
            commit(placed.filterNot { it.item.key == p.item.key })
        }

        /** Window coordinates to the cell under them. */
        fun cellAt(where: Offset): Pair<Int, Int> {
            val local = where - rootPos
            return (local.x / cellW).toInt().coerceIn(0, cols - 1) to
                (local.y / cellH).toInt().coerceIn(0, rows - 1)
        }

        // The drawer drives the carry and the grid answers it. Everything here runs from
        // the drawer's gesture rather than from a composition, which is why it writes to
        // the same drag state the grid's own drags use: one carried thing at a time, one
        // set of variables, one landing outline.
        if (carry != null) {
            DisposableEffect(carry, cellW, cellH, cols, rows, rootPos, live) {
                carry.aim = { where ->
                    val local = where - rootPos
                    pointer = local
                    overDock = dockRect.contains(where)
                    val (tc, tr) = cellAt(where)
                    dropCol = tc
                    dropRow = tr
                    // an app already on the home screen is allowed to land on itself
                    val self = carry.app?.let { Item.App(it.packageName, it.activityName) }
                    val mine = self?.let { me -> placed.firstOrNull { it.item.key == me.key } }
                    dropOk = fitsHere(
                        live,
                        Placed(self ?: Item.Clock, pagerState.currentPage, tc, tr, 1, 1),
                        cols, rows,
                        mine?.item
                    )
                    edgeDir = when {
                        local.x < wPx * 0.06f -> -1
                        local.x > wPx * 0.94f -> 1
                        else -> 0
                    }
                }
                carry.put = { item, where ->
                    val (tc, tr) = cellAt(where)
                    val page = pagerState.currentPage
                    val already = placed.firstOrNull { it.item.key == item.key }
                    val room = fitsHere(
                        live,
                        Placed(item, page, tc, tr, 1, 1),
                        cols, rows,
                        already?.item
                    )
                    commit(
                        when {
                            // already on the home screen somewhere: this is a move
                            already != null && room ->
                                placed.map {
                                    if (it.item.key == item.key) {
                                        it.copy(page = page, col = tc, row = tr)
                                    } else it
                                }
                            already != null -> placed
                            room -> placed + Placed(item, page, tc, tr)
                            // dropped on a taken cell: it still comes over, it just has
                            // to find its own room rather than being thrown away
                            else -> placed + Placed(item)
                        }
                    )
                }
                carry.drop = { edgeDir = 0; overDock = false }
                onDispose {
                    carry.aim = null
                    carry.put = null
                    carry.drop = null
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !busy,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val pageItems = live.filter { it.page == page }

            Box(Modifier.fillMaxSize()) {
                // bare wallpaper: hold to rearrange, tap to stop, and whatever a double
                // tap was set to. A single page has no sideways scrolling to protect, so
                // the swipe gestures live here too in that case; with more than one page
                // the pager has the better claim on a sideways drag.
                val sideways = onSideSwipe.takeIf { kept == 1 && !editing }
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(editing, onDoubleTap != null) {
                            detectTapGestures(
                                onLongPress = { if (!editing) onEditing(true) },
                                onDoubleTap = onDoubleTap?.let { act -> { _: Offset -> act() } },
                                onTap = { if (editing) onEditing(false) }
                            )
                        }
                        .then(
                            if (sideways == null) Modifier else Modifier.pointerInput(sideways) {
                                var total = 0f
                                var fired = false
                                detectHorizontalDragGestures(
                                    onDragStart = { total = 0f; fired = false },
                                    onDragEnd = { total = 0f; fired = false },
                                    onDragCancel = { total = 0f; fired = false }
                                ) { _, dx ->
                                    if (fired) return@detectHorizontalDragGestures
                                    total += dx
                                    val threshold = SIDE_SWIPE_DP.dp.toPx()
                                    if (total < -threshold) { fired = true; sideways(-1) }
                                    else if (total > threshold) { fired = true; sideways(1) }
                                }
                            }
                        )
                )

                if (editing || busy) {
                    val ink = LocalInk.current
                    Canvas(Modifier.fillMaxSize()) {
                        val inset = 6f
                        for (r in 0 until rows) {
                            for (c in 0 until cols) {
                                drawRoundRect(
                                    color = ink.copy(alpha = 0.16f),
                                    topLeft = Offset(c * cellW + inset, r * cellH + inset),
                                    size = Size(cellW - inset * 2, cellH - inset * 2),
                                    cornerRadius = CornerRadius(26f, 26f),
                                    style = Stroke(width = 2.5f)
                                )
                            }
                        }
                    }
                }

                // where the carried item would land. Nothing is landing on the grid while
                // the finger is over the dock, so the outline would be a lie.
                if (busy && !overDock && sizeKey == null &&
                    page == pagerState.currentPage && folderOnto == null
                ) {
                    val ink = LocalInk.current
                    // an app off the drawer is always one cell; a widget brings its own size
                    val dw = dragged?.w ?: if (coming != null) 1 else traySpan.first
                    val dh = dragged?.h ?: if (coming != null) 1 else traySpan.second
                    Canvas(Modifier.fillMaxSize()) {
                        val inset = 6f
                        val at = Offset(dropCol * cellW + inset, dropRow * cellH + inset)
                        val box = Size(cellW * dw - inset * 2, cellH * dh - inset * 2)
                        drawRoundRect(
                            color = if (dropOk) ink.copy(alpha = 0.20f) else Color.Red.copy(alpha = 0.18f),
                            topLeft = at,
                            size = box,
                            cornerRadius = CornerRadius(26f, 26f)
                        )
                        drawRoundRect(
                            color = if (dropOk) ink.copy(alpha = 0.85f) else Color.Red.copy(alpha = 0.7f),
                            topLeft = at,
                            size = box,
                            cornerRadius = CornerRadius(26f, 26f),
                            style = Stroke(width = 6f)
                        )
                    }
                }

                // the size a widget is being stretched to
                val stretching = live.firstOrNull { it.item.key == sizeKey }
                if (stretching != null && stretching.page == page) {
                    val ink = LocalInk.current
                    Canvas(Modifier.fillMaxSize()) {
                        drawRoundRect(
                            color = ink.copy(alpha = 0.8f),
                            topLeft = Offset(stretching.col * cellW + 4f, stretching.row * cellH + 4f),
                            size = Size(cellW * sizeW - 8f, cellH * sizeH - 8f),
                            cornerRadius = CornerRadius(26f, 26f),
                            style = Stroke(width = 6f)
                        )
                    }
                }

                pageItems.forEach { p ->
                    key(p.item.key) {
                        val isDragged = p.item.key == dragKey
                        Box(
                            Modifier
                                .offset {
                                    IntOffset(
                                        (p.col * cellW).roundToInt(),
                                        (p.row * cellH).roundToInt()
                                    )
                                }
                                .size(
                                    width = with(density) { (cellW * p.w).toDp() },
                                    height = with(density) { (cellH * p.h).toDp() }
                                )
                                .graphicsLayer { alpha = if (isDragged) 0f else 1f }
                        ) {
                            val lift: (Offset) -> Unit = { inTile ->
                                dragKey = p.item.key
                                grab = inTile
                                pointer = Offset(p.col * cellW, p.row * cellH) + inTile
                                dropCol = p.col
                                dropRow = p.row
                                dropOk = true
                                folderOnto = null
                            }

                            val carry: (Offset) -> Unit = { delta ->
                                pointer += delta
                                overDock = p.item is Item.App && p.w == 1 && p.h == 1 &&
                                    dockRect.contains(rootPos + pointer)
                                val left = pointer.x - grab.x
                                val top = pointer.y - grab.y
                                val tc = (left / cellW).roundToInt().coerceIn(0, maxOf(0, cols - p.w))
                                val tr = (top / cellH).roundToInt().coerceIn(0, maxOf(0, rows - p.h))

                                edgeDir = when {
                                    pointer.x < wPx * 0.06f -> -1
                                    pointer.x > wPx * 0.94f -> 1
                                    else -> 0
                                }

                                // dropping one icon squarely onto another makes a folder
                                var onto: String? = null
                                if (p.w == 1 && p.h == 1 && p.item !is Item.Widget) {
                                    val hc = (pointer.x / cellW).toInt().coerceIn(0, cols - 1)
                                    val hr = (pointer.y / cellH).toInt().coerceIn(0, rows - 1)
                                    val under = live.firstOrNull {
                                        it.page == pagerState.currentPage &&
                                            it.item.key != p.item.key &&
                                            it.covers(hc, hr) &&
                                            it.item !is Item.Widget
                                    }
                                    if (under != null) {
                                        val dx = abs(pointer.x - (hc + 0.5f) * cellW) / (cellW / 2f)
                                        val dy = abs(pointer.y - (hr + 0.5f) * cellH) / (cellH / 2f)
                                        if (dx < FOLDER_HIT && dy < FOLDER_HIT) onto = under.item.key
                                    }
                                }
                                folderOnto = onto
                                dropCol = tc
                                dropRow = tr
                                dropOk = onto != null || fitsHere(
                                    live,
                                    p.copy(page = pagerState.currentPage, col = tc, row = tr),
                                    cols, rows, p.item
                                )
                            }

                            val land: (Boolean) -> Unit = { moved ->
                                val onto = folderOnto
                                val landed = pagerState.currentPage
                                val tc = dropCol
                                val tr = dropRow
                                val ok = dropOk
                                val toDock = overDock
                                val dropX = (rootPos + pointer).x
                                dragKey = null
                                folderOnto = null
                                edgeDir = 0
                                overDock = false
                                val app = p.item as? Item.App
                                when {
                                    !moved -> onItemMenu(p.item)
                                    // off the grid and into the dock, which takes it off
                                    // the grid: the same icon in both places would be two
                                    // of it, not one in two places
                                    toDock && app != null && onToDock(app, dropX) -> remove(p)
                                    onto != null -> commit(mergeIntoFolder(placed, p.item.key, onto))
                                    ok -> moveTo(p.item.key, landed, tc, tr)
                                }
                            }

                            when (val item = p.item) {
                                is Item.Clock -> ClockCell(
                                    editing = editing,
                                    onLift = lift,
                                    onCarry = carry,
                                    onLand = { land(true) },
                                    onCancel = { dragKey = null; folderOnto = null; edgeDir = 0 },
                                    onResizeStart = {
                                        sizeKey = p.item.key
                                        sizeW = p.w
                                        sizeH = p.h
                                        sizeCorner = Offset((p.col + p.w) * cellW, (p.row + p.h) * cellH)
                                    },
                                    onResize = { delta ->
                                        sizeCorner += delta
                                        val nw = ((sizeCorner.x - p.col * cellW) / cellW)
                                            .roundToInt().coerceIn(1, cols - p.col)
                                        val nh = ((sizeCorner.y - p.row * cellH) / cellH)
                                            .roundToInt().coerceIn(1, rows - p.row)
                                        if (fitsHere(live, p.copy(w = nw, h = nh), cols, rows, p.item)) {
                                            sizeW = nw
                                            sizeH = nh
                                        }
                                    },
                                    onResizeEnd = {
                                        val k = sizeKey
                                        sizeKey = null
                                        if (k != null) resize(k, sizeW, sizeH)
                                    },
                                    onRemove = { remove(p) }
                                )

                                is Item.Widget -> WidgetCell(
                                    widgets = widgets,
                                    item = item,
                                    editing = editing,
                                    widthDp = with(density) { (cellW * p.w).toDp().value.toInt() },
                                    heightDp = with(density) { (cellH * p.h).toDp().value.toInt() },
                                    onLift = lift,
                                    onCarry = carry,
                                    onLand = { land(true) },
                                    onCancel = { dragKey = null; folderOnto = null; edgeDir = 0 },
                                    onResizeStart = {
                                        sizeKey = p.item.key
                                        sizeW = p.w
                                        sizeH = p.h
                                        sizeCorner = Offset((p.col + p.w) * cellW, (p.row + p.h) * cellH)
                                    },
                                    onResize = { delta ->
                                        sizeCorner += delta
                                        val nw = ((sizeCorner.x - p.col * cellW) / cellW)
                                            .roundToInt().coerceIn(1, cols - p.col)
                                        val nh = ((sizeCorner.y - p.row * cellH) / cellH)
                                            .roundToInt().coerceIn(1, rows - p.row)
                                        if (fitsHere(live, p.copy(w = nw, h = nh), cols, rows, p.item)) {
                                            sizeW = nw
                                            sizeH = nh
                                        }
                                    },
                                    onResizeEnd = {
                                        val k = sizeKey
                                        sizeKey = null
                                        if (k != null) resize(k, sizeW, sizeH)
                                    },
                                    onRemove = { remove(p) }
                                )

                                else -> Box(
                                    Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ItemTile(
                                        item = item,
                                        iconOf = iconOf,
                                        highlightFolder = folderOnto == p.item.key,
                                        modifier = Modifier
                                            .pointerInput(p.item.key) {
                                                detectTapGestures(
                                                    onTap = {
                                                        when (item) {
                                                            is Item.App -> onLaunch(item)
                                                            is Item.Folder -> onOpenFolder(item)
                                                            else -> Unit
                                                        }
                                                    }
                                                )
                                            }
                                            .pointerInput(p.item.key, live, cols, rows) {
                                                var moved = 0f
                                                val slop = with(density) { 14.dp.toPx() }
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { moved = 0f; lift(it) },
                                                    onDrag = { change, delta ->
                                                        change.consume()
                                                        moved += abs(delta.x) + abs(delta.y)
                                                        carry(delta)
                                                    },
                                                    onDragEnd = { land(moved >= slop) },
                                                    onDragCancel = {
                                                        dragKey = null
                                                        folderOnto = null
                                                        edgeDir = 0
                                                    }
                                                )
                                            }
                                    )

                                    if (editing && (canRemoveApps || item is Item.Folder)) {
                                        RemoveBadge(
                                            Modifier.align(Alignment.TopEnd),
                                            onClick = { remove(p) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // the item under the finger, drawn over everything
        dragged?.let { p ->
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (pointer.x - grab.x).roundToInt(),
                            (pointer.y - grab.y).roundToInt()
                        )
                    }
                    .size(
                        width = with(density) { (cellW * p.w).toDp() },
                        height = with(density) { (cellH * p.h).toDp() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when (val item = p.item) {
                    is Item.Clock -> HomeClock()
                    is Item.Widget -> Box(
                        Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(LocalInk.current.copy(alpha = 0.22f))
                    )
                    else -> ItemTile(
                        item = item,
                        iconOf = iconOf,
                        hideLabel = true,
                        modifier = Modifier.scale(1.15f)
                    )
                }
            }
        }

        if (pageCount > 1 && style.showPageDots) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                repeat(pageCount) { i ->
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

        if (editing && !busy && !tray) {
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // which page you are on and how many there are, since an empty page looks
                // like every other empty page
                val here = pagerState.currentPage
                val emptyHere = live.none { it.page == here }

                Row(
                    Modifier.padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // a page with nothing on it can go; one holding something cannot,
                    // since that would be a delete wearing a different label
                    if (emptyHere && kept > 1) {
                        EditChip("− Page") { onPages(kept - 1, here) }
                    }
                    Text(
                        "Page ${here + 1} of $kept",
                        color = LocalInk.current.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontFamily = style.fontFamily,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(style.bgColor).copy(alpha = 0.92f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                    if (kept < 16) EditChip("+ Page") { onPages(kept + 1, -1) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditChip("Wallpaper", onClick = onWallpaper)
                    EditChip("Widgets", onClick = { tray = true })
                    EditChip("Style", onClick = onStyle)
                    EditChip("Done", onClick = { onEditing(false) })
                }
            }
        }

        // a widget on its way out of the tray, drawn as the block it will become
        if (trayInfo != null) {
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (pointer.x - grab.x).roundToInt(),
                            (pointer.y - grab.y).roundToInt()
                        )
                    }
                    .size(
                        width = with(density) { (cellW * traySpan.first).toDp() },
                        height = with(density) { (cellH * traySpan.second).toDp() }
                    )
                    .padding(4.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(LocalInk.current.copy(alpha = 0.25f))
            )
        }

        if (tray) {
            BackHandler(enabled = trayInfo == null) { tray = false }

            val cellWDp = with(density) { cellW.toDp().value }
            val cellHDp = with(density) { cellH.toDp().value }

            fun spanFor(info: AppWidgetProviderInfo): Pair<Int, Int> {
                val (w, h) = widgets.defaultSpan(info, cellWDp, cellHDp)
                return w.coerceAtMost(cols) to h.coerceAtMost(rows)
            }

            if (trayInfo == null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures(onTap = { tray = false }) }
                )
            }

            // Kept in the tree, only invisible, while a widget is being carried: the
            // drag belongs to the row it started on, and taking that row out of the
            // composition would cancel the gesture halfway across the screen.
            WidgetTray(
                widgets = widgets,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { alpha = if (trayInfo == null) 1f else 0f },
                onClose = { tray = false },
                drag = WidgetDragHooks(
                    start = { info, inWindow ->
                        traySpan = spanFor(info)
                        trayInfo = info
                        pointer = inWindow - rootPos
                        grab = Offset(
                            cellW * traySpan.first / 2f,
                            cellH * traySpan.second / 2f
                        )
                        dropCol = 0
                        dropRow = 0
                        dropOk = false
                    },
                    move = { delta ->
                        pointer += delta
                        val tw = traySpan.first
                        val th = traySpan.second
                        val tc = ((pointer.x - grab.x) / cellW)
                            .roundToInt().coerceIn(0, maxOf(0, cols - tw))
                        val tr = ((pointer.y - grab.y) / cellH)
                            .roundToInt().coerceIn(0, maxOf(0, rows - th))
                        edgeDir = when {
                            pointer.x < wPx * 0.06f -> -1
                            pointer.x > wPx * 0.94f -> 1
                            else -> 0
                        }
                        dropCol = tc
                        dropRow = tr
                        dropOk = fitsHere(
                            live,
                            Placed(Item.Widget(-1), pagerState.currentPage, tc, tr, tw, th),
                            cols, rows
                        )
                    },
                    end = {
                        val info = trayInfo
                        val ok = dropOk
                        val landed = pagerState.currentPage
                        val tc = dropCol
                        val tr = dropRow
                        trayInfo = null
                        tray = false
                        edgeDir = 0
                        // a drop on a taken spot still adds the widget, it just has to
                        // find its own room rather than being thrown away
                        if (info != null) {
                            if (ok) onPlaceWidget(info, landed, tc, tr, traySpan.first, traySpan.second)
                            else onPlaceWidget(info, -1, 0, 0, traySpan.first, traySpan.second)
                        }
                    },
                    cancel = { trayInfo = null; edgeDir = 0 }
                ),
                onPick = { info ->
                    val (w, h) = spanFor(info)
                    tray = false
                    onPlaceWidget(info, -1, 0, 0, w, h)
                }
            )
        }
    }
}

/** The catalogue, as a sheet over the surface it is being dragged onto. */
@Composable
private fun WidgetTray(
    widgets: LauncherWidgets,
    modifier: Modifier,
    onClose: () -> Unit,
    drag: WidgetDragHooks,
    onPick: (AppWidgetProviderInfo) -> Unit
) {
    val style = LocalStyle.current
    Column(
        modifier
            .fillMaxWidth()
            .fillMaxHeight(0.72f)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Color(style.bgColor).copy(alpha = 0.97f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Widgets",
                color = LocalInk.current,
                fontSize = 16.sp,
                fontFamily = style.fontFamily,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Close",
                color = LocalInk.current.copy(alpha = 0.7f),
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            WidgetCatalog(widgets, drag, onPick)
        }
    }
}

@Composable
private fun EditChip(text: String, onClick: () -> Unit) {
    val style = LocalStyle.current
    Text(
        text,
        color = LocalInk.current,
        fontSize = 13.sp,
        fontFamily = style.fontFamily,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(style.bgColor).copy(alpha = 0.92f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 12.dp, vertical = 9.dp)
    )
}

@Composable
private fun RemoveBadge(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center
    ) {
        Text("×", color = Color.White, fontSize = 13.sp)
    }
}

/**
 * The time and the date, as they appear on the home screen. Which of the two show, how
 * big they are and in what font all come from [LocalStyle]; where they sit comes from the
 * grid, like everything else.
 */
@Composable
internal fun HomeClock() {
    val style = LocalStyle.current
    if (!style.showClock && !style.showDate) return

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

    // unbounded, so a clock too tall for the cells it was given spills over them rather
    // than squeezing the date down to a sliver
    Column(
        modifier = Modifier.wrapContentHeight(
            align = Alignment.CenterVertically,
            unbounded = true
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (style.showClock) {
            Text(
                time,
                color = LocalInk.current,
                fontSize = style.clockSp.sp,
                fontFamily = style.fontFamily,
                maxLines = 1,
                style = TextStyle(shadow = overWallpaper)
            )
        }
        if (style.showDate) {
            Text(
                date,
                color = LocalInk.current.copy(alpha = 0.85f),
                fontSize = style.dateSp.sp,
                fontFamily = style.fontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = overWallpaper)
            )
        }
    }
}

/**
 * The clock, in whatever cells it was put in. It ignores touches while the surface is
 * idle, so a long press on it reaches the wallpaper underneath and turns on edit mode,
 * which is where it can then be moved, resized or taken off altogether.
 */
@Composable
private fun ClockCell(
    editing: Boolean,
    onLift: (Offset) -> Unit,
    onCarry: (Offset) -> Unit,
    onLand: () -> Unit,
    onCancel: () -> Unit,
    onResizeStart: () -> Unit,
    onResize: (Offset) -> Unit,
    onResizeEnd: () -> Unit,
    onRemove: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        HomeClock()

        if (editing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(LocalInk.current.copy(alpha = 0.12f))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { onLift(it) },
                            onDrag = { change, delta -> change.consume(); onCarry(delta) },
                            onDragEnd = onLand,
                            onDragCancel = onCancel
                        )
                    }
            )

            RemoveBadge(Modifier.align(Alignment.TopEnd).padding(4.dp), onClick = onRemove)

            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { onResizeStart() },
                            onDrag = { change, delta -> change.consume(); onResize(delta) },
                            onDragEnd = onResizeEnd,
                            onDragCancel = onResizeEnd
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("⇲", color = Color.White, fontSize = 13.sp)
            }
        }
    }
}

/**
 * One widget in its cell. Its own view gets every touch while the surface is idle, which
 * is what makes a widget worth having; in edit mode a sheet goes over the top so the
 * drag and the corner handle are what the finger reaches instead.
 */
@Composable
private fun WidgetCell(
    widgets: LauncherWidgets,
    item: Item.Widget,
    editing: Boolean,
    widthDp: Int,
    heightDp: Int,
    onLift: (Offset) -> Unit,
    onCarry: (Offset) -> Unit,
    onLand: () -> Unit,
    onCancel: () -> Unit,
    onResizeStart: () -> Unit,
    onResize: (Offset) -> Unit,
    onResizeEnd: () -> Unit,
    onRemove: () -> Unit
) {
    val info = remember(item.appWidgetId) { widgets.manager.getAppWidgetInfo(item.appWidgetId) }
    if (info == null) return

    // tells the provider how much room it has. Keyed, because the call is a broadcast to
    // the widget and firing it per recomposition would be rude.
    LaunchedEffect(widthDp, heightDp) {
        runCatching {
            widgets.manager.updateAppWidgetOptions(
                item.appWidgetId,
                LauncherWidgets.sizeOptions(widthDp, heightDp)
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(4.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(LocalInk.current.copy(alpha = 0.10f))
    ) {
        AndroidView(
            factory = { ctx ->
                widgets.host.createView(ctx, item.appWidgetId, info).apply {
                    setAppWidget(item.appWidgetId, info)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (editing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(LocalInk.current.copy(alpha = 0.12f))
                    .pointerInput(item.appWidgetId) {
                        detectDragGestures(
                            onDragStart = { onLift(it) },
                            onDrag = { change, delta -> change.consume(); onCarry(delta) },
                            onDragEnd = onLand,
                            onDragCancel = onCancel
                        )
                    }
            )

            RemoveBadge(Modifier.align(Alignment.TopEnd).padding(4.dp), onClick = onRemove)

            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .pointerInput(item.appWidgetId) {
                        detectDragGestures(
                            onDragStart = { onResizeStart() },
                            onDrag = { change, delta -> change.consume(); onResize(delta) },
                            onDragEnd = onResizeEnd,
                            onDragCancel = onResizeEnd
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("⇲", color = Color.White, fontSize = 13.sp)
            }
        }
    }
}

/** Drops the dragged icon onto another one, making or growing a folder. */
private fun mergeIntoFolder(items: List<Placed>, fromKey: String, ontoKey: String): List<Placed> {
    val from = items.firstOrNull { it.item.key == fromKey } ?: return items
    val onto = items.firstOrNull { it.item.key == ontoKey } ?: return items

    val draggedApps: List<Item.App> = when (val d = from.item) {
        is Item.App -> listOf(d)
        is Item.Folder -> d.apps
        else -> return items
    }

    val merged: Item.Folder = when (val t = onto.item) {
        is Item.Folder -> t.copy(apps = t.apps + draggedApps)
        is Item.App -> Item.Folder(newFolderId(), "Folder", listOf(t) + draggedApps)
        else -> return items
    }

    return items.mapNotNull {
        when (it.item.key) {
            fromKey -> null
            ontoKey -> it.copy(item = merged)
            else -> it
        }
    }
}

@Composable
fun ItemTile(
    item: Item,
    iconOf: (Item.App) -> AppEntry?,
    highlightFolder: Boolean = false,
    highlightSlot: Boolean = false,
    hideLabel: Boolean = false,
    modifier: Modifier = Modifier
) {
    val style = LocalStyle.current
    val scale by animateFloatAsState(if (highlightFolder) 1.18f else 1f, label = "folderScale")

    Column(
        modifier = modifier.padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(style.iconDp.dp)
                .scale(scale)
                .then(
                    if (highlightSlot) Modifier.border(
                        2.dp, LocalInk.current.copy(alpha = 0.55f), RoundedCornerShape(16.dp)
                    ) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            when (item) {
                is Item.App -> iconOf(item)?.let {
                    Image(
                        bitmap = it.icon,
                        contentDescription = it.label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().alpha(style.iconAlpha)
                    )
                }
                is Item.Folder -> FolderIcon(item, iconOf)
                else -> Unit
            }

            if (item is Item.App && style.badges && item.pkg in LocalBadges.current) {
                BadgeDot(Modifier.align(Alignment.TopEnd))
            }
        }

        if (style.showLabels && !hideLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                when (item) {
                    is Item.App -> iconOf(item)?.label ?: ""
                    is Item.Folder -> item.name
                    else -> ""
                },
                color = LocalInk.current.copy(alpha = 0.95f),
                fontSize = style.labelSp.sp,
                fontFamily = style.familyFor((item as? Item.App)?.let(iconOf)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = TextStyle(shadow = overWallpaper)
            )
        }
    }
}

@Composable
private fun FolderIcon(folder: Item.Folder, iconOf: (Item.App) -> AppEntry?) {
    val style = LocalStyle.current
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(style.folderRadius))
            .background(LocalInk.current.copy(alpha = style.folderAlpha))
            .padding(5.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (r in 0 until 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (c in 0 until 2) {
                        val a = folder.apps.getOrNull(r * 2 + c)
                        Box(Modifier.weight(1f).aspectRatio(1f)) {
                            a?.let { app ->
                                iconOf(app)?.let {
                                    Image(
                                        bitmap = it.icon,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().alpha(style.iconAlpha)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
