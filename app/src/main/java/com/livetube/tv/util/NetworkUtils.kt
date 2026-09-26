package com.livetube.tv.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream

/** Small, cancellation-friendly HTTP helpers used by config, release, and extractor clients. */
object NetworkUtils {
    data class DownloadResult(val file: File, val finalUrl: String)

    fun getText(
        url: String,
        headers: Map<String, String> = emptyMap(),
        maxBytes: Int = Constants.MAX_CONFIG_BYTES,
    ): String {
        val connection = openConnection(url, "GET", headers)
        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("HTTP $code ${connection.responseMessage ?: ""}".trim())
            }
            val stream = BufferedInputStream(connection.inputStream)
            val decoded = if (connection.contentEncoding?.contains("gzip", true) == true) {
                GZIPInputStream(stream)
            } else {
                stream
            }
            decoded.use { readTextLimited(it, maxBytes) }
        } finally {
            connection.disconnect()
        }
    }

    fun downloadToFile(
        url: String,
        destination: File,
        headers: Map<String, String> = emptyMap(),
        maxBytes: Long = Constants.MAX_APK_BYTES,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null,
        resume: Boolean = false,
    ): DownloadResult {
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.part")
        val resumeFrom = if (resume && temporary.isFile && temporary.length() in 1..maxBytes) {
            temporary.length()
        } else {
            temporary.delete()
            0L
        }
        val requestHeaders = if (resumeFrom > 0L) {
            headers + ("Range" to "bytes=$resumeFrom-")
        } else {
            headers
        }
        val connection = openConnection(url, "GET", requestHeaders)
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("HTTP $code ${connection.responseMessage ?: ""}".trim())
            }
            val appending = resumeFrom > 0L && code == HttpURLConnection.HTTP_PARTIAL
            if (resumeFrom > 0L && !appending) {
                // The server ignored the range request, so the file has to restart.
                temporary.delete()
            }
            val alreadyHave = if (appending) resumeFrom else 0L
            val remainingHeader = connection.contentLength
            val declaredTotal = when {
                remainingHeader > 0 -> alreadyHave + remainingHeader
                else -> -1L
            }
            if (declaredTotal > maxBytes) throw IOException("Download is larger than the limit")
            connection.inputStream.use { input ->
                FileOutputStream(temporary, appending).use { rawOutput ->
                    BufferedOutputStream(rawOutput).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = alreadyHave
                        onProgress?.invoke(total, declaredTotal)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > maxBytes) throw IOException("Download is larger than the limit")
                            output.write(buffer, 0, count)
                            onProgress?.invoke(total, declaredTotal)
                        }
                        output.flush()
                        rawOutput.fd.sync()
                    }
                }
            }
            if (!temporary.isFile || temporary.length() == 0L) {
                throw IOException("The download produced no data")
            }
            if (declaredTotal > 0L && temporary.length() != declaredTotal) {
                throw IOException("The download is incomplete")
            }
            try {
                try {
                    Files.move(
                        temporary.toPath(),
                        destination.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(
                        temporary.toPath(),
                        destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: UnsupportedOperationException) {
                    Files.move(
                        temporary.toPath(),
                        destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
            } catch (error: Exception) {
                throw IOException("Could not atomically save the download", error)
            }
            return DownloadResult(destination, connection.url.toString())
        } catch (error: Throwable) {
            if (!resume) temporary.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun hasNetwork(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return try {
            val network = manager.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: SecurityException) {
            false
        }
    }

    private fun openConnection(
        url: String,
        method: String,
        headers: Map<String, String>,
    ): HttpURLConnection {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = Constants.CONNECT_TIMEOUT_MS
            readTimeout = Constants.READ_TIMEOUT_MS
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Accept-Encoding", "gzip")
            setRequestProperty("User-Agent", Constants.userAgent())
        }
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        return connection
    }

    private fun readTextLimited(input: java.io.InputStream, maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) throw IOException("Response is larger than the limit")
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }
}
