package com.livetube.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.livetube.tv.data.Channel
import com.livetube.tv.player.PlaybackPhase
import com.livetube.tv.player.PlaybackState
import kotlinx.coroutines.delay

private const val TRANSIENT_STATUS_MS = 4_000L

/**
 * Small, self-dismissing playback status surface.
 *
 * Normal playback stays completely free of a permanent information box. While a stream is
 * being resolved a lightweight chip is shown, and retry / about stay available as a
 * focused surface while playback is failing.
 */
@Composable
fun PlaybackStatusOverlay(
    channel: Channel?,
    playback: PlaybackState,
    usingCachedData: Boolean,
    requestFocus: Boolean,
    onRetry: () -> Unit,
    onAbout: () -> Unit,
    onOverlayFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val phase = playback.phase
    val isProblem = phase == PlaybackPhase.ERROR || phase == PlaybackPhase.ENDED
    val isTransient = phase == PlaybackPhase.EXTRACTING || phase == PlaybackPhase.BUFFERING

    var transientVisible by remember { mutableStateOf(true) }
    LaunchedEffect(phase, playback.message, channel?.id) {
        transientVisible = true
        if (!isProblem) {
            delay(TRANSIENT_STATUS_MS)
            transientVisible = false
        }
    }

    if (isProblem) {
        PlaybackProblemCard(
            channelName = channel?.name,
            playback = playback,
            usingCachedData = usingCachedData,
            requestFocus = requestFocus,
            onRetry = onRetry,
            onAbout = onAbout,
            onOverlayFocusChanged = onOverlayFocusChanged,
            modifier = modifier,
        )
    } else if (isTransient && transientVisible) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(10.dp),
            color = Color(0xD9101923),
            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = channel?.name.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = playback.message.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB8C8D8),
                )
            }
        }
    } else {
        LaunchedEffect(Unit) { onOverlayFocusChanged(false) }
    }
}

@Composable
private fun PlaybackProblemCard(
    channelName: String?,
    playback: PlaybackState,
    usingCachedData: Boolean,
    requestFocus: Boolean,
    onRetry: () -> Unit,
    onAbout: () -> Unit,
    onOverlayFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val retryFocusRequester = remember { FocusRequester() }
    val message = playback.message ?: if (playback.phase == PlaybackPhase.ENDED) {
        "The live broadcast has ended."
    } else {
        "The live stream could not be played."
    }

    LaunchedEffect(Unit) {
        if (requestFocus) runCatching { retryFocusRequester.requestFocus() }
    }

    Surface(
        modifier = modifier.widthIn(max = 520.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xEE101923),
        border = BorderStroke(1.dp, Color(0x66FFB4AB)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (playback.phase == PlaybackPhase.ENDED) "Playback ended" else "Playback problem",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            channelName?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF8B9D),
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFFFD6D2),
            )
            Text(
                text = if (usingCachedData) "Using saved channel guide" else "Channel guide synced",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF9FB1C2),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .focusRequester(retryFocusRequester)
                        .onFocusChanged { onOverlayFocusChanged(it.isFocused) },
                ) { Text("RETRY") }
                Button(
                    onClick = onAbout,
                    modifier = Modifier.onFocusChanged { onOverlayFocusChanged(it.isFocused) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263544)),
                ) { Text("ABOUT") }
            }
        }
    }
}
