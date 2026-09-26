package com.livetube.tv.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.livetube.tv.data.AppSettings
import com.livetube.tv.data.ChannelSyncState
import com.livetube.tv.data.ChannelSyncStatus
import com.livetube.tv.update.GitHubRelease
import com.livetube.tv.update.UpdateCheckResult
import com.livetube.tv.util.Constants
import com.livetube.tv.util.DateFormats
import com.livetube.tv.util.ExternalLinks

private const val SETTINGS_FOCUS_ATTEMPTS = 6

enum class SettingsPage {
    ROOT,
    GENERAL,
    UPDATES,
    CHANNEL_DATA,
    ABOUT,
}

/**
 * LiveTube TV settings.
 *
 * The screen is one dialog with a single page stack so BACK always means "go back one level" and
 * never leaves the user stuck. About is hosted here as one of the sections.
 */
@Composable
fun SettingsScreen(
    startPage: SettingsPage = SettingsPage.ROOT,
    settings: AppSettings,
    updateResult: UpdateCheckResult,
    updateCheckInProgress: Boolean,
    updateCheckFeedback: UpdateCheckFeedback?,
    updateInstallInProgress: Boolean,
    syncStatus: ChannelSyncStatus,
    syncInProgress: Boolean,
    syncFeedback: ChannelSyncFeedback?,
    onStartPageConsumed: () -> Unit = {},
    onSetAutoCheckUpdates: (Boolean) -> Unit,
    onCheckForUpdate: () -> Unit,
    onDownloadUpdate: (GitHubRelease) -> Unit,
    onSyncChannels: () -> Unit,
    onFeedbackConsumed: () -> Unit = {},
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf(startPage) }
    var aboutPage by remember { mutableStateOf(AboutPage.HOME) }
    val availableRelease = (updateResult as? UpdateCheckResult.Available)?.release
    val pageFocusRequester = rememberPageFocus(page, page)
    val aboutFocusRequester = rememberPageFocus(aboutPage, aboutPage)

    LaunchedEffect(startPage) {
        if (startPage != SettingsPage.ROOT) {
            page = startPage
            onStartPageConsumed()
        }
    }

    Dialog(
        onDismissRequest = {
            when {
                page == SettingsPage.ABOUT && aboutPage != AboutPage.HOME -> aboutPage = AboutPage.HOME
                page != SettingsPage.ROOT -> page = SettingsPage.ROOT
                else -> onDismiss()
            }
        },
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
                    .widthIn(max = 760.dp)
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.9f),
                shape = RoundedCornerShape(26.dp),
                color = TvPalette.Surface,
                border = BorderStroke(1.dp, TvPalette.Border),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SettingsHeader(
                        title = when (page) {
                            SettingsPage.ROOT -> "Settings"
                            SettingsPage.GENERAL -> "General"
                            SettingsPage.UPDATES -> "Updates"
                            SettingsPage.CHANNEL_DATA -> "Channel Data"
                            SettingsPage.ABOUT -> "About"
                        },
                        subtitle = when (page) {
                            SettingsPage.ROOT -> "LiveTube TV"
                            SettingsPage.ABOUT -> when (aboutPage) {
                                AboutPage.HOME -> "About LiveTube TV"
                                AboutPage.DEVELOPER -> "Developer"
                                AboutPage.APP_INFORMATION -> "App Information"
                                AboutPage.LATEST_VERSION -> "Get the Latest Version"
                            }

                            else -> null
                        },
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when (page) {
                            SettingsPage.ROOT -> SettingsRootRows(
                                focusRequester = pageFocusRequester,
                                updateBadge = availableRelease != null,
                                onOpenGeneral = { page = SettingsPage.GENERAL },
                                onOpenUpdates = { page = SettingsPage.UPDATES },
                                onOpenChannelData = { page = SettingsPage.CHANNEL_DATA },
                                onOpenAbout = {
                                    aboutPage = AboutPage.HOME
                                    page = SettingsPage.ABOUT
                                },
                            )

                            SettingsPage.GENERAL -> SettingsGeneralRows(
                                onOpenSource = { onOpenUrl(Constants.projectUrl()) },
                            )

                            SettingsPage.UPDATES -> SettingsUpdateRows(
                                settings = settings,
                                updateResult = updateResult,
                                checkInProgress = updateCheckInProgress,
                                installInProgress = updateInstallInProgress,
                                availableRelease = availableRelease,
                                focusRequester = pageFocusRequester,
                                onSetAutoCheckUpdates = onSetAutoCheckUpdates,
                                onCheckNow = {
                                    onFeedbackConsumed()
                                    onCheckForUpdate()
                                },
                                onDownloadUpdate = onDownloadUpdate,
                            )

                            SettingsPage.CHANNEL_DATA -> SettingsChannelDataRows(
                                settings = settings,
                                status = syncStatus,
                                syncInProgress = syncInProgress,
                                focusRequester = pageFocusRequester,
                                onSyncNow = {
                                    onFeedbackConsumed()
                                    onSyncChannels()
                                },
                            )

                            SettingsPage.ABOUT -> AboutPageBody(
                                page = aboutPage,
                                availableRelease = availableRelease,
                                focusRequester = aboutFocusRequester,
                                onOpenLatest = { aboutPage = AboutPage.LATEST_VERSION },
                                onOpenDeveloper = { aboutPage = AboutPage.DEVELOPER },
                                onOpenSource = { onOpenUrl(Constants.projectUrl()) },
                                onOpenInformation = { aboutPage = AboutPage.APP_INFORMATION },
                                onOpenProfile = { onOpenUrl(Constants.ownerProfileUrl()) },
                                onOpenRepository = { onOpenUrl(Constants.projectUrl()) },
                                onOpenReleases = { onOpenUrl(Constants.projectReleasesUrl()) },
                                onInstallUpdate = onDownloadUpdate,
                            )
                        }

                        updateCheckFeedback?.let { feedback ->
                            FeedbackCard(
                                text = when (feedback) {
                                    is UpdateCheckFeedback.UpToDate ->
                                        "Your app is up to date. Current version ${AppVersionText.display()}"
                                    is UpdateCheckFeedback.Failed -> feedback.message
                                },
                                tone = when (feedback) {
                                    is UpdateCheckFeedback.UpToDate -> TvPalette.Success
                                    is UpdateCheckFeedback.Failed -> TvPalette.Warning
                                },
                            )
                        }
                        // The sync result belongs to the page that started the sync.
                        if (page == SettingsPage.CHANNEL_DATA) {
                            syncFeedback?.let { feedback ->
                                FeedbackCard(
                                    text = when (feedback) {
                                        is ChannelSyncFeedback.Updated ->
                                            "Channels updated to version ${feedback.dataVersion} " +
                                                "(${feedback.channelCount} channels)"
                                        is ChannelSyncFeedback.UpToDate ->
                                            "Channels are already up to date " +
                                                "(version ${feedback.dataVersion}, " +
                                                "${feedback.channelCount} channels)"
                                        is ChannelSyncFeedback.KeptLocal ->
                                            "Version ${feedback.dataVersion} is newer than the " +
                                                "downloaded version ${feedback.remoteDataVersion}; " +
                                                "keeping the current channels"
                                        is ChannelSyncFeedback.Failed -> feedback.message
                                    },
                                    tone = when (feedback) {
                                        is ChannelSyncFeedback.Updated,
                                        is ChannelSyncFeedback.UpToDate,
                                        is ChannelSyncFeedback.KeptLocal,
                                        -> TvPalette.Success
                                        is ChannelSyncFeedback.Failed -> TvPalette.Warning
                                    },
                                )
                            }
                        }
                    }

                    AboutCloseButton(onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader(
    title: String,
    subtitle: String?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TvPalette.TextPrimary,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TvPalette.TextMuted,
            )
        }
    }
}

