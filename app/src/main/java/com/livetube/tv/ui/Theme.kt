package com.livetube.tv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LiveTubeColors = darkColorScheme(
    primary = Color(0xFF66D1FF),
    onPrimary = Color(0xFF003548),
    secondary = Color(0xFF9CCCFE),
    background = Color(0xFF070B12),
    onBackground = Color(0xFFE8EEF5),
    surface = Color(0xFF101923),
    onSurface = Color(0xFFE8EEF5),
    surfaceVariant = Color(0xFF263544),
    onSurfaceVariant = Color(0xFFB9C8D6),
    error = Color(0xFFFFB4AB),
)

@Composable
fun LiveTubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LiveTubeColors,
        typography = Typography(),
        content = content,
    )
}
