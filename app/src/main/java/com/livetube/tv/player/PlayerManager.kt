package com.livetube.tv.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.livetube.tv.extractor.ExtractionResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface PlayerEvent {
    data class StateChanged(val phase: PlaybackPhase) : PlayerEvent
    data class Error(val message: String) : PlayerEvent
}

/** Owns one lifecycle-bound ExoPlayer instance for the activity. */
class PlayerManager(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()

    private val _phase = MutableStateFlow(PlaybackPhase.IDLE)
    val phase: StateFlow<PlaybackPhase> = _phase.asStateFlow()
    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val next = when (playbackState) {
                    Player.STATE_BUFFERING -> PlaybackPhase.BUFFERING
                    Player.STATE_READY -> PlaybackPhase.PLAYING
                    Player.STATE_ENDED -> PlaybackPhase.ENDED
                    Player.STATE_IDLE -> PlaybackPhase.IDLE
                    // ExoPlayer reports the error through onPlayerError. Avoid emitting a
                    // second ERROR event here or the bounded recovery counter advances twice.
                    else -> return
                }
                _phase.value = next
                _events.tryEmit(PlayerEvent.StateChanged(next))
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying && _phase.value == PlaybackPhase.PLAYING) {
                    _phase.value = PlaybackPhase.PAUSED
                    _events.tryEmit(PlayerEvent.StateChanged(PlaybackPhase.PAUSED))
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _phase.value = PlaybackPhase.ERROR
                _events.tryEmit(
                    PlayerEvent.Error("The stream stopped responding. A fresh connection will be tried."),
                )
            }
        })
    }

    fun play(result: ExtractionResult.Success) {
        val metadata = MediaMetadata.Builder()
            .setTitle(result.title)
            .setArtist(result.channelName)
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(result.mediaUrl)
            .setMimeType(result.mimeType)
            .setMediaMetadata(metadata)
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    fun pause() {
        player.playWhenReady = false
    }

    fun resume() {
        player.playWhenReady = true
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
    }

    fun release() {
        player.release()
    }
}
