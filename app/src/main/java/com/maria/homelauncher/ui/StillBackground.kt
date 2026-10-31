package com.maria.homelauncher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.maria.homelauncher.OWN_NEVER
import com.maria.homelauncher.Wallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The home wallpaper, drawn by the launcher rather than by the system.
 *
 * Normally nothing: the window is transparent and the system draws the wallpaper behind
 * it, which is how a launcher is supposed to work and is free. This is for the phone
 * where that surface sometimes comes up empty and the home screen is black while the
 * lock screen is fine. See `Prefs.ownWallpaper` for why none of that is fixable from
 * here, and why drawing a copy is.
 *
 * [mode] is the setting, not the answer: `Wallpaper.drawOwn` works out whether this
 * phone needs it, and it is asked again every time the launcher comes back to the front,
 * so a phone that stops needing it stops paying for it.
 *
 * It sits under everything the launcher draws, so the panels are translucent over it the
 * same way they are over the real thing, and it costs nothing at all while another app
 * is in front: the window is gone and so is the bitmap's only reader.
 */
@Composable
fun StillBackground(mode: Int, modifier: Modifier = Modifier) {
    if (mode == OWN_NEVER) return
    val ctx = LocalContext.current
    var pic by remember { mutableStateOf<ImageBitmap?>(null) }
    var stamp by remember { mutableStateOf(0L) }

    // the rotation can change the picture while another app is in front, so the copy is
    // looked at again on the way back; its date is cheap and decoding it again is not
    var resumed by remember { mutableStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val watch = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumed++
        }
        owner.lifecycle.addObserver(watch)
        onDispose { owner.lifecycle.removeObserver(watch) }
    }

    LaunchedEffect(mode, resumed) {
        val check = withContext(Dispatchers.IO) {
            Wallpaper.drawOwn(ctx, mode) to Wallpaper.ownFile(ctx).lastModified()
        }
        val (wanted, now) = check
        if (!wanted) {
            // the picture behind us is fine again, so let go of ours
            pic = null
            stamp = 0L
            return@LaunchedEffect
        }
        if (pic != null && now == stamp) return@LaunchedEffect
        val next: Bitmap? = Wallpaper.ownCopy(ctx)
        stamp = now
        if (next != null) pic = next.asImageBitmap()
    }

    pic?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}
