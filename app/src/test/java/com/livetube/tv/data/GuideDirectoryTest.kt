package com.livetube.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guide navigation is derived from the active document: language -> category -> channel.
 *
 * These tests cover the rules the guide depends on, including the requirement that opening the
 * guide lands on the channel that is playing.
 */
class GuideDirectoryTest {

    private val channels = listOf(
        channel("ndtv", "NDTV", "News", "Hindi News", "Hindi", order = 1),
        channel("aaj_tak", "Aaj Tak", "News", "Hindi News", "Hindi", order = 2),
        channel("pravasi", "Pravasi TV", "Devotional", "Hindu Devotional", "Hindi", order = 3),
        channel("bbc", "BBC News", "News", "International News", "English", order = 4),
        channel("al_jazeera", "Al Jazeera", "News", "International News", "English", order = 5),
        channel("dw", "DW News", "News", "International News", "English", order = 6),
        channel("nhk", "NHK", "Nature & Earth", "Wildlife", "Japanese", order = 7),
        channel("tv5", "TV5", "News", "French News", "Instrumental", order = 8),
        // Disabled channels must never create a language, a category or a card.
        channel("hidden", "Hidden TV", "Sports", "Cricket", "Tamil", order = 9, enabled = false),
    )

    private val directory = GuideDirectoryFactory.build(channels, favoriteChannelIds = setOf("bbc"))

    @Test
    fun languagesComeFromTheDocument() {
        assertEquals(
            listOf("English", "Hindi", "Instrumental", "Japanese"),
            directory.languageNames(),
        )
    }

    @Test
    fun categoriesAreDerivedPerLanguage() {
        assertEquals(listOf("Devotional", "News"), directory.categoryNames("Hindi"))
        assertEquals(listOf("News"), directory.categoryNames("English"))
        assertEquals(listOf("Nature & Earth"), directory.categoryNames("Japanese"))
    }

    @Test
    fun aLanguageNeverOffersACategoryItHasNoChannelFor() {
        // Sports only exists for a disabled Tamil channel, so it must not appear anywhere.
        assertTrue(directory.categoryNames("Hindi").none { it == "Sports" })
        assertTrue(directory.categoryNames("English").none { it == "Sports" })
        assertTrue(directory.languageNames().none { it == "Tamil" })
    }

    @Test
    fun channelRowContainsOnlyThatLanguageAndCategory() {
        assertEquals(
            listOf("ndtv", "aaj_tak"),
            directory.channelsFor("Hindi", "News").map(Channel::id),
        )
        assertEquals(
            listOf("bbc", "al_jazeera", "dw"),
            directory.channelsFor("English", "News").map(Channel::id),
        )
        assertTrue(directory.channelsFor("English", "Devotional").isEmpty())
    }

    // Requirement: the guide must open directly on the channel that is playing.
    @Test
    fun resolvesThePlayingChannelIntoLanguageCategoryAndIndex() {
        val target = GuideDirectoryFactory.resolve(directory, playingChannelId = "dw")

        assertEquals("English", target.language)
        assertEquals("News", target.category)
        assertEquals(2, target.channelIndex)
        assertEquals("dw", directory.channelsFor(target.language, target.category)[target.channelIndex].id)
    }

    @Test
    fun aPlayingChannelInAnotherCategoryStillResolves() {
        val target = GuideDirectoryFactory.resolve(directory, playingChannelId = "nhk")

        assertEquals("Japanese", target.language)
        assertEquals("Nature & Earth", target.category)
        assertEquals(0, target.channelIndex)
    }

    @Test
    fun anUnknownPlayingChannelFallsBackToTheRememberedPosition() {
        val target = GuideDirectoryFactory.resolve(
            directory,
            playingChannelId = "removed_channel",
            preferredLanguage = "Japanese",
            preferredCategory = "Nature & Earth",
        )

        assertEquals("Japanese", target.language)
        assertEquals("Nature & Earth", target.category)
        assertEquals(0, target.channelIndex)
    }

    @Test
    fun withoutAnyPlayingChannelTheFirstLanguageIsUsed() {
        val target = GuideDirectoryFactory.resolve(directory, playingChannelId = null)

        assertEquals("English", target.language)
        assertEquals("News", target.category)
        assertEquals(0, target.channelIndex)
    }

