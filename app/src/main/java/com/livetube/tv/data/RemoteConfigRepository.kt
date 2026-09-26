package com.livetube.tv.data

import com.livetube.tv.util.Constants
import com.livetube.tv.util.NetworkUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fetches and validates the GitHub-hosted channel document. */
class RemoteConfigRepository : ChannelRemoteSource {
    override suspend fun fetch(): ChannelDocument? = withContext(Dispatchers.IO) {
        val url = Constants.channelsUrl() ?: return@withContext null
        try {
            val raw = NetworkUtils.getText(
                url,
                headers = mapOf("Accept" to "application/json"),
            )
            // Parsing performs the full structural validation; a malformed or incompatible
            // document is rejected here and never reaches the cache.
            JsonUtils.parseDocument(raw)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }
}
