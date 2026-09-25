package com.livetube.tv.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException

interface FavoriteStorage {
    val favoriteChannelIds: Flow<Set<String>>
    suspend fun update(transform: (Set<String>) -> Set<String>)
}

/** Device-local favorite IDs. This repository never reads or writes the central catalogue. */
class FavoritesRepository(private val storage: FavoriteStorage) {
    constructor(dataStore: DataStore<Preferences>) : this(DataStoreFavoriteStorage(dataStore))

    val favoriteChannelIds: Flow<Set<String>> = storage.favoriteChannelIds

    suspend fun add(channelId: String) {
        val id = validateChannelId(channelId)
        storage.update { current -> current + id }
    }

    suspend fun remove(channelId: String) {
        val id = validateChannelId(channelId)
        storage.update { current -> current - id }
    }

    suspend fun setFavorite(channelId: String, favorite: Boolean) {
        if (favorite) add(channelId) else remove(channelId)
    }

    private fun validateChannelId(channelId: String): String {
        require(CHANNEL_ID_PATTERN.matches(channelId)) { "Invalid favorite channel id" }
        return channelId
    }

    companion object {
        private const val FILE_NAME = "favorite_channels.preferences_pb"
        private val CHANNEL_ID_PATTERN = Regex("[a-z0-9][a-z0-9_-]{0,63}")

        fun create(context: Context, scope: CoroutineScope): FavoritesRepository {
            val dataStoreDirectory = File(context.applicationContext.filesDir, "datastore")
                .apply { mkdirs() }
            val dataStore = PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = scope,
                produceFile = {
                    File(dataStoreDirectory, FILE_NAME)
                },
            )
            return FavoritesRepository(dataStore)
        }
    }
}

private class DataStoreFavoriteStorage(
    private val dataStore: DataStore<Preferences>,
) : FavoriteStorage {
    override val favoriteChannelIds: Flow<Set<String>> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences[FAVORITE_CHANNEL_IDS].orEmpty().toSet() }

    override suspend fun update(transform: (Set<String>) -> Set<String>) {
        dataStore.edit { preferences ->
            preferences[FAVORITE_CHANNEL_IDS] = transform(
                preferences[FAVORITE_CHANNEL_IDS].orEmpty(),
            ).toSet()
        }
    }

    private companion object {
        val FAVORITE_CHANNEL_IDS = stringSetPreferencesKey("favorite_channel_ids")
    }
}
