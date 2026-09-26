package com.livetube.tv.ui

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.livetube.tv.BuildConfig
import com.livetube.tv.LiveTubeApplication
import com.livetube.tv.data.AppSettings
import com.livetube.tv.data.ChannelRepository
import com.livetube.tv.data.ChannelSyncManager
import com.livetube.tv.data.ChannelSyncResult
import com.livetube.tv.data.ChannelSyncState
import com.livetube.tv.data.SettingsRepository
import com.livetube.tv.extractor.YouTubeExtractor
import com.livetube.tv.player.PlaybackController
import com.livetube.tv.player.PlayerManager
import com.livetube.tv.update.ApkDownloadProgress
import com.livetube.tv.update.DownloadResult
import com.livetube.tv.update.GitHubRelease
import com.livetube.tv.update.UpdateCheckResult
import com.livetube.tv.update.UpdateInstaller
import com.livetube.tv.update.UpdateManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

private const val LAUNCH_BRANDING_DURATION_MS = 1_100L
private const val UNABLE_TO_CHECK_MESSAGE = "Unable to check updates. Check your internet connection."
private val ALLOW_INSTALL_MESSAGE = UpdateInstaller.ALLOW_INSTALL_MESSAGE
private const val INSTALL_CANCELLED_MESSAGE = "The update was not installed. You can try again."

class MainActivity : ComponentActivity() {
    /**
     * Remote navigation keys are only intercepted while the bottom guide is closed.
     * While the guide is visible Compose owns the D-pad so its two rows stay navigable.
     */
    private val remoteKeyEvents = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    private var captureRemoteNavigation = true
    private lateinit var playbackController: PlaybackController
    private lateinit var channelRepository: ChannelRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var channelSyncManager: ChannelSyncManager
    private lateinit var updateManager: UpdateManager
    private lateinit var updateInstaller: UpdateInstaller
    private var downloadJob: Job? = null
    private var downloadedUpdate: Pair<GitHubRelease, File>? = null
    private var pendingInstallVersionCode: Long = 0L

