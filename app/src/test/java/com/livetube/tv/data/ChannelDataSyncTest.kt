package com.livetube.tv.data

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Covers the channel-data update rule: `data_version` decides, validation gates activation, and a
 * failure never disturbs the working document.
 */
class ChannelDataSyncTest {
    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    // A) local v2 + remote v3 -> v3 becomes active
    @Test
    fun higherRemoteVersionIsActivated() = runBlocking {
        val repository = newRepository(temporaryFolder.newFolder("a"), parsed(DOCUMENT_V3))

        val result = repository.refresh()

        assertTrue(result is ChannelRefreshResult.Updated)
        assertEquals(3, repository.activeDataVersion)
        assertEquals(3, repository.document.value.dataVersion)
        assertEquals(3, (result as ChannelRefreshResult.Updated).document.dataVersion)
    }

    // B) same version -> nothing changes
    @Test
    fun sameVersionKeepsCurrentData() = runBlocking {
        val repository = newRepository(temporaryFolder.newFolder("b"), parsed(DOCUMENT_V2))
        val before = repository.document.value

        val result = repository.refresh()

        assertTrue(result is ChannelRefreshResult.Current)
        assertEquals(2, repository.activeDataVersion)
        assertTrue(before === repository.document.value)
    }

    // C) downgrade is refused
    @Test
    fun olderRemoteVersionIsNotApplied() = runBlocking {
        val repository = seededRepository(
            directory = temporaryFolder.newFolder("c"),
            seed = parsed(DOCUMENT_V3),
            remoteDocument = parsed(DOCUMENT_V1),
        )
        assertEquals(3, repository.activeDataVersion)

        val result = repository.refresh()

        assertTrue(result is ChannelRefreshResult.Downgrade)
        assertEquals(3, repository.activeDataVersion)
        assertEquals(1, (result as ChannelRefreshResult.Downgrade).remoteDataVersion)
    }

    // D) malformed higher version -> local stays
    @Test
    fun malformedHigherVersionIsRejected() = runBlocking {
        val repository = newRepository(
            temporaryFolder.newFolder("d"),
            null,
            remoteRaw = """{"schema_version":2,"data_version":3,"updated_at":"2026-09-26T00:00:00Z","channels":"nope"}""",
        )

        val result = repository.refresh()

        assertTrue(result is ChannelRefreshResult.Failed)
        assertEquals(2, repository.activeDataVersion)
    }

    // E) empty channels -> rejected
    @Test
    fun emptyChannelListIsRejected() = runBlocking {
        val repository = newRepository(
            temporaryFolder.newFolder("e"),
            null,
            remoteRaw = """{"schema_version":2,"data_version":3,"updated_at":"2026-09-26T00:00:00Z","channels":[]}""",
        )

        val result = repository.refresh()

        assertTrue(result is ChannelRefreshResult.Failed)
        assertEquals(2, repository.activeDataVersion)
    }

    // F) duplicate ids -> rejected
    @Test
    fun duplicateChannelIdsAreRejected() = runBlocking {
        val duplicate = document(
            dataVersion = 3,
            channels = listOf(
                channel("alpha", sortOrder = 1, name = "Alpha"),
                channel("alpha", sortOrder = 2, name = "Alpha Two"),
            ),
        )
        val repository = newRepository(temporaryFolder.newFolder("f"), null, remoteRaw = duplicate)

        val result = repository.refresh()

        assertTrue(result is ChannelRefreshResult.Failed)
        assertEquals(2, repository.activeDataVersion)
    }

    // G) network failure -> local data keeps working
    @Test
    fun networkFailureKeepsLocalData() = runBlocking {
        val repository = newRepository(temporaryFolder.newFolder("g"), remoteUnavailable = true)

        val result = repository.refresh()

        assertTrue(result is ChannelRefreshResult.Failed)
        assertEquals(2, repository.activeDataVersion)
        assertEquals(2, repository.document.value.channels.size)
    }

    // J) a successful sync publishes the new channels through the existing state flow
    @Test
    fun successfulSyncEmitsTheNewDocument() = runBlocking {
        val repository = newRepository(temporaryFolder.newFolder("j"), parsed(DOCUMENT_V3))
        val versions = mutableListOf(repository.document.value.dataVersion)

        repository.refresh()
        versions += repository.document.value.dataVersion

        assertEquals(listOf(2, 3), versions)
        assertEquals(2, repository.document.value.channels.size)
    }

    // The version rule itself, independent of I/O.
    @Test
    fun versionRuleIsTheOnlyAuthority() {
        val local = parsed(DOCUMENT_V2)
        assertEquals(
            ChannelDataVersionDecision.UPDATE,
            ChannelDataVersion.decide(parsed(DOCUMENT_V3), local),
        )
        assertEquals(
            ChannelDataVersionDecision.CURRENT,
            ChannelDataVersion.decide(parsed(DOCUMENT_V2), local),
        )
        assertEquals(
            ChannelDataVersionDecision.DOWNGRADE,
            ChannelDataVersion.decide(parsed(DOCUMENT_V1), local),
        )
        assertEquals(ChannelDataVersionDecision.UPDATE, ChannelDataVersion.decide(local, null))
    }