@Composable
private fun SettingsRootRows(
    focusRequester: FocusRequester?,
    updateBadge: Boolean,
    onOpenGeneral: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenChannelData: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    AboutActionRow(
        icon = Icons.Outlined.Tune,
        title = "General",
        subtitle = "App information and preferences",
        focusRequester = focusRequester,
        onClick = onOpenGeneral,
    )
    AboutActionRow(
        icon = Icons.Outlined.SystemUpdate,
        title = "Updates",
        subtitle = "Check for new versions of LiveTube TV",
        badge = if (updateBadge) "NEW" else null,
        onClick = onOpenUpdates,
    )
    AboutActionRow(
        icon = Icons.Outlined.CloudSync,
        title = "Channel Data",
        subtitle = "Sync the channel guide from GitHub",
        onClick = onOpenChannelData,
    )
    AboutActionRow(
        icon = Icons.Outlined.Info,
        title = "About",
        subtitle = "Developer, source code and app information",
        onClick = onOpenAbout,
    )
}

@Composable
private fun SettingsGeneralRows(onOpenSource: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = TvPalette.Card,
        border = BorderStroke(1.dp, TvPalette.Border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AboutInfoRow("App", "LiveTube TV")
            AboutInfoRow("App Version", AppVersionText.display())
            AboutInfoRow("Purpose", "Watch live TV channels on Android TV")
            AboutInfoRow("Channel guide", "Works offline with the saved guide")
        }
    }
    AboutActionRow(
        icon = Icons.Outlined.Storage,
        title = "Source Code",
        subtitle = "This project is open source on GitHub",
        onClick = onOpenSource,
    )
}

