package com.livetube.tv.data

import java.util.Locale

/** Stable navigation metadata. IDs are safe to persist; names are the catalogue-facing labels. */
data class CategoryDefinition(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val sourceBacked: Boolean,
)

data class SubcategoryDefinition(
    val categoryId: String,
    val id: String,
    val name: String,
    val sortOrder: Int,
)

/** Single source of truth for the deliberately small Android TV category hierarchy. */
object ChannelCatalog {
    const val ALL_SUBCATEGORY_ID: String = "all"
    const val FAVORITES_CATEGORY_ID: String = "favorites"

    val categories: List<CategoryDefinition> = listOf(
        CategoryDefinition(FAVORITES_CATEGORY_ID, "Favorites", 0, sourceBacked = false),
        CategoryDefinition("news", "News", 1, sourceBacked = true),
        CategoryDefinition("regional", "Regional", 2, sourceBacked = true),
        CategoryDefinition("devotional", "Devotional", 3, sourceBacked = true),
        CategoryDefinition("kids_family", "Kids & Family", 4, sourceBacked = true),
        CategoryDefinition("knowledge", "Knowledge", 5, sourceBacked = true),
        CategoryDefinition("music_entertainment", "Music & Entertainment", 6, sourceBacked = true),
        CategoryDefinition("sports_live", "Sports & Live", 7, sourceBacked = true),
    )

    val subcategories: List<SubcategoryDefinition> = listOf(
        subcategory("news", "hindi_news", "Hindi News", 1),
        subcategory("news", "english_global_news", "English & Global News", 2),
        subcategory("news", "business_market", "Business & Market", 3),
        subcategory("news", "international_news", "International News", 4),
        subcategory("news", "debate_digital_media", "Debate & Digital Media", 5),

        subcategory("regional", "up_uk", "Uttar Pradesh & Uttarakhand", 1),
        subcategory("regional", "bihar_jharkhand", "Bihar & Jharkhand", 2),
        subcategory("regional", "punjab_haryana", "Punjab & Haryana", 3),
        subcategory("regional", "bhojpuri", "Bhojpuri", 4),
        subcategory("regional", "marathi", "Marathi", 5),
        subcategory("regional", "bengali", "Bengali", 6),
        subcategory("regional", "telugu", "Telugu", 7),
        subcategory("regional", "tamil", "Tamil", 8),
        subcategory("regional", "kannada", "Kannada", 9),
        subcategory("regional", "malayalam", "Malayalam", 10),
        subcategory("regional", "gujarati", "Gujarati", 11),
        subcategory("regional", "odia_north_east", "Odia & North-East", 12),

        subcategory("devotional", "hindu_devotional", "Hindu Devotional", 1),
        subcategory("devotional", "darshan_aarti", "Live Darshan & Aarti", 2),
        subcategory("devotional", "gurbani_sikh", "Gurbani & Sikh", 3),
        subcategory("devotional", "islamic", "Islamic", 4),
        subcategory("devotional", "christian", "Christian", 5),

        subcategory("kids_family", "cartoons_animation", "Cartoons & Animation", 1),
        subcategory("kids_family", "rhymes_nursery", "Rhymes & Nursery", 2),
        subcategory("kids_family", "kids_learning", "Kids Learning", 3),

        subcategory("knowledge", "science_technology", "Science & Technology", 1),
        subcategory("knowledge", "space", "Space", 2),
        subcategory("knowledge", "history_nature", "History & Nature", 3),
        subcategory("knowledge", "travel", "Travel", 4),
        subcategory("knowledge", "documentaries", "Documentaries", 5),

        subcategory("music_entertainment", "bollywood_retro", "Bollywood & Retro", 1),
        subcategory("music_entertainment", "indie_pop", "Indie & Pop", 2),
        subcategory("music_entertainment", "bhakti_classical", "Bhakti & Classical", 3),
        subcategory("music_entertainment", "youth_entertainment", "Youth & Entertainment", 4),

        subcategory("sports_live", "sports_news", "Sports News", 1),
        subcategory("sports_live", "cricket", "Cricket", 2),
        subcategory("sports_live", "fitness_yoga", "Fitness & Yoga", 3),
        subcategory("sports_live", "gaming_esports", "Gaming & Esports", 4),
        subcategory("sports_live", "parliament_governance", "Parliament & Governance", 5),
        subcategory("sports_live", "weather_live_events", "Weather & Live Events", 6),
    )

