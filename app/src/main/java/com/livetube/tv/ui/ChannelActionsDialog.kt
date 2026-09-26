package com.livetube.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.livetube.tv.data.Channel

@Composable
fun ChannelActionsDialog(
    channel: Channel,
    favorite: Boolean,
    onPlay: () -> Unit,
    onSetFavorite: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val playFocusRequester = rememberDialogFocusRequester()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(channel.subcategory)
                Text(
                    text = "${channel.language} • ${channel.region}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onPlay,
                modifier = Modifier.focusRequester(playFocusRequester),
            ) { Text("PLAY") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onSetFavorite(!favorite) }) {
                    Text(if (favorite) "REMOVE FAVORITE" else "ADD FAVORITE")
                }
                TextButton(onClick = onDismiss) { Text("CLOSE") }
            }
        },
        modifier = Modifier,
    )
}
