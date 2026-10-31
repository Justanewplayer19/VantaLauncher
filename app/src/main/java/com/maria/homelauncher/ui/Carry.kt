package com.maria.homelauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
 * The little i in the corner of an icon: everything that used to need a long press.
 *
 * The hold is faster than it was, but a hold is still a thing you have to know about and
 * wait through. This is a button, and a button needs neither.
 */
@Composable
internal fun InfoBadge(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val style = LocalStyle.current
    val ink = LocalInk.current
    val dot = (style.iconDp * 0.34f).coerceIn(17f, 22f)

    // Only a little bigger than the circle it draws, and never pushed outside the icon:
    // whatever the badge takes, the icon loses, and a tile that opens its options when
    // you meant to open the app is worse than a badge that is a touch small. The tile
    // around it is bigger than the icon, so the miss lands on the app rather than on
    // nothing at all.
    Box(
        modifier
            .size((dot + 4f).dp)
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center
    ) {
        // the panel colour under a ring of the text colour, so it reads on a pale icon
        // and a dark one without knowing which it landed on
        Box(
            Modifier
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
}