    // updated_at alone must never trigger an update
    @Test
    fun updatedAtAloneNeverTriggersAnUpdate() {
        val local = parsed(DOCUMENT_V2)
        val sameVersionNewerStamp = parsed(DOCUMENT_V2.replace("2026-09-21", "2026-12-31"))

        assertEquals(
            ChannelDataVersionDecision.CURRENT,
            ChannelDataVersion.decide(sameVersionNewerStamp, local),
        )
    }

    // New categories and subcategories are valid; they must not invalidate a document
    @Test
    fun newCategoriesAreAccepted() {
        val exotic = document(
            dataVersion = 3,
            channels = listOf(
                channel("ocean", 1, "Ocean TV", category = "Nature & Science", subcategory = "Ocean Exploration"),
            ),
        )

        val parsed = JsonUtils.parseDocument(exotic)

        assertEquals(1, parsed.channels.size)
        assertEquals("Nature & Science", parsed.channels.first().category)
        assertEquals("Ocean Exploration", parsed.channels.first().subcategory)
    }

    // A catalogue may use categories the app has never seen; the guide must still show them.
    @Test
    fun guideKeepsChannelsWithUnknownCategoriesVisible() {
        val channels = listOf(
            channelModel("ocean", 1, "Ocean TV", category = "Nature & Science", subcategory = "Ocean Exploration"),
            channelModel("news1", 2, "News One", category = "News", subcategory = "Hindi News"),
        )

        val directory = GuideDirectoryFactory.build(channels, favoriteChannelIds = emptySet())

        assertEquals(listOf("Hindi"), directory.languageNames())
        assertEquals(listOf("Nature & Science", "News"), directory.categoryNames("Hindi"))
        assertEquals(listOf("ocean"), directory.channelsFor("Hindi", "Nature & Science").map { it.id })
    }

    @Test
    fun missingRemoteConfigurationIsReported() = runBlocking {
        val repository = ChannelRepository(
            cache = ChannelCache(temporaryFolder.newFolder("h")),
            remote = ChannelRemoteSource { null },
            bundled = { null },
            isRemoteConfigured = { false },
        )

        val result = repository.refresh()

        assertEquals(ChannelRepository.SYNC_NOT_CONFIGURED, (result as ChannelRefreshResult.Failed).reason)
    }

    @Test
    fun concurrentSyncsAreSerialised() = runBlocking {
        val directory = temporaryFolder.newFolder("i")
        val repository = ChannelRepository(
            cache = ChannelCache(directory),
            remote = ChannelRemoteSource { parsed(DOCUMENT_V3) },
            bundled = { null },
            isRemoteConfigured = { true },
        )

        val first = async { repository.refresh() }
        val second = async { repository.refresh() }
        first.await()
        second.await()

        // The second call observed the newer local version, so exactly one document is active.
        assertEquals(3, repository.activeDataVersion)
        assertEquals(3, ChannelCache(directory).loadCurrent()?.dataVersion)
    }

    @Test
    fun secondPromotionRotatesThePreviousCopy() {
        val cache = ChannelCache(temporaryFolder.newFolder("j2"))
        cache.promote(parsed(DOCUMENT_V2))
        cache.promote(parsed(DOCUMENT_V3))
        cache.promote(parsed(DOCUMENT_V4))

        assertEquals(4, cache.loadCurrent()?.dataVersion)
        assertEquals(3, cache.loadPrevious()?.dataVersion)
    }

    @Test
    fun firstPromotionHasNoPreviousCopy() {
        val cache = ChannelCache(temporaryFolder.newFolder("j3"))
        cache.promote(parsed(DOCUMENT_V2))

        assertNotNull(cache.loadCurrent())
        assertNull(cache.loadPrevious())
    }

    // A failing sync must not disturb the files on disk
    @Test
    fun failedSyncLeavesCacheUntouched() = runBlocking {
        val directory = temporaryFolder.newFolder("k")
        val cache = ChannelCache(directory)
        cache.promote(parsed(DOCUMENT_V2))
        val repository = ChannelRepository(
            cache = cache,
            remote = ChannelRemoteSource { null },
            bundled = { null },
            isRemoteConfigured = { true },
        )

        repository.refresh()

        assertEquals(2, cache.loadCurrent()?.dataVersion)
        assertTrue(File(directory, "channels-previous.json").exists().not())
    }

    /**
     * Repository whose active document is the local v2 cache, with [remoteDocument] served as the
     * remote copy. A null [remoteDocument] means the download failed.
     */
    private fun newRepository(
        directory: File,
        remoteDocument: ChannelDocument? = parsed(DOCUMENT_V3),
        remoteRaw: String? = null,
        remoteUnavailable: Boolean = false,
    ): ChannelRepository {
        val source = when {
            remoteUnavailable -> ChannelRemoteSource { null }
            remoteRaw != null -> ChannelRemoteSource { JsonUtils.parseDocument(remoteRaw) }
            else -> ChannelRemoteSource { remoteDocument }
        }
        val cache = ChannelCache(directory)
        // The device already runs the local v2 catalogue.
        cache.promote(parsed(DOCUMENT_V2))
        return ChannelRepository(
            cache = cache,
            remote = source,
            bundled = { null },
            isRemoteConfigured = { true },
        )
    }

