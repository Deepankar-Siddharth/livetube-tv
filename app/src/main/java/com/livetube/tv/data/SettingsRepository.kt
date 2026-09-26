package com.livetube.tv.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException

/** Device-local application settings. */
data class AppSettings(
    val autoCheckUpdates: Boolean = DEFAULT_AUTO_CHECK_UPDATES,
    val lastUpdateCheckMillis: Long = 0L,
    val lastChannelSyncMillis: Long = 0L,
) {
    val hasCheckedForUpdates: Boolean get() = lastUpdateCheckMillis > 0L
    val hasSyncedChannels: Boolean get() = lastChannelSyncMillis > 0L

    companion object {
        const val DEFAULT_AUTO_CHECK_UPDATES = true
        val EMPTY = AppSettings()
    }
}

interface SettingsStorage {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

/**
 * Stores the update preference and the timestamps of the last update check and channel sync.
 *
 * Nothing in here talks to the network; the values only describe what the app already did.
 */
class SettingsRepository(private val storage: SettingsStorage) {
    constructor(dataStore: DataStore<Preferences>) : this(DataStoreSettingsStorage(dataStore))

    val settings: Flow<AppSettings> = storage.settings

    val autoCheckUpdates: Flow<Boolean> = settings.map { it.autoCheckUpdates }
    val lastUpdateCheckMillis: Flow<Long> = settings.map { it.lastUpdateCheckMillis }
    val lastChannelSyncMillis: Flow<Long> = settings.map { it.lastChannelSyncMillis }

    suspend fun setAutoCheckUpdates(enabled: Boolean) {
        storage.update { it.copy(autoCheckUpdates = enabled) }
    }

    suspend fun recordUpdateCheck(checkedAtMillis: Long = System.currentTimeMillis()) {
        storage.update { it.copy(lastUpdateCheckMillis = checkedAtMillis) }
    }

    suspend fun recordChannelSync(syncedAtMillis: Long = System.currentTimeMillis()) {
        storage.update { it.copy(lastChannelSyncMillis = syncedAtMillis) }
    }

    suspend fun clearUpdateCheck() {
        storage.update { it.copy(lastUpdateCheckMillis = 0L) }
    }

    companion object {
        private const val FILE_NAME = "app_settings.preferences_pb"

        fun create(context: Context, scope: CoroutineScope): SettingsRepository {
            val dataStoreDirectory = File(context.applicationContext.filesDir, "datastore")
                .apply { mkdirs() }
            val dataStore = PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = scope,
                produceFile = { File(dataStoreDirectory, FILE_NAME) },
            )
            return SettingsRepository(dataStore)
        }
    }
}

private class DataStoreSettingsStorage(
    private val dataStore: DataStore<Preferences>,
) : SettingsStorage {
    override val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            AppSettings(
                autoCheckUpdates = preferences[AUTO_CHECK_UPDATES]
                    ?: AppSettings.DEFAULT_AUTO_CHECK_UPDATES,
                lastUpdateCheckMillis = preferences[LAST_UPDATE_CHECK] ?: 0L,
                lastChannelSyncMillis = preferences[LAST_CHANNEL_SYNC] ?: 0L,
            )
        }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { preferences ->
            val updated = transform(
                AppSettings(
                    autoCheckUpdates = preferences[AUTO_CHECK_UPDATES]
                        ?: AppSettings.DEFAULT_AUTO_CHECK_UPDATES,
                    lastUpdateCheckMillis = preferences[LAST_UPDATE_CHECK] ?: 0L,
                    lastChannelSyncMillis = preferences[LAST_CHANNEL_SYNC] ?: 0L,
                ),
            )
            preferences[AUTO_CHECK_UPDATES] = updated.autoCheckUpdates
            preferences[LAST_UPDATE_CHECK] = updated.lastUpdateCheckMillis
            preferences[LAST_CHANNEL_SYNC] = updated.lastChannelSyncMillis
        }
    }

    private companion object {
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check_millis")
        val LAST_CHANNEL_SYNC = longPreferencesKey("last_channel_sync_millis")
    }
}
