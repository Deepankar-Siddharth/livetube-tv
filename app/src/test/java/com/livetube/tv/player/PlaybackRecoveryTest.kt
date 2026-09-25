package com.livetube.tv.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRecoveryTest {
    @Test
    fun usesBoundedBackoffSequence() {
        assertEquals(listOf(1L, 2L, 4L, 8L, 15L, 30L), (0..5).map {
            PlaybackController.retryDelaySeconds(it)
        })
        assertEquals(null, PlaybackController.retryDelaySeconds(6))
    }
}
