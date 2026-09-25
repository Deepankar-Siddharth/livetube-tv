package com.livetube.tv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.livetube.tv.BuildConfig
import com.livetube.tv.R
import com.livetube.tv.update.GitHubRelease

@Composable
fun UpdateDialog(
    release: GitHubRelease,
    onInstall: () -> Unit,
    onLater: () -> Unit,
) {
    val version = release.version?.toString() ?: release.tagName
    val extractorVersion = Regex("""NewPipeExtractor\s+v?([0-9]+\.[0-9]+\.[0-9]+)""", RegexOption.IGNORE_CASE)
        .find(release.body)
        ?.groupValues
        ?.get(1)
        ?: BuildConfig.NEWPIPE_EXTRACTOR_VERSION.removePrefix("v")
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("UPDATE AVAILABLE", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LiveTube TV $version")
                Text("NewPipeExtractor $extractorVersion")
                if (release.body.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    release.body.lineSequence()
                        .filter { it.isNotBlank() }
                        .take(6)
                        .forEach { Text("• ${it.trim().removePrefix("- ")}") }
                } else {
                    Text("• Improved live extraction and playback reliability")
                    Text("• Bug fixes")
                }
            }
        },
        confirmButton = {
            Button(onClick = onInstall) { Text("UPDATE") }
        },
        dismissButton = {
            OutlinedButton(onClick = onLater) { Text("LATER") }
        },
    )
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Image(
                painter = painterResource(R.drawable.livetube_logo),
                contentDescription = stringResource(R.string.about),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("App version: ${BuildConfig.VERSION_NAME}")
                Text("NewPipeExtractor: ${BuildConfig.NEWPIPE_EXTRACTOR_VERSION.removePrefix("v")}")
                Text("Playback: Media3 adaptive live playback")
                Text("Data source: validated channels.json with offline cache")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } },
    )
}

@Composable
fun ExitDialog(onExit: () -> Unit, onStay: () -> Unit) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text("EXIT LIVETUBE TV?", fontWeight = FontWeight.Bold) },
        text = { Text("You can return to the live guide at any time.") },
        confirmButton = { Button(onClick = onExit) { Text("EXIT") } },
        dismissButton = { OutlinedButton(onClick = onStay) { Text("STAY") } },
    )
}
