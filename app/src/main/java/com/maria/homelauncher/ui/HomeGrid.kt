package com.maria.homelauncher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.maria.homelauncher.LocalInk
import com.maria.homelauncher.AppEntry
import com.maria.homelauncher.Item
import com.maria.homelauncher.LocalBadges
import com.maria.homelauncher.LocalStyle
import com.maria.homelauncher.newFolderId
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.floor

/** Pointer must land inside this fraction of a cell's half-extent to mean "make a folder". */
private const val FOLDER_HIT = 0.38f

@Composable
fun HomeGrid(
    items: List<Item>,
    iconOf: (Item.App) -> AppEntry?,
    onLaunch: (Item.App) -> Unit,
    onReorder: (List<Item>) -> Unit,
    onOpenFolder: (Item.Folder) -> Unit,
    onItemMenu: (Item) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalStyle.current
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val cols = style.columns
        val cellH = with(density) {
            (style.iconDp.dp + (if (style.showLabels) 26.dp else 10.dp) + 18.dp).toPx()
        }
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val fit = maxOf(1, floor(hPx / cellH).toInt())
        val rows = if (style.rows > 0) minOf(style.rows, 12) else fit
        val capacity = cols * rows
        val cellW = wPx / cols
        val cellHActual = hPx / rows

        val pages = remember(items, capacity) { items.chunked(capacity).ifEmpty { listOf(emptyList()) } }
        val pagerState = rememberPagerState(pageCount = { pages.size })

        var dragFrom by remember { mutableStateOf(-1) }
        var dragPos by remember { mutableStateOf(Offset.Zero) }
        var target by remember { mutableStateOf(-1) }
        var folderTarget by remember { mutableStateOf(-1) }
        var edgeDir by remember { mutableStateOf(0) }

        // Dwell at a page edge during a drag flips the page.
        LaunchedEffect(edgeDir, pagerState.currentPage) {
            if (edgeDir == 0) return@LaunchedEffect
            delay(550)
            val next = pagerState.currentPage + edgeDir
            if (next in pages.indices) pagerState.animateScrollToPage(next)
        }

        fun globalIndex(page: Int, local: Int) = page * capacity + local

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = dragFrom < 0,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val pageItems = pages.getOrElse(page) { emptyList() }

            Column(Modifier.fillMaxSize()) {
                repeat(rows) { r ->
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        repeat(cols) { c ->
                            val local = r * cols + c
                            val gi = globalIndex(page, local)
                            val item = pageItems.getOrNull(local)

                            Box(
                                Modifier.weight(1f).fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (item != null) {
                                    val isDragged = gi == dragFrom
                                    val isFolderTarget = gi == folderTarget
                                    val isTarget = gi == target && folderTarget < 0

                                    ItemTile(
                                        item = item,
                                        iconOf = iconOf,
                                        highlightFolder = isFolderTarget,
                                        highlightSlot = isTarget,
                                        modifier = Modifier
                                            .graphicsLayer { alpha = if (isDragged) 0f else 1f }
                                            .pointerInput(gi, items, capacity) {
                                                detectTapGestures(
                                                    onTap = {
                                                        when (item) {
                                                            is Item.App -> onLaunch(item)
                                                            is Item.Folder -> onOpenFolder(item)
                                                        }
                                                    }
                                                )
                                            }
                                            .pointerInput(gi, items, capacity) {
                                                var moved = 0f
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { local0 ->
                                                        moved = 0f
                                                        dragFrom = gi
                                                        target = gi
                                                        folderTarget = -1
                                                        // start from the dragged cell's centre
                                                        dragPos = Offset(
                                                            (c + 0.5f) * cellW,
                                                            (r + 0.5f) * cellHActual
                                                        ) + (local0 - Offset(size.width / 2f, size.height / 2f))
                                                    },
                                                    onDrag = { change, delta ->
                                                        change.consume()
                                                        moved += abs(delta.x) + abs(delta.y)
                                                        dragPos += delta

                                                        val cx = dragPos.x.coerceIn(0f, wPx - 1f)
                                                        val cy = dragPos.y.coerceIn(0f, hPx - 1f)
                                                        val tc = (cx / cellW).toInt().coerceIn(0, cols - 1)
                                                        val tr = (cy / cellHActual).toInt().coerceIn(0, rows - 1)
                                                        val tLocal = tr * cols + tc
                                                        val tGlobal = globalIndex(pagerState.currentPage, tLocal)

                                                        edgeDir = when {
                                                            cx < wPx * 0.06f -> -1
                                                            cx > wPx * 0.94f -> 1
                                                            else -> 0
                                                        }

                                                        // distance from the hovered cell's centre, normalised
                                                        val ccx = (tc + 0.5f) * cellW
                                                        val ccy = (tr + 0.5f) * cellHActual
                                                        val dx = abs(cx - ccx) / (cellW / 2f)
                                                        val dy = abs(cy - ccy) / (cellHActual / 2f)
                                                        val central = dx < FOLDER_HIT && dy < FOLDER_HIT

                                                        val occupied = tGlobal != dragFrom &&
                                                            tGlobal < items.size
                                                        folderTarget = if (central && occupied) tGlobal else -1
                                                        target = tGlobal.coerceIn(0, maxOf(0, items.size - 1))
                                                    },
                                                    onDragEnd = {
                                                        val from = dragFrom
                                                        val ft = folderTarget
                                                        val to = target
                                                        dragFrom = -1
                                                        folderTarget = -1
                                                        target = -1
                                                        edgeDir = 0

                                                        val tapOnly = with(density) { moved < 14.dp.toPx() }
                                                        if (from < 0) return@detectDragGesturesAfterLongPress

                                                        when {
                                                            tapOnly && ft < 0 && to == from ->
                                                                onItemMenu(items[from])
                                                            ft >= 0 -> onReorder(mergeIntoFolder(items, from, ft))
                                                            to != from -> onReorder(moveItem(items, from, to))
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        dragFrom = -1; folderTarget = -1; target = -1; edgeDir = 0
                                                    }
                                                )
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Drop guide: show the slot lattice while dragging so the landing cell is obvious.
        if (dragFrom >= 0) {
            val localTarget = target - pagerState.currentPage * capacity
            // read outside the draw lambda: a composition local is not available in one
            val ink = LocalInk.current
            Canvas(Modifier.fillMaxSize()) {
                val inset = 6f
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        val li = r * cols + c
                        val isDrop = li == localTarget && folderTarget < 0
                        val box = androidx.compose.ui.geometry.Size(
                            cellW - inset * 2, cellHActual - inset * 2
                        )
                        val at = Offset(c * cellW + inset, r * cellHActual + inset)
                        if (isDrop) {
                            drawRoundRect(
                                color = ink.copy(alpha = 0.18f),
                                topLeft = at,
                                size = box,
                                cornerRadius = CornerRadius(26f, 26f)
                            )
                        }
                        drawRoundRect(
                            color = ink.copy(alpha = if (isDrop) 0.85f else 0.22f),
                            topLeft = at,
                            size = box,
                            cornerRadius = CornerRadius(26f, 26f),
                            style = Stroke(width = if (isDrop) 6f else 2.5f)
                        )
                    }
                }
            }
        }

        // the item under the finger, drawn above the pager
        if (dragFrom in items.indices) {
            val item = items[dragFrom]
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (dragPos.x - cellW / 2f).toInt(),
                            (dragPos.y - cellHActual / 2f).toInt()
                        )
                    }
                    .width(with(density) { cellW.toDp() })
                    .height(with(density) { cellHActual.toDp() }),
                contentAlignment = Alignment.Center
            ) {
                ItemTile(item = item, iconOf = iconOf, hideLabel = true, modifier = Modifier.scale(1.15f))
            }
        }

        if (pages.size > 1 && style.showPageDots) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp),
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

private fun moveItem(items: List<Item>, from: Int, to: Int): List<Item> {
    if (from !in items.indices) return items
    val out = items.toMutableList()
    val it = out.removeAt(from)
    out.add(to.coerceIn(0, out.size), it)
    return out
}

private fun mergeIntoFolder(items: List<Item>, from: Int, onto: Int): List<Item> {
    if (from !in items.indices || onto !in items.indices || from == onto) return items
    val dragged = items[from]
    val target = items[onto]

    val draggedApps: List<Item.App> = when (dragged) {
        is Item.App -> listOf(dragged)
        is Item.Folder -> dragged.apps
    }

    val merged: Item.Folder = when (target) {
        is Item.Folder -> target.copy(apps = target.apps + draggedApps)
        is Item.App -> Item.Folder(newFolderId(), "Folder", listOf(target) + draggedApps)
    }

    val out = items.toMutableList()
    out[onto] = merged
    out.removeAt(from)
    return out
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
