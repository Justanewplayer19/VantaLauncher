package com.maria.homelauncher.ui

import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * A GIF or a video drawn behind everything, as the background.
 *
 * It cannot be the system wallpaper: `WallpaperManager` takes a bitmap, so a moving
 * wallpaper is either a live wallpaper service of its own or a layer the launcher draws
 * itself. This is the second, which keeps it to the launcher's own window and means it
 * stops costing anything the moment another app is in front.
 *
 * Both halves pause with the lifecycle. A background that keeps decoding while the screen
 * belongs to something else is just a way to flatten a battery.
 */
@Composable
fun LiveBackground(uri: String, loop: Boolean, modifier: Modifier = Modifier) {
    if (uri.isBlank()) return
    val ctx = LocalContext.current
    val parsed = remember(uri) { Uri.parse(uri) }
    val isVideo = remember(uri) {
        val mime = runCatching { ctx.contentResolver.getType(parsed) }.getOrNull()
        mime?.startsWith("video/") == true ||
            Regex("\\.(mp4|webm|mkv|3gp|m4v|mov)$", RegexOption.IGNORE_CASE)
                .containsMatchIn(parsed.toString())
    }

    if (isVideo) VideoBackground(parsed, loop, modifier)
    else AnimatedBackground(parsed, loop, modifier)
}

/**
 * GIF and animated WebP. [AnimatedImageDrawable] arrived in API 28; below that the file
 * still shows, as its first frame, which is better than a blank screen and is the same
 * thing a non-repeating animation ends on anyway.
 */
@Composable
private fun AnimatedBackground(uri: Uri, loop: Boolean, modifier: Modifier) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val drawable by produceState<Drawable?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeDrawable(
                        ImageDecoder.createSource(ctx.contentResolver, uri)
                    )
                } else {
                    ctx.contentResolver.openInputStream(uri)?.use {
                        BitmapDrawable(ctx.resources, android.graphics.BitmapFactory.decodeStream(it))
                    }
                }
            }.getOrNull()
        }
    }

    val shown = drawable ?: return

    DisposableEffect(lifecycle, shown) {
        val observer = LifecycleEventObserver { _, event ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@LifecycleEventObserver
            val animated = shown as? AnimatedImageDrawable ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> runCatching { animated.start() }
                Lifecycle.Event.ON_PAUSE -> runCatching { animated.stop() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    AndroidView(
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
        update = { view ->
            view.setImageDrawable(shown)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                (shown as? AnimatedImageDrawable)?.let {
                    // repeatCount counts repeats after the first run, so 0 plays once and
                    // leaves the last frame up, which is the other half of the setting
                    it.repeatCount =
                        if (loop) AnimatedImageDrawable.REPEAT_INFINITE else 0
                    runCatching { it.start() }
                }
            }
        },
        modifier = modifier
    )
}

/**
 * Video, on a TextureView rather than a SurfaceView: a SurfaceView punches a hole through
 * the window, and this one is translucent on purpose.
 *
 * Not looping means the player simply reaches the end and stops, and the last frame stays
 * on the texture, which is exactly the "stop on the last frame" behaviour.
 */
@Composable
private fun VideoBackground(uri: Uri, loop: Boolean, modifier: Modifier) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(uri) { MediaPlayer() }
    var ready by remember(uri) { mutableStateOf(false) }

    DisposableEffect(player) {
        onDispose { runCatching { player.release() } }
    }

    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // start() before prepare throws, hence the guard rather than a try
                Lifecycle.Event.ON_RESUME -> runCatching { if (ready) player.start() }
                Lifecycle.Event.ON_PAUSE -> runCatching { if (player.isPlaying) player.pause() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // looping can change without the file changing, and the player takes it live
    LaunchedEffect(loop, ready) {
        if (ready) runCatching { player.isLooping = loop }
    }

    AndroidView(
        factory = { context ->
            TextureView(context).apply {
                isOpaque = false
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        texture: SurfaceTexture, width: Int, height: Int
                    ) {
                        runCatching {
                            player.reset()
                            player.setDataSource(context, uri)
                            player.setSurface(Surface(texture))
                            // a background with sound is nobody's idea of a background
                            player.setVolume(0f, 0f)
                            player.isLooping = loop
                            player.setOnPreparedListener {
                                ready = true
                                cropToFill(this@apply, it.videoWidth, it.videoHeight)
                                runCatching { it.start() }
                            }
                            player.prepareAsync()
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(
                        texture: SurfaceTexture, width: Int, height: Int
                    ) {
                        if (ready) {
                            cropToFill(this@apply, player.videoWidth, player.videoHeight)
                        }
                    }

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        ready = false
                        runCatching { player.reset() }
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
        modifier = modifier
    )
}

/**
 * A TextureView stretches whatever it is given to its own shape. This scales the larger
 * way instead and lets the overflow fall off the edges, which is what every other
 * wallpaper does.
 */
private fun cropToFill(view: TextureView, videoWidth: Int, videoHeight: Int) {
    if (videoWidth <= 0 || videoHeight <= 0 || view.width <= 0 || view.height <= 0) return
    val viewRatio = view.width.toFloat() / view.height
    val videoRatio = videoWidth.toFloat() / videoHeight
    val scaleX: Float
    val scaleY: Float
    if (videoRatio > viewRatio) {
        scaleX = videoRatio / viewRatio
        scaleY = 1f
    } else {
        scaleX = 1f
        scaleY = viewRatio / videoRatio
    }
    val matrix = Matrix()
    matrix.setScale(
        max(scaleX, 1f), max(scaleY, 1f),
        view.width / 2f, view.height / 2f
    )
    view.setTransform(matrix)
}
