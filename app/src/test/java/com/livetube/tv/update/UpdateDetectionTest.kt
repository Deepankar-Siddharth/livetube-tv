package com.livetube.tv.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDetectionTest {
    @Test
    fun ignoresDraftsAndPrereleasesAndSelectsHighestStableVersion() {
        val releases = UpdateManager.parseReleases(
            """
            [
              {"tag_name":"v1.0.3","name":"v1.0.3","draft":false,"prerelease":false,"assets":[{"name":"livetube-tv-1.0.3.apk","browser_download_url":"https://github.com/a/b/releases/download/v1.0.3/app.apk"}]},
              {"tag_name":"v1.1.0","name":"v1.1.0","draft":true,"prerelease":false,"assets":[]},
              {"tag_name":"v1.0.4","name":"v1.0.4","draft":false,"prerelease":true,"assets":[]},
              {"tag_name":"v1.0.10","name":"v1.0.10","draft":false,"prerelease":false,"assets":[{"name":"livetube-tv-1.0.10.apk","browser_download_url":"https://github.com/a/b/releases/download/v1.0.10/app.apk"}]}
            ]
            """.trimIndent(),
        )
        val latest = UpdateManager.selectLatestStable(releases)
        assertEquals("v1.0.10", latest?.tagName)
        assertTrue(latest?.apkAsset() != null)
    }
}
