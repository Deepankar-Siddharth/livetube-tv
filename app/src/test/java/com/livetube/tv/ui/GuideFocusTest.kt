package com.livetube.tv.ui

import com.livetube.tv.data.Channel
import com.livetube.tv.data.ChannelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Opening the guide focuses the channel that is playing, and browsing never changes playback.
 */
class GuideFocusTest {
    private val channels = (1..25).map { index ->
        channel(
            id = "channel_$index",
            name = "Channel $index",
            sortOrder = index,
            category = if (index % 2 == 0) "Regional" else "News",
        )
    }

    // K) the playing channel is focused, not the first channel
    @Test
    fun openingTheGuideFocusesThePlayingChannel() {
        val playing = channels[19] // channel #20

        val target = GuideFocus.initialTarget(
            channels = channels,
            playingChannelId = playing.id,
            preferredCategoryId = null,
        )

        assertEquals(
            ChannelCatalog.idForCustomName(playing.category),
            ChannelCatalog.guideCategories(channels)[target.categoryIndex].id,
        )
        val visible = GuideFocus.channelsFor(channels, ChannelCatalog.guideCategories(channels), target.categoryIndex)
        assertEquals(playing.id, visible?.get(target.channelIndex)?.id)
        assertTrue(target.focusChannelRow)
    }

    // M) after switching playback, reopening the guide focuses the new channel
    @Test
    fun reopeningFocusesTheNewlyPlayingChannel() {
        val guideCategories = ChannelCatalog.guideCategories(channels)

        val firstOpen = GuideFocus.initialTarget(channels, "channel_20", null)
        // The viewer browses to another channel and selects it.
        val browsedIndex = (firstOpen.channelIndex + 3).coerceAtMost(visibleSize(firstOpen, guideCategories) - 1)
        val selected = visible(firstOpen, guideCategories)[browsedIndex]
        val secondOpen = GuideFocus.initialTarget(channels, selected.id, null)

        assertEquals(selected.id, visible(secondOpen, guideCategories)[secondOpen.channelIndex].id)
        assertTrue(secondOpen.focusChannelRow)
    }

    // The preferred category is only a fallback when the playing channel is unknown
    @Test
    fun unknownPlayingChannelFallsBackToThePreferredCategory() {
        val preferred = ChannelCatalog.idForCustomName("Regional")

        val target = GuideFocus.initialTarget(
            channels = channels,
            playingChannelId = "removed_channel",
            preferredCategoryId = preferred,
        )

        assertEquals(preferred, ChannelCatalog.guideCategories(channels)[target.categoryIndex].id)
        assertEquals(0, target.channelIndex)
    }

    // No playing channel and no preference: the first category is used and nothing crashes
    @Test
    fun noPlayingChannelUsesFirstCategory() {
        val target = GuideFocus.initialTarget(channels, null, null)

        // The first category is Favorites, which is empty without favorites, so focus stays on the
        // category row instead of pointing at a channel that is not there.
        assertEquals(ChannelCatalog.FAVORITES_CATEGORY_ID, ChannelCatalog.guideCategories(channels)[target.categoryIndex].id)
        assertEquals(0, target.channelIndex)
        assertFalse(target.focusChannelRow)
    }

    // Computing the focus target is a pure read: it never selects or switches playback
    @Test
    fun computingTheTargetIsPure() {
        val first = GuideFocus.initialTarget(channels, "channel_20", null)
        val second = GuideFocus.initialTarget(channels, "channel_20", null)

        assertEquals(first, second)
        val visible = GuideFocus.channelsFor(
            channels,
            ChannelCatalog.guideCategories(channels),
            first.categoryIndex,
        ).orEmpty()
        assertEquals("channel_20", visible[first.channelIndex].id)
    }

    @Test
    fun categoriesIncludeDocumentOnlyEntries() {
        val withNewCategory = channels + channel(
            id = "ocean_1",
            name = "Ocean",
            sortOrder = 99,
            category = "Nature & Science",
        )

        val categories = ChannelCatalog.guideCategories(withNewCategory)

        assertTrue(categories.any { it.name == "Nature & Science" && !it.known })
        // Built-in categories keep their defined order and come first.
        assertEquals(ChannelCatalog.FAVORITES_CATEGORY_ID, categories.first().id)
        assertEquals(ChannelCatalog.categories.map { it.name }, categories.take(ChannelCatalog.categories.size).map { it.name })
    }

    @Test
    fun subcategoriesComeFromTheActiveDocument() {
        val withNewSubcategory = channels + channel(
            id = "regional_1",
            name = "Regional One",
            sortOrder = 50,
            category = "Regional",
            subcategory = "Marathi News",
        )

        val regionalId = ChannelCatalog.idForCustomName("Regional")
        val subcategories = ChannelCatalog.subcategoriesForChannels(withNewSubcategory, regionalId)

        assertTrue(subcategories.any { it.name == "Bhojpuri" })
        assertTrue(subcategories.any { it.name == "Marathi News" })
        val visible = ChannelCatalog.filter(withNewSubcategory, regionalId, subcategories.last().id)
        assertEquals(listOf("regional_1"), visible.map { it.id })
    }

    // A subcategory filter that hides the playing channel leaves focus on the first visible row
    @Test
    fun subcategoryFilterThatHidesThePlayingChannelFallsBackSafely() {
        val guideCategories = ChannelCatalog.guideCategories(channels)
        val newsIndex = guideCategories.indexOfFirst { it.name == "News" }
        val subcategories = ChannelCatalog.subcategoriesForChannels(channels, guideCategories[newsIndex].id)
        val filtered = subcategories.first()
        val visible = GuideFocus.channelsFor(channels, guideCategories, newsIndex, subcategoryId = filtered.id).orEmpty()

        val index = GuideFocus.indexOfChannel(visible, "channel_20")

        assertEquals(0, index)
    }

    private fun visible(
        target: GuideFocus.Target,
        guideCategories: List<com.livetube.tv.data.GuideCategory>,
    ): List<Channel> = GuideFocus.channelsFor(channels, guideCategories, target.categoryIndex).orEmpty()

    private fun visibleSize(
        target: GuideFocus.Target,
        guideCategories: List<com.livetube.tv.data.GuideCategory>,
    ): Int = visible(target, guideCategories).size

    private fun channel(
        id: String,
        name: String,
        sortOrder: Int,
        category: String = "News",
        subcategory: String = "Hindi News",
    ): Channel {
        val handle = "@${id.replace("_", "")}"
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
}
