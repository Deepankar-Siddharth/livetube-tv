package com.livetube.tv

import android.app.Application
import com.livetube.tv.data.FavoritesRepository
import com.livetube.tv.data.SettingsRepository
import com.livetube.tv.extractor.YouTubeExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class LiveTubeApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val favoritesRepository: FavoritesRepository by lazy {
        FavoritesRepository.create(this, applicationScope)
    }
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository.create(this, applicationScope)
    }

    override fun onCreate() {
        super.onCreate()
        YouTubeExtractor.initialize()
    }
}
