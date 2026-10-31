package com.maria.homelauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maria.homelauncher.LocalInk
import com.maria.homelauncher.LocalStyle

/**
 * How long a finger has to sit still before an icon is its to move.
 *
 * Android's own long press is half a second, which is what this used to be, and half a
 * second of holding still before anything happens is a long time to be told nothing.
 * This is short enough to feel like part of the same movement and long enough that an
 * ordinary tap, which is well under a tenth of a second, never trips it.
 */
private const val HOLD_MS = 200L

/**
 * Tap, hold, and hold-then-drag on one icon, from one detector.
 *
 * Doing this with two modifiers -- a tap detector and one of the stock drag detectors --
 * puts them in a race over the same finger, and both of them win: the app opens and the
 * menu opens behind it. One detector cannot disagree with itself.
 *
 * Nothing is consumed until the hold is through, because until then the finger might
 * belong to the drawer's list or the home pager, and they have the better claim on it.
 * A finger that wanders in that window is handed over by doing nothing at all.
 *
 * [onMove] deltas and the [onStart] position are in the node's own coordinates.
 */
suspend fun PointerInputScope.iconGestures(
    onTap: () -> Unit,
    /** Held, then let go without going anywhere. */
    onStill: () -> Unit,
    onStart: (Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    // the i in the corner is a child of this tile and takes its own touches
    if (down.isConsumed) return@awaitEachGesture
    val slop = viewConfiguration.touchSlop

    var tapped = false
    val gone = withTimeoutOrNull(HOLD_MS) {
        var wander = 0f
        while (true) {
            val event = awaitPointerEvent()
            val c = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!c.pressed) {
                tapped = true
                c.consume()
                break
            }
            // a scroll or a page turn got going: the finger is theirs now
            if (c.isConsumed) break
            wander += (c.position - c.previousPosition).getDistance()
            if (wander > slop) break
        }
    }

    // the timeout firing is the whole signal: the finger stayed, so the icon is ours
    if (gone != null) {
        if (tapped) onTap()
        return@awaitEachGesture
    }

    onStart(down.position)
    var moved = 0f
    var lifted = false
    while (true) {
        val event = awaitPointerEvent()
        val c = event.changes.firstOrNull { it.id == down.id } ?: break
        if (!c.pressed) {
            lifted = true
            c.consume()
            break
        }
        val delta = c.positionChange()
        c.consume()
        if (delta != Offset.Zero) {
            moved += delta.getDistance()
            onMove(delta)
        }
    }

    when {
        !lifted -> onCancel()
        moved < slop -> onStill()
        else -> onEnd()
    }
}

/**
 * Which one icon is showing its i.
 *
 * One, and only while it has been asked for: a hold puts the i on the app under the
 * finger and takes it off whatever had it before. An i on every app at once is noise on
 * a screen that is mostly icons, and it answers a question nobody asked.
 *
 * It sits above all three surfaces because a hold in the drawer has to clear a pick made
 * on the home screen, and the two know nothing about each other.
 */
@Stable
class InfoPick {

    var key by mutableStateOf<String?>(null)
        private set

    /**
     * When the pick last moved.
     *
     * A gesture notes the time it began and leaves the pick alone if it was the one that
     * set it. That is the whole trick behind clearing on a tap anywhere: the hold that
     * puts the i up ends with a finger coming off the screen too, and without this it
     * would clear the pick it had just made.
     */
    var mark = 0L
        private set

    fun show(k: String) {
        key = k
        mark = System.nanoTime()
    }

    fun clear() {
        key = null
        mark = System.nanoTime()
    }
}

val LocalInfoPick = staticCompositionLocalOf { InfoPick() }

/**
 * Takes the i down again at the end of any gesture that did not put it up.
 *
 * On [PointerEventPass.Final] and waiting for the finger to leave, so every tile has
 * already had the event and decided what it meant. A tap on the picked icon opens its
 * options and this clears up behind it; a tap anywhere else just clears.
 */
fun Modifier.clearsInfoPick(pick: InfoPick): Modifier = pointerInput(pick) {
    awaitEachGesture {
        val began = System.nanoTime()
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        do {
            val event = awaitPointerEvent(PointerEventPass.Final)
        } while (event.changes.any { it.pressed })
        if (pick.key != null && pick.mark < began) pick.clear()
    }
}

/**
 * The little i on the icon you held.
 *
 * It is a mark, not a button. The icon it is sitting on is the button: tapping that is
 * what opens the options, so there is no small target to miss and nothing for the i to
 * steal from the app underneath it.
 */
@Composable
internal fun InfoBadge(modifier: Modifier = Modifier) {
    val style = LocalStyle.current
    val ink = LocalInk.current
    val dot = (style.iconDp * 0.34f).coerceIn(17f, 22f)

    // the panel colour under a ring of the text colour, so it reads on a pale icon and a
    // dark one without knowing which it landed on
    Box(
        modifier
            .size(dot.dp)
            .clip(CircleShape)
            .background(Color(style.bgColor).copy(alpha = 0.94f))
            .border(1.dp, ink.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "i",
            color = ink.copy(alpha = 0.95f),
            fontSize = (dot * 0.60f).sp,
            fontWeight = FontWeight.Bold,
            fontFamily = style.fontFamily
        )
    }
}
