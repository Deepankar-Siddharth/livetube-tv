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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.charset.StandardCharsets

/** What a channel refresh did with the remote document. */
sealed interface ChannelRefreshResult {
    data class Updated(
        val document: ChannelDocument,
        val previousDataVersion: Int?,
    ) : ChannelRefreshResult

    data class Current(val document: ChannelDocument) : ChannelRefreshResult

    data class Downgrade(val document: ChannelDocument, val remoteDataVersion: Int) : ChannelRefreshResult

    data class Failed(val reason: String) : ChannelRefreshResult
}

/** Remote source of the channel document. */
fun interface ChannelRemoteSource {
    /** Returns the downloaded document, or null when the download or parsing failed. */
    suspend fun fetch(): ChannelDocument?
}

/**
 * Owns the offline-first channel state used by the UI.
 *
 * Startup order is the two-level cache first (current, then previous) and the bundled document
 * only as the last local fallback. Remote synchronisation runs behind that: a document is only
 * activated when its `data_version` is higher than the active one *and* it parses and validates.
 * Writes are serialised through a mutex and go through [ChannelCache], so repeated "Sync Now"
 * presses can never interleave.
 */
class ChannelRepository(
    private val cache: ChannelCache,
    private val remote: ChannelRemoteSource,
    private val bundled: () -> ChannelDocument?,
    private val isRemoteConfigured: () -> Boolean,
) {
    constructor(context: Context) : this(
        cache = ChannelCache(context.applicationContext.filesDir),
        remote = RemoteConfigRepository(),
        bundled = { readBundledDocument(context.applicationContext) },
        isRemoteConfigured = { Constants.channelsUrl() != null },
    )

    private val syncMutex = Mutex()
    private val _cacheSource = MutableStateFlow(ChannelCacheSource.CURRENT)
    private val _document = MutableStateFlow(loadInitialDocument())
    private val _refreshing = MutableStateFlow(false)
    private val _usingCachedData = MutableStateFlow(true)

    val document: StateFlow<ChannelDocument> = _document.asStateFlow()
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    val usingCachedData: StateFlow<Boolean> = _usingCachedData.asStateFlow()
    val cacheSource: StateFlow<ChannelCacheSource> = _cacheSource.asStateFlow()

    /** The version currently on screen. */
    val activeDataVersion: Int get() = _document.value.dataVersion

    fun loadInitialDocument(): ChannelDocument = try {
        val cached = cache.load()
        if (cached != null) {
            _cacheSource.value = cached.source
            cached.document
        } else {
            // Bundled data is only used when no valid cached document exists; it is never
            // written into the cache, so it cannot overwrite a newer local document.
            bundled() ?: emptyDocument()
        }
    } catch (_: Exception) {
        bundled() ?: emptyDocument()
    }

    fun refreshInBackground(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            refresh()
        }
    }

    /**
     * Downloads and, when the remote `data_version` is newer, activates the remote document.
     *
     * Concurrent calls are serialised; a second caller waits and then re-evaluates the version
     * rule, so two simultaneous syncs can never corrupt the cache or downgrade the data.
     */
    suspend fun refresh(): ChannelRefreshResult = syncMutex.withLock {
        _refreshing.value = true
        try {
            if (!isRemoteConfigured()) {
                return@withLock ChannelRefreshResult.Failed(SYNC_NOT_CONFIGURED)
            }
            val fetched = remote.fetch() ?: return@withLock ChannelRefreshResult.Failed(UNABLE_TO_SYNC)
            val current = _document.value
            when (ChannelDataVersion.decide(fetched, current)) {
                ChannelDataVersionDecision.UPDATE -> activate(fetched, current)
                ChannelDataVersionDecision.CURRENT -> {
                    // The remote document proves the local copy is current.
                    _usingCachedData.value = false
                    ChannelRefreshResult.Current(current)
                }

                ChannelDataVersionDecision.DOWNGRADE -> ChannelRefreshResult.Downgrade(
                    document = current,
                    remoteDataVersion = fetched.dataVersion,
                )

                ChannelDataVersionDecision.INCOMPATIBLE -> ChannelRefreshResult.Failed(
                    "The channel guide uses an unsupported format",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ChannelRefreshResult.Failed(UNABLE_TO_SYNC)
        } finally {
            _refreshing.value = false
        }
    }

    /** Promotes a validated document and publishes it to the UI. */
    private suspend fun activate(
        candidate: ChannelDocument,
        previous: ChannelDocument,
    ): ChannelRefreshResult {
        // Validation is part of activation: an invalid candidate never replaces the active data.
        JsonUtils.validateDocument(candidate)
        cache.promote(candidate)
        _document.value = candidate
        _usingCachedData.value = false
        _cacheSource.value = ChannelCacheSource.CURRENT
        return ChannelRefreshResult.Updated(candidate, previous.dataVersion.takeIf { previous.channels.isNotEmpty() })
    }

    /**
     * Repairs an unreadable current document from the previous copy.
     * Returns the restored document, or null when there is nothing to restore.
     */
    fun recoverFromPreviousCache(): ChannelDocument? {
        val restored = cache.restorePrevious() ?: return null
        _document.value = restored
        _cacheSource.value = ChannelCacheSource.CURRENT
        return restored
    }

    private fun emptyDocument() = ChannelDocument(
        schemaVersion = JsonUtils.SCHEMA_VERSION,
        dataVersion = 0,
        updatedAt = "1970-01-01T00:00:00Z",
        channels = emptyList(),
    )


    companion object {
        const val UNABLE_TO_SYNC = "Unable to sync channels"
        const val SYNC_NOT_CONFIGURED = "Channel sync is not configured in this build"

        /** Last local fallback: the bundled document, read once and never cached. */
        fun readBundledDocument(context: Context): ChannelDocument? = try {
            context.assets.open("channels.json").use { input ->
                JsonUtils.parseDocument(input.readBytes().toString(StandardCharsets.UTF_8))
            }
        } catch (_: Exception) {
            // A damaged optional asset must not prevent the TV UI from starting. The UI can
            // explain that no channels are available and a later remote refresh can recover.
            null
        }
    }
}

/**
 * Runs channel synchronisation and exposes a small status object for the settings UI.
 *
 * The manager owns no Compose or other UI code; it only starts syncs and reports what happened.
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
                    ChannelSyncResult.UpToDate(
                        channelCount = refresh.document.channels.size,
                        dataVersion = refresh.document.dataVersion,
                    )
                }

                is ChannelRefreshResult.Downgrade -> ChannelSyncResult.KeptLocal(
                    dataVersion = refresh.document.dataVersion,
                    remoteDataVersion = refresh.remoteDataVersion,
                    channelCount = refresh.document.channels.size,
                )

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
                dataVersion = result.dataVersion,
            )

            is ChannelSyncResult.KeptLocal -> previous.copy(
                state = ChannelSyncState.UP_TO_DATE,
                lastSyncMillis = syncedAt,
                channelCount = result.channelCount,
                dataVersion = result.dataVersion,
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
    data class UpToDate(val channelCount: Int, val dataVersion: Int) : ChannelSyncResult

    /** The remote document was older, so the local one was kept. */
    data class KeptLocal(
        val dataVersion: Int,
        val remoteDataVersion: Int,
        val channelCount: Int,
    ) : ChannelSyncResult

    data class Failed(val reason: String) : ChannelSyncResult
}