    private suspend fun ChannelRepository.refresh(rawDocument: String): ChannelRefreshResult =
        ChannelRepository(
            cache = ChannelCache(temporaryFolder.newFolder("refresh")),
            remote = ChannelRemoteSource { JsonUtils.parseDocument(rawDocument) },
            bundled = { null },
            isRemoteConfigured = { true },
        ).refresh()

    /** Repository whose active document is [seed] and whose remote serves [remoteDocument]. */
    private fun seededRepository(
        directory: File,
        seed: ChannelDocument,
        remoteDocument: ChannelDocument?,
    ): ChannelRepository {
        val cache = ChannelCache(directory)
        cache.promote(seed)
        return ChannelRepository(
            cache = cache,
            remote = ChannelRemoteSource { remoteDocument },
            bundled = { null },
            isRemoteConfigured = { true },
        )
    }

    private fun parsed(raw: String): ChannelDocument = JsonUtils.parseDocument(raw)

    private fun document(
        dataVersion: Int,
        channels: List<String>,
        updatedAt: String = "2026-09-26T00:00:00Z",
    ): String {
        val array = JSONArray()
        channels.forEach { array.put(JSONObject(it)) }
        return """{"schema_version":2,"data_version":$dataVersion,"updated_at":"$updatedAt","channels":$array}"""
    }

    private fun channelModel(
        id: String,
        sortOrder: Int,
        name: String,
        category: String = "News",
        subcategory: String = "Hindi News",
    ): Channel {
        val handle = "@${id.replace("-", "")}"
        return Channel(
            id = id,
            name = name,
            category = category,
            subcategory = subcategory,
            language = "Hindi",
            region = "National",
            logo = "https://example.com/$id.png",
            youtubeHandle = handle,
            liveUrl = "https://www.youtube.com/$handle/live",
            enabled = true,
            sortOrder = sortOrder,
        )
    }

    private fun channel(
        id: String,
        sortOrder: Int,
        name: String,
        category: String = "News",
        subcategory: String = "Hindi News",
    ): String {
        val handle = "@${id.replace("-", "")}"
        return """{
            "id":"$id",
            "name":"$name",
            "category":"$category",
            "subcategory":"$subcategory",
            "language":"Hindi",
            "region":"National",
            "logo":"https://example.com/$id.png",
            "youtube_handle":"$handle",
            "live_url":"https://www.youtube.com/$handle/live",
            "enabled":true,
            "sort_order":$sortOrder
        }""".trimIndent()
    }

    private companion object {
        const val DOCUMENT_V1 =  """{"schema_version":2,"data_version":1,"updated_at":"2026-09-20T00:00:00Z","channels":[{"id":"a","name":"A","category":"News","subcategory":"Hindi News","language":"Hindi","region":"National","logo":"https://example.com/a.png","youtube_handle":"@a","live_url":"https://www.youtube.com/@a/live","enabled":true,"sort_order":1}]}"""
        const val DOCUMENT_V2 = """{"schema_version":2,"data_version":2,"updated_at":"2026-09-21T00:00:00Z","channels":[{"id":"a","name":"A","category":"News","subcategory":"Hindi News","language":"Hindi","region":"National","logo":"https://example.com/a.png","youtube_handle":"@a","live_url":"https://www.youtube.com/@a/live","enabled":true,"sort_order":1},{"id":"b","name":"B","category":"News","subcategory":"Hindi News","language":"Hindi","region":"National","logo":"https://example.com/b.png","youtube_handle":"@b","live_url":"https://www.youtube.com/@b/live","enabled":true,"sort_order":2}]}"""
        const val DOCUMENT_V3 = """{"schema_version":2,"data_version":3,"updated_at":"2026-09-26T00:00:00Z","channels":[{"id":"c","name":"C","category":"News","subcategory":"Hindi News","language":"Hindi","region":"National","logo":"https://example.com/c.png","youtube_handle":"@c","live_url":"https://www.youtube.com/@c/live","enabled":true,"sort_order":1},{"id":"d","name":"D","category":"Regional","subcategory":"Bhojpuri","language":"Hindi","region":"North India","logo":"https://example.com/d.png","youtube_handle":"@d","live_url":"https://www.youtube.com/@d/live","enabled":true,"sort_order":2}]}"""
        const val DOCUMENT_V4 = """{"schema_version":2,"data_version":4,"updated_at":"2026-10-01T00:00:00Z","channels":[{"id":"e","name":"E","category":"News","subcategory":"Hindi News","language":"Hindi","region":"National","logo":"https://example.com/e.png","youtube_handle":"@e","live_url":"https://www.youtube.com/@e/live","enabled":true,"sort_order":1}]}"""
    }
}
