package com.livetube.tv.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.livetube.tv.data.Channel

/**
 * Channel actions, reached by holding OK on a channel card.
 *
 * This is also how a channel is added to or removed from favorites, so it stays available in the
 * guide. Focus starts on Play, and every action shares the dialog focus treatment.
 */
@Composable
fun ChannelActionsDialog(
    channel: Channel,
    favorite: Boolean,
    onPlay: () -> Unit,
    onSetFavorite: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val playFocusRequester = rememberDialogFocusRequester()
    TvDialog(
        title = channel.name,
        subtitle = "${channel.language} • ${channel.category} • ${channel.subcategory}",
        onDismissRequest = onDismiss,
    ) {
        Spacer(Modifier.height(4.dp))
        TvDialogActions {
            TvDialogButton(
                text = "Play",
                onClick = onPlay,
                style = TvActionStyle.PRIMARY,
                focusRequester = playFocusRequester,
            )
            TvDialogButton(
                text = if (favorite) "Remove favorite" else "Add favorite",
                onClick = { onSetFavorite(!favorite) },
            )
            TvDialogButton(text = "Close", onClick = onDismiss)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Favorites stay on this device and are never part of the channel guide data.",
            style = MaterialTheme.typography.labelSmall,
            color = TvPalette.TextMuted,
            fontWeight = FontWeight.Normal,
        )
    }
}
