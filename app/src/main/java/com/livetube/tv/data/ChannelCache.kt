package com.livetube.tv.data

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Which cached copy produced the active document. */
enum class ChannelCacheSource {
    CURRENT,
    PREVIOUS,
}

data class CachedChannelDocument(
    val document: ChannelDocument,
    val source: ChannelCacheSource,
)

/**
 * Two-level local store for the last known-good channel documents.
 *
 * `current` is the active document and `previous` is the copy that was active before it. A new
 * document is validated by the caller and only then promoted: the current bytes are copied to
 * `previous` first, so a bad write can never destroy the working document, and a corrupt
 * `current` can always fall back to `previous`.
 *
 * Every write goes through a temporary file plus an atomic replace, so the app never observes a
 * half-written document.
 */
class ChannelCache(private val directory: File) {
    private val currentFile = File(directory, CURRENT_FILE)
    private val previousFile = File(directory, PREVIOUS_FILE)

    /** Active document: current first, then the previous known-good copy. */
    fun load(): CachedChannelDocument? {
        readValidated(currentFile)?.let { return CachedChannelDocument(it, ChannelCacheSource.CURRENT) }
        readValidated(previousFile)
            ?.let { return CachedChannelDocument(it, ChannelCacheSource.PREVIOUS) }
        return null
    }

    fun loadCurrent(): ChannelDocument? = readValidated(currentFile)

    fun loadPrevious(): ChannelDocument? = readValidated(previousFile)

    /**
     * Activates [document]: the current copy becomes the previous fallback and the new document
     * becomes current. Both writes are atomic and the previous copy is written first.
     */
    fun promote(document: ChannelDocument) {
        // The caller must have validated the document; re-validating here keeps a bad payload
        // from ever reaching the cache.
        JsonUtils.validateDocument(document)
        if (currentFile.isFile) {
            copyAtomically(currentFile, previousFile)
        }
        val temporary = File(directory, "$CURRENT_FILE.tmp")
        writeAtomically(document, temporary, currentFile)
    }

    /** Restores [previous] as the active document, used when current cannot be read. */
    fun restorePrevious(): ChannelDocument? {
        val previous = readValidated(previousFile) ?: return null
        val temporary = File(directory, "$CURRENT_FILE.tmp")
        writeAtomically(previous, temporary, currentFile)
        return previous
    }

    fun clear() {
        currentFile.delete()
        previousFile.delete()
    }

    private fun readValidated(file: File): ChannelDocument? {
        if (!file.isFile || file.length() == 0L) return null
        return try {
            JsonUtils.parseDocument(file.readText(StandardCharsets.UTF_8))
        } catch (_: Exception) {
            // A partially written or manually edited file is ignored; the caller falls back to
            // the other copy or to the bundled document.
            null
        }
    }

    private fun writeAtomically(document: ChannelDocument, temporary: File, destination: File) {
        val encoded = JsonUtils.encodeDocument(document)
        FileOutputStream(temporary).use { output ->
            output.write(encoded.toByteArray(StandardCharsets.UTF_8))
            output.fd.sync()
        }
        try {
            move(temporary, destination)
        } catch (error: Exception) {
            // Never fall back to truncating a good file; the previous copy stays intact.
            temporary.delete()
            throw IllegalStateException("Could not atomically update the channel cache", error)
        }
    }

    private fun copyAtomically(source: File, destination: File) {
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        source.inputStream().use { input ->
            FileOutputStream(temporary).use { rawOutput ->
                input.copyTo(rawOutput)
                rawOutput.fd.sync()
            }
        }
        try {
            move(temporary, destination)
        } catch (error: Exception) {
            temporary.delete()
            throw IllegalStateException("Could not rotate the channel cache", error)
        }
    }

    private fun move(source: File, destination: File) {
        directory.mkdirs()
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (_: UnsupportedOperationException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val CURRENT_FILE = "channels-current.json"
        const val PREVIOUS_FILE = "channels-previous.json"
    }
}
