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
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Pointer must land inside this fraction of a cell half-extent to mean "make a folder". */
private const val FOLDER_HIT = 0.38f

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
    onPlaceWidget: (AppWidgetProviderInfo, Int, Int, Int, Int, Int) -> Unit,
    onCellSize: (Float, Float) -> Unit,
    canRemoveApps: Boolean,
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

        var tray by remember { mutableStateOf(false) }
        var trayInfo by remember { mutableStateOf<AppWidgetProviderInfo?>(null) }
        var traySpan by remember { mutableStateOf(2 to 1) }

        var sizeKey by remember { mutableStateOf<String?>(null) }
        var sizeW by remember { mutableStateOf(1) }
        var sizeH by remember { mutableStateOf(1) }
        var sizeCorner by remember { mutableStateOf(Offset.Zero) }

        val dragged = live.firstOrNull { it.item.key == dragKey }
        val busy = dragKey != null || sizeKey != null || trayInfo != null

        // a spare page on the end while rearranging, so an item can be carried to one
        // that does not exist yet
        val pageCount = lastPage + 1 + if (busy || editing || tray) 1 else 0
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

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !busy,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val pageItems = live.filter { it.page == page }

            Box(Modifier.fillMaxSize()) {
                // bare wallpaper: hold to rearrange, tap to stop
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(editing) {
                            detectTapGestures(
                                onLongPress = { if (!editing) onEditing(true) },
                                onTap = { if (editing) onEditing(false) }
                            )
                        }
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

                // where the carried item would land
                if (busy && sizeKey == null && page == pagerState.currentPage && folderOnto == null) {
                    val ink = LocalInk.current
                    val dw = dragged?.w ?: traySpan.first
                    val dh = dragged?.h ?: traySpan.second
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
                                dragKey = null
                                folderOnto = null
                                edgeDir = 0
                                when {
                                    !moved -> onItemMenu(p.item)
                                    onto != null -> commit(mergeIntoFolder(placed, p.item.key, onto))
                                    ok -> moveTo(p.item.key, landed, tc, tr)
                                }
                            }

                            when (val item = p.item) {
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
                Text(
                    if (pagerState.currentPage == pageCount - 1 && pageCount > 1)
                        "New page  ·  drop something here to keep it"
                    else "Page ${pagerState.currentPage + 1} of $pageCount",
                    color = LocalInk.current.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontFamily = style.fontFamily,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(style.bgColor).copy(alpha = 0.92f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
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
        is Item.Widget -> return items
    }

    val merged: Item.Folder = when (val t = onto.item) {
        is Item.Folder -> t.copy(apps = t.apps + draggedApps)
        is Item.App -> Item.Folder(newFolderId(), "Folder", listOf(t) + draggedApps)
        is Item.Widget -> return items
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
                is Item.Widget -> Unit
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
                    is Item.Widget -> ""
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
