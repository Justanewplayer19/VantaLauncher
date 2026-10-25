package com.maria.homelauncher.ui

import android.graphics.Color as AColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maria.homelauncher.LocalInk

/**
 * Hue / saturation / brightness / alpha bars, a live swatch, and hex + RGBA entry.
 *
 * Dragging only moves local state. [onCommit] fires on release and on a valid typed value,
 * so an expensive listener (re-rendering every icon, say) runs once per gesture instead of
 * once per frame.
 */
@Composable
fun ColorPicker(
    value: Long,
    onCommit: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val hsv = remember { FloatArray(3) }

    var h by remember { mutableStateOf(0f) }
    var s by remember { mutableStateOf(1f) }
    var v by remember { mutableStateOf(1f) }
    var a by remember { mutableStateOf(1f) }

    // pull the incoming colour apart once, and again if it changes from outside
    LaunchedEffect(value) {
        val argb = value.toInt()
        AColor.colorToHSV(argb, hsv)
        h = hsv[0]; s = hsv[1]; v = hsv[2]
        a = ((argb ushr 24) and 0xFF) / 255f
    }

    fun argbNow(): Long {
        val rgb = AColor.HSVToColor(floatArrayOf(h, s, v))
        return ((a * 255f).toInt().coerceIn(0, 255).toLong() shl 24) or (rgb.toLong() and 0xFFFFFF)
    }

    val live = argbNow()

    // pointerInput(Unit) keeps the lambdas it was built with, so this must read the
    // state values rather than close over a remembered snapshot of them
    fun commit() = onCommit(argbNow())

    val liveColor = Color(live)
    val opaque = Color(AColor.HSVToColor(floatArrayOf(h, s, v)))

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(liveColor)
                    .border(1.dp, LocalInk.current.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            )
            Spacer(Modifier.width(14.dp))
            HexField(live) { onCommit(it) }
        }

        Spacer(Modifier.height(14.dp))

        Bar(
            brush = Brush.horizontalGradient(
                (0..6).map { Color(AColor.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) }
            ),
            fraction = h / 360f,
            thumb = Color(AColor.HSVToColor(floatArrayOf(h, 1f, 1f))),
            label = "Hue",
            readout = h.toInt().toString(),
            onChange = { h = it * 360f },
            onCommit = ::commit
        )

        Bar(
            brush = Brush.horizontalGradient(
                listOf(
                    Color(AColor.HSVToColor(floatArrayOf(h, 0f, v))),
                    Color(AColor.HSVToColor(floatArrayOf(h, 1f, v)))
                )
            ),
            fraction = s,
            thumb = opaque,
            label = "Saturation",
            readout = (s * 100).toInt().toString(),
            onChange = { s = it },
            onCommit = ::commit
        )

        Bar(
            brush = Brush.horizontalGradient(
                listOf(Color.Black, Color(AColor.HSVToColor(floatArrayOf(h, s, 1f))))
            ),
            fraction = v,
            thumb = opaque,
            label = "Brightness",
            readout = (v * 100).toInt().toString(),
            onChange = { v = it },
            onCommit = ::commit
        )

        Bar(
            brush = Brush.horizontalGradient(
                listOf(opaque.copy(alpha = 0f), opaque)
            ),
            fraction = a,
            thumb = liveColor,
            label = "Alpha",
            readout = (a * 255).toInt().toString(),
            onChange = { a = it },
            onCommit = ::commit
        )

        Spacer(Modifier.height(12.dp))
        RgbaRow(live) { onCommit(it) }
    }
}

