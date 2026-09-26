package com.livetube.tv.data

/**
 * Ordering rules for the channels of the active document.
 *
 * The guide no longer carries a built-in language or category list: both are read from the
 * catalogue, so a newer `data_version` can add languages and categories without a code change. What
 * remains here is the single ordering the whole app relies on.
 */
object ChannelCatalog {

    /** Enabled channels in guide order: catalogue sort order first, then name. */
    fun enabledChannels(channels: List<Channel>): List<Channel> = channels
        .asSequence()
        .filter(Channel::enabled)
        .sortedWith(CHANNEL_ORDER)
        .toList()

    private val CHANNEL_ORDER: Comparator<Channel> =
        compareBy(Channel::sortOrder).thenBy(String.CASE_INSENSITIVE_ORDER, Channel::name)
}
