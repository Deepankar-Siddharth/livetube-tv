package com.livetube.tv.util

import com.livetube.tv.BuildConfig
import java.util.Locale

/** Static application endpoints and small configuration helpers. */
object Constants {
    const val CHANNEL_FILE = "data/channels.json"
    const val GITHUB_API_BASE = "https://api.github.com"
    const val RAW_GITHUB_BASE = "https://raw.githubusercontent.com"
    const val CONNECT_TIMEOUT_MS = 15_000
    const val READ_TIMEOUT_MS = 20_000
    const val MAX_CONFIG_BYTES = 2 * 1024 * 1024
    const val MAX_APK_BYTES = 300L * 1024L * 1024L
    const val GUIDE_TIMEOUT_MS = 5_000L
    val RETRY_DELAYS_SECONDS = longArrayOf(1L, 2L, 4L, 8L, 15L, 30L)
    private val REPOSITORY_PART = Regex("[A-Za-z0-9_.-]+")
    private val BRANCH_PART = Regex("[A-Za-z0-9_./-]+")

    /** Public project identity used by the About experience. */
    const val PROJECT_OWNER = "Deepankar-Siddharth"
    const val PROJECT_NAME = "livetube-tv"
    const val GITHUB_WEB_BASE = "https://github.com"

    fun ownerProfileUrl(): String = "$GITHUB_WEB_BASE/$PROJECT_OWNER"

    fun projectUrl(): String = "$GITHUB_WEB_BASE/$PROJECT_OWNER/$PROJECT_NAME"

    fun projectReleasesUrl(): String = "${projectUrl()}/releases"

    data class RepositoryConfig(
        val owner: String,
        val repository: String,
        val branch: String,
    )

    fun repositoryConfig(): RepositoryConfig? {
        val owner = BuildConfig.GITHUB_OWNER.trim()
        val repository = BuildConfig.GITHUB_REPOSITORY.trim()
        val branch = BuildConfig.GITHUB_BRANCH.trim().ifEmpty { "main" }
        if (!REPOSITORY_PART.matches(owner) || !REPOSITORY_PART.matches(repository)) {
            return null
        }
        if (!BRANCH_PART.matches(branch) || branch.contains("..")) {
            return null
        }
        return RepositoryConfig(owner, repository, branch)
    }

    fun channelsUrl(): String? = repositoryConfig()?.let {
        "$RAW_GITHUB_BASE/${it.owner}/${it.repository}/${it.branch}/$CHANNEL_FILE"
    }

    fun apiUrl(path: String): String? = repositoryConfig()?.let {
        "$GITHUB_API_BASE/repos/${it.owner}/${it.repository}$path"
    }

    fun userAgent(): String = "LiveTube-TV/${BuildConfig.VERSION_NAME} (Android TV)"

    fun normalizedVersion(version: String): String = version.trim().removePrefix("v")

    fun isGitHubDownloadHost(host: String?): Boolean {
        val normalized = host?.lowercase(Locale.ROOT) ?: return false
        return normalized == "github.com" ||
            normalized == "api.github.com" ||
            normalized == "objects.githubusercontent.com" ||
            normalized.endsWith(".githubusercontent.com")
    }
}
