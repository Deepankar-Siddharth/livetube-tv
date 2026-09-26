package com.livetube.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelParserTest {
    @Test
    fun parsesValidDocument() {
        val document = JsonUtils.parseDocument(validDocument())
        assertEquals(2, document.schemaVersion)
        assertEquals(2, document.dataVersion)
        assertEquals(1, document.channels.size)
        val channel = document.channels.single()
        assertEquals("aaj_tak", channel.id)
        assertEquals("Hindi News", channel.subcategory)
        assertEquals("Hindi", channel.language)
        assertEquals("National", channel.region)
    }

    @Test
    fun migratesLegacyDocumentToCurrentSchema() {
        val document = JsonUtils.parseDocument(legacyDocument())
        assertEquals(2, document.schemaVersion)
        val channel = document.channels.single()
        assertEquals("News", channel.category)
        assertEquals("Hindi News", channel.subcategory)
        assertEquals("Hindi", channel.language)
        assertEquals("National", channel.region)
    }

    @Test
    fun rejectsMalformedJson() {
        assertTrue(runCatching { JsonUtils.parseDocument("{not-json") }.isFailure)
    }

    @Test
    fun rejectsMissingRequiredField() {
        val invalid = validDocument().replace("\"enabled\": true,", "")
        assertTrue(runCatching { JsonUtils.parseDocument(invalid) }.isFailure)
    }

    @Test
    fun rejectsUnknownFieldsAndInvalidTimestamps() {
        val unknownRoot = validDocument().replace(
            "\"schema_version\": 2,",
            "\"schema_version\": 2, \"unexpected\": true,",
        )
        assertTrue(runCatching { JsonUtils.parseDocument(unknownRoot) }.isFailure)

        val invalidTimestamp = validDocument().replace(
            "2026-09-25T00:00:00Z",
            "2026-02-30T00:00:00Z",
        )
        assertTrue(runCatching { JsonUtils.parseDocument(invalidTimestamp) }.isFailure)
    }

    /**
     * A newer catalogue may introduce categories and subcategories. Rejecting them would make a
     * valid document unusable and silently hide channels, so only the text itself is checked.
     */
    @Test
    fun acceptsNewCategoriesAndSubcategories() {
        val newSubcategory = validDocument().replace("\"Hindi News\"", "\"Regional Indian News\"")
        val newCategory = newSubcategory.replace("\"category\": \"News\"", "\"category\": \"Nature & Science\"")
        val renamedCategory = newSubcategory.replace(
            "\"category\": \"News\"",
            "\"category\": \"Wildlife & Nature Cams\"",
        )

        val parsed = JsonUtils.parseDocument(newCategory)
        val renamed = JsonUtils.parseDocument(renamedCategory)

        assertEquals("Nature & Science", parsed.channels.single().category)
        assertEquals("Regional Indian News", parsed.channels.single().subcategory)
        assertEquals("Wildlife & Nature Cams", renamed.channels.single().category)
    }

    @Test
    fun rejectsBlankOrOversizedCategoryText() {
        val blank = validDocument().replace("\"category\": \"News\"", "\"category\": \"   \"")
        val oversized = validDocument().replace("\"category\": \"News\"", "\"category\": \"${"x".repeat(65)}\"")

        assertTrue(runCatching { JsonUtils.parseDocument(blank) }.isFailure)
        assertTrue(runCatching { JsonUtils.parseDocument(oversized) }.isFailure)
    }

    @Test
    fun rejectsCaseVariantDuplicateHandles() {
        val invalid = validDocument().replace(
            "\"channels\": [",
            "\"channels\": [{\"id\":\"other\",\"name\":\"Other\",\"category\":\"News\",\"subcategory\":\"Hindi News\",\"language\":\"Hindi\",\"region\":\"National\",\"logo\":\"https://example.com/a.png\",\"youtube_handle\":\"@AAJTAK\",\"live_url\":\"https://www.youtube.com/@AAJTAK/live\",\"enabled\":true,\"sort_order\":2},",
        )
        assertTrue(runCatching { JsonUtils.parseDocument(invalid) }.isFailure)
    }

    @Test
    fun rejectsDuplicateIds() {
        val invalid = validDocument().replace(
            "\"channels\": [",
            "\"channels\": [{\"id\":\"aaj_tak\",\"name\":\"Other\",\"category\":\"News\",\"subcategory\":\"Hindi News\",\"language\":\"Hindi\",\"region\":\"National\",\"logo\":\"https://example.com/a.png\",\"youtube_handle\":\"@other\",\"live_url\":\"https://www.youtube.com/@other/live\",\"enabled\":true,\"sort_order\":2},",
        )
        assertTrue(runCatching { JsonUtils.parseDocument(invalid) }.isFailure)
    }

    private fun validDocument(): String = """
        {
          "schema_version": 2,
          "data_version": 2,
          "updated_at": "2026-09-25T00:00:00Z",
          "channels": [
            {
              "id": "aaj_tak",
              "name": "Aaj Tak",
              "category": "News",
              "subcategory": "Hindi News",
              "language": "Hindi",
              "region": "National",
              "logo": "https://example.com/aaj.png",
              "youtube_handle": "@aajtak",
              "live_url": "https://www.youtube.com/@aajtak/live",
              "enabled": true,
              "sort_order": 1
            }
          ]
        }
    """.trimIndent()

    private fun legacyDocument(): String = """
        {
          "schema_version": 1,
          "data_version": 1,
          "updated_at": "2026-09-25T00:00:00Z",
          "channels": [
            {
              "id": "aaj_tak",
              "name": "Aaj Tak",
              "category": "National Hindi News",
              "logo": "https://example.com/aaj.png",
              "youtube_handle": "@aajtak",
              "live_url": "https://www.youtube.com/@aajtak/live",
              "enabled": true,
              "sort_order": 1
            }
          ]
        }
    """.trimIndent()
}
