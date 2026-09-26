package com.livetube.tv.data

import org.json.JSONObject

/** A single canonical YouTube live channel. Temporary media URLs never belong in this model. */
data class Channel(
    val id: String,
    val name: String,
    val category: String,
    val subcategory: String,
    val language: String,
    val region: String,
    val logo: String,
    val youtubeHandle: String,
    val liveUrl: String,
    val enabled: Boolean,
    val sortOrder: Int,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("category", category)
        put("subcategory", subcategory)
        put("language", language)
        put("region", region)
        put("logo", logo)
        put("youtube_handle", youtubeHandle)
        put("live_url", liveUrl)
        put("enabled", enabled)
        put("sort_order", sortOrder)
    }
}

data class ChannelDocument(
    val schemaVersion: Int,
    val dataVersion: Int,
    val updatedAt: String,
    val channels: List<Channel>,
) {
    fun enabledChannels(): List<Channel> = ChannelCatalog.enabledChannels(channels)

    /**
     * True when this remote document should replace [other] on the device.
     *
     * `data_version` is the official channel-data version; `updated_at` is metadata only and is
     * never used to decide whether to update.
     */
    fun isNewerThan(other: ChannelDocument): Boolean = ChannelDataVersion.isNewer(this, other)

    /** True when both documents declare the same channel-data version. */
    fun hasSameDataVersion(other: ChannelDocument): Boolean = dataVersion == other.dataVersion

    fun toJson(): JSONObject = JSONObject().apply {
        put("schema_version", schemaVersion)
        put("data_version", dataVersion)
        put("updated_at", updatedAt)
        put("channels", org.json.JSONArray(channels.map(Channel::toJson)))
    }
}
