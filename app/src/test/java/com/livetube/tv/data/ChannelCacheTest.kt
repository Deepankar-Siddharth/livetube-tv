package com.livetube.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Startup order and the two-level cache: current, then previous, then the bundled document.
 */
class ChannelCacheTest {
    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var directory: File
    private lateinit var cache: ChannelCache

    private fun setUp() {
        directory = temporaryFolder.newFolder()
        cache = ChannelCache(directory)
    }

    @Test
    fun emptyCacheHasNoDocument() {
        setUp()
        assertNull(cache.load())
    }

    @Test
    fun promotionMakesTheDocumentCurrent() {
        setUp()
        cache.promote(JsonUtils.parseDocument(document(2)))

        val loaded = cache.load()

        assertNotNull(loaded)
        assertEquals(ChannelCacheSource.CURRENT, loaded?.source)
        assertEquals(2, loaded?.document?.dataVersion)
    }

    // H) a corrupt current file falls back to the previous known-good copy.
    // `previous` holds the document that was active before `current`, so after promoting v2 then
    // v3 the pair is current = v3 and previous = v2.
    @Test
    fun corruptCurrentFallsBackToPrevious() {
        setUp()
        cache.promote(JsonUtils.parseDocument(document(2)))
        cache.promote(JsonUtils.parseDocument(document(3)))
        File(directory, "channels-current.json").writeText("{ this is not json")

        val loaded = cache.load()

        assertNotNull(loaded)
        assertEquals(ChannelCacheSource.PREVIOUS, loaded?.source)
        assertEquals(2, loaded?.document?.dataVersion)
    }

    @Test
    fun corruptCurrentIsRestorableFromPrevious() {
        setUp()
        cache.promote(JsonUtils.parseDocument(document(2)))
        cache.promote(JsonUtils.parseDocument(document(3)))
        File(directory, "channels-current.json").writeText("garbage")

        val restored = cache.restorePrevious()

        assertNotNull(restored)
        assertEquals(2, restored?.dataVersion)
        assertEquals(2, cache.loadCurrent()?.dataVersion)
        assertEquals(2, cache.loadPrevious()?.dataVersion)
    }

    // I) with no usable cache the bundled document is used, and it is never written to the cache
    @Test
    fun bundledDocumentIsUsedButNotCached() {
        setUp()
        val bundled = document(7)
        val repository = ChannelRepository(
            cache = cache,
            remote = ChannelRemoteSource { null },
            bundled = { JsonUtils.parseDocument(bundled) },
            isRemoteConfigured = { false },
        )

        assertEquals(7, repository.activeDataVersion)
        assertNull("the bundled document must not be cached", cache.loadCurrent())
        assertNull(cache.loadPrevious())
    }

    @Test
    fun cachedDocumentWinsOverBundled() {
        setUp()
        cache.promote(JsonUtils.parseDocument(document(4)))
        val repository = ChannelRepository(
            cache = cache,
            remote = ChannelRemoteSource { null },
            bundled = { JsonUtils.parseDocument(document(1)) },
            isRemoteConfigured = { false },
        )

        assertEquals(4, repository.activeDataVersion)
    }

    @Test
    fun previousWinsOverBundledWhenCurrentIsUnusable() {
        setUp()
        cache.promote(JsonUtils.parseDocument(document(2)))
        cache.promote(JsonUtils.parseDocument(document(3)))
        File(directory, "channels-current.json").delete()
        val repository = ChannelRepository(
            cache = cache,
            remote = ChannelRemoteSource { null },
            bundled = { JsonUtils.parseDocument(document(1)) },
            isRemoteConfigured = { false },
        )

        assertEquals(2, repository.activeDataVersion)
        assertEquals(ChannelCacheSource.PREVIOUS, repository.cacheSource.value)
    }

    @Test
    fun promotionRefusesAnInvalidDocument() {
        setUp()
        cache.promote(JsonUtils.parseDocument(document(2)))
        val invalid = ChannelDocument(
            schemaVersion = JsonUtils.SCHEMA_VERSION,
            dataVersion = 3,
            updatedAt = "2026-09-26T00:00:00Z",
            channels = emptyList(),
        )

        val failed = runCatching { cache.promote(invalid) }

        assertTrue(failed.isFailure)
        assertEquals(2, cache.loadCurrent()?.dataVersion)
    }

    @Test
    fun writesAreAtomicAndLeaveNoTemporaryFiles() {
        setUp()
        cache.promote(JsonUtils.parseDocument(document(2)))
        cache.promote(JsonUtils.parseDocument(document(3)))

        val files = directory.listFiles()?.map { it.name }?.sorted().orEmpty()
        assertEquals(listOf("channels-current.json", "channels-previous.json"), files)
    }

    private fun document(dataVersion: Int): String = """
        {"schema_version":2,"data_version":$dataVersion,"updated_at":"2026-09-26T00:00:00Z","channels":[
          {"id":"a","name":"A","category":"News","subcategory":"Hindi News","language":"Hindi","region":"National",
           "logo":"https://example.com/a.png","youtube_handle":"@a",
           "live_url":"https://www.youtube.com/@a/live","enabled":true,"sort_order":1}
        ]}
    """.trimIndent()
}
