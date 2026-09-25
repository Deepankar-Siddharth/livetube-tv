package com.livetube.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlGenerationTest {
    @Test
    fun convertsHandleToCanonicalLiveUrl() {
        assertEquals(
            "https://www.youtube.com/@mirrornow/live",
            JsonUtils.canonicalLiveUrl("@mirrornow"),
        )
        assertEquals(
            "https://www.youtube.com/@mirrornow/live",
            JsonUtils.canonicalLiveUrl("mirrornow"),
        )
    }

    @Test
    fun acceptsCanonicalUrlInput() {
        val url = "https://www.youtube.com/@mirrornow/live"
        assertTrue(JsonUtils.isCanonicalLiveUrl(url))
        assertEquals(url, JsonUtils.canonicalLiveUrl(url))
    }
}
