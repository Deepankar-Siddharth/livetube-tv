package com.livetube.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.livetube.tv.BuildConfig
import com.livetube.tv.R
import com.livetube.tv.update.GitHubRelease
import com.livetube.tv.update.UpdateCheckResult
import com.livetube.tv.util.Constants

private val AboutRed = Color(0xFFFF1F3D)
private val AboutSurface = Color(0xF20A121C)
private val AboutCard = Color(0xFF16212E)
private val AboutCardFocused = Color(0xFF22303F)
private val AboutBorder = Color(0x1FFFFFFF)
private val AboutTextPrimary = Color(0xFFF2F6FA)
private val AboutTextMuted = Color(0xFFA9BACB)
private const val ABOUT_FOCUS_ATTEMPTS = 6

private enum class AboutPage {
    HOME,
    DEVELOPER,
    APP_INFORMATION,
    LATEST_VERSION,
}

/**
 * User-facing About experience for LiveTube TV.
 *
 * Only information a viewer needs is shown: how to get updates, who builds the app, that it is
 * open source, and the app version. Build, dependency, extraction and cache internals are
 * deliberately not part of this screen.
 */
@Composable
fun AboutScreen(
    updateResult: UpdateCheckResult,
    onOpenUrl: (String) -> Unit,
    onInstallUpdate: (GitHubRelease) -> Unit,
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf(AboutPage.HOME) }
    val pageFocusRequester = rememberPageFocusRequester(page)
    val availableRelease = (updateResult as? UpdateCheckResult.Available)?.release

    Dialog(
        onDismissRequest = {
            if (page == AboutPage.HOME) onDismiss() else page = AboutPage.HOME
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
                color = AboutSurface,
                border = BorderStroke(1.dp, AboutBorder),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AboutPageHeader(page = page)

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when (page) {
                            AboutPage.HOME -> AboutHomeRows(
                                availableRelease = availableRelease,
                                focusRequester = pageFocusRequester,
                                onOpenLatest = { page = AboutPage.LATEST_VERSION },
                                onOpenDeveloper = { page = AboutPage.DEVELOPER },
                                onOpenSource = { onOpenUrl(Constants.projectUrl()) },
                                onOpenInformation = { page = AboutPage.APP_INFORMATION },
                            )

                            AboutPage.DEVELOPER -> AboutDeveloperRows(
                                focusRequester = pageFocusRequester,
                                onOpenProfile = { onOpenUrl(Constants.ownerProfileUrl()) },
                                onOpenRepository = { onOpenUrl(Constants.projectUrl()) },
                            )

                            AboutPage.APP_INFORMATION -> AboutInformationRows()

                            AboutPage.LATEST_VERSION -> AboutLatestVersionRows(
                                availableRelease = availableRelease,
                                focusRequester = pageFocusRequester,
                                onOpenReleases = { onOpenUrl(Constants.projectReleasesUrl()) },
                                onInstallUpdate = onInstallUpdate,
                            )
                        }
                    }

                    AboutCloseButton(onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun AboutPageHeader(page: AboutPage) {
    when (page) {
        AboutPage.HOME -> AboutBrandHeader()
        AboutPage.DEVELOPER -> AboutSubPageHeader(
            title = "Developer",
            subtitle = "Deepankar Siddharth",
            description = "Independent developer building open-source tools and applications.",
        )

        AboutPage.APP_INFORMATION -> AboutSubPageHeader(
            title = "App Information",
            subtitle = "LiveTube TV",
            description = "Everything you need to know about this app.",
        )

        AboutPage.LATEST_VERSION -> AboutSubPageHeader(
            title = "Get the Latest Version",
            subtitle = "Download the latest APK and view release notes on GitHub.",
        )
    }
}

@Composable
private fun AboutHomeRows(
    availableRelease: GitHubRelease?,
    focusRequester: FocusRequester,
    onOpenLatest: () -> Unit,
    onOpenDeveloper: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenInformation: () -> Unit,
) {
    AboutActionRow(
        icon = Icons.Outlined.SystemUpdate,
        title = "Download Latest Version",
        subtitle = if (availableRelease != null) {
            "Version ${availableRelease.displayVersion} is available"
        } else {
            "Get updates, releases and source code on GitHub"
        },
        badge = if (availableRelease != null) "NEW" else null,
        focusRequester = focusRequester,
        onClick = onOpenLatest,
    )
    AboutActionRow(
        icon = Icons.Outlined.Person,
        title = "Developer",
        subtitle = "Deepankar Siddharth",
        onClick = onOpenDeveloper,
    )
    AboutActionRow(
        icon = Icons.Outlined.Code,
        title = "Open Source",
        subtitle = "This project is open source on GitHub",
        onClick = onOpenSource,
    )
    AboutActionRow(
        icon = Icons.Outlined.Info,
        title = "App Information",
        subtitle = "Version and license",
        onClick = onOpenInformation,
    )
}

@Composable
private fun AboutDeveloperRows(
    focusRequester: FocusRequester,
    onOpenProfile: () -> Unit,
    onOpenRepository: () -> Unit,
) {
    AboutActionRow(
        icon = Icons.Outlined.Person,
        title = "GitHub Profile",
        subtitle = "Open developer profile on GitHub",
        focusRequester = focusRequester,
        onClick = onOpenProfile,
    )
    AboutActionRow(
        icon = Icons.Outlined.Code,
        title = "LiveTube TV Repository",
        subtitle = "Open the LiveTube TV source repository",
        onClick = onOpenRepository,
    )
}

@Composable
private fun AboutInformationRows() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AboutCard,
        border = BorderStroke(1.dp, AboutBorder),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AboutInfoRow("App Version", displayVersion())
            if (BuildConfig.DEBUG) {
                AboutInfoRow("Build", "Development build")
            }
            AboutInfoRow("Source Code", "Open source on GitHub")
            AboutInfoRow("License", "MIT License")
            AboutInfoRow("Developer", "Deepankar Siddharth")
            AboutInfoRow("Repository", "LiveTube TV")
        }
    }
}

