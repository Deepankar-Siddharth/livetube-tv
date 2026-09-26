package com.livetube.tv.update

import android.content.Context
import com.livetube.tv.util.Constants
import com.livetube.tv.util.NetworkUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URI
import kotlin.coroutines.coroutineContext

/** Download progress for a release APK. */
data class ApkDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
) {
    /** 0..100, or null while the server has not reported a size yet. */
    val percent: Int?
        get() = if (totalBytes > 0L) {
            ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
        } else {
            null
        }
}

/** Outcome of a completed APK transfer. */
data class ApkDownloadResult(
    val file: File,
    val finalUrl: String,
)

/**
 * Downloads a release APK that belongs to the official GitHub release.
 *
 * The downloader is deliberately narrow: it only accepts the asset URL reported by the
 * GitHub Releases API, keeps every request on GitHub asset hosts, resumes an interrupted
 * transfer, and verifies the saved file before handing it back.
 */
class ApkDownloader(context: android.content.Context) {
    private val appContext = context.applicationContext

    suspend fun download(
        asset: ReleaseAsset,
        onProgress: (ApkDownloadProgress) -> Unit = {},
    ): ApkDownloadResult = withContext(Dispatchers.IO) {
        val initialUrl = requireTrustedUrl(asset.downloadUrl)
        val destination = targetFile(asset)
        val destinationBefore = File(appContext.cacheDir, "updates").listFiles()
            ?.filter { it.isFile && it.name.endsWith(ASSET_SUFFIX) }
            .orEmpty()
        val result = try {
            val download = NetworkUtils.downloadToFile(
                url = initialUrl,
                destination = destination,
                headers = mapOf("Accept" to "application/vnd.android.package-archive"),
                maxBytes = Constants.MAX_APK_BYTES,
                onProgress = { downloaded, total ->
                    onProgress(ApkDownloadProgress(downloaded, total))
                },
                resume = true,
            )
            val finalUrl = requireTrustedUrl(download.finalUrl)
            verifyDownloadedFile(download.file, asset)
            ApkDownloadResult(file = download.file, finalUrl = finalUrl)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw IOException(userFacingMessage(error), error)
        }

        // Only one release APK is kept on the device.
        destinationBefore
            .orEmpty()
            .filter { it != result.file }
            .forEach { stale -> stale.delete() }
        result
    }

    /** Removes any partially downloaded or downloaded release APK. */
    fun clearDownloads() {
        File(appContext.cacheDir, "updates").listFiles()
            ?.filter { it.isFile && it.name.endsWith(ASSET_SUFFIX) }
            ?.forEach { it.delete() }
    }

    private fun targetFile(asset: ReleaseAsset): File {
        val safeName = asset.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(File(appContext.cacheDir, "updates"), safeName)
    }

    private fun requireTrustedUrl(url: String): String {
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            throw IOException("The release link is malformed")
        }
        if (!Constants.isGitHubDownloadHost(uri.host)) {
            throw IOException("The release link is not a GitHub download")
        }
        return url
    }

    private suspend fun verifyDownloadedFile(file: File, asset: ReleaseAsset) {
        coroutineContext.ensureActive()
        if (!file.isFile) throw IOException("The downloaded update is missing")
        val size = file.length()
        if (size <= 0L) throw IOException("The downloaded update is empty")
        if (asset.sizeBytes > 0L && size != asset.sizeBytes) {
            throw IOException("The downloaded update is incomplete")
        }
        if (size > Constants.MAX_APK_BYTES) {
            throw IOException("The downloaded update is too large")
        }
    }

    private fun userFacingMessage(error: Exception): String = when (error) {
        is java.net.SocketTimeoutException -> "The update download timed out"
        is java.net.UnknownHostException -> "No internet connection"
        is IOException -> error.message ?: "The update could not be downloaded"
        else -> "The update could not be downloaded"
    }

    companion object {
        private const val ASSET_SUFFIX = ".apk"
    }
}
