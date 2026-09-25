package com.livetube.tv.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Atomic on-device cache for the last validated channel document. */
class ChannelCache(context: Context) {
    private val file = File(context.filesDir, "channels.json")
    private val temporaryFile = File(context.filesDir, "channels.json.tmp")

    fun load(): ChannelDocument? {
        if (!file.isFile) return null
        return try {
            JsonUtils.parseDocument(file.readText(StandardCharsets.UTF_8))
        } catch (_: Exception) {
            // A partially written or manually edited cache is ignored; it is never allowed to
            // prevent the bundled/remote document from being used.
            null
        }
    }

    fun save(document: ChannelDocument) {
        val encoded = JsonUtils.encodeDocument(document)
        FileOutputStream(temporaryFile).use { output ->
            output.write(encoded.toByteArray(StandardCharsets.UTF_8))
            output.fd.sync()
        }
        try {
            try {
                Files.move(
                    temporaryFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporaryFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: UnsupportedOperationException) {
                Files.move(
                    temporaryFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } catch (error: Exception) {
            // Never fall back to truncating the last good cache file. The caller can retry
            // the remote refresh while the previous validated document remains intact.
            temporaryFile.delete()
            throw IllegalStateException("Could not atomically update the channel cache", error)
        }
    }
}