    private val categoriesById = categories.associateBy(CategoryDefinition::id)
    private val categoriesByName = categories.associateBy(CategoryDefinition::name)
    private val subcategoriesById = subcategories.associateBy(SubcategoryDefinition::id)
    private val subcategoriesByCategory = subcategories.groupBy(SubcategoryDefinition::categoryId)

    val sourceCategoryNames: Set<String> = categories
        .filter(CategoryDefinition::sourceBacked)
        .mapTo(linkedSetOf(), CategoryDefinition::name)

    val subcategoryNamesByCategory: Map<String, Set<String>> = categories.associate { category ->
        category.name to subcategoriesByCategory[category.id]
            .orEmpty()
            .mapTo(linkedSetOf(), SubcategoryDefinition::name)
    }

    fun category(categoryId: String): CategoryDefinition? = categoriesById[categoryId]

    fun categoryByName(name: String): CategoryDefinition? = categoriesByName[name]

    fun subcategoriesFor(categoryId: String): List<SubcategoryDefinition> =
        subcategoriesByCategory[categoryId].orEmpty().sortedBy(SubcategoryDefinition::sortOrder)

    fun isValidClassification(category: String, subcategory: String): Boolean =
        subcategoryNamesByCategory[category]?.contains(subcategory) == true

    fun enabledChannels(channels: List<Channel>): List<Channel> = channels
        .asSequence()
        .filter(Channel::enabled)
        .sortedWith(CHANNEL_ORDER)
        .toList()

    fun filter(
        channels: List<Channel>,
        categoryId: String,
        subcategoryId: String? = null,
        favoriteChannelIds: Set<String> = emptySet(),
    ): List<Channel> {
        val category = category(categoryId) ?: return emptyList()
        return channels.asSequence()
            .filter(Channel::enabled)
            .filter { channel ->
                when {
                    category.id == FAVORITES_CATEGORY_ID -> channel.id in favoriteChannelIds
                    else -> channel.category == category.name
                }
            }
            .filter { channel ->
                subcategoryId == null ||
                    subcategoryId == ALL_SUBCATEGORY_ID ||
                    channel.subcategory == subcategoriesById[subcategoryId]?.name
            }
            .sortedWith(CHANNEL_ORDER)
            .toList()
    }

    fun search(channels: List<Channel>, query: String): List<Channel> {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) return enabledChannels(channels)
        return enabledChannels(channels).filter { channel ->
            listOf(
                channel.name,
                channel.category,
                channel.subcategory,
                channel.language,
                channel.region,
            ).any { value -> value.lowercase(Locale.ROOT).contains(needle) }
        }
    }

    fun adjacentCategoryId(currentId: String, offset: Int): String {
        if (offset == 0) return currentId
        val currentIndex = categories.indexOfFirst { it.id == currentId }.takeIf { it >= 0 } ?: 0
        val nextIndex = (currentIndex + offset).coerceIn(categories.indices)
        return categories[nextIndex].id
    }

    private val CHANNEL_ORDER: Comparator<Channel> =
        compareBy(Channel::sortOrder).thenBy(String.CASE_INSENSITIVE_ORDER, Channel::name)

    private fun subcategory(
        categoryId: String,
        id: String,
        name: String,
        sortOrder: Int,
    ): SubcategoryDefinition = SubcategoryDefinition(categoryId, id, name, sortOrder)
}
