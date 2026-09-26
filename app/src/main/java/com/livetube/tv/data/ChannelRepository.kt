package com.livetube.tv.data

import android.content.Context
import com.livetube.tv.util.Constants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

/** What a channel refresh did with the remote document. */
sealed interface ChannelRefreshResult {
    data class Updated(val document: ChannelDocument) : ChannelRefreshResult
    data class Current(val document: ChannelDocument) : ChannelRefreshResult
    data class Failed(val reason: String) : ChannelRefreshResult
}

/**
 * Owns the offline-first channel state used by the UI.
 *
 * The repository stays the single owner of the document; synchronisation policy, timestamps
 * and user-facing status are layered on top by [ChannelSyncManager].
 */
class ChannelRepository(context: Context) {
    private val appContext = context.applicationContext
    private val cache = ChannelCache(appContext)
    private val remote = RemoteConfigRepository()
    private val _document = MutableStateFlow(loadInitialDocument())
    private val _refreshing = MutableStateFlow(false)
    private val _usingCachedData = MutableStateFlow(true)

    val document: StateFlow<ChannelDocument> = _document.asStateFlow()
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    val usingCachedData: StateFlow<Boolean> = _usingCachedData.asStateFlow()

    fun loadInitialDocument(): ChannelDocument = cache.load() ?: bundledDocument()

    fun refreshInBackground(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            refresh()
        }
    }

    suspend fun refresh(): ChannelRefreshResult {
        _refreshing.value = true
        return try {
            if (Constants.channelsUrl() == null) {
                return ChannelRefreshResult.Failed(SYNC_NOT_CONFIGURED)
            }
            val fetched = remote.fetch()
                ?: return ChannelRefreshResult.Failed(UNABLE_TO_SYNC)
            val current = _document.value
            if (fetched.isNewerThan(current)) {
                // Never replace a newer local document with an older remote one.
                cache.save(fetched)
                _document.value = fetched
                _usingCachedData.value = false
                ChannelRefreshResult.Updated(fetched)
            } else {
                // An identical remote document proves that the local copy is current.
                if (fetched.sameRevisionAs(current)) _usingCachedData.value = false
                ChannelRefreshResult.Current(current)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ChannelRefreshResult.Failed(UNABLE_TO_SYNC)
        } finally {
            _refreshing.value = false
        }
    }

    private fun bundledDocument(): ChannelDocument = try {
        appContext.assets.open("channels.json").use { input ->
            JsonUtils.parseDocument(input.readBytes().toString(StandardCharsets.UTF_8))
        }
    } catch (_: Exception) {
        // A damaged optional asset must not prevent the TV UI from starting. The UI can
        // explain that no channels are available and a later remote refresh can recover.
        ChannelDocument(
            schemaVersion = JsonUtils.SCHEMA_VERSION,
            dataVersion = 0,
            updatedAt = "1970-01-01T00:00:00Z",
            channels = emptyList(),
        )
    }

    companion object {
        const val UNABLE_TO_SYNC = "Unable to sync channels"
        const val SYNC_NOT_CONFIGURED = "Channel sync is not configured in this build"
    }
}

/**
 * Downloads, validates and applies the GitHub-hosted channel document.
 *
 * The manager owns synchronisation policy: it runs the refresh in the background, records when
 * the last successful sync happened, and exposes a small status object for the settings UI. No
 * Compose or other UI code belongs here.
 */
class ChannelSyncManager(
    private val channelRepository: ChannelRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val _status = MutableStateFlow(ChannelSyncStatus())
    val status: StateFlow<ChannelSyncStatus> = _status.asStateFlow()

    init {
        // Start from the guide the app already has so the count is correct before the first sync.
        val document = channelRepository.document.value
        _status.value = ChannelSyncStatus(
            state = if (document.channels.isEmpty()) {
                ChannelSyncState.FAILED
            } else {
                ChannelSyncState.UP_TO_DATE
            },
            channelCount = document.channels.size,
            dataVersion = document.dataVersion,
        )
    }

    /** Keeps the guide current on every launch without blocking startup. */
    fun syncOnLaunch(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) { sync() }
    }

    suspend fun sync(): ChannelSyncResult {
        val previous = _status.value
        _status.value = previous.copy(state = ChannelSyncState.SYNCING, message = null)
        val result = try {
            when (val refresh = channelRepository.refresh()) {
                is ChannelRefreshResult.Updated -> {
                    settingsRepository.recordChannelSync()
                    ChannelSyncResult.Updated(
                        channelCount = refresh.document.channels.size,
                        dataVersion = refresh.document.dataVersion,
                    )
                }

                is ChannelRefreshResult.Current -> {
                    settingsRepository.recordChannelSync()
                    ChannelSyncResult.UpToDate(channelCount = refresh.document.channels.size)
                }

                is ChannelRefreshResult.Failed -> ChannelSyncResult.Failed(refresh.reason)
            }
        } catch (error: CancellationException) {
            _status.value = previous.copy(state = ChannelSyncState.FAILED, message = UNABLE_TO_SYNC)
            throw error
        } catch (_: Exception) {
            _status.value = previous.copy(state = ChannelSyncState.FAILED, message = UNABLE_TO_SYNC)
            ChannelSyncResult.Failed(UNABLE_TO_SYNC)
        }
        val syncedAt = System.currentTimeMillis()
        _status.value = when (result) {
            is ChannelSyncResult.Updated -> ChannelSyncStatus(
                state = ChannelSyncState.UPDATED,
                lastSyncMillis = syncedAt,
                channelCount = result.channelCount,
                dataVersion = result.dataVersion,
            )

            is ChannelSyncResult.UpToDate -> ChannelSyncStatus(
                state = ChannelSyncState.UP_TO_DATE,
                lastSyncMillis = syncedAt,
                channelCount = result.channelCount,
            )

            is ChannelSyncResult.Failed -> previous.copy(
                state = ChannelSyncState.FAILED,
                message = result.reason,
            )
        }
        return result
    }

    companion object {
        const val UNABLE_TO_SYNC = ChannelRepository.UNABLE_TO_SYNC
    }
}

enum class ChannelSyncState {
    SYNCING,
    UP_TO_DATE,
    UPDATED,
    FAILED,
}

data class ChannelSyncStatus(
    val state: ChannelSyncState = ChannelSyncState.UP_TO_DATE,
    val lastSyncMillis: Long = 0L,
    val channelCount: Int = 0,
    val dataVersion: Int = 0,
    val message: String? = null,
)

sealed interface ChannelSyncResult {
    data class Updated(val channelCount: Int, val dataVersion: Int) : ChannelSyncResult
    data class UpToDate(val channelCount: Int) : ChannelSyncResult
    data class Failed(val reason: String) : ChannelSyncResult
}
