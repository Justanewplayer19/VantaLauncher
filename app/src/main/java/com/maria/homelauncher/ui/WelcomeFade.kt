package com.maria.homelauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maria.homelauncher.Style
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The handover from the intro to the home screen.
 *
 * The cover starts as the same flat colour the intro pages were drawn on, so the last page
 * leaves and this arrives without a seam. Then the word shows up, holds, and the whole
 * thing dissolves to reveal the wallpaper and the grid underneath. A tap cuts it short,
 * because nobody wants to sit through an animation twice.
 *
 * Both moving values are read inside `drawBehind` and `graphicsLayer` lambdas rather than
 * in the body. Read in the body, every frame would recompose this whole function; read in
 * a lambda, the frame is a redraw of one rectangle and a nudge to one layer. The cover is
 * a translucent rect and not `Modifier.alpha`, because alpha on a full-screen box makes
 * the renderer allocate and blend an offscreen buffer for all of it, every frame, which on
 * a soft-GPU emulator costs more than the whole frame budget.
 */
@Composable
fun WelcomeFade(style: Style, onEnd: () -> Unit) {
    val end by rememberUpdatedState(onEnd)
    val cover = remember { Animatable(1f) }
    val word = remember { Animatable(0f) }
    val drift = remember { Animatable(16f) }
    val bg = Color(style.bgColor)
    val accent = Color(style.accent)

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { drift.animateTo(0f, tween(1100, easing = LinearOutSlowInEasing)) }
            word.animateTo(1f, tween(900, easing = LinearOutSlowInEasing))
        }
        delay(850)
        word.animateTo(0f, tween(600, easing = LinearEasing))
        // linear, because a long dissolve on an eased curve reads as a lurch then a crawl
        cover.animateTo(0f, tween(1800, easing = LinearEasing))
        end()
    }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(bg, alpha = cover.value) }
            // swallow everything underneath, and let a tap skip the rest
            .pointerInput(Unit) { detectTapGestures { end() } },
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Welcome",
            color = accent,
            fontSize = 40.sp,
            fontFamily = style.fontFamily,
            modifier = Modifier.graphicsLayer {
                alpha = word.value * cover.value
                translationY = drift.value.dp.toPx()
            }
        )
    }
}
