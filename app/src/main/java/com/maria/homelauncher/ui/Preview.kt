package com.maria.homelauncher.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maria.homelauncher.LocalInk
import com.maria.homelauncher.AppEntry
import com.maria.homelauncher.BgMode
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.maria.homelauncher.DrawerMode
import com.maria.homelauncher.GearSide
import com.maria.homelauncher.SearchPos
import com.maria.homelauncher.Style
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Pane { Home, Drawer }

/**
 * A scaled-down mock of the launcher that re-renders as settings change. It shows the real
 * icons (already baked at the current shape and tint) and the first chosen wallpaper, so
 * what you see here is what the real surface will look like.
 */
@Composable
fun StylePreview(
    style: Style,
    apps: List<AppEntry>,
    wallpaperUris: List<String>,
    single: Boolean = false,
    modifier: Modifier = Modifier
) {
    var pane by remember { mutableStateOf(Pane.Home) }
    val wallpaper = rememberWallpaper(wallpaperUris.firstOrNull())

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Pane.entries.forEach { p ->
                val on = p == pane
                Text(
                    if (p == Pane.Drawer && single) "Search" else p.name,
                    color = if (on) Color.Black else LocalInk.current.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (on) Color(style.accent) else LocalInk.current.copy(alpha = 0.08f)
                        )
                        .clickable { pane = p }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(330.dp)
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, LocalInk.current.copy(alpha = 0.15f), RoundedCornerShape(22.dp))
        ) {
            // wallpaper, or a stand-in gradient when none is chosen
            if (wallpaper != null) {
                Image(
                    bitmap = wallpaper,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(style.accent).copy(alpha = 0.35f),
                                    Color(style.bgColor)
                                )
                            )
                        )
                )
            }

            when (pane) {
                Pane.Home -> if (single) SurfacePane(style, apps) else HomePane(style, apps)
                Pane.Drawer -> DrawerPane(style, apps, searchOnly = single)
            }
        }

        Text(
            if (wallpaperUris.isEmpty())
                "Add wallpaper images below to preview them here."
            else "Showing your first wallpaper image.",
            color = LocalInk.current.copy(alpha = 0.4f),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun HomePane(style: Style, apps: List<AppEntry>) {
    Column(
        Modifier.fillMaxSize().padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(10.dp))
        if (style.showClock) {
            Text(
                if (style.clock24) "21:41" else "9:41",
                color = LocalInk.current,
                fontSize = (style.clockSp * 0.42f).sp,
                fontFamily = style.fontFamily
            )
        }
        if (style.showDate) {
            Text(
                "Friday, Oct 23",
                color = LocalInk.current.copy(alpha = 0.8f),
                fontSize = (style.dateSp * 0.7f).sp,
                fontFamily = style.fontFamily
            )
        }

        Spacer(Modifier.weight(1f))

        Row(
            Modifier
                .then(
                    if (style.dockBg) Modifier
                        .clip(RoundedCornerShape((style.dockRadius * 0.6f).dp))
                        .background(LocalInk.current.copy(alpha = 0.10f))
                    else Modifier
                )
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            apps.take(style.dockMax).forEach { MiniIcon(it, style, label = false) }
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** One-surface home: every app on the pages, with a slim clock line instead of a dock. */
@Composable
private fun SurfacePane(style: Style, apps: List<AppEntry>) {
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        val clockLine = when {
            style.showClock && style.showDate ->
                if (style.clock24) "21:41  ·  Fri, Oct 23" else "9:41  ·  Fri, Oct 23"
            style.showClock -> if (style.clock24) "21:41" else "9:41"
            style.showDate -> "Fri, Oct 23"
            else -> ""
        }
        if (clockLine.isNotEmpty()) {
            Text(
                clockLine,
                color = LocalInk.current.copy(alpha = 0.75f),
                fontSize = (style.dateSp * 0.7f).sp,
                fontFamily = style.fontFamily,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                textAlign = TextAlign.Center
            )
        }
        MiniGrid(apps.take(style.columns * 4), style.columns, style)
    }
}

@Composable
private fun DrawerPane(style: Style, apps: List<AppEntry>, searchOnly: Boolean = false) {
    val panel = when (style.bgMode) {
        BgMode.Solid -> Color(style.bgColor)
        else -> Color(style.bgColor).copy(alpha = style.bgAlpha)
    }

    Column(Modifier.fillMaxSize().background(panel).padding(10.dp)) {
        if (style.searchPos == SearchPos.Top) MiniSearch(style)

        Box(Modifier.weight(1f)) {
            val cols = style.columns
            val perPage = cols * 3

            if (searchOnly) {
                Text(
                    "Type to find any app, including hidden ones.",
                    color = LocalInk.current.copy(alpha = 0.4f),
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp)
                )
            } else when (style.drawerMode) {
                DrawerMode.Scroll -> MiniGrid(apps.take(perPage), cols, style)

                DrawerMode.Pages -> {
                    val pages = remember(apps, perPage) {
                        apps.chunked(perPage).ifEmpty { listOf(emptyList()) }
                    }
                    val pager = rememberPagerState(pageCount = { pages.size })

                    HorizontalPager(
                        state = pager,
                        // pages with fewer rows would otherwise float to the middle
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxSize()
                    ) { i ->
                        MiniGrid(pages[i], cols, style)
                    }

                    if (pages.size > 1) {
                        Row(
                            Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            repeat(pages.size) { i ->
                                val on = i == pager.currentPage
                                Box(
                                    Modifier
                                        .size(if (on) 5.dp else 4.dp)
                                        .clip(CircleShape)
                                        .background(
                                            LocalInk.current.copy(alpha = if (on) 0.9f else 0.35f)
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }

        if (style.searchPos == SearchPos.Bottom) MiniSearch(style)
    }
}

@Composable
private fun MiniSearch(style: Style) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val gear = @Composable {
            Icon(
                Icons.Default.Settings,
                null,
                tint = LocalInk.current.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
        if (style.gearSide == GearSide.Left) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { gear() }
        } else {
            Spacer(Modifier.width(24.dp))
        }

        Row(
            Modifier
                .weight(1f)
                .height(26.dp)
                .clip(CircleShape)
                .background(LocalInk.current.copy(alpha = 0.12f))
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                null,
                tint = LocalInk.current.copy(alpha = 0.5f),
                modifier = Modifier.size(11.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Search",
                color = LocalInk.current.copy(alpha = 0.45f),
                fontSize = 9.sp,
                fontFamily = style.fontFamily
            )
        }

        if (style.gearSide == GearSide.Right) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { gear() }
        } else {
            Spacer(Modifier.width(24.dp))
        }
    }
}

@Composable
private fun MiniIcon(app: AppEntry, style: Style, label: Boolean) {
    // scale the real icon size down proportionally so relative sizing stays honest
    val size = (style.iconDp * 0.52f).dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            bitmap = app.icon,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size).alpha(style.iconAlpha)
        )
        if (label) {
            Spacer(Modifier.height(3.dp))
            Text(
                app.label,
                color = LocalInk.current.copy(alpha = 0.9f),
                fontSize = (style.labelSp * 0.62f).sp,
                fontFamily = style.fontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun rememberWallpaper(uri: String?): ImageBitmap? {
    val ctx = LocalContext.current
    var bmp by remember(uri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(uri) {
        if (uri == null) {
            bmp = null
            return@LaunchedEffect
        }
        bmp = withContext(Dispatchers.IO) {
            runCatching {
                // decode bounds first so a full-size photo is not pulled into memory
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(Uri.parse(uri))?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                var sample = 1
                while (opts.outHeight / sample > 900) sample *= 2

                val real = BitmapFactory.Options().apply { inSampleSize = sample }
                ctx.contentResolver.openInputStream(Uri.parse(uri))?.use {
                    BitmapFactory.decodeStream(it, null, real)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    return bmp
}

@Composable
private fun MiniGrid(apps: List<AppEntry>, cols: Int, style: Style) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        apps.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                row.forEach {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        MiniIcon(it, style, label = style.showLabels)
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
