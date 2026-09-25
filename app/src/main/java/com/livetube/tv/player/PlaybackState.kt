package com.livetube.tv.player

import com.livetube.tv.data.Channel
import com.livetube.tv.extractor.ExtractionResult

enum class PlaybackPhase {
    IDLE,
    EXTRACTING,
    BUFFERING,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR,
}

data class PlaybackState(
    val phase: PlaybackPhase = PlaybackPhase.IDLE,
    val channel: Channel? = null,
    val resolutionHeight: Int? = null,
    val isLive: Boolean = false,
    val message: String? = null,
    val retryAttempt: Int = 0,
    val retryInSeconds: Long? = null,
    val canRetry: Boolean = false,
    val lastFailure: ExtractionResult.Failure? = null,
)
