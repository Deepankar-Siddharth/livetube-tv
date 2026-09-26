package com.livetube.tv.data

/**
 * Navigation structure of the guide, derived entirely from the active channel document.
 *
 * The catalogue is organized as language -> category -> channel, so the guide needs exactly
 * three rows and nothing else. Nothing here is hardcoded: a language appears because channels
 * use it, a category appears because that language has enabled channels in it, and the channel
 * row contains those channels. A newer `data_version` can therefore introduce new languages or
 * categories and the guide follows without a code change.
 */
data class GuideDirectory(
    private val languages: List<GuideNode>,
    private val byLanguage: Map<String, List<GuideNode>>,
) {
    /** Languages that have at least one enabled channel, in a stable order. */
    val languageNodes: List<GuideNode> get() = languages

    val isEmpty: Boolean get() = languages.isEmpty()

    fun language(name: String): GuideNode? = languages.firstOrNull { it.name == name }

    fun languageNames(): List<String> = languages.map(GuideNode::name)

    /** Categories available for [languageName]; empty when the language is unknown. */
    fun categoriesFor(languageName: String): List<GuideNode> = byLanguage[languageName].orEmpty()

    fun categoryNames(languageName: String): List<String> =
        categoriesFor(languageName).map(GuideNode::name)

    /** Channels of one language and category, in guide order. */
    fun channelsFor(languageName: String, categoryName: String): List<Channel> =
        byLanguage[languageName]
            ?.firstOrNull { it.name == categoryName }
            ?.channels
            .orEmpty()


    /**
     * Index of [channelId] inside [channelsFor] of the language and category that contain it.
     */
    fun indexOf(languageName: String, categoryName: String, channelId: String): Int =
        channelsFor(languageName, categoryName).indexOfFirst { it.id == channelId }

    /** Language and category that contain [channelId], or null when it is not in the guide. */
    fun locationOf(channelId: String): Pair<String, String>? {
        languages.forEach { language ->
            categoriesFor(language.name).forEach { category ->
                if (category.channels.any { it.id == channelId }) {
                    return language.name to category.name
                }
            }
        }
        return null
    }
}

/** One language or category in the guide, with the channels it resolves to. */
data class GuideNode(
    val name: String,
    val channels: List<Channel>,
)

/** Where the guide should place language, category and channel focus. */
data class GuideTarget(
    val language: String,
    val category: String,
    val channelIndex: Int,
)

/**
 * Builds the guide navigation structure from the channels of the active document.
 *
 * Only enabled channels take part, so a language or category without an enabled channel is never
 * offered. Order is deterministic (language then category name, channels by sort order), which
 * keeps D-pad movement predictable between openings.
 */
object GuideDirectoryFactory {

    fun build(
        channels: List<Channel>,
        favoriteChannelIds: Set<String>,
    ): GuideDirectory {
        val enabled = ChannelCatalog.enabledChannels(channels)
        val languages = enabled
            .map(Channel::language)
            .filter(String::isNotBlank)
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

        val nodes = languages.map { language ->
            val inLanguage = enabled.filter { it.language == language }
            val categories = inLanguage
                .map(Channel::category)
                .filter(String::isNotBlank)
                .distinct()
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
            GuideNode(
                name = language,
                channels = categories.flatMap { category ->
                    inLanguage.filter { it.category == category }
                },
            )
        }
        val byLanguage = nodes.associate { node ->
            node.name to node.channels
                .map(Channel::category)
                .filter(String::isNotBlank)
                .distinct()
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
                .map { category ->
                    GuideNode(
                        name = category,
                        channels = node.channels.filter { it.category == category },
                    )
                }
        }
        return GuideDirectory(languages = nodes, byLanguage = byLanguage)
    }

    /**
     * Resolves the row selection for the guide.
     *
     * A playing channel that is still in the guide wins, so opening the guide lands on what the
     * viewer is watching. A channel that disappeared, was disabled or is unknown falls back to
     * the remembered position and finally to the first available language, category and channel.
     */
    fun resolve(
        directory: GuideDirectory,
        playingChannelId: String?,
        preferredLanguage: String? = null,
        preferredCategory: String? = null,
    ): GuideTarget {
        if (directory.isEmpty) return GuideTarget(language = "", category = "", channelIndex = 0)

        val playing = playingChannelId?.let { id ->
            directory.locationOf(id)
        }
        if (playing != null) {
            val (language, category) = playing
            val index = directory.indexOf(language, category, playingChannelId!!)
            return GuideTarget(language, category, index.coerceAtLeast(0))
        }

        val language = preferredLanguage
            ?.takeIf { name -> directory.languageNames().contains(name) }
            ?: directory.languageNames().first()
        val categories = directory.categoryNames(language)
        val category = preferredCategory?.takeIf { categories.contains(it) } ?: categories.first()
        val playingIndex = playingChannelId
            ?.let { id -> directory.indexOf(language, category, id) }
            ?.takeIf { it >= 0 }
        return GuideTarget(language, category, playingIndex ?: 0)
    }

    /**
     * Category to show after the viewer moves to [languageName].
     *
     * A channel belongs to exactly one language, so the playing channel cannot survive a language
     * change. The remembered category is used when the new language still offers it, otherwise the
     * first category of that language, which keeps the row predictable.
     */
    fun categoryAfterLanguageChange(
        directory: GuideDirectory,
        languageName: String,
        preferredCategory: String?,
    ): String {
        val categories = directory.categoryNames(languageName)
        if (categories.isEmpty()) return ""
        return preferredCategory?.takeIf { categories.contains(it) } ?: categories.first()
    }
}