    /** Confirms an installation by comparing version codes instead of trusting the result code. */
    private val installLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val expected = pendingInstallVersionCode
        pendingInstallVersionCode = 0L
        val message = if (expected > 0L && installedVersionCode() >= expected) {
            "LiveTube TV is updated. Restart the app to use the new version."
        } else {
            INSTALL_CANCELLED_MESSAGE
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val application = application as LiveTubeApplication
        val favoritesRepository = application.favoritesRepository
        settingsRepository = application.settingsRepository
        channelRepository = ChannelRepository(this)
        channelSyncManager = ChannelSyncManager(channelRepository, settingsRepository)
        val playerManager = PlayerManager(this)
        playbackController = PlaybackController(YouTubeExtractor(), playerManager)
        updateManager = UpdateManager()
        updateInstaller = UpdateInstaller(this)

        // The guide is always available offline; the remote refresh only runs behind it.
        channelSyncManager.syncOnLaunch(lifecycleScope)
        setContent {
            LiveTubeTheme {
                val document by channelRepository.document.collectAsStateWithLifecycle()
                val playback by playbackController.state.collectAsStateWithLifecycle()
                val refreshing by channelRepository.refreshing.collectAsStateWithLifecycle()
                val usingCachedData by channelRepository.usingCachedData.collectAsStateWithLifecycle()
                val favoriteChannelIds by favoritesRepository.favoriteChannelIds
                    .collectAsStateWithLifecycle(initialValue = emptySet())
                val settings by settingsRepository.settings
                    .collectAsStateWithLifecycle(initialValue = AppSettings.EMPTY)
                val syncStatus by channelSyncManager.status.collectAsStateWithLifecycle()

                var updateResult by remember {
                    mutableStateOf<UpdateCheckResult>(
                        UpdateCheckResult.Unavailable("Checking in background"),
                    )
                }
                var updateFlowState by remember { mutableStateOf<UpdateFlowState>(UpdateFlowState.Idle) }
                var updateCheckInProgress by remember { mutableStateOf(false) }
                var updateCheckFeedback by remember { mutableStateOf<UpdateCheckFeedback?>(null) }
                var updateInstallInProgress by remember { mutableStateOf(false) }
                var manualSyncRunning by remember { mutableStateOf(false) }
                var syncFeedback by remember { mutableStateOf<ChannelSyncFeedback?>(null) }
                var showLaunchBranding by remember { mutableStateOf(true) }
                val syncInProgress = manualSyncRunning || syncStatus.state == ChannelSyncState.SYNCING

                fun startUpdateDownload(release: GitHubRelease) {
                    if (downloadJob?.isActive == true) return
                    val version = release.displayVersionForUser()
                    val ready = downloadedUpdate
                    if (ready != null && ready.first.version == release.version && ready.second.isFile) {
                        // The APK from an earlier attempt is still verified and usable.
                        updateFlowState = UpdateFlowState.Ready(version)
                        return
                    }
                    updateFlowState = UpdateFlowState.Downloading(version, null)
                    downloadJob = lifecycleScope.launch {
                        val outcome = updateInstaller.download(release) { progress ->
                            updateFlowState = UpdateFlowState.Downloading(version, progress.percent)
                        }
                        when (outcome) {
                            is DownloadResult.Ready -> {
                                downloadedUpdate = release to outcome.file
                                updateFlowState = UpdateFlowState.Ready(version)
                            }

                            DownloadResult.PermissionRequired -> {
                                updateInstaller.requestInstallPermission(this@MainActivity)
                                updateFlowState = UpdateFlowState.Failed(ALLOW_INSTALL_MESSAGE)
                            }

                            is DownloadResult.Failed -> {
                                updateFlowState = UpdateFlowState.Failed(outcome.message)
                            }
                        }
                    }
                }

                suspend fun startUpdateCheck(manual: Boolean) {
                    if (updateCheckInProgress) return
                    updateCheckInProgress = true
                    updateCheckFeedback = null
                    val result = updateManager.checkForUpdate()
                    settingsRepository.recordUpdateCheck()
                    updateResult = result
                    updateCheckInProgress = false
                    when (result) {
                        is UpdateCheckResult.Available -> startUpdateDownload(result.release)
                        is UpdateCheckResult.Current -> if (manual) {
                            updateCheckFeedback = UpdateCheckFeedback.UpToDate
                        }

                        is UpdateCheckResult.Failed -> if (manual) {
                            updateCheckFeedback = UpdateCheckFeedback.Failed(UNABLE_TO_CHECK_MESSAGE)
                        }

                        is UpdateCheckResult.Unavailable -> if (manual) {
                            updateCheckFeedback = UpdateCheckFeedback.Failed(result.reason)
                        }
                    }
                }

                suspend fun runChannelSync(manual: Boolean) {
                    if (manualSyncRunning) return
                    manualSyncRunning = true
                    syncFeedback = null
                    val result = channelSyncManager.sync()
                    manualSyncRunning = false
                    if (manual) {
                        syncFeedback = when (result) {
                            is ChannelSyncResult.Updated ->
                                ChannelSyncFeedback.Updated(result.channelCount)

                            is ChannelSyncResult.UpToDate ->
                                ChannelSyncFeedback.UpToDate(result.channelCount)

                            is ChannelSyncResult.Failed -> ChannelSyncFeedback.Failed(result.reason)
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    val preferences = settingsRepository.settings.first()
                    if (preferences.autoCheckUpdates) {
                        startUpdateCheck(manual = false)
                    } else {
                        updateCheckInProgress = false
                    }
                }
                LaunchedEffect(Unit) {
                    delay(LAUNCH_BRANDING_DURATION_MS)
                    showLaunchBranding = false
                }

                if (showLaunchBranding) {
                    SplashBranding()
                } else {
                    MainScreen(
                        document = document,
                        playback = playback,
                        playerManager = playerManager,
                        isRefreshing = refreshing,
                        usingCachedData = usingCachedData,
                        favoriteChannelIds = favoriteChannelIds,
                        updateResult = updateResult,
                        settings = settings,
                        updateFlowState = updateFlowState,
                        updateCheckInProgress = updateCheckInProgress,
                        updateCheckFeedback = updateCheckFeedback,
                        updateInstallInProgress = updateInstallInProgress,
                        syncStatus = syncStatus,
                        syncInProgress = syncInProgress,
                        syncFeedback = syncFeedback,
                        onSelectChannel = playbackController::select,
                        onRetryPlayback = playbackController::retryNow,
                        onSetFavorite = { channel, favorite ->
                            lifecycleScope.launch {
                                favoritesRepository.setFavorite(channel.id, favorite)
                            }
                        },
                        onDownloadUpdate = { release -> startUpdateDownload(release) },
                        onInstallDownloadedUpdate = {
                            val ready = downloadedUpdate
                            if (ready == null) {
                                updateFlowState = UpdateFlowState.Failed(INSTALL_CANCELLED_MESSAGE)
                            } else {
                                val intent = updateInstaller.installIntent(ready.second)
                                if (intent == null) {
                                    lifecycleScope.launch {
                                        updateInstaller.requestInstallPermission(this@MainActivity)
                                    }
                                    updateFlowState = UpdateFlowState.Failed(ALLOW_INSTALL_MESSAGE)
                                } else {
                                    updateInstallInProgress = true
                                    pendingInstallVersionCode =
                                        updateInstaller.versionCodeOf(ready.second)
                                    updateFlowState = UpdateFlowState.Idle
                                    runCatching { installLauncher.launch(intent) }
                                        .onFailure {
                                            updateFlowState =
                                                UpdateFlowState.Failed(INSTALL_CANCELLED_MESSAGE)
                                        }
                                    updateInstallInProgress = false
                                }
                            }
                        },
                        onPostponeUpdate = {
                            updateFlowState = UpdateFlowState.Idle
                        },
                        onDismissUpdateMessage = {
                            updateFlowState = UpdateFlowState.Idle
                        },
                        onSetAutoCheckUpdates = { enabled ->
                            lifecycleScope.launch { settingsRepository.setAutoCheckUpdates(enabled) }
                        },
                        onCheckForUpdate = {
                            lifecycleScope.launch { startUpdateCheck(manual = true) }
                        },
                        onSyncChannels = {
                            lifecycleScope.launch { runChannelSync(manual = true) }
                        },
                        onConsumeUpdateFeedback = { updateCheckFeedback = null },
                        onConsumeSyncFeedback = { syncFeedback = null },
                        remoteKeyEvents = remoteKeyEvents,
                        onRemoteNavigationModeChanged = { captureRemoteNavigation = it },
                        onExit = { finish() },
                    )
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val isRemoteNavigation = keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        if (captureRemoteNavigation && isRemoteNavigation && event.repeatCount == 0) {
            remoteKeyEvents.tryEmit(keyCode)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStart() {
        super.onStart()
        if (::playbackController.isInitialized) {
            playbackController.resume()
        }
    }

    override fun onStop() {
        super.onStop()
        if (::playbackController.isInitialized) {
            playbackController.pause()
        }
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        if (::playbackController.isInitialized) {
            playbackController.release()
        }
        super.onDestroy()
    }

    private fun installedVersionCode(): Long = try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    } catch (_: PackageManager.NameNotFoundException) {
        0L
    }

    private fun GitHubRelease.displayVersionForUser(): String =
        version?.toString() ?: tagName.removePrefix("v").ifBlank { BuildConfig.VERSION_NAME }

}
