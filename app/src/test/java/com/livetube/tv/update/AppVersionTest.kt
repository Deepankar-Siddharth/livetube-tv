package com.livetube.tv.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun comparesNumericComponentsNumerically() {
        assertTrue(AppVersion.parse("1.0.0")!! < AppVersion.parse("1.0.1")!!)
        assertTrue(AppVersion.parse("1.0.9")!! < AppVersion.parse("1.0.10")!!)
        assertTrue(AppVersion.parse("1.1.0")!! > AppVersion.parse("1.0.9")!!)
    }

    @Test
    fun handlesReleaseTagPrefix() {
        assertEquals(AppVersion.parse("1.2.3"), AppVersion.parse("v1.2.3"))
    }

    @Test
    fun rejectsMalformedVersion() {
        assertEquals(null, AppVersion.parse("1.2"))
        assertEquals(null, AppVersion.parse("latest"))
    }
}
