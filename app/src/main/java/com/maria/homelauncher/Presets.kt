package com.maria.homelauncher

/**
 * Whole looks, in one tap.
 *
 * Each one is a nod to an interface worth remembering, not a reproduction: this is a
 * launcher, so what it can actually borrow is the palette, the shape of things, the amount
 * of glass and the typeface. A preset touches only the look. Your columns, dock, gestures,
 * hidden apps and widgets are not part of it and are left where they are.
 *
 * [font] is a Google Fonts family and is fetched the first time it is used. If there is no
 * font provider on the phone, the rest of the preset still applies and the text falls back
 * to the built-in family.
 */
data class Preset(
    val name: String,
    val blurb: String,
    val accent: Long,
    val bg: Long,
    val ink: Long = 0xFFFFFFFF,
    val bgMode: BgMode = BgMode.Blur,
    val bgAlpha: Float = 0.80f,
    val blur: Int = 48,
    val shape: IconShape = IconShape.Circle,
    val tint: TintMode = TintMode.Off,
    val iconBg: Long = 0xFFBFC9FF,
    val iconColor: Long = 0xFF0B0B0F,
    val dockBg: Boolean = true,
    val dockRadius: Int = 30,
    val folderRadius: Int = 26,
    val font: String = ""
) {
    /** Keeps everything that is not a colour, a shape or a typeface. */
    fun applyTo(s: Style) = s.copy(
        accent = accent,
        bgColor = bg,
        ink = ink,
        bgMode = bgMode,
        bgAlpha = bgAlpha,
        blurRadius = blur,
        iconShape = shape,
        iconTint = tint,
        iconBg = iconBg,
        iconColor = iconColor,
        dockBg = dockBg,
        dockRadius = dockRadius,
        folderRadius = folderRadius,
        fontId = font,
        autoAccent = false
    )
}

