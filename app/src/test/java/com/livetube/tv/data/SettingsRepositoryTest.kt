package com.livetube.tv.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {
    @Test
    fun defaultsToAutomaticUpdateChecksAndNoTimestamps() = runBlocking {
        val repository = SettingsRepository(FakeSettingsStorage())
        val settings = repository.settings.first()
        assertTrue(settings.autoCheckUpdates)
        assertFalse(settings.hasCheckedForUpdates)
        assertFalse(settings.hasSyncedChannels)
        assertEquals(0L, settings.lastUpdateCheckMillis)
        assertEquals(0L, settings.lastChannelSyncMillis)
    }

    @Test
    fun persistsPreferencesAndTimestampsAcrossRepositoryInstances() = runBlocking {
        val backend = PersistentBackend()
        val repository = SettingsRepository(FakeSettingsStorage(backend))
        repository.setAutoCheckUpdates(false)
        repository.recordUpdateCheck(1_700_000_000_000L)
        repository.recordChannelSync(1_700_000_500_000L)

        val reopened = SettingsRepository(FakeSettingsStorage(backend)).settings.first()
        assertFalse(reopened.autoCheckUpdates)
        assertTrue(reopened.hasCheckedForUpdates)
        assertTrue(reopened.hasSyncedChannels)
        assertEquals(1_700_000_000_000L, reopened.lastUpdateCheckMillis)
        assertEquals(1_700_000_500_000L, reopened.lastChannelSyncMillis)

        // Recording a new check keeps the other values intact.
        repository.recordUpdateCheck(1_700_000_900_000L)
        val afterSecondCheck = SettingsRepository(FakeSettingsStorage(backend)).settings.first()
        assertEquals(1_700_000_900_000L, afterSecondCheck.lastUpdateCheckMillis)
        assertEquals(1_700_000_500_000L, afterSecondCheck.lastChannelSyncMillis)
        assertFalse(afterSecondCheck.autoCheckUpdates)
    }

    @Test
    fun clearingTheUpdateCheckKeepsThePreference() = runBlocking {
        val repository = SettingsRepository(FakeSettingsStorage())
        repository.recordUpdateCheck(42L)
        repository.clearUpdateCheck()
        val settings = repository.settings.first()
        assertFalse(settings.hasCheckedForUpdates)
        assertTrue(settings.autoCheckUpdates)
    }

    private class FakeSettingsStorage(
        private val backend: PersistentBackend = PersistentBackend(),
    ) : SettingsStorage {
        override val settings: Flow<AppSettings> = backend.state
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            backend.state.value = transform(backend.state.value)
        }
    }

    private class PersistentBackend {
        val state = MutableStateFlow(AppSettings.EMPTY)
    }
}
