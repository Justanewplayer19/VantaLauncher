package com.maria.homelauncher.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * A track played while the home screen is the thing in front. MP3, OGG, WAV, M4A, FLAC,
 * anything the phone can decode.
 *
 * It does not ask for audio focus. Taking focus would pause whatever is already playing
 * every single time you pressed home, which is not a trade anybody would accept for
 * background music, so this mixes underneath instead and stays quiet by default.
 *
 * It stops the moment the home screen is not in front. An app that keeps playing from
 * behind is a notification you cannot find the source of.
 */
@Composable
fun HomeAudio(uri: String, loop: Boolean, volume: Int) {
    if (uri.isBlank()) return
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val parsed = remember(uri) { Uri.parse(uri) }
    val player = remember(uri) { MediaPlayer() }
    var ready by remember(uri) { mutableStateOf(false) }

    DisposableEffect(player, parsed) {
        runCatching {
            // has to be set before prepare, and media rather than notification so the
            // volume key the user reaches for is the one that moves it
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player.setDataSource(ctx, parsed)
            player.isLooping = loop
            player.setOnPreparedListener {
                ready = true
                it.setVolume(volume / 100f, volume / 100f)
                // only start straight away if the home screen is actually in front: the
                // file can finish preparing after something else has already come forward
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    runCatching { it.start() }
                }
            }
            player.prepareAsync()
        }
        onDispose {
            ready = false
            runCatching { if (player.isPlaying) player.stop() }
            runCatching { player.release() }
        }
    }

    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> runCatching { if (ready) player.start() }
                Lifecycle.Event.ON_PAUSE -> runCatching { if (player.isPlaying) player.pause() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // both of these are changed from settings while the player is already going
    LaunchedEffect(loop, ready) {
        if (ready) runCatching { player.isLooping = loop }
    }
    LaunchedEffect(volume, ready) {
        if (ready) runCatching { player.setVolume(volume / 100f, volume / 100f) }
    }
}
