package com.maria.homelauncher.ui

import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import com.maria.homelauncher.LocalInk
import com.maria.homelauncher.LauncherWidgets
import com.maria.homelauncher.LocalStyle
import com.maria.homelauncher.WidgetItem

/**
 * The widgets the user placed, stacked vertically. Lives on the home screen in both
 * modes: between the clock and the dock with a drawer, and above the app pages on one
 * surface. Draws nothing at all when there are none, so it costs nothing until used.
 */
@Composable
fun WidgetStack(widgets: LauncherWidgets, modifier: Modifier = Modifier) {
    val items = widgets.items
    if (items.isEmpty()) return

    LaunchedEffect(Unit) { widgets.prune() }

    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEach { item ->
            key(item.appWidgetId) { WidgetFrame(widgets, item) }
        }
    }
}

@Composable
private fun WidgetFrame(widgets: LauncherWidgets, item: WidgetItem) {
    val info = remember(item.appWidgetId) { widgets.manager.getAppWidgetInfo(item.appWidgetId) }
    if (info == null) return

    val widthDp = LocalConfiguration.current.screenWidthDp - 32

    // tells the provider how much room it has. Keyed, because the non-deprecated call
    // is a broadcast to the widget and firing it per recomposition would be rude.
    LaunchedEffect(widthDp, item.heightDp) {
        runCatching {
            widgets.manager.updateAppWidgetOptions(
                item.appWidgetId,
                LauncherWidgets.sizeOptions(widthDp, item.heightDp)
            )
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(item.heightDp.dp)
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
    }
}

// --- settings ---

/**
 * The Widgets settings page. Two states, not two pages: the list of what you have, and
 * the picker. Back inside the picker returns to the list rather than leaving settings,
 * which is why this owns a BackHandler of its own.
 */
@Composable
fun WidgetsPage(widgets: LauncherWidgets) {
    var picking by remember { mutableStateOf(false) }
    BackHandler(enabled = picking) { picking = false }

    if (picking) {
        WidgetPicker(widgets) { info ->
            widgets.add(info)
            picking = false
        }
        return
    }

    val items = widgets.items
    val ctx = LocalContext.current

    if (items.isEmpty()) {
        Text(
            "Nothing placed yet. Widgets sit on your home screen above the dock, or " +
                "above your apps on one surface.",
            color = LocalInk.current.copy(alpha = 0.55f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(bottom = 14.dp)
        )
    }

    items.forEachIndexed { index, item ->
        val info = remember(item.appWidgetId) {
            widgets.manager.getAppWidgetInfo(item.appWidgetId)
        }
        val label = info?.loadLabel(ctx.packageManager) ?: "Widget"

        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(LocalInk.current.copy(alpha = 0.06f))
                .padding(14.dp)
        ) {
            Text(label, color = LocalInk.current, fontSize = 15.sp)

            SliderRow("Height", item.heightDp.toFloat(), 80f, 420f) {
                widgets.setHeight(item.appWidgetId, it.toInt())
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallAction("Up", index > 0) { widgets.move(index, index - 1) }
                SmallAction("Down", index < items.lastIndex) { widgets.move(index, index + 1) }
                Spacer(Modifier.weight(1f))
                SmallAction("Remove", true) { widgets.remove(item.appWidgetId) }
            }
        }
    }

    Spacer(Modifier.height(4.dp))
    Button(onClick = { picking = true }) { Text("Add widget") }

    Hint(
        "Some widgets ask you to confirm before they can be added, and a few open their " +
            "own setup screen first. That is the widget's own doing, not the launcher."
    )
}

@Composable
private fun SmallAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled) {
        Text(text, fontSize = 13.sp)
    }
}

@Composable
private fun WidgetPicker(widgets: LauncherWidgets, onPick: (AppWidgetProviderInfo) -> Unit) {
    val ctx = LocalContext.current
    val style = LocalStyle.current
    val all = remember { widgets.providers() }

    if (all.isEmpty()) {
        Text(
            "No apps on this device offer widgets.",
            color = LocalInk.current.copy(alpha = 0.55f),
            fontSize = 13.sp
        )
        return
    }

    all.forEach { info ->
        val label = info.loadLabel(ctx.packageManager).orEmpty()
        val preview = rememberWidgetPreview(info)

        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { onPick(info) }
                .background(LocalInk.current.copy(alpha = 0.06f))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)),
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
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    label,
                    color = LocalInk.current,
                    fontSize = 15.sp,
                    fontFamily = style.fontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${cells(info.minWidth, ctx)} x ${cells(info.minHeight, ctx)}",
                    color = LocalInk.current.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

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
