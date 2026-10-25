package com.maria.homelauncher.ui

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maria.homelauncher.LocalInk
import com.maria.homelauncher.AppEntry
import com.maria.homelauncher.IconShape
import com.maria.homelauncher.Style
import kotlinx.coroutines.launch

private const val PAGES = 6

@Composable
fun Onboarding(
    style: Style,
    onStyle: (Style) -> Unit,
    single: Boolean,
    onSingle: (Boolean) -> Unit,
    apps: List<AppEntry>,
    wallpaperUris: List<String>,
    onSkip: () -> Unit,
    onDone: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { PAGES })
    val accent = Color(style.accent)

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(style.bgColor))
            .systemBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.End
        ) {
            if (pager.currentPage < PAGES - 1) {
                Text(
                    "Skip",
                    color = LocalInk.current.copy(alpha = 0.55f),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onSkip)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        HorizontalPager(
            state = pager,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.weight(1f)
        ) { page ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 26.dp)
            ) {
                when (page) {
                    0 -> Welcome(accent)
                    1 -> MakeItHome(accent) {
                        val i = Intent(AndroidSettings.ACTION_HOME_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        val ok = runCatching { ctx.startActivity(i) }.isSuccess
                        if (!ok) {
                            runCatching {
                                ctx.startActivity(
                                    Intent(AndroidSettings.ACTION_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    }
                    2 -> GettingAround(accent)
                    3 -> PickALook(style, onStyle, apps, wallpaperUris, single, accent)
                    4 -> TwoModes(style, single, onSingle, apps, wallpaperUris, accent)
                    5 -> Finish(accent)
                }
                Spacer(Modifier.height(30.dp))
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(PAGES) { i ->
                val on = i == pager.currentPage
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (on) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (on) accent else LocalInk.current.copy(alpha = 0.25f))
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pager.currentPage > 0) {
                OutlinedButton(onClick = {
                    scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
                }) { Text("Back") }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            Button(onClick = {
                if (pager.currentPage == PAGES - 1) onDone()
                else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            }) {
                Text(if (pager.currentPage == PAGES - 1) "Start" else "Next")
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, color = LocalInk.current, fontSize = 27.sp, modifier = Modifier.padding(bottom = 14.dp))
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        color = LocalInk.current.copy(alpha = 0.72f),
        fontSize = 15.sp,
        lineHeight = 23.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun Bullet(head: String, text: String, accent: Color) {
    Row(Modifier.padding(bottom = 14.dp)) {
        Box(
            Modifier
                .padding(top = 7.dp, end = 12.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Column {
            Text(head, color = LocalInk.current, fontSize = 15.sp)
            Text(
                text,
                color = LocalInk.current.copy(alpha = 0.6f),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun Welcome(accent: Color) {
    Spacer(Modifier.height(30.dp))
    Text("Home", color = accent, fontSize = 44.sp)
    Spacer(Modifier.height(10.dp))
    Title("A launcher you can shape.")
    Body(
        "This replaces your home screen. Nothing here is permanent and nothing is " +
            "set in stone. Every choice in this intro can be changed later."
    )
    Body("Six short screens. You can skip them.")
}

@Composable
private fun MakeItHome(accent: Color, onOpen: () -> Unit) {
    Spacer(Modifier.height(20.dp))
    Title("Make it your home screen")
    Body(
        "Right now you are probably still using your old launcher. Android decides " +
            "which one opens when you press the home button, and you have to choose."
    )
    Bullet("Open home app settings", "The button below takes you straight there.", accent)
    Bullet("Pick \"Home\"", "That is this app.", accent)
    Bullet("Changed your mind?", "Same screen, pick your old launcher back. Nothing is lost.", accent)

    Spacer(Modifier.height(6.dp))
    Button(onClick = onOpen) { Text("Open home app settings") }

    Spacer(Modifier.height(16.dp))
    Body(
        "If that button does not land on the right page, it is under " +
            "Settings → Apps → Default apps → Home app."
    )
}

@Composable
private fun GettingAround(accent: Color) {
    Spacer(Modifier.height(20.dp))
    Title("Getting around")
    Bullet("Swipe up for your apps", "Start the swipe from the middle of the screen, not the very bottom edge. That part belongs to Android.", accent)
    Bullet("Type to search", "The search bar finds any app, including ones you have hidden.", accent)
    Bullet("Long-press an app", "Add it to the dock, hide it, open app info, or uninstall it.", accent)
    Bullet("Back does nothing on home", "That is deliberate. Home is the bottom of the stack.", accent)
    Bullet("The gear is your settings", "It sits next to the search bar.", accent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickALook(
    style: Style,
    onStyle: (Style) -> Unit,
    apps: List<AppEntry>,
    wallpaperUris: List<String>,
    single: Boolean,
    accent: Color
) {
    Spacer(Modifier.height(14.dp))
    Title("Pick a look")
    Body("The preview updates as you change these. Nothing here is final.")

    StylePreview(
        style = style,
        apps = apps,
        wallpaperUris = wallpaperUris,
        single = single,
        modifier = Modifier.padding(bottom = 18.dp)
    )

    Text("Icon shape", color = LocalInk.current.copy(alpha = 0.7f), fontSize = 13.sp)
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 6.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconShape.entries.forEach { shape ->
            FilterChip(
                selected = shape == style.iconShape,
                onClick = { onStyle(style.copy(iconShape = shape)) },
                label = { Text(shape.label) }
            )
        }
    }

    Text("Columns", color = LocalInk.current.copy(alpha = 0.7f), fontSize = 13.sp)
    Row(
        Modifier.padding(top = 6.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        (3..6).forEach { n ->
            val on = n == style.columns
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (on) LocalInk.current.copy(alpha = 0.22f) else LocalInk.current.copy(alpha = 0.06f)
                    )
                    .clickable { onStyle(style.copy(columns = n)) },
                contentAlignment = Alignment.Center
            ) { Text("$n", color = LocalInk.current, fontSize = 15.sp) }
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Show app names", color = LocalInk.current, fontSize = 15.sp)
        Switch(
            checked = style.showLabels,
            onCheckedChange = { onStyle(style.copy(showLabels = it)) }
        )
    }

    Spacer(Modifier.height(10.dp))
    Body(
        "There is much more in Settings: one-colour icons, accent and background " +
            "colours, fonts, where the search bar sits, and a live preview of all of it."
    )
}

@Composable
private fun TwoModes(
    style: Style,
    single: Boolean,
    onSingle: (Boolean) -> Unit,
    apps: List<AppEntry>,
    wallpaperUris: List<String>,
    accent: Color
) {
    Spacer(Modifier.height(14.dp))
    Title("Two ways to use it")

    StylePreview(
        style = style,
        apps = apps,
        wallpaperUris = wallpaperUris,
        single = single,
        modifier = Modifier.padding(bottom = 18.dp)
    )

    Bullet(
        "Home screen plus a drawer",
        "The usual Android way. A home screen with a clock and a dock, and all your apps " +
            "tucked behind a swipe up.",
        accent
    )
    Bullet(
        "One surface",
        "No drawer at all. Every app sits on the home pages, like an iPhone. Drag icons " +
            "around, drop one on another to make a folder, swipe up to search.",
        accent
    )

    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Use one surface", color = LocalInk.current, fontSize = 15.sp)
        Switch(checked = single, onCheckedChange = onSingle)
    }

    Spacer(Modifier.height(10.dp))
    Body("You can flip between them whenever you want. Your folders are remembered either way.")
}

@Composable
private fun Finish(accent: Color) {
    Spacer(Modifier.height(30.dp))
    Title("That is everything")
    Body("A few things worth knowing:")
    Bullet("Wallpapers", "Settings can rotate through a folder of images on a timer, or just set one.", accent)
    Bullet("Hidden apps", "Long-press an app and hide it. It only appears when you search for it.", accent)
    Bullet("Stuck?", "Everything lives behind the gear next to the search bar, including this intro.", accent)
    Spacer(Modifier.height(8.dp))
    Body("Have a poke around. Nothing here can break anything.")
}
