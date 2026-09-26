package com.livetube.tv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Dark, near-black scheme with a single restrained accent.
 *
 * The accent is the LiveTube red rather than a system blue, so no Material component can fall back
 * to a large bright selection block. Component colors are consumed by the custom surfaces in
 * TvPalette, which keep the focus treatment consistent across the guide and the dialogs.
 */
private val LiveTubeColors = darkColorScheme(
    primary = Color(0xFFFF1F3D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0x33FF1F3D),
    onPrimaryContainer = Color(0xFFFF8B9D),
    secondary = Color(0xFFA2ACB8),
    onSecondary = Color(0xFF0B0E13),
    background = Color(0xFF07090C),
    onBackground = Color(0xFFF3F5F7),
    surface = Color(0xFF0B0E13),
    onSurface = Color(0xFFF3F5F7),
    surfaceVariant = Color(0xFF161B22),
    onSurfaceVariant = Color(0xFFA2ACB8),
    outline = Color(0x2EFFFFFF),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF0B0E13),
)

@Composable
fun LiveTubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LiveTubeColors,
        typography = Typography(),
        content = content,
    )
}
