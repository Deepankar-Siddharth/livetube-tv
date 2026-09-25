package com.livetube.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.livetube.tv.R
import com.livetube.tv.data.Channel
import com.livetube.tv.player.PlaybackPhase
import com.livetube.tv.player.PlaybackState

@Composable
fun InfoOverlay(
    channel: Channel?,
    playback: PlaybackState,
    usingCachedData: Boolean,
    onRetry: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
    onOverlayFocusChanged: (Boolean) -> Unit = {},
) {
    if (channel == null && playback.message == null) {
        LaunchedEffect(Unit) { onOverlayFocusChanged(false) }
        return
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(18.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xCC101923))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (channel != null) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                    error = androidx.compose.ui.res.painterResource(R.drawable.livetube_icon),
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel?.name ?: "LiveTube TV",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = channel?.category ?: "Android TV live guide",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB8C8D8),
                )
            }
            if (
                channel != null && playback.isLive &&
                playback.phase in setOf(PlaybackPhase.BUFFERING, PlaybackPhase.PLAYING, PlaybackPhase.PAUSED)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF5C5C)),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("LIVE", color = Color(0xFFFF8B8B), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (playback.message != null) {
            Text(
                text = playback.message,
                color = if (playback.phase == PlaybackPhase.ERROR) Color(0xFFFFB4AB) else Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        val details = buildList {
            playback.resolutionHeight?.let { add("Adaptive quality • ${it}p available") }
            add(if (usingCachedData) "Using saved channel guide" else "Channel guide synced")
            if (playback.retryInSeconds != null) add("Retry ${playback.retryAttempt} in ${playback.retryInSeconds}s")
        }
        if (details.isNotEmpty()) {
            Text(
                text = details.joinToString("  •  "),
                color = Color(0xFF9FB1C2),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (playback.phase == PlaybackPhase.ERROR || playback.phase == PlaybackPhase.ENDED) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.onFocusChanged { onOverlayFocusChanged(it.isFocused) },
                ) { Text("RETRY") }
            }
            Button(
                onClick = onAbout,
                modifier = Modifier.onFocusChanged { onOverlayFocusChanged(it.isFocused) },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF263544),
                ),
            ) { Text("ABOUT") }
        }
    }
}
