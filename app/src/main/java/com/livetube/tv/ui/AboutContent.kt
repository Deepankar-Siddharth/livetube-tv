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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.livetube.tv.BuildConfig
import com.livetube.tv.R
import com.livetube.tv.update.GitHubRelease
import com.livetube.tv.update.UpdateCheckResult

/** Pages of the About experience, hosted by the Settings screen. */
internal enum class AboutPage {
    HOME,
    DEVELOPER,
    APP_INFORMATION,
    LATEST_VERSION,
}

/**
 * About content only: the pages themselves, without a dialog or a navigation stack.
 *
 * Only information a viewer needs is shown. Build, dependency, extraction and cache internals are
 * deliberately not part of this screen.
 */
@Composable
internal fun AboutPageHeader(page: AboutPage) {
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
internal fun AboutPageBody(
    page: AboutPage,
    availableRelease: GitHubRelease?,
    focusRequester: FocusRequester?,
    onOpenLatest: () -> Unit,
    onOpenDeveloper: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenInformation: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenRepository: () -> Unit,
    onOpenReleases: () -> Unit,
    onInstallUpdate: (GitHubRelease) -> Unit,
) {
    when (page) {
        AboutPage.HOME -> AboutHomeRows(
            availableRelease = availableRelease,
            focusRequester = focusRequester,
            onOpenLatest = onOpenLatest,
            onOpenDeveloper = onOpenDeveloper,
            onOpenSource = onOpenSource,
            onOpenInformation = onOpenInformation,
        )

        AboutPage.DEVELOPER -> AboutDeveloperRows(
            focusRequester = focusRequester,
            onOpenProfile = onOpenProfile,
            onOpenRepository = onOpenRepository,
        )

        AboutPage.APP_INFORMATION -> AboutInformationRows()

        AboutPage.LATEST_VERSION -> AboutLatestVersionRows(
            availableRelease = availableRelease,
            focusRequester = focusRequester,
            onOpenReleases = onOpenReleases,
            onInstallUpdate = onInstallUpdate,
        )
    }
}

@Composable
private fun AboutHomeRows(
    availableRelease: GitHubRelease?,
    focusRequester: FocusRequester?,
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
    focusRequester: FocusRequester?,
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
        color = TvPalette.Card,
        border = BorderStroke(1.dp, TvPalette.Border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AboutInfoRow("App Version", AppVersionText.display())
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
    focusRequester: FocusRequester?,
    onOpenReleases: () -> Unit,
    onInstallUpdate: (GitHubRelease) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (availableRelease != null) Color(0xFF2A131C) else TvPalette.Card,
        border = BorderStroke(1.dp, if (availableRelease != null) TvPalette.Red else TvPalette.Border),
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
                    color = TvPalette.TextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (availableRelease != null) {
                        "Version ${availableRelease.displayVersion} is ready to install"
                    } else {
                        "Current version ${AppVersionText.display()}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TvPalette.TextMuted,
                )
            }
            if (availableRelease != null) {
                Spacer(Modifier.width(16.dp))
                TvPillButton(
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
        // so it is placed directly on the panel instead of on a light card.
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
            color = TvPalette.TextPrimary,
        )
    }
}

@Composable
internal fun AboutSubPageHeader(
    title: String,
    subtitle: String,
    description: String? = null,
    logoSize: androidx.compose.ui.unit.Dp = 54.dp,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.livetube_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(logoSize),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TvPalette.TextPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TvPalette.TextMuted,
            )
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TvPalette.TextMuted,
                )
            }
        }
    }
}

@Composable
internal fun AboutActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    focusRequester: FocusRequester? = null,
    trailing: @Composable (() -> Unit)? = null,
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
            .background(if (focused) TvPalette.CardFocused else TvPalette.Card)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else TvPalette.Border,
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
                tint = if (focused) TvPalette.Red else TvPalette.TextMuted,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                    color = TvPalette.TextPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TvPalette.TextMuted,
                    maxLines = 2,
                )
            }
            if (badge != null) {
                Spacer(Modifier.width(12.dp))
                TvBadge(text = badge)
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            } else {
                Spacer(Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = if (focused) TvPalette.TextPrimary else TvPalette.TextMuted,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
internal fun TvBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = TvPalette.Red,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
internal fun TvPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var focused by remember(text) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(120),
        label = "pillButtonFocus",
    )
    val shape = RoundedCornerShape(10.dp)
    val background = when {
        !enabled -> TvPalette.SurfaceStrong
        focused -> TvPalette.RedBright
        else -> TvPalette.Red
    }
    Surface(
        modifier = modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        color = Color.Transparent,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Color.White else TvPalette.TextMuted,
        )
    }
}

@Composable
internal fun AboutCloseButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.03f else 1f,
        animationSpec = tween(120),
        label = "closeFocus",
    )
    val shape = RoundedCornerShape(12.dp)
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .background(if (focused) TvPalette.SurfaceFocused else TvPalette.SurfaceStrong)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) Color.White else TvPalette.Border,
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
                    tint = if (focused) TvPalette.TextPrimary else TvPalette.TextMuted,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "CLOSE",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (focused) TvPalette.TextPrimary else TvPalette.TextMuted,
                )
            }
        }
    }
}

@Composable
internal fun AboutInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TvPalette.TextMuted,
            modifier = Modifier.width(170.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = TvPalette.TextPrimary,
        )
    }
}

/** User-facing version helpers. Build-type suffixes are never part of the version string. */
internal object AppVersionText {
    fun display(): String = BuildConfig.VERSION_NAME.substringBefore('-')
}

internal val GitHubRelease.displayVersion: String
    get() = version?.toString() ?: tagName.removePrefix("v")

/** Maps an update check result to a short, non-technical status line for the settings page. */
internal fun UpdateCheckResult.statusText(): String = when (this) {
    is UpdateCheckResult.Available -> "Update available: ${release.displayVersion}"
    is UpdateCheckResult.Current -> "Up to date"
    is UpdateCheckResult.Unavailable -> reason
    is UpdateCheckResult.Failed -> "Unable to check updates"
}
