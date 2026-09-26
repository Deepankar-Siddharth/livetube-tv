package com.livetube.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelCatalogTest {

    @Test
    fun enabledChannelsAreOrderedBySortOrderThenName() {
        val channels = listOf(
            channel("z_news", "Z News", order = 2),
            channel("a_news", "A News", order = 2),
            channel("global", "Global TV", order = 1),
            channel("dev", "Dev TV", order = 3),
        )

        assertEquals(
            listOf("global", "a_news", "z_news", "dev"),
            ChannelCatalog.enabledChannels(channels).map(Channel::id),
        )
    }

    @Test
    fun disabledChannelsNeverAppear() {
        val channels = listOf(
            channel("visible", "Visible TV", order = 1),
            channel("hidden", "Hidden TV", order = 2, enabled = false),
        )

        val enabled = ChannelCatalog.enabledChannels(channels)

        assertEquals(listOf("visible"), enabled.map(Channel::id))
        assertTrue(enabled.none { it.id == "hidden" })
    }

    @Test
    fun anEmptyCatalogueStaysEmpty() {
        assertTrue(ChannelCatalog.enabledChannels(emptyList()).isEmpty())
    }

    private fun channel(
        id: String,
        name: String,
        order: Int,
        enabled: Boolean = true,
    ) = Channel(
        id = id,
        name = name,
        category = "News",
        subcategory = "Hindi News",
        language = "Hindi",
        region = "National",
        logo = "https://example.com/$id.png",
        youtubeHandle = "@$id",
        liveUrl = "https://www.youtube.com/@$id/live",
        enabled = enabled,
        sortOrder = order,
    )
}
