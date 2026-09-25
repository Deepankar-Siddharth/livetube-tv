package com.livetube.tv.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

/** Owns the offline-first channel state used by the UI. */
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

    suspend fun refresh(): Boolean {
        _refreshing.value = true
        return try {
            val fetched = remote.fetch() ?: return false
            val current = _document.value
            if (fetched.dataVersion > current.dataVersion) {
                // Never replace a newer local document with an older remote one.
                cache.save(fetched)
                _document.value = fetched
                _usingCachedData.value = false
            } else if (fetched == current) {
                // An identical remote document proves that the local copy is current.
                _usingCachedData.value = false
            }
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
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
}
