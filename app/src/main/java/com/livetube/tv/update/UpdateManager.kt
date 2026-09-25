package com.livetube.tv.update

import com.livetube.tv.BuildConfig
import com.livetube.tv.util.Constants
import com.livetube.tv.util.NetworkUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

sealed interface UpdateCheckResult {
    data class Available(val release: GitHubRelease) : UpdateCheckResult
    data class Current(val version: AppVersion) : UpdateCheckResult
    data class Unavailable(val reason: String) : UpdateCheckResult
    data class Failed(val reason: String) : UpdateCheckResult
}

/** Checks stable GitHub releases without ever blocking startup or playback. */
class UpdateManager {

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) {
            return@withContext UpdateCheckResult.Unavailable("Updates are disabled for debug builds")
        }
        val endpoint = Constants.apiUrl("/releases?per_page=100")
            ?: return@withContext UpdateCheckResult.Unavailable(
                "GitHub repository is not configured in this build",
            )
        try {
            val body = NetworkUtils.getText(
                endpoint,
                headers = mapOf(
                    "Accept" to "application/vnd.github+json",
                    "X-GitHub-Api-Version" to "2022-11-28",
                ),
                maxBytes = 4 * 1024 * 1024,
            )
            val releases = parseReleases(body)
            val latest = selectLatestInstallableStable(releases)
                ?: return@withContext UpdateCheckResult.Unavailable("No stable APK release was found")
            val installed = AppVersion.parse(BuildConfig.VERSION_NAME)
                ?: return@withContext UpdateCheckResult.Failed("Installed version is malformed")
            if (latest.version == null || latest.apkAsset() == null) {
                return@withContext UpdateCheckResult.Unavailable("Latest release has no APK asset")
            }
            if (latest.version!! > installed) {
                UpdateCheckResult.Available(latest)
            } else {
                UpdateCheckResult.Current(installed)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val message = when (error) {
                is java.net.SocketTimeoutException -> "GitHub took too long to respond"
                is java.io.IOException -> "GitHub is temporarily unavailable"
                else -> "Update check failed"
            }
            UpdateCheckResult.Failed(message)
        }
    }

    companion object {
        fun parseReleases(body: String): List<GitHubRelease> {
            val array = JSONArray(body)
            return buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(GitHubRelease.fromJson(item))
                }
            }
        }

        fun selectLatestStable(releases: List<GitHubRelease>): GitHubRelease? = releases
            .asSequence()
            .filterNot { it.draft || it.prerelease }
            .mapNotNull { release -> release.version?.let { release to it } }
            .maxWithOrNull(compareBy<Pair<GitHubRelease, AppVersion>> { it.second })
            ?.first

        /** Select the newest stable release that can actually be installed. */
        fun selectLatestInstallableStable(releases: List<GitHubRelease>): GitHubRelease? = releases
            .asSequence()
            .filterNot { it.draft || it.prerelease }
            .filter { it.apkAsset() != null }
            .mapNotNull { release -> release.version?.let { release to it } }
            .maxWithOrNull(compareBy<Pair<GitHubRelease, AppVersion>> { it.second })
            ?.first
    }
}
