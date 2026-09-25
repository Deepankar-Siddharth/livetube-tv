package com.livetube.tv.extractor

import com.livetube.tv.data.Channel
import com.livetube.tv.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.LinkedHashMap
import java.util.zip.GZIPInputStream

/**
 * Resolves a channel's current live item and asks NewPipeExtractor for a fresh manifest.
 * The canonical channel URL is the only input; extracted media URLs are never persisted.
 */
class YouTubeExtractor {
    private val extractionMutex = Mutex()

    suspend fun extract(channel: Channel): ExtractionResult = extractionMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                extractBlocking(channel)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                ExtractionResult.Failure(
                    reason = ExtractionResult.FailureReason.EXTRACTION_FAILED,
                    userMessage = "The live stream could not be resolved right now.",
                )
            } catch (_: LinkageError) {
                ExtractionResult.Failure(
                    reason = ExtractionResult.FailureReason.EXTRACTION_FAILED,
                    userMessage = "The live extraction component is unavailable on this device.",
                )
            }
        }
    }

    private fun extractBlocking(channel: Channel): ExtractionResult {
        val baseUrl = channelBaseUrl(channel.liveUrl)
        val service = NewPipe.getServiceByUrl(baseUrl)
        val channelInfo = ChannelInfo.getInfo(service, baseUrl)
        val liveTab = channelInfo.tabs.firstOrNull { tab ->
            tab.contentFilters.any { it.equals(ChannelTabs.LIVESTREAMS, ignoreCase = true) }
        } ?: return ExtractionResult.Failure(
            reason = ExtractionResult.FailureReason.NO_LIVE_BROADCAST,
            userMessage = "This channel does not expose a live-streams tab.",
        )

        val tabExtractor = service.getChannelTabExtractor(liveTab)
        tabExtractor.fetchPage()
        val liveItem = tabExtractor.getInitialPage().items
            .asSequence()
            .filterIsInstance<StreamInfoItem>()
            .firstOrNull { item ->
                item.streamType == StreamType.LIVE_STREAM ||
                    item.streamType == StreamType.AUDIO_LIVE_STREAM
            }
            ?: return ExtractionResult.Failure(
                reason = ExtractionResult.FailureReason.NO_LIVE_BROADCAST,
                userMessage = "This channel has no live broadcast at the moment.",
            )

        // Use the YouTube stream handler directly. The generic service URL dispatcher
        // also probes playlist handlers, which calls a Java 10 URLDecoder overload that
        // is not available on some Android TV runtimes even with library desugaring.
        val streamLinkHandler = YoutubeStreamLinkHandlerFactory.getInstance().fromUrl(liveItem.url)
        val streamInfo = StreamInfo.getInfo(service.getStreamExtractor(streamLinkHandler))
        val hlsUrl = streamInfo.hlsUrl.orEmpty().trim()
        val dashUrl = streamInfo.dashMpdUrl.orEmpty().trim()
        val media = when {
            hlsUrl.isNotEmpty() -> MediaSource(hlsUrl, "application/x-mpegURL")
            dashUrl.isNotEmpty() -> MediaSource(dashUrl, "application/dash+xml")
            else -> progressiveSource(streamInfo)
        }

        val resolution = streamInfo.videoStreams
            .mapNotNull(VideoStream::getHeight)
            .filter { it > 0 }
            .maxOrNull()
        return ExtractionResult.Success(
            mediaUrl = media.url,
            mimeType = media.mimeType,
            title = streamInfo.name.orEmpty().ifBlank { liveItem.name.orEmpty().ifBlank { channel.name } },
            channelName = streamInfo.uploaderName.orEmpty().ifBlank { channel.name },
            resolutionHeight = resolution,
            isLive = streamInfo.streamType == StreamType.LIVE_STREAM ||
                streamInfo.streamType == StreamType.AUDIO_LIVE_STREAM,
        )
    }

    private fun progressiveSource(streamInfo: StreamInfo): MediaSource {
        val stream = streamInfo.videoStreams
            .asSequence()
            .filter { it.isUrl && !it.isVideoOnly() }
            .maxByOrNull { it.bitrate }
            ?: throw IOException("No self-contained playable stream was returned")
        val mimeType = stream.format?.mimeType ?: "video/mp4"
        return MediaSource(stream.content, mimeType)
    }

    private fun channelBaseUrl(liveUrl: String): String {
        val uri = URI(liveUrl)
        require(
            uri.scheme.equals("https", true) &&
                uri.host.equals("www.youtube.com", true) &&
                uri.userInfo == null && uri.query == null && uri.fragment == null
        ) {
            "Only canonical YouTube live URLs are accepted"
        }
        val path = uri.path.orEmpty().trimEnd('/')
        require(path.endsWith("/live")) { "The live URL must end in /live" }
        return "https://www.youtube.com${path.removeSuffix("/live")}"
    }

    private data class MediaSource(val url: String, val mimeType: String)

    companion object {
        fun initialize() {
            try {
                NewPipe.init(YoutubeDownloader())
            } catch (_: Exception) {
                // A device-specific extractor failure is reported as a playback failure;
                // it must not prevent the offline channel guide from starting.
            } catch (_: LinkageError) {
                // Keep the TV shell usable when an optional extractor class is unavailable.
            }
        }
    }
}

/** Minimal NewPipeExtractor downloader backed by Android's HTTP stack. */
private class YoutubeDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val connection = (URL(request.url()).openConnection() as HttpURLConnection).apply {
            requestMethod = request.httpMethod()
            connectTimeout = Constants.CONNECT_TIMEOUT_MS
            readTimeout = Constants.READ_TIMEOUT_MS
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("User-Agent", Constants.userAgent())
            setRequestProperty("Accept-Encoding", "gzip")
        }
        request.headers().forEach { (name, values) ->
            if (values.isNotEmpty()) connection.setRequestProperty(name, values.joinToString(", "))
        }
        request.dataToSend()?.let { body ->
            connection.doOutput = true
            connection.setRequestProperty("Content-Length", body.size.toString())
            connection.outputStream.use { it.write(body) }
        }
        return try {
            val status = connection.responseCode
            val bodyStream = if (status in 200..299) connection.inputStream else connection.errorStream
            val decodedStream = if (connection.contentEncoding?.contains("gzip", true) == true) {
                bodyStream?.let(::GZIPInputStream)
            } else {
                bodyStream
            }
            val body = decodedStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val headers = LinkedHashMap<String, List<String>>()
            connection.headerFields.forEach { (name, values) ->
                if (name != null) headers[name] = values
            }
            Response(
                status,
                connection.responseMessage.orEmpty(),
                headers,
                body,
                connection.url.toString(),
            )
        } finally {
            connection.disconnect()
        }
    }
}