@Composable
private fun Bar(
    brush: Brush,
    fraction: Float,
    thumb: Color,
    label: String,
    readout: String,
    onChange: (Float) -> Unit,
    onCommit: () -> Unit
) {
    val density = LocalDensity.current
    val track = 26.dp
    val knob = 20.dp

    val change by rememberUpdatedState(onChange)
    val commit by rememberUpdatedState(onCommit)

    Column(Modifier.padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = LocalInk.current.copy(alpha = 0.7f), fontSize = 12.sp)
            Text(readout, color = LocalInk.current.copy(alpha = 0.45f), fontSize = 12.sp)
        }
        Spacer(Modifier.height(5.dp))

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val widthPx = with(density) { maxWidth.toPx() }
            val knobPx = with(density) { knob.toPx() }
            val travel = (widthPx - knobPx).coerceAtLeast(1f)

            fun pick(x: Float) = change(((x - knobPx / 2f) / travel).coerceIn(0f, 1f))

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(track)
                    .clip(RoundedCornerShape(13.dp))
                    .background(brush)
                    .border(1.dp, LocalInk.current.copy(alpha = 0.14f), RoundedCornerShape(13.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { pick(it.x); commit() }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = { commit() },
                            onDragCancel = { commit() }
                        ) { ev, _ -> pick(ev.position.x) }
                    }
            )

            Box(
                Modifier
                    .offset(x = with(density) { (fraction.coerceIn(0f, 1f) * travel).toDp() })
                    .padding(top = 3.dp)
                    .size(knob)
                    .clip(CircleShape)
                    .background(thumb)
                    .border(2.dp, LocalInk.current, CircleShape)
            )
        }
    }
}

@Composable
private fun HexField(value: Long, onCommit: (Long) -> Unit) {
    var text by remember { mutableStateOf(hexOf(value)) }
    var editing by remember { mutableStateOf(false) }

    // keep in step with the bars unless the field is being typed in
    LaunchedEffect(value, editing) {
        if (!editing) text = hexOf(value)
    }

    Column {
        Text("Hex", color = LocalInk.current.copy(alpha = 0.7f), fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(LocalInk.current.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            BasicTextField(
                value = text,
                onValueChange = { raw ->
                    text = raw.take(9)
                    parseHex(raw)?.let(onCommit)
                },
                singleLine = true,
                cursorBrush = SolidColor(LocalInk.current),
                textStyle = TextStyle(color = LocalInk.current, fontSize = 14.sp),
                modifier = Modifier
                    .width(110.dp)
                    .onFocusChanged { editing = it.isFocused }
            )
        }
    }
}

@Composable
private fun RgbaRow(value: Long, onCommit: (Long) -> Unit) {
    val argb = value.toInt()
    val parts = listOf(
        "R" to ((argb shr 16) and 0xFF),
        "G" to ((argb shr 8) and 0xFF),
        "B" to (argb and 0xFF),
        "A" to ((argb ushr 24) and 0xFF)
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEachIndexed { i, (name, n) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(name, color = LocalInk.current.copy(alpha = 0.6f), fontSize = 11.sp)
                Spacer(Modifier.height(3.dp))
                NumberCell(n) { typed ->
                    val r = if (i == 0) typed else (argb shr 16) and 0xFF
                    val g = if (i == 1) typed else (argb shr 8) and 0xFF
                    val b = if (i == 2) typed else argb and 0xFF
                    val al = if (i == 3) typed else (argb ushr 24) and 0xFF
                    onCommit(
                        ((al.toLong() and 0xFF) shl 24) or
                            ((r.toLong() and 0xFF) shl 16) or
                            ((g.toLong() and 0xFF) shl 8) or
                            (b.toLong() and 0xFF)
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberCell(value: Int, onCommit: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    var editing by remember { mutableStateOf(false) }

    LaunchedEffect(value, editing) {
        if (!editing) text = value.toString()
    }

    Box(
        Modifier
            .width(56.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(LocalInk.current.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        BasicTextField(
            value = text,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(3)
                text = digits
                digits.toIntOrNull()?.let { if (it in 0..255) onCommit(it) }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            cursorBrush = SolidColor(LocalInk.current),
            textStyle = TextStyle(color = LocalInk.current, fontSize = 13.sp),
            modifier = Modifier.fillMaxWidth().onFocusChanged { editing = it.isFocused }
        )
    }
}

private fun hexOf(argb: Long): String =
    "#%08X".format(argb and 0xFFFFFFFFL)

/** Accepts RGB, RRGGBB or AARRGGBB, with or without a leading #. */
private fun parseHex(raw: String): Long? {
    val t = raw.trim().removePrefix("#")
    if (t.any { it.digitToIntOrNull(16) == null }) return null
    val v = t.toLongOrNull(16) ?: return null
    return when (t.length) {
        3 -> {
            val r = (v shr 8 and 0xF) * 17
            val g = (v shr 4 and 0xF) * 17
            val b = (v and 0xF) * 17
            0xFF000000L or (r shl 16) or (g shl 8) or b
        }
        6 -> 0xFF000000L or v
        8 -> v
        else -> null
    }
}