@Composable
private fun SettingsUpdateRows(
    settings: AppSettings,
    updateResult: UpdateCheckResult,
    checkInProgress: Boolean,
    installInProgress: Boolean,
    availableRelease: GitHubRelease?,
    focusRequester: FocusRequester?,
    onSetAutoCheckUpdates: (Boolean) -> Unit,
    onCheckNow: () -> Unit,
    onDownloadUpdate: (GitHubRelease) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = TvPalette.Card,
        border = BorderStroke(1.dp, TvPalette.Border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AboutInfoRow("Current Version", AppVersionText.display())
            AboutInfoRow(
                label = "Status",
                value = when {
                    checkInProgress -> "Checking…"
                    availableRelease != null -> "Update available: ${availableRelease.displayVersion}"
                    else -> updateResult.statusText()
                },
            )
            AboutInfoRow(
                label = "Last Checked",
                value = if (settings.hasCheckedForUpdates) {
                    DateFormats.dateTime(settings.lastUpdateCheckMillis)
                } else {
                    "Never"
                },
            )
        }
    }
    AboutActionRow(
        icon = Icons.Outlined.SystemUpdate,
        title = "Check for Update",
        subtitle = "Contact GitHub for the latest stable release",
        focusRequester = focusRequester,
        trailing = {
            Text(
                text = if (checkInProgress) "…" else "GO",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = TvPalette.Red,
            )
        },
        onClick = { if (!checkInProgress) onCheckNow() },
    )
    SettingsToggleRow(
        title = "Auto Check Updates",
        subtitle = "Check for a new version every time the app starts",
        checked = settings.autoCheckUpdates,
        onCheckedChange = onSetAutoCheckUpdates,
    )
    if (availableRelease != null) {
        AboutActionRow(
            icon = Icons.Outlined.SystemUpdate,
            title = "Download ${availableRelease.displayVersion}",
            subtitle = "Download and install the update inside LiveTube TV",
            badge = if (installInProgress) "…" else "NEW",
            onClick = { onDownloadUpdate(availableRelease) },
        )
    }
}

