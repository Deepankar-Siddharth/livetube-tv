package com.livetube.tv.ui

import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableSharedFlow
import com.livetube.tv.LiveTubeApplication
import com.livetube.tv.data.ChannelRepository
import com.livetube.tv.extractor.YouTubeExtractor
import com.livetube.tv.player.PlaybackController
import com.livetube.tv.player.PlayerManager
import com.livetube.tv.update.InstallResult
import com.livetube.tv.update.UpdateCheckResult
import com.livetube.tv.update.UpdateInstaller
import com.livetube.tv.update.UpdateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LAUNCH_BRANDING_DURATION_MS = 1_100L

class MainActivity : ComponentActivity() {
    private val remoteKeyEvents = MutableSharedFlow<Int>(replay = 1, extraBufferCapacity = 16)
    private var captureRemoteNavigation = true
    private lateinit var playbackController: PlaybackController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val channelRepository = ChannelRepository(this)
        val favoritesRepository = (application as LiveTubeApplication).favoritesRepository
        val playerManager = PlayerManager(this)
        playbackController = PlaybackController(YouTubeExtractor(), playerManager)
        val updateManager = UpdateManager()
        val updateInstaller = UpdateInstaller(this)

        channelRepository.refreshInBackground(lifecycleScope)
        setContent {
            LiveTubeTheme {
                val document by channelRepository.document.collectAsStateWithLifecycle()
                val playback by playbackController.state.collectAsStateWithLifecycle()
                val refreshing by channelRepository.refreshing.collectAsStateWithLifecycle()
                val usingCachedData by channelRepository.usingCachedData.collectAsStateWithLifecycle()
                val favoriteChannelIds by favoritesRepository.favoriteChannelIds
                    .collectAsStateWithLifecycle(initialValue = emptySet())
                var updateResult by remember {
                    mutableStateOf<UpdateCheckResult>(
                        UpdateCheckResult.Unavailable("Checking in background"),
                    )
                }
                var showLaunchBranding by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    updateResult = updateManager.checkForUpdate()
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
                        onSelectChannel = playbackController::select,
                        onRetryPlayback = playbackController::retryNow,
                        onInstallUpdate = { release ->
                            lifecycleScope.launch {
                                val result = updateInstaller.downloadAndInstall(this@MainActivity, release)
                                val message = when (result) {
                                    InstallResult.Started -> "Android package installer opened"
                                    InstallResult.PermissionRequired ->
                                        "Allow installation from this app, then choose Update again"
                                    is InstallResult.Failed -> result.message
                                }
                                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                            }
                        },
                        onSetFavorite = { channel, favorite ->
                            lifecycleScope.launch {
                                favoritesRepository.setFavorite(channel.id, favorite)
                            }
                        },
                        onDismissUpdate = { updateResult = UpdateCheckResult.Unavailable("Later") },
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
        if (::playbackController.isInitialized) {
            playbackController.pause()
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (::playbackController.isInitialized) {
            playbackController.release()
        }
        super.onDestroy()
    }
}
