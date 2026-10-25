package com.maria.homelauncher.ui

import android.appwidget.AppWidgetProviderInfo
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.maria.homelauncher.Item
import com.maria.homelauncher.LauncherWidgets
import com.maria.homelauncher.LocalInk
import com.maria.homelauncher.LocalStyle
import com.maria.homelauncher.Placed

/**
 * The Widgets settings page. Two states, not two pages: the list of what you have, and
 * the catalogue. Back inside the catalogue returns to the list rather than leaving
 * settings, which is why this owns a BackHandler of its own.
 *
 * Where a widget sits and how big it is is settled on the home screen itself now, by
 * dragging it, so this page only adds and removes.
 */
@Composable
fun WidgetsPage(
    widgets: LauncherWidgets,
    placed: List<Placed>,
    onRemove: (Placed) -> Unit
) {
    var picking by remember { mutableStateOf(false) }
    BackHandler(enabled = picking) { picking = false }

    if (picking) {
        WidgetCatalog(widgets) { info ->
            widgets.add(info)
            picking = false
        }
        return
    }

    val mine = placed.filter { it.item is Item.Widget }
    val ctx = LocalContext.current

    if (mine.isEmpty()) {
        Text(
            "Nothing placed yet. A widget lands on your home screen, where you can drag " +
                "it anywhere and pull its bottom corner to resize it.",
            color = LocalInk.current.copy(alpha = 0.55f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(bottom = 14.dp)
        )
    }

    mine.forEach { p ->
        val id = (p.item as Item.Widget).appWidgetId
        val info = remember(id) { widgets.manager.getAppWidgetInfo(id) }
        val label = info?.loadLabel(ctx.packageManager) ?: "Widget"

        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(LocalInk.current.copy(alpha = 0.06f))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, color = LocalInk.current, fontSize = 15.sp)
                Text(
                    "Page ${p.page + 1}, ${p.w} by ${p.h}",
                    color = LocalInk.current.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
            OutlinedButton(onClick = { onRemove(p) }) { Text("Remove", fontSize = 13.sp) }
        }
    }

    Spacer(Modifier.height(4.dp))
    Button(onClick = { picking = true }) { Text("Add widget") }

    Hint(
        "Hold your wallpaper to rearrange the home screen, where the same list can be " +
            "dragged straight onto a spot. Some widgets ask you to confirm first, and a " +
            "few open their own setup screen; that is the widget's own doing."
    )
}

/** What a widget drag out of the catalogue reports back to whoever is placing it. */
internal class WidgetDragHooks(
    /** Pointer position in window coordinates, so the caller can map it to its own. */
    val start: (AppWidgetProviderInfo, Offset) -> Unit,
    val move: (Offset) -> Unit,
    val end: () -> Unit,
    val cancel: () -> Unit
)

/**
 * Every widget on the phone, grouped under the app it came from and collapsed until
 * asked for: a phone with a dozen Google apps on it otherwise opens on a wall of
 * previews. Tapping one adds it; holding one and dragging it out is how it gets placed
 * on a particular spot, which is what [drag] is for.
 */
@Composable
internal fun WidgetCatalog(
    widgets: LauncherWidgets,
    drag: WidgetDragHooks? = null,
    onPick: (AppWidgetProviderInfo) -> Unit
) {
    val ctx = LocalContext.current
    val style = LocalStyle.current
    val pm = ctx.packageManager

    val groups = remember {
        widgets.providers()
            .groupBy { it.provider.packageName }
            .map { (pkg, list) -> appLabel(pm, pkg) to list }
            .sortedBy { it.first.lowercase() }
    }

    if (groups.isEmpty()) {
        Text(
            "No apps on this device offer widgets.",
            color = LocalInk.current.copy(alpha = 0.55f),
            fontSize = 13.sp
        )
        return
    }

    var open by remember { mutableStateOf<String?>(null) }

    groups.forEach { (app, list) ->
        val expanded = open == app
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(LocalInk.current.copy(alpha = 0.06f))
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { open = if (expanded) null else app }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    app,
                    color = LocalInk.current,
                    fontSize = 15.sp,
                    fontFamily = style.fontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${list.size}",
                    color = LocalInk.current.copy(alpha = 0.45f),
                    fontSize = 12.sp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (expanded) "⌃" else "⌄",
                    color = LocalInk.current.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
                    list.forEach { info ->
                        WidgetEntry(info, drag, onPick)
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetEntry(
    info: AppWidgetProviderInfo,
    drag: WidgetDragHooks?,
    onPick: (AppWidgetProviderInfo) -> Unit
) {
    val ctx = LocalContext.current
    val style = LocalStyle.current
    val label = info.loadLabel(ctx.packageManager).orEmpty()
    val preview = rememberWidgetPreview(info)
    var pos by remember { mutableStateOf(Offset.Zero) }

    var m = Modifier
        .fillMaxWidth()
        .padding(bottom = 8.dp)
        .clip(RoundedCornerShape(14.dp))
        .clickable { onPick(info) }
        .background(LocalInk.current.copy(alpha = 0.07f))
        .padding(10.dp)

    if (drag != null) {
        m = m
            .onGloballyPositioned { pos = it.positionInWindow() }
            .pointerInput(info.provider) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { local -> drag.start(info, pos + local) },
                    onDrag = { change, delta -> change.consume(); drag.move(delta) },
                    onDragEnd = { drag.end() },
                    onDragCancel = { drag.cancel() }
                )
            }
    }

    Row(m, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            preview?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = LocalInk.current,
                fontSize = 14.sp,
                fontFamily = style.fontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${cells(info.minWidth, ctx)} x ${cells(info.minHeight, ctx)}" +
                    if (drag != null) "  ·  hold to drag out" else "",
                color = LocalInk.current.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }
    }
}

private fun appLabel(pm: PackageManager, pkg: String): String = runCatching {
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
}.getOrDefault(pkg)

/** Rough home-screen cell count, the way every widget list states a size. */
private fun cells(px: Int, ctx: android.content.Context): Int {
    val dp = px / ctx.resources.displayMetrics.density
    return ((dp + 30) / 70).toInt().coerceAtLeast(1)
}

@Composable
private fun rememberWidgetPreview(info: AppWidgetProviderInfo): ImageBitmap? {
    val ctx = LocalContext.current
    return remember(info.provider) {
        runCatching {
            val d = info.loadPreviewImage(ctx, 0) ?: info.loadIcon(ctx, 0)
            d?.toBitmap(
                d.intrinsicWidth.takeIf { it > 0 } ?: 96,
                d.intrinsicHeight.takeIf { it > 0 } ?: 96
            )?.asImageBitmap()
        }.getOrNull()
    }
}
