package com.maria.homelauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maria.homelauncher.LocalInk
import com.maria.homelauncher.AppEntry
import com.maria.homelauncher.Item
import com.maria.homelauncher.GearSide
import com.maria.homelauncher.LocalStyle
import com.maria.homelauncher.SearchPos

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderOverlay(
    folder: Item.Folder,
    iconOf: (Item.App) -> AppEntry?,
    onLaunch: (Item.App) -> Unit,
    onRename: (String) -> Unit,
    onMoveOut: (Item.App) -> Unit,
    onClose: () -> Unit
) {
    val style = LocalStyle.current
    val accent = Color(style.accent)
    var name by remember(folder.id) { mutableStateOf(folder.name) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(style.bgColor).copy(alpha = 0.97f))
                // swallow taps so clicking the card does not close it
                .clickable(enabled = false) {}
                .padding(20.dp)
        ) {
            BasicTextField(
                value = name,
                onValueChange = { name = it; onRename(it) },
                singleLine = true,
                cursorBrush = SolidColor(accent),
                textStyle = TextStyle(
                    color = LocalInk.current,
                    fontSize = 19.sp,
                    fontFamily = style.fontFamily
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            )
            Text(
                "${folder.apps.size} apps  ·  long-press to move one out",
                color = LocalInk.current.copy(alpha = 0.45f),
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 14.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.heightIn(max = 340.dp)
            ) {
                items(folder.apps, key = { it.key }) { app ->
                    val entry = iconOf(app)
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .combinedClickable(
                                onClick = { onLaunch(app) },
                                onLongClick = { onMoveOut(app) }
                            )
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        entry?.let {
                            Image(
                                bitmap = it.icon,
                                contentDescription = it.label,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(style.iconDp.dp).alpha(style.iconAlpha)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            entry?.label ?: "",
                            color = LocalInk.current.copy(alpha = 0.9f),
                            fontSize = style.labelSp.sp,
                            fontFamily = style.familyFor(entry),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/** Search-only panel for single-surface mode, where there is no app drawer. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchOverlay(
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit
) {
    val style = LocalStyle.current
    val accent = Color(style.accent)
    var query by remember { mutableStateOf("") }

    val hits = remember(apps, query) {
        if (query.isBlank()) emptyList()
        else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    val bar = @Composable {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (style.gearSide == GearSide.Left) {
                Text(
                    "Settings",
                    color = accent,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onSettings)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )
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
                    null,
                    tint = LocalInk.current.copy(alpha = 0.55f),
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            "Search apps",
                            color = LocalInk.current.copy(alpha = 0.45f),
                            fontSize = style.searchSp.sp,
                            fontFamily = style.fontFamily
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
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
                Text(
                    "Settings",
                    color = accent,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onSettings)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
            Text(
                "Close",
                color = accent,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(panelColor(style))
            .systemBarsPadding()
            .displayCutoutPadding()
            .imePadding()
    ) {
        if (style.searchPos == SearchPos.Top) bar()

        Box(Modifier.weight(1f)) {
        if (query.isBlank()) {
            Text(
                "Type to find any app, including hidden ones.",
                color = LocalInk.current.copy(alpha = 0.4f),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(style.columns),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(hits, key = { it.packageName + it.activityName }) { app ->
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onLaunch(app) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            bitmap = app.icon,
                            contentDescription = app.label,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(style.iconDp.dp).alpha(style.iconAlpha)
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            app.label,
                            color = LocalInk.current.copy(alpha = 0.92f),
                            fontSize = style.labelSp.sp,
                            fontFamily = style.familyFor(app),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        }

        if (style.searchPos == SearchPos.Bottom) bar()
    }
}