@Composable
private fun AboutLatestVersionRows(
    availableRelease: GitHubRelease?,
    focusRequester: FocusRequester,
    onOpenReleases: () -> Unit,
    onInstallUpdate: (GitHubRelease) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (availableRelease != null) Color(0xFF2A131C) else AboutCard,
        border = BorderStroke(1.dp, if (availableRelease != null) AboutRed else AboutBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (availableRelease != null) "New version available" else "You are up to date",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AboutTextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (availableRelease != null) {
                        "Version ${availableRelease.displayVersion} is ready to install"
                    } else {
                        "Current version ${displayVersion()}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AboutTextMuted,
                )
            }
            if (availableRelease != null) {
                Spacer(Modifier.width(16.dp))
                AboutPillButton(
                    text = "UPDATE",
                    onClick = { onInstallUpdate(availableRelease) },
                )
            }
        }
    }
    AboutActionRow(
        icon = Icons.Outlined.SystemUpdate,
        title = "Open GitHub Releases",
        subtitle = "Download the latest APK and read the release notes",
        focusRequester = focusRequester,
        onClick = onOpenReleases,
    )
}

@Composable
private fun AboutBrandHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The lockup is drawn for dark surfaces (transparent background, white wordmark),
        // so it is placed directly on the About background instead of on a light card.
        Image(
            painter = painterResource(R.drawable.livetube_logo),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(92.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Watch Live TV on Android TV",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = AboutTextPrimary,
        )
    }
}

@Composable
private fun AboutSubPageHeader(
    title: String,
    subtitle: String,
    description: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.livetube_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(54.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = AboutTextPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = AboutTextMuted,
            )
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = AboutTextMuted,
                )
            }
        }
    }
}

@Composable
private fun AboutActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember(title) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = tween(120),
        label = "aboutRowFocus",
    )
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (focused) AboutCardFocused else AboutCard)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else AboutBorder,
                shape = shape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        color = Color.Transparent,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (focused) AboutRed else AboutTextMuted,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                    color = AboutTextPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AboutTextMuted,
                    maxLines = 2,
                )
            }
            if (badge != null) {
                Spacer(Modifier.width(12.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AboutRed,
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = if (focused) AboutTextPrimary else AboutTextMuted,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun AboutPillButton(
    text: String,
    onClick: () -> Unit,
) {
    var focused by remember(text) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(120),
        label = "aboutButtonFocus",
    )
    Surface(
        modifier = Modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color(0xFFFF3B57) else AboutRed)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        color = Color.Transparent,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun AboutCloseButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.03f else 1f,
        animationSpec = tween(120),
        label = "aboutCloseFocus",
    )
    val shape = RoundedCornerShape(12.dp)
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .background(if (focused) Color(0xFF2B3E52) else Color(0xFF18242F))
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) Color.White else AboutBorder,
                    shape = shape,
                )
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 28.dp, vertical = 9.dp),
            color = Color.Transparent,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = null,
                    tint = if (focused) AboutTextPrimary else AboutTextMuted,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "CLOSE",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (focused) AboutTextPrimary else AboutTextMuted,
                )
            }
        }
    }
}

@Composable
private fun AboutInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = AboutTextMuted,
            modifier = Modifier.width(170.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = AboutTextPrimary,
        )
    }
}

/** Focuses the first row of a page so D-pad users always start at a known position. */
@Composable
private fun rememberPageFocusRequester(page: AboutPage): FocusRequester {
    val requester = remember(page) { FocusRequester() }
    LaunchedEffect(page, requester) {
        repeat(ABOUT_FOCUS_ATTEMPTS) {
            withFrameNanos { }
            if (runCatching { requester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    return requester
}

/** User-facing version: build-type suffixes are never shown as part of the version. */
private fun displayVersion(): String = BuildConfig.VERSION_NAME.substringBefore('-')

private val GitHubRelease.displayVersion: String
    get() = version?.toString() ?: tagName.removePrefix("v")
