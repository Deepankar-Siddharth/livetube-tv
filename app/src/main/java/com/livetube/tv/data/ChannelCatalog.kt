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

/**
 * One entry of the guide's category row.
 *
 * [known] is false for a category that only exists in the active document. Such categories are
 * appended after the built-in ones so a newer catalogue can add categories without hiding any
 * channel from the guide.
 */
data class GuideCategory(
    val id: String,
    val name: String,
    val known: Boolean,
)

/** Single source of truth for the deliberately small Android TV category hierarchy. */
object ChannelCatalog {
    const val ALL_SUBCATEGORY_ID: String = "all"
    const val FAVORITES_CATEGORY_ID: String = "favorites"
    private const val CUSTOM_CATEGORY_PREFIX = "custom:"
    private const val CUSTOM_SUBCATEGORY_PREFIX = "custom:"

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

    /**
     * Category row for the guide: the built-in categories first, then any category that only
     * exists in the active document (sorted by name) so new catalogue categories stay reachable.
     */
    /**
     * Display names of categories that only exist in the active document, keyed by their
     * generated id. [guideCategories] is the entry point that fills this map, and the guide always
     * builds its rows before it filters, so an id can always be resolved back to its name.
     */
    private val customCategoryNames: MutableMap<String, String> = linkedMapOf()

    fun guideCategories(channels: List<Channel>): List<GuideCategory> {
        val known = categories.map { GuideCategory(it.id, it.name, known = true) }
        val knownNames = categories.mapTo(linkedSetOf(), CategoryDefinition::name)
        val extra = channels.asSequence()
            .map(Channel::category)
            .filterNot { it in knownNames }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .map { name ->
                val id = idForCustomName(name)
                customCategoryNames[id] = name
                GuideCategory(id, name, known = false)
            }
            .toList()
        return known + extra
    }

    /** Stable id for a category name, including names that are not built in. */
    fun idForCustomName(name: String): String {
        categoryByName(name)?.let { return it.id }
        val slug = name.lowercase(Locale.ROOT)
            .replace(NON_SLUG_CHARACTERS, "-")
            .trim('-')
            .take(48)
            .ifEmpty { "category" }
        return "$CUSTOM_CATEGORY_PREFIX$slug"
    }

    /**
     * Resolves a guide category id back to the catalogue category name, if it has one.
     *
     * Built-in ids resolve from the definitions. Ids generated for document-only categories resolve
     * from [customCategoryNames], which [guideCategories] fills; the guide always builds its rows
     * before filtering, so resolution never depends on a display name being a valid id.
     */
    fun nameForGuideCategoryId(id: String): String? {
        if (id.startsWith(CUSTOM_CATEGORY_PREFIX)) return customCategoryNames[id]
        return category(id)?.name
    }

    /**
     * Subcategories available for a guide category: the built-in definitions first, then any
     * subcategory that the active document introduces for that category.
     */
    fun subcategoriesForChannels(
        channels: List<Channel>,
        categoryId: String,
    ): List<SubcategoryDefinition> {
        val name = nameForGuideCategoryId(categoryId) ?: return emptyList()
        val defined = subcategoriesFor(categoryId).map(SubcategoryDefinition::name).toList()
        val extra = channels.asSequence()
            .filter { it.category == name }
            .map(Channel::subcategory)
            .filterNot { it in defined }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .toList()
        val known = defined.mapIndexed { index, subcategoryName ->
            SubcategoryDefinition(categoryId, subcategoryId(categoryId, subcategoryName), subcategoryName, index + 1)
        }
        val discovered = extra.mapIndexed { index, subcategoryName ->
            SubcategoryDefinition(
                categoryId = categoryId,
                id = subcategoryId(categoryId, subcategoryName),
                name = subcategoryName,
                sortOrder = known.size + index + 1,
            )
        }
        return known + discovered
    }

    private fun subcategoryId(categoryId: String, name: String): String {
        subcategoriesFor(categoryId).firstOrNull { it.name == name }?.let { return it.id }
        val slug = name.lowercase(Locale.ROOT)
            .replace(NON_SLUG_CHARACTERS, "-")
            .trim('-')
            .take(48)
            .ifEmpty { "subcategory" }
        return "$CUSTOM_SUBCATEGORY_PREFIX$slug"
    }

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
        val categoryName = nameForGuideCategoryId(categoryId) ?: return emptyList()
        val subcategoryName = when {
            subcategoryId == null || subcategoryId == ALL_SUBCATEGORY_ID -> null
            else -> subcategoriesForChannels(channels, categoryId)
                .firstOrNull { it.id == subcategoryId }
                ?.name
        }
        // An unknown subcategory id must not hide the whole category.
        if (subcategoryName == null && subcategoryId != null && subcategoryId != ALL_SUBCATEGORY_ID) {
            return emptyList()
        }
        return channels.asSequence()
            .filter(Channel::enabled)
            .filter { channel ->
                when {
                    categoryId == FAVORITES_CATEGORY_ID -> channel.id in favoriteChannelIds
                    else -> channel.category == categoryName
                }
            }
            .filter { channel -> subcategoryName == null || channel.subcategory == subcategoryName }
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

    private val NON_SLUG_CHARACTERS = Regex("[^a-z0-9]+")

    private fun subcategory(
        categoryId: String,
        id: String,
        name: String,
        sortOrder: Int,
    ): SubcategoryDefinition = SubcategoryDefinition(categoryId, id, name, sortOrder)
}
