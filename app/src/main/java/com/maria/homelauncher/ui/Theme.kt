package com.maria.homelauncher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.maria.homelauncher.AppEntry
import com.maria.homelauncher.Fonts
import com.maria.homelauncher.LocalInk
import com.maria.homelauncher.LocalStyle
import com.maria.homelauncher.Style

@Composable
fun LauncherTheme(style: Style, content: @Composable () -> Unit) {
    val accent = Color(style.accent)
    val bg = Color(style.bgColor)
    val ink = Color(style.ink)

    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = if (style.ink == 0xFFFFFFFFL) Color.Black else Color.White,
        secondary = accent,
        background = Color.Transparent,
        surface = bg,
        onSurface = ink,
        onBackground = ink,
        surfaceVariant = bg.copy(alpha = 0.6f),
        outline = ink.copy(alpha = 0.35f)
    )

    androidx.compose.runtime.CompositionLocalProvider(
        LocalStyle provides style,
        LocalInk provides ink
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

val Style.fontFamily: FontFamily get() = Fonts.family(fontId, font)

/**
 * The family for one app's name. A font set on that app wins; everything else follows the
 * global choice. Kept here so the half-dozen places that draw a name cannot drift apart.
 */
fun Style.familyFor(app: AppEntry?): FontFamily = fontFamilyFor(app?.font.orEmpty())

/** The same question asked with a bare family name, for the pickers. */
fun Style.fontFamilyFor(id: String): FontFamily =
    if (id.isNotBlank()) Fonts.family(id, font) else fontFamily