@Composable
private fun SettingsChannelDataRows(
    settings: AppSettings,
    status: ChannelSyncStatus,
    syncInProgress: Boolean,
    focusRequester: FocusRequester?,
    onSyncNow: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = TvPalette.Card,
        border = BorderStroke(1.dp, TvPalette.Border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AboutInfoRow(
                label = "Last Sync",
                value = if (settings.hasSyncedChannels) {
                    DateFormats.dateTime(settings.lastChannelSyncMillis)
                } else {
                    "Never"
                },
            )
            AboutInfoRow("Channels", status.channelCount.toString())
            AboutInfoRow("Data Version", status.dataVersion.toString())
            AboutInfoRow(
                label = "Status",
                value = when (status.state) {
                    ChannelSyncState.SYNCING -> "Syncing channels…"
                    ChannelSyncState.UPDATED -> "Channels updated"
                    ChannelSyncState.UP_TO_DATE -> "Up to date"
                    ChannelSyncState.FAILED -> status.message
                        ?: ChannelSyncStatus().let { "Unable to sync channels" }
                },
            )
        }
    }
    if (syncInProgress) {
        SettingsProgressRow(label = "Syncing channels…", fraction = null)
    }
    AboutActionRow(
        icon = Icons.Outlined.Sync,
        title = "Sync Now",
        subtitle = "Download the latest channel guide from GitHub",
        focusRequester = focusRequester,
        trailing = {
            Text(
                text = if (syncInProgress) "…" else "GO",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = TvPalette.Red,
            )
        },
        onClick = { if (!syncInProgress) onSyncNow() },
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    AboutActionRow(
        icon = Icons.Outlined.CheckCircle,
        title = title,
        subtitle = subtitle,
        trailing = {
            TvToggle(checked = checked)
        },
        onClick = { onCheckedChange(!checked) },
    )
}

@Composable
internal fun TvToggle(checked: Boolean) {
    val fraction by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(160, easing = LinearEasing),
        label = "toggle",
    )
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (checked) TvPalette.Red else TvPalette.SurfaceStrong)
            .border(
                width = 1.dp,
                color = if (checked) TvPalette.RedBright else TvPalette.Border,
                shape = RoundedCornerShape(15.dp),
            ),
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .align(Alignment.CenterStart)
                .offsetBy(fraction)
                .size(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (checked) Color.White else TvPalette.TextMuted),
        )
    }
}

@Composable
private fun SettingsProgressRow(
    label: String,
    fraction: Float?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = TvPalette.Card,
        border = BorderStroke(1.dp, TvPalette.Border),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TvPalette.TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = TvPalette.Red,
                    trackColor = TvPalette.SurfaceStrong,
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
        }
    }
}

@Composable
private fun FeedbackCard(
    text: String,
    tone: Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = TvPalette.Card,
        border = BorderStroke(1.dp, tone),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = tone,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = TvPalette.TextPrimary,
            )
        }
    }
}

private fun Modifier.offsetBy(fraction: Float): Modifier = this.then(
    Modifier.padding(start = (26 * fraction).dp),
)

/** Focuses the first row of the current page so D-pad users always start at a known position. */
@Composable
internal fun rememberPageFocus(
    page: Any,
    secondKey: Any,
): FocusRequester {
    val requester = remember(page, secondKey) { FocusRequester() }
    LaunchedEffect(page, secondKey, requester) {
        repeat(SETTINGS_FOCUS_ATTEMPTS) {
            withFrameNanos { }
            if (runCatching { requester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    return requester
}

/** Short result of a manual update check, shown inside the settings screen. */
sealed interface UpdateCheckFeedback {
    data object UpToDate : UpdateCheckFeedback
    data class Failed(val message: String) : UpdateCheckFeedback
}

/** Short result of a manual channel sync, shown inside the settings screen. */
sealed interface ChannelSyncFeedback {
    data class Updated(val channelCount: Int, val dataVersion: Int) : ChannelSyncFeedback
    data class UpToDate(val channelCount: Int, val dataVersion: Int) : ChannelSyncFeedback
    data class KeptLocal(val dataVersion: Int, val remoteDataVersion: Int) : ChannelSyncFeedback
    data class Failed(val message: String) : ChannelSyncFeedback
}