    @Test
    fun aDisabledPlayingChannelIsNotResolved() {
        val target = GuideDirectoryFactory.resolve(directory, playingChannelId = "hidden")

        assertNull(directory.locationOf("hidden"))
        assertEquals("English", target.language)
    }

    @Test
    fun changingLanguageFallsBackToTheFirstCategoryOfThatLanguage() {
        assertEquals(
            "Devotional",
            GuideDirectoryFactory.categoryAfterLanguageChange(
                directory = directory,
                languageName = "Hindi",
                preferredCategory = "Nature & Earth",
            ),
        )
    }

    @Test
    fun changingLanguageKeepsARememberedCategoryWhenItStillExists() {
        assertEquals(
            "Devotional",
            GuideDirectoryFactory.categoryAfterLanguageChange(
                directory = directory,
                languageName = "Hindi",
                preferredCategory = "Devotional",
            ),
        )
    }

    /**
     * Within one language the playing channel is kept when the viewer browses to a category that
     * still contains it, which is what the guide does when a category chip receives focus.
     */
    @Test
    fun aCategoryThatStillContainsThePlayingChannelKeepsItsIndex() {
        val twoCategoryLanguage = GuideDirectoryFactory.build(
            channels + channel("ndtv_sport", "NDTV Sports", "Sports", "Cricket", "Hindi", order = 11),
            favoriteChannelIds = emptySet(),
        )

        assertEquals(
            listOf("ndtv", "aaj_tak"),
            twoCategoryLanguage.channelsFor("Hindi", "News").map(Channel::id),
        )
        assertEquals(0, twoCategoryLanguage.indexOf("Hindi", "News", "ndtv"))
        assertEquals(-1, twoCategoryLanguage.indexOf("Hindi", "Sports", "ndtv"))
    }

    @Test
    fun favoritesAreALocalListAndNotPartOfTheTaxonomy() {
        // Favorites never appear as a language or a category; the guide filters them locally.
        assertTrue(directory.languageNames().none { it.contains("Favorites", ignoreCase = true) })
        assertTrue(
            directory.categoriesFor("English").none { it.name.contains("Favorites", ignoreCase = true) },
        )
        assertEquals(listOf("bbc"), channels.filter { it.id in setOf("bbc") }.map(Channel::id))
    }

    @Test
    fun anEmptyCatalogueProducesAnEmptyDirectory() {
        val empty = GuideDirectoryFactory.build(emptyList(), favoriteChannelIds = emptySet())

        assertTrue(empty.isEmpty)
        assertTrue(empty.languageNames().isEmpty())
        assertEquals(GuideTarget("", "", 0), GuideDirectoryFactory.resolve(empty, null))
    }

    @Test
    fun aCatalogueOfOnlyDisabledChannelsIsAlsoEmpty() {
        val disabledOnly = GuideDirectoryFactory.build(
            channels.filter { !it.enabled },
            favoriteChannelIds = setOf("hidden"),
        )

        assertTrue(disabledOnly.isEmpty)
    }

    @Test
    fun locationLookupIsExactAndSafe() {
        assertEquals("Hindi" to "News", directory.locationOf("aaj_tak"))
        assertNull(directory.locationOf("not_here"))
        assertEquals(-1, directory.indexOf("English", "News", "not_here"))
    }

    @Test
    fun everyEnabledChannelIsReachableFromTheDirectory() {
        val reachable = directory.languageNames()
            .flatMap { language ->
                directory.categoryNames(language).flatMap { category ->
                    directory.channelsFor(language, category)
                }
            }
            .map(Channel::id)
            .toSet()

        assertEquals(
            channels.filter(Channel::enabled).map(Channel::id).toSet(),
            reachable,
        )
    }

    private fun channel(
        id: String,
        name: String,
        category: String,
        subcategory: String,
        language: String,
        order: Int,
        enabled: Boolean = true,
    ) = Channel(
        id = id,
        name = name,
        category = category,
        subcategory = subcategory,
        language = language,
        region = "National",
        logo = "https://example.com/$id.png",
        youtubeHandle = "@$id",
        liveUrl = "https://www.youtube.com/@$id/live",
        enabled = enabled,
        sortOrder = order,
    )
}
