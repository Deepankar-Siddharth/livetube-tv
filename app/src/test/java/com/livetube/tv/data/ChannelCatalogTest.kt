package com.livetube.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelCatalogTest {
    private val channels = listOf(
        channel("z_news", "Z News", "News", "Hindi News", "Hindi", "National", order = 2),
        channel("a_news", "A News", "News", "Hindi News", "Hindi", "National", order = 2),
        channel("global", "Global TV", "News", "English & Global News", "English", "International", order = 1),
        channel("dev", "Dev TV", "Devotional", "Hindu Devotional", "Hindi", "National", order = 3),
        channel("disabled", "Disabled TV", "News", "Hindi News", "Hindi", "National", order = 1, enabled = false),
    )

    @Test
    fun topLevelCategoryOrderIsStable() {
        assertEquals(
            listOf(
                "Favorites",
                "News",
                "Regional",
                "Devotional",
                "Kids & Family",
                "Knowledge",
                "Music & Entertainment",
                "Sports & Live",
            ),
            ChannelCatalog.categories.map(CategoryDefinition::name),
        )
    }

    @Test
    fun filtersByCategoryAndSubcategory() {
        assertEquals(
            listOf("global"),
            ChannelCatalog.filter(channels, "news", "english_global_news").map(Channel::id),
        )
        assertEquals(
            listOf("a_news", "z_news"),
            ChannelCatalog.filter(channels, "news", "hindi_news").map(Channel::id),
        )
    }

    @Test
    fun disabledChannelsNeverAppear() {
        val visible = ChannelCatalog.filter(channels, "news")
        assertTrue(visible.none { it.id == "disabled" })
    }

    @Test
    fun sortsByOrderThenName() {
        assertEquals(listOf("global", "a_news", "z_news"), ChannelCatalog.filter(channels, "news").map(Channel::id))
    }

    @Test
    fun favoritesUseLocalIdsAndIgnoreOtherCategories() {
        assertEquals(
            listOf("z_news"),
            ChannelCatalog.filter(
                channels,
                ChannelCatalog.FAVORITES_CATEGORY_ID,
                favoriteChannelIds = setOf("z_news", "not_in_catalogue"),
            ).map(Channel::id),
        )
    }

    @Test
    fun emptySelectionReturnsNoPlaceholderCards() {
        assertTrue(ChannelCatalog.filter(channels, "kids_family").isEmpty())
        assertTrue(
            ChannelCatalog.filter(
                channels,
                ChannelCatalog.FAVORITES_CATEGORY_ID,
            ).isEmpty(),
        )
    }

    @Test
    fun searchUsesAllClassificationFields() {
        assertEquals(listOf("global"), ChannelCatalog.search(channels, "international").map(Channel::id))
        assertEquals(listOf("dev"), ChannelCatalog.search(channels, "devotional").map(Channel::id))
        assertEquals(listOf("a_news", "z_news"), ChannelCatalog.search(channels, "hindi news").map(Channel::id))
    }

    @Test
    fun adjacentCategorySupportsDpadMovementWithoutReordering() {
        assertEquals("favorites", ChannelCatalog.adjacentCategoryId("favorites", -1))
        assertEquals("news", ChannelCatalog.adjacentCategoryId("favorites", 1))
        assertEquals("sports_live", ChannelCatalog.adjacentCategoryId("sports_live", 1))
    }

    private fun channel(
        id: String,
        name: String,
        category: String,
        subcategory: String,
        language: String,
        region: String,
        order: Int,
        enabled: Boolean = true,
    ) = Channel(
        id = id,
        name = name,
        category = category,
        subcategory = subcategory,
        language = language,
        region = region,
        logo = "https://example.com/$id.png",
        youtubeHandle = "@$id",
        liveUrl = "https://www.youtube.com/@$id/live",
        enabled = enabled,
        sortOrder = order,
    )
}
