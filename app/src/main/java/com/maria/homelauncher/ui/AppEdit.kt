package com.maria.homelauncher.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maria.homelauncher.LocalInk
import com.maria.homelauncher.AppEntry
import com.maria.homelauncher.Fonts
import com.maria.homelauncher.LocalStyle
import com.maria.homelauncher.Overrides

/**
 * Rename one app or give it a different icon. Writes through [Overrides] and asks the
 * caller to re-render, since the icon is baked into the app list rather than drawn live.
 */
@Composable
fun AppEditDialog(
    app: AppEntry,
    overrides: Overrides,
    onChanged: () -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val style = LocalStyle.current
    val key = "${app.packageName}/${app.activityName}"

    var name by remember(key) { mutableStateOf(overrides.label(key) ?: app.label) }
    var font by remember(key) { mutableStateOf(overrides.font(key).orEmpty()) }
    var pickingFont by remember(key) { mutableStateOf(false) }
    var note by remember(key) { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        note = if (overrides.setIcon(key, uri)) {
            onChanged()
            "icon changed"
        } else {
            "could not read that image"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(style.bgColor),
        title = { Text("Customise", color = LocalInk.current) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        bitmap = app.icon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        app.packageName,
                        color = LocalInk.current.copy(alpha = 0.45f),
                        fontSize = 11.sp
                    )
                }

                Spacer(Modifier.height(18.dp))
                Text("Name", color = LocalInk.current.copy(alpha = 0.7f), fontSize = 12.sp)
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(LocalInk.current.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        singleLine = true,
                        cursorBrush = SolidColor(LocalInk.current),
                        textStyle = TextStyle(
                            color = LocalInk.current,
                            fontSize = 15.sp,
                            fontFamily = style.fontFamily
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(18.dp))
                Text("Font", color = LocalInk.current.copy(alpha = 0.7f), fontSize = 12.sp)
                Spacer(Modifier.height(5.dp))

                if (!pickingFont) {
                    FontRow(
                        name = if (font.isBlank()) "Same as everything else"
                        else Fonts.label(font, style.font),
                        selected = true,
                        family = style.fontFamilyFor(font),
                        trailing = "Change"
                    ) { pickingFont = true }
                } else {
                    // the dialog is already scrollable, so the picker sits inside it rather
                    // than opening yet another layer on top of a long-press menu
                    Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        FontPicker(
                            selected = font,
                            googleOk = Fonts.googleAvailable(ctx),
                            onPick = { font = it; pickingFont = false },
                            leading = {
                                FontRow(
                                    name = "Same as everything else",
                                    selected = font.isBlank(),
                                    family = style.fontFamily
                                ) { font = ""; pickingFont = false }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { picker.launch(arrayOf("image/*")) }) {
                        Text("Choose image")
                    }
                    if (overrides.hasAny(key)) {
                        TextButton(onClick = {
                            overrides.clear(key)
                            name = app.label
                            font = ""
                            note = "back to default"
                            onChanged()
                        }) { Text("Reset") }
                    }
                }

                note?.let {
                    Text(
                        it,
                        color = LocalInk.current.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                overrides.setLabel(key, name.takeIf { it != app.label })
                overrides.setFont(key, font)
                onChanged()
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
