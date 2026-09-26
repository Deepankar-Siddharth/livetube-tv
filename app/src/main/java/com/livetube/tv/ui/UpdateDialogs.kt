package com.livetube.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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
        title = "Update Available",
        version = state.version,
        onDismissRequest = onDismiss,
    ) {
        Text(
            text = "Downloading update…",
            style = MaterialTheme.typography.bodyMedium,
            color = TvPalette.TextMuted,
        )
        Spacer(Modifier.height(12.dp))
        if (state.percent != null) {
            LinearProgressIndicator(
                progress = { state.percent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = TvPalette.Red,
                trackColor = TvPalette.SurfaceStrong,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${state.percent}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = TvPalette.TextPrimary,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = TvPalette.Red,
                trackColor = TvPalette.SurfaceStrong,
            )
        }
        Spacer(Modifier.height(16.dp))
        TvFocusableButton(
            text = "CONTINUE IN BACKGROUND",
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
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
        title = "Update Ready",
        version = state.version,
        onDismissRequest = onLater,
    ) {
        Text(
            text = "The update has been downloaded and verified. Install it now?",
            style = MaterialTheme.typography.bodyMedium,
            color = TvPalette.TextMuted,
        )
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvFocusableButton(
                text = "INSTALL",
                onClick = onInstall,
                primary = true,
                modifier = Modifier.weight(1f),
            )
            TvFocusableButton(
                text = "LATER",
                onClick = onLater,
                modifier = Modifier.weight(1f),
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
            style = MaterialTheme.typography.titleMedium,
            color = TvPalette.TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Current version: ${AppVersionText.display()}",
            style = MaterialTheme.typography.bodyMedium,
            color = TvPalette.TextMuted,
        )
        Spacer(Modifier.height(20.dp))
        TvFocusableButton(
            text = "CLOSE",
            onClick = onClose,
            primary = true,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
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
            color = TvPalette.TextMuted,
        )
        Spacer(Modifier.height(20.dp))
        TvFocusableButton(
            text = "CLOSE",
            onClick = onClose,
            primary = true,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun TvUpdateDialog(
    icon: ImageVector,
    title: String,
    version: String?,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScopeAlias.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxWidth(0.8f),
                shape = RoundedCornerShape(24.dp),
                color = TvPalette.Surface,
                border = BorderStroke(1.dp, TvPalette.Border),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = TvPalette.Red,
                            modifier = Modifier.size(30.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TvPalette.TextPrimary,
                            )
                            if (version != null) {
                                Text(
                                    text = "LiveTube TV $version",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TvPalette.TextMuted,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun TvFocusableButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    TvPillButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = true,
    )
}

/** Alias so the dialog content lambda can use Column alignment helpers. */
private typealias ColumnScopeAlias = androidx.compose.foundation.layout.ColumnScope