val PRESETS = listOf(
    Preset(
        name = "Aero Glass",
        blurb = "Windows Vista and 7. Deep blue behind a lot of glass.",
        accent = 0xFF6FC3F7, bg = 0xFF0A1C2E,
        bgMode = BgMode.Blur, bgAlpha = 0.62f, blur = 72,
        shape = IconShape.Squircle, dockRadius = 14, folderRadius = 14,
        font = "g:Open Sans"
    ),
    Preset(
        name = "Luna",
        blurb = "Windows XP. That blue taskbar and the green start button.",
        accent = 0xFF7CC576, bg = 0xFF11387A,
        bgMode = BgMode.Dim, bgAlpha = 0.88f,
        shape = IconShape.Rounded, dockRadius = 10, folderRadius = 10,
        font = "g:PT Sans"
    ),
    Preset(
        name = "Metro",
        blurb = "Windows Phone. Flat, square, loud, and no glass anywhere.",
        accent = 0xFF00B7C3, bg = 0xFF000000,
        bgMode = BgMode.Solid, bgAlpha = 1f,
        shape = IconShape.Square, tint = TintMode.Mono,
        iconBg = 0xFF00B7C3, iconColor = 0xFFFFFFFF,
        dockBg = false, dockRadius = 0, folderRadius = 0,
        font = "g:Open Sans"
    ),
    Preset(
        name = "Mica",
        blurb = "Windows 11. Rounded, quiet, a hint of the wallpaper coming through.",
        accent = 0xFF60CDFF, bg = 0xFF1F1F24,
        bgMode = BgMode.Blur, bgAlpha = 0.72f, blur = 56,
        shape = IconShape.Squircle, dockRadius = 22, folderRadius = 18,
        font = "g:Inter"
    ),
    Preset(
        name = "Aqua",
        blurb = "Mac OS X. Glossy blue on brushed grey.",
        accent = 0xFF3C8CFF, bg = 0xFF2B2F36,
        bgMode = BgMode.Dim, bgAlpha = 0.84f,
        shape = IconShape.Circle, dockRadius = 24, folderRadius = 18,
        font = "g:Lato"
    ),
    Preset(
        name = "Liquid Glass",
        blurb = "macOS Tahoe and iOS 26. As transparent as this will go.",
        accent = 0xFFE8E4FF, bg = 0xFF14141C,
        bgMode = BgMode.Blur, bgAlpha = 0.42f, blur = 96,
        shape = IconShape.Squircle, dockRadius = 34, folderRadius = 30,
        font = "g:Figtree"
    ),
    Preset(
        name = "Big Sur",
        blurb = "Pastel, rounded, floating. The light one.",
        accent = 0xFF3F6DF0, bg = 0xFFF2F0F7, ink = 0xFF14141A,
        bgMode = BgMode.Dim, bgAlpha = 0.86f,
        shape = IconShape.Squircle,
        dockRadius = 30, folderRadius = 26,
        font = "g:Inter"
    ),
    Preset(
        name = "Holo",
        blurb = "Ice Cream Sandwich. Black, cyan, and very sure of itself.",
        accent = 0xFF33B5E5, bg = 0xFF060608,
        bgMode = BgMode.Solid, bgAlpha = 1f,
        shape = IconShape.Square, dockBg = false, dockRadius = 2, folderRadius = 2,
        font = "g:Roboto Condensed"
    ),
    Preset(
        name = "Gingerbread",
        blurb = "Android 2.3. Black backgrounds, green highlights.",
        accent = 0xFF8BC34A, bg = 0xFF0A0A0A,
        bgMode = BgMode.Solid, bgAlpha = 1f,
        shape = IconShape.Rounded, dockRadius = 6, folderRadius = 6,
        font = "g:Roboto"
    ),
    Preset(
        name = "Material You",
        blurb = "Android 12. Big soft shapes, colour pulled from the wallpaper.",
        accent = 0xFFD0BCFF, bg = 0xFF1C1B20,
        bgMode = BgMode.Blur, bgAlpha = 0.76f, blur = 40,
        shape = IconShape.Circle, dockRadius = 32, folderRadius = 28,
        font = "g:Roboto"
    ),
    Preset(
        name = "Yaru",
        blurb = "Ubuntu. Aubergine and orange.",
        accent = 0xFFE95420, bg = 0xFF2C001E,
        bgMode = BgMode.Dim, bgAlpha = 0.88f,
        shape = IconShape.Circle, dockRadius = 12, folderRadius = 12,
        font = "g:Ubuntu"
    ),
    Preset(
        name = "NeXTSTEP",
        blurb = "Monochrome, square, and all business.",
        accent = 0xFFB8B8B8, bg = 0xFF2A2A2A,
        bgMode = BgMode.Solid, bgAlpha = 1f,
        shape = IconShape.Square, tint = TintMode.Mono,
        iconBg = 0xFF3A3A3A, iconColor = 0xFFE8E8E8,
        dockRadius = 0, folderRadius = 0,
        font = "g:IBM Plex Sans"
    ),
    Preset(
        name = "Command Line",
        blurb = "MS-DOS. Green on black, and a cursor you can almost hear.",
        accent = 0xFF33FF66, bg = 0xFF000000, ink = 0xFF33FF66,
        bgMode = BgMode.Solid, bgAlpha = 1f,
        shape = IconShape.Square, tint = TintMode.Mono,
        iconBg = 0xFF001A05, iconColor = 0xFF33FF66,
        dockBg = false, dockRadius = 0, folderRadius = 0,
        font = "g:VT323"
    ),
    Preset(
        name = "Workbench",
        blurb = "Amiga. Blue and orange, drawn on a machine with four colours.",
        accent = 0xFFFF8800, bg = 0xFF0A2A8C,
        bgMode = BgMode.Solid, bgAlpha = 1f,
        shape = IconShape.Square, dockRadius = 0, folderRadius = 0,
        font = "g:Press Start 2P"
    ),
    Preset(
        name = "Haiku",
        blurb = "Yellow tabs on light grey. BeOS, still going.",
        accent = 0xFFFFB700, bg = 0xFFE4E4E0, ink = 0xFF1A1A1A,
        bgMode = BgMode.Solid, bgAlpha = 1f,
        shape = IconShape.Square, dockRadius = 2, folderRadius = 2,
        font = "g:Noto Sans"
    ),
    Preset(
        name = "webOS",
        blurb = "Palm. Dark cards with a soft glow.",
        accent = 0xFF58C4F0, bg = 0xFF101418,
        bgMode = BgMode.Blur, bgAlpha = 0.70f, blur = 52,
        shape = IconShape.Squircle, dockRadius = 26, folderRadius = 22,
        font = "g:Nunito Sans"
    ),
    Preset(
        name = "BlackBerry",
        blurb = "Dark panels, tight grid, built for getting things done.",
        accent = 0xFFCC2229, bg = 0xFF131313,
        bgMode = BgMode.Dim, bgAlpha = 0.90f,
        shape = IconShape.Rounded, dockRadius = 8, folderRadius = 8,
        font = "g:Archivo Narrow"
    ),
    Preset(
        name = "Flat",
        blurb = "iOS 7. White, thin type, blurred panels, nothing fake.",
        accent = 0xFF0A84FF, bg = 0xFFF7F7FA, ink = 0xFF101014,
        bgMode = BgMode.Blur, bgAlpha = 0.68f, blur = 64,
        shape = IconShape.Squircle, dockRadius = 24, folderRadius = 20,
        font = "g:Jost"
    )
)
