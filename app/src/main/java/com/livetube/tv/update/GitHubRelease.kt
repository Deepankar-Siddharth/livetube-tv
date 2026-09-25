package com.livetube.tv.update

import org.json.JSONObject

 data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)

data class GitHubRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val publishedAt: String,
    val assets: List<ReleaseAsset>,
) {
    val version: AppVersion?
        get() = AppVersion.parse(tagName) ?: AppVersion.parse(name)

    fun apkAsset(): ReleaseAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val preferredName = version?.let { "livetube-tv-${it}.apk" }
        return apks.firstOrNull { preferredName != null && it.name.equals(preferredName, true) }
            ?: apks.firstOrNull { !it.name.contains("symbols", true) }
            ?: apks.firstOrNull()
    }

    companion object {
        fun fromJson(json: JSONObject): GitHubRelease {
            val assetsJson = json.optJSONArray("assets")
            val assets = buildList {
                if (assetsJson != null) {
                    for (index in 0 until assetsJson.length()) {
                        val asset = assetsJson.optJSONObject(index) ?: continue
                        val url = asset.optString("browser_download_url")
                        val name = asset.optString("name")
                        if (url.isNotBlank() && name.isNotBlank()) {
                            add(ReleaseAsset(name, url, asset.optLong("size", 0L)))
                        }
                    }
                }
            }
            return GitHubRelease(
                tagName = json.optString("tag_name"),
                name = json.optString("name"),
                body = json.optString("body"),
                htmlUrl = json.optString("html_url"),
                draft = json.optBoolean("draft", false),
                prerelease = json.optBoolean("prerelease", false),
                publishedAt = json.optString("published_at"),
                assets = assets,
            )
        }
    }
}
