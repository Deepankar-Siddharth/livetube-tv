package com.livetube.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Visual state of the update flow owned by the activity. */
sealed interface UpdateFlowState {
    data object Idle : UpdateFlowState
    data class Downloading(val version: String, val percent: Int?) : UpdateFlowState
    data class Ready(val version: String) : UpdateFlowState
    data class Failed(val message: String) : UpdateFlowState
}

/**
 * Progress surface shown while the release APK is being downloaded.
 *
 * It never shows build internals: only the release version and the download progress.
 */
@Composable
fun UpdateDownloadDialog(
    state: UpdateFlowState.Downloading,
    onDismiss: () -> Unit,
) {
    TvUpdateDialog(
        icon = Icons.Outlined.Download,
        title = "Update available",
        version = state.version,
        onDismissRequest = onDismiss,
    ) {
        Text(
            text = "Downloading update…",
            style = MaterialTheme.typography.bodyMedium,
            color = TvPalette.TextSecondary,
        )
        Spacer(Modifier.height(10.dp))
        if (state.percent != null) {
            LinearProgressIndicator(
                progress = { state.percent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = TvPalette.Red,
                trackColor = TvPalette.SurfaceStrong,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${state.percent}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = TvPalette.TextPrimary,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = TvPalette.Red,
                trackColor = TvPalette.SurfaceStrong,
            )
        }
        Spacer(Modifier.height(12.dp))
        TvDialogActions {
            TvDialogButton(
                text = "Continue in background",
                onClick = onDismiss,
                focusRequester = rememberDialogFocusRequester(),
            )
        }
    }
}

/** Asks whether to install the downloaded update right now. */
@Composable
fun UpdateReadyDialog(
    state: UpdateFlowState.Ready,
    onInstall: () -> Unit,
    onLater: () -> Unit,
) {
    TvUpdateDialog(
        icon = Icons.Outlined.SystemUpdate,
        title = "Update ready",
        version = state.version,
        onDismissRequest = onLater,
    ) {
        Text(
            text = "The update has been downloaded and verified. Install it now?",
            style = MaterialTheme.typography.bodyMedium,
            color = TvPalette.TextSecondary,
        )
        Spacer(Modifier.height(14.dp))
        TvDialogActions {
            TvDialogButton(
                text = "Later",
                onClick = onLater,
                focusRequester = rememberDialogFocusRequester(),
            )
            TvDialogButton(
                text = "Install",
                onClick = onInstall,
                style = TvActionStyle.PRIMARY,
            )
        }
    }
}

/** Shown when a manual check finds that the installed app is already current. */
@Composable
fun UpdateUpToDateDialog(
    onClose: () -> Unit,
) {
    TvUpdateDialog(
        icon = Icons.Outlined.CheckCircle,
        title = "LiveTube TV",
        version = null,
        onDismissRequest = onClose,
    ) {
        Text(
            text = "Your app is up to date.",
            style = MaterialTheme.typography.titleSmall,
            color = TvPalette.TextPrimary,
        )
        Text(
            text = "Current version: ${AppVersionText.display()}",
            style = MaterialTheme.typography.bodySmall,
            color = TvPalette.TextMuted,
        )
        Spacer(Modifier.height(12.dp))
        TvDialogActions {
            TvDialogButton(
                text = "Close",
                onClick = onClose,
                style = TvActionStyle.PRIMARY,
                focusRequester = rememberDialogFocusRequester(),
            )
        }
    }
}

/** Friendly failure surface for a download or check that could not complete. */
@Composable
fun UpdateMessageDialog(
    title: String,
    message: String,
    onClose: () -> Unit,
) {
    TvUpdateDialog(
        icon = Icons.Outlined.SystemUpdate,
        title = title,
        version = null,
        onDismissRequest = onClose,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TvPalette.TextSecondary,
        )
        Spacer(Modifier.height(12.dp))
        TvDialogActions {
            TvDialogButton(
                text = "Close",
                onClick = onClose,
                style = TvActionStyle.PRIMARY,
                focusRequester = rememberDialogFocusRequester(),
            )
        }
    }
}

@Composable
private fun TvUpdateDialog(
    icon: ImageVector,
    title: String,
    version: String?,
    onDismissRequest: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    TvDialog(
        title = title,
        subtitle = version?.let { "LiveTube TV $it" },
        onDismissRequest = onDismissRequest,
        maxWidth = 480.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TvPalette.Red,
                modifier = Modifier.size(20.dp),
            )
        }
        content()
    }
}

