package com.livetube.tv.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.livetube.tv.R
import com.livetube.tv.data.Channel
import com.livetube.tv.data.ChannelCatalog
import com.livetube.tv.data.ChannelDocument
import com.livetube.tv.player.PlaybackController
import com.livetube.tv.player.PlaybackPhase
import com.livetube.tv.player.PlaybackState
import com.livetube.tv.player.PlayerManager
import com.livetube.tv.update.GitHubRelease
import com.livetube.tv.update.UpdateCheckResult
import com.livetube.tv.util.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
fun MainScreen(
    document: ChannelDocument,
    playback: PlaybackState,
    playerManager: PlayerManager,
    isRefreshing: Boolean,
    usingCachedData: Boolean,
    favoriteChannelIds: Set<String>,
    updateResult: UpdateCheckResult,
    onSelectChannel: (Channel) -> Unit,
    onSetFavorite: (Channel, Boolean) -> Unit,
    onRetryPlayback: () -> Unit,
    onInstallUpdate: (GitHubRelease) -> Unit,
    onDismissUpdate: () -> Unit,
    remoteKeyEvents: SharedFlow<Int>,
    onRemoteNavigationModeChanged: (Boolean) -> Unit,
    onExit: () -> Unit,
) {
    val enabledChannels = document.enabledChannels()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var guideVisible by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var actionChannel by remember { mutableStateOf<Channel?>(null) }
    var overlayHasFocus by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    var guideModalVisible by remember { mutableStateOf(false) }
    var rememberedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var subcategoryFilterId by rememberSaveable {
        mutableStateOf(ChannelCatalog.ALL_SUBCATEGORY_ID)
    }

    LaunchedEffect(playback.channel) {
        if (playback.channel == null) overlayHasFocus = false
    }

    // The activity only captures remote keys while the guide is closed, so the two guide
    // rows keep their own D-pad handling. This is toggled synchronously on every change
    // to keep activity and Compose key ownership in sync.
    fun setGuideVisible(visible: Boolean) {
        guideVisible = visible
        onRemoteNavigationModeChanged(!visible)
    }

    fun handleRemoteKey(key: Key): Boolean {
        interaction += 1
        if (!guideVisible && overlayHasFocus) {
            return when (key) {
                Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> false
                Key.DirectionLeft, Key.DirectionRight -> {
                    if (playback.phase == PlaybackPhase.ERROR || playback.phase == PlaybackPhase.ENDED) {
                        false
                    } else {
                        setGuideVisible(true)
                        true
                    }
                }

                Key.DirectionUp, Key.DirectionDown -> {
                    setGuideVisible(true)
                    true
                }

                else -> false
            }
        }
        return when (key) {
            Key.DirectionUp,
            Key.DirectionDown,
            Key.DirectionLeft,
            Key.DirectionRight,
            Key.Enter,
            Key.NumPadEnter,
            Key.DirectionCenter,
            -> {
                if (!guideVisible) {
                    setGuideVisible(true)
                    true
                } else {
                    false
                }
            }

            else -> false
        }
    }

    LaunchedEffect(Unit) {
        remoteKeyEvents.collect { keyCode ->
            val key = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> Key.DirectionUp
                KeyEvent.KEYCODE_DPAD_DOWN -> Key.DirectionDown
                KeyEvent.KEYCODE_DPAD_LEFT -> Key.DirectionLeft
                KeyEvent.KEYCODE_DPAD_RIGHT -> Key.DirectionRight
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                -> Key.Enter
                else -> null
            }
            if (key != null) handleRemoteKey(key)
        }
    }

    LaunchedEffect(document.dataVersion, enabledChannels.size) {
        val currentStillExists = enabledChannels.any { it.id == selectedId }
        if (!currentStillExists) {
            enabledChannels.firstOrNull()?.let { first ->
                selectedId = first.id
                onSelectChannel(first)
            }
        }
    }

    LaunchedEffect(guideVisible, interaction, actionChannel, guideModalVisible) {
        if (guideVisible && actionChannel == null && !guideModalVisible) {
            delay(Constants.GUIDE_TIMEOUT_MS)
            setGuideVisible(false)
        }
    }

    LaunchedEffect(
        updateResult,
        showExitDialog,
        showAboutDialog,
        actionChannel,
        overlayHasFocus,
        guideModalVisible,
    ) {
        val modalVisible = updateResult is UpdateCheckResult.Available ||
            showExitDialog ||
            showAboutDialog ||
            actionChannel != null ||
            guideModalVisible
        onRemoteNavigationModeChanged(!guideVisible && !modalVisible && !overlayHasFocus)
    }

    val updateAvailable = updateResult as? UpdateCheckResult.Available
    BackHandler {
        when {
            actionChannel != null -> actionChannel = null
            showExitDialog -> showExitDialog = false
            showAboutDialog -> showAboutDialog = false
            updateAvailable != null -> onDismissUpdate()
            guideVisible -> setGuideVisible(false)
            else -> showExitDialog = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // BACK is owned by the BackHandler below so its behavior stays predictable.
                if (event.key == Key.Back) return@onPreviewKeyEvent false
                handleRemoteKey(event.key)
            },
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = playerManager.player
                }
            },
            update = { view -> view.player = playerManager.player },
            modifier = Modifier.fillMaxSize(),
        )

        if (enabledChannels.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.livetube_icon),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(112.dp),
                )
                Text(
                    text = "No enabled channels are available.\nConfigure github.owner and github.repository in the build.",
                    color = Color.White,
                )
            }
        }

        PlaybackStatusOverlay(
            channel = playback.channel,
            playback = playback,
            usingCachedData = usingCachedData,
            requestFocus = !guideVisible,
            onRetry = onRetryPlayback,
            onAbout = { showAboutDialog = true },
            onOverlayFocusChanged = { overlayHasFocus = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        )

        if (isRefreshing && !guideVisible) {
            Text(
                text = "SYNCING CHANNEL GUIDE…",
                color = Color(0xFFB8C8D8),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.TopEnd).padding(18.dp),
            )
        }

        if (guideVisible) {
            ChannelGuide(
                channels = enabledChannels,
                favoriteChannelIds = favoriteChannelIds,
                selectedChannelId = selectedId,
                liveChannelId = playback.channel?.id?.takeIf { playback.isLive },
                preferredCategoryId = rememberedCategoryId,
                subcategoryFilterId = subcategoryFilterId,
                onCategoryChange = { categoryId ->
                    rememberedCategoryId = categoryId
                    if (subcategoryFilterId != ChannelCatalog.ALL_SUBCATEGORY_ID) {
                        subcategoryFilterId = ChannelCatalog.ALL_SUBCATEGORY_ID
                    }
                },
                onSubcategoryFilterChange = { subcategoryFilterId = it },
                onModalChanged = { guideModalVisible = it },
                onChannelSelected = { channel ->
                    selectedId = channel.id
                    onSelectChannel(channel)
                    setGuideVisible(false)
                    interaction += 1
                },
                onChannelActions = { channel ->
                    actionChannel = channel
                    interaction += 1
                },
                onShowAbout = {
                    showAboutDialog = true
                    setGuideVisible(false)
                    interaction += 1
                },
                onInteraction = { interaction += 1 },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    actionChannel?.let { channel ->
        ChannelActionsDialog(
            channel = channel,
            favorite = channel.id in favoriteChannelIds,
            onPlay = {
                selectedId = channel.id
                onSelectChannel(channel)
                actionChannel = null
                setGuideVisible(false)
                interaction += 1
            },
            onSetFavorite = { favorite -> onSetFavorite(channel, favorite) },
            onDismiss = { actionChannel = null },
        )
    }
    if (updateAvailable != null) {
        UpdateDialog(
            release = updateAvailable.release,
            onInstall = { onInstallUpdate(updateAvailable.release) },
            onLater = onDismissUpdate,
        )
    }
    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
    if (showExitDialog) {
        ExitDialog(
            onExit = onExit,
            onStay = { showExitDialog = false },
        )
    }
}
