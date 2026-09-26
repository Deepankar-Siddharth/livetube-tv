package com.livetube.tv.ui

import com.livetube.tv.data.Channel
import com.livetube.tv.data.ChannelCatalog
import com.livetube.tv.data.GuideCategory

/**
 * Where the guide should place its focus when it opens.
 *
 * Opening the guide is a read-only operation: it never selects a channel and never asks the
 * player to change. The playing channel is the starting point, so the viewer continues from
 * where the video is, not from the first channel of the first category.
 */
object GuideFocus {

    data class Target(
        val categoryIndex: Int,
        val channelIndex: Int,
        val focusChannelRow: Boolean,
    )

    /**
     * Row index of [channelId] in [visible], or 0 when it is not in the row that is rendered.
     */
    fun indexOfChannel(visible: List<Channel>, channelId: String?): Int {
        if (channelId == null) return 0
        return visible.indexOfFirst { it.id == channelId }.takeIf { it >= 0 } ?: 0
    }

    /**
     * @param channels enabled channels of the active document
     * @param playingChannelId channel that is currently playing (or was last selected)
     * @param preferredCategoryId category the viewer browsed last, used only as a fallback
     * @param subcategoryId subcategory filter in effect, so the index matches the visible row
     */
    fun initialTarget(
        channels: List<Channel>,
        playingChannelId: String?,
        preferredCategoryId: String?,
        subcategoryId: String? = null,
        guideCategories: List<GuideCategory> = ChannelCatalog.guideCategories(channels),
    ): Target {
        val playing = playingChannelId?.let { id -> channels.firstOrNull { it.id == id } }
        val preferredIndex = guideCategories.indexOfFirst { it.id == preferredCategoryId }
            .takeIf { it >= 0 }
        if (playing != null) {
            val playingCategoryId = ChannelCatalog.idForCustomName(playing.category)
            val categoryIndex = guideCategories.indexOfFirst { it.id == playingCategoryId }
                .takeIf { it >= 0 }
                ?: preferredIndex
                ?: 0
            // The filter is honoured so the index points at the same list the guide renders. A
            // playing channel hidden by an active filter falls back to the first visible row.
            val visible = channelsFor(channels, guideCategories, categoryIndex, subcategoryId = subcategoryId)
            val channelIndex = visible?.indexOfFirst { it.id == playing.id }?.takeIf { it >= 0 }
            if (channelIndex != null) {
                return Target(categoryIndex, channelIndex, focusChannelRow = true)
            }
            return Target(categoryIndex, 0, focusChannelRow = !visible.isNullOrEmpty())
        }
        val categoryIndex = preferredIndex ?: 0
        val firstChannel = channelsFor(channels, guideCategories, categoryIndex)
        return Target(categoryIndex, 0, focusChannelRow = !firstChannel.isNullOrEmpty())
    }

    /** Channels shown for a guide category, mirroring what the guide itself renders. */
    fun channelsFor(
        channels: List<Channel>,
        guideCategories: List<GuideCategory>,
        categoryIndex: Int,
        favoriteChannelIds: Set<String> = emptySet(),
        subcategoryId: String? = null,
    ): List<Channel>? {
        val category = guideCategories.getOrNull(categoryIndex) ?: return null
        return ChannelCatalog.filter(
            channels = channels,
            categoryId = category.id,
            subcategoryId = subcategoryId,
            favoriteChannelIds = favoriteChannelIds,
        )
    }
}
