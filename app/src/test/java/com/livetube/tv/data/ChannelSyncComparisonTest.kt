package com.livetube.tv.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelSyncComparisonTest {
    @Test
    fun newerDataVersionWins() {
        val current = document(schema = 2, data = 5, updated = "2026-09-01T00:00:00Z")
        val remote = document(schema = 2, data = 6, updated = "2026-09-02T00:00:00Z")
        assertTrue(remote.isNewerThan(current))
        assertFalse(current.isNewerThan(remote))
    }

    @Test
    fun identicalRevisionsAreNotNewer() {
        val current = document(schema = 2, data = 5, updated = "2026-09-01T00:00:00Z")
        val remote = document(schema = 2, data = 5, updated = "2026-09-01T00:00:00Z")
        assertFalse(remote.isNewerThan(current))
        assertTrue(remote.sameRevisionAs(current))
        assertFalse(remote.sameRevisionAs(document(schema = 2, data = 5, updated = "2026-09-03T00:00:00Z")))
    }

    @Test
    fun newerSchemaVersionWinsEvenWithALowerDataVersion() {
        val current = document(schema = 1, data = 99, updated = "2026-09-01T00:00:00Z")
        val remote = document(schema = 2, data = 1, updated = "2026-01-01T00:00:00Z")
        assertTrue(remote.isNewerThan(current))
        assertFalse(current.isNewerThan(remote))
    }

    @Test
    fun updatedAtOnlyDecidesWhenVersionsMatch() {
        val current = document(schema = 2, data = 5, updated = "2026-09-01T00:00:00Z")
        val later = document(schema = 2, data = 5, updated = "2026-09-05T00:00:00Z")
        val earlier = document(schema = 2, data = 5, updated = "2026-08-30T00:00:00Z")
        assertTrue(later.isNewerThan(current))
        assertFalse(earlier.isNewerThan(current))
    }

    private fun document(schema: Int, data: Int, updated: String) = ChannelDocument(
        schemaVersion = schema,
        dataVersion = data,
        updatedAt = updated,
        channels = emptyList(),
    )
}
