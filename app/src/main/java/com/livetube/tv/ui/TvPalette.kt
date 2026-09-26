package com.livetube.tv.ui

import androidx.compose.ui.graphics.Color

/**
 * Shared colours for the LiveTube TV overlay surfaces (About, Settings and update dialogs).
 *
 * The palette stays dark and translucent so the video keeps playing visibly behind it, with the
 * LiveTube red reserved for accents and highlights.
 */
internal object TvPalette {
    val Red = Color(0xFFFF1F3D)
    val RedBright = Color(0xFFFF3B57)
    val Surface = Color(0xF20A121C)
    val Card = Color(0xFF16212E)
    val CardFocused = Color(0xFF22303F)
    val SurfaceStrong = Color(0xFF18242F)
    val SurfaceFocused = Color(0xFF2B3E52)
    val Border = Color(0x1FFFFFFF)
    val TextPrimary = Color(0xFFF2F6FA)
    val TextMuted = Color(0xFFA9BACB)
    val Success = Color(0xFF6BE59B)
    val Warning = Color(0xFFFFC46B)
}
