package com.livetube.tv.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Shared colours and metrics for the LiveTube TV surfaces.
 *
 * The visual language is deliberately restrained: a near-black backdrop, one accent colour
 * (LiveTube red) used only for emphasis, neutral text, hairline borders and a single focus
 * treatment. No component relies on a large filled highlight, so the focused element is always
 * readable without looking like a system dialog.
 */
internal object TvPalette {
    val Red = Color(0xFFFF1F3D)
    val RedBright = Color(0xFFFF4A63)
    val RedWash = Color(0x26FF1F3D)

    /** Guide and dialog backdrop. */
    val Scrim = Color(0xE60A0C10)

    /** Panel behind guide rows and dialogs. */
    val Surface = Color(0xF00B0E13)
    val SurfaceStrong = Color(0xFF161B22)

    /** Resting and focused card fills. */
    val Card = Color(0xFF11161D)
    val CardFocused = Color(0xFF1C242E)

    val Border = Color(0x14FFFFFF)
    val BorderStrong = Color(0x2EFFFFFF)

    val TextPrimary = Color(0xFFF3F5F7)
    val TextSecondary = Color(0xFFA2ACB8)
    val TextMuted = Color(0xFF6E7883)

    val Success = Color(0xFF6BE59B)
    val Warning = Color(0xFFFFC46B)
    val Danger = Color(0xFFFF6B6B)

    /** Focus outline used by every focusable surface in the app. */
    val FocusRing = Color(0xFFF3F5F7)
}

/**
 * One place for the app's spacing and sizing so the guide, cards and dialogs stay on the same
 * compact scale. Values are deliberately TV-readable rather than minimal.
 */
internal object TvMetrics {
    val ScreenPadding = 32.dp
    val GuideRowHeight = 34.dp
    val GuideRowSpacing = 10.dp
    val CardWidth = 148.dp
    val CardHeight = 84.dp
    val CardLogo = 38.dp
    val CornerSmall = 8.dp
    val CornerMedium = 12.dp
    val CornerLarge = 18.dp
    val DialogMaxWidth = 520.dp
}
