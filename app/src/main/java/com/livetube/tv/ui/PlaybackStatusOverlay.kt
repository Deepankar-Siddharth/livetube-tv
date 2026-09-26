package com.livetube.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.livetube.tv.data.Channel
import com.livetube.tv.player.PlaybackPhase
import com.livetube.tv.player.PlaybackState
import kotlinx.coroutines.delay

private const val TRANSIENT_STATUS_MS = 3_500L

/**
 * Small, self-dismissing playback status surface.
 *
 * Normal playback stays completely free of a permanent information box. While a stream is being
 * resolved a lightweight chip is shown, and retry / about stay available as a focused surface while
 * playback is failing. No technical stream values are shown: only what the viewer needs.
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
            shape = RoundedCornerShape(TvMetrics.CornerMedium),
            color = TvPalette.Surface,
            border = BorderStroke(1.dp, TvPalette.Border),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = channel?.name.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TvPalette.TextPrimary,
                )
                Text(
                    text = playback.message.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TvPalette.TextSecondary,
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
        modifier = modifier.widthIn(max = 440.dp),
        shape = RoundedCornerShape(TvMetrics.CornerMedium),
        color = TvPalette.Surface,
        border = BorderStroke(1.dp, TvPalette.BorderStrong),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (playback.phase == PlaybackPhase.ENDED) "Playback ended" else "Playback problem",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TvPalette.TextPrimary,
            )
            channelName?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TvPalette.RedBright,
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = TvPalette.TextSecondary,
            )
            Text(
                text = if (usingCachedData) "Using saved channel guide" else "Channel guide synced",
                style = MaterialTheme.typography.labelSmall,
                color = TvPalette.TextMuted,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvDialogButton(
                    text = "Retry",
                    onClick = onRetry,
                    style = TvActionStyle.PRIMARY,
                    focusRequester = retryFocusRequester,
                    onFocused = { onOverlayFocusChanged(true) },
                )
                TvDialogButton(
                    text = "About",
                    onClick = onAbout,
                    onFocused = { onOverlayFocusChanged(true) },
                )
            }
        }
    }
}
