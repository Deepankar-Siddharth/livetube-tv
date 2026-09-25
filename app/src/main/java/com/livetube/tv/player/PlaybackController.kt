package com.livetube.tv.player

import com.livetube.tv.data.Channel
import com.livetube.tv.extractor.ExtractionResult
import com.livetube.tv.extractor.YouTubeExtractor
import com.livetube.tv.util.Constants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

/** Coordinates extraction, Media3, and bounded automatic recovery. */
class PlaybackController(
    private val extractor: YouTubeExtractor,
    private val playerManager: PlayerManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var currentChannel: Channel? = null
    private var selectionGeneration = 0L
    private var extractionJob: Job? = null
    private var retryJob: Job? = null
    private var retryAttempt = 0
    private var hasRenderedFrames = false

    init {
        scope.launch {
            playerManager.events.collectLatest { event ->
                when (event) {
                    is PlayerEvent.StateChanged -> handleState(event.phase)
                    is PlayerEvent.Error -> handlePlayerError()
                }
            }
        }
    }

    fun select(channel: Channel) {
        if (currentChannel?.id == channel.id &&
            _state.value.phase !in setOf(PlaybackPhase.ERROR, PlaybackPhase.IDLE)
        ) {
            return
        }
        currentChannel = channel
        selectionGeneration += 1
        val generation = selectionGeneration
        retryAttempt = 0
        hasRenderedFrames = false
        retryJob?.cancel()
        extractionJob?.cancel()
        // Stop the previous item before starting a new extraction. This prevents a late
        // player error from an old channel from being attributed to the new selection.
        playerManager.stop()
        extractionJob = scope.launch { extractAndMaybeSchedule(channel, generation) }
    }

    fun retryNow() {
        val channel = currentChannel ?: return
        selectionGeneration += 1
        val generation = selectionGeneration
        retryJob?.cancel()
        extractionJob?.cancel()
        retryAttempt = 0
        hasRenderedFrames = false
        playerManager.stop()
        extractionJob = scope.launch { extractAndMaybeSchedule(channel, generation) }
    }

    fun pause() = playerManager.pause()

    fun resume() = playerManager.resume()

    fun release() {
        selectionGeneration += 1
        retryJob?.cancel()
        extractionJob?.cancel()
        scope.coroutineContext.cancel()
        playerManager.release()
    }

    private suspend fun extractAndMaybeSchedule(channel: Channel, generation: Long) {
        if (!currentCoroutineContext().isActive || !isCurrentSelection(channel, generation)) return
        _state.value = PlaybackState(
            phase = PlaybackPhase.EXTRACTING,
            channel = channel,
            message = "Finding the current live stream…",
            canRetry = true,
        )
        val result = extractor.extract(channel)
        // Extraction libraries can finish a blocking request after their coroutine has
        // been cancelled. Re-check the generation before publishing or playing its URL.
        if (!currentCoroutineContext().isActive || !isCurrentSelection(channel, generation)) return

        when (result) {
            is ExtractionResult.Success -> {
                _state.value = _state.value.copy(
                    phase = PlaybackPhase.BUFFERING,
                    resolutionHeight = result.resolutionHeight,
                    isLive = result.isLive,
                    message = "Buffering live video…",
                    retryAttempt = retryAttempt,
                    retryInSeconds = null,
                    canRetry = true,
                    lastFailure = null,
                )
                try {
                    playerManager.play(result)
                } catch (error: Exception) {
                    scheduleRetry(
                        channel,
                        generation,
                        ExtractionResult.Failure(
                            reason = ExtractionResult.FailureReason.EXTRACTION_FAILED,
                            userMessage = "The media player could not start this stream.",
                        ),
                    )
                } catch (_: LinkageError) {
                    scheduleRetry(
                        channel,
                        generation,
                        ExtractionResult.Failure(
                            reason = ExtractionResult.FailureReason.EXTRACTION_FAILED,
                            userMessage = "This device is missing a required media playback component.",
                        ),
                    )
                }
            }

            is ExtractionResult.Failure -> scheduleRetry(channel, generation, result)
        }
    }

    private fun scheduleRetry(
        channel: Channel,
        generation: Long,
        failure: ExtractionResult.Failure,
    ) {
        if (!isCurrentSelection(channel, generation)) return
        if (retryAttempt >= Constants.RETRY_DELAYS_SECONDS.size) {
            _state.value = _state.value.copy(
                phase = PlaybackPhase.ERROR,
                message = failure.userMessage,
                canRetry = true,
                retryInSeconds = null,
                lastFailure = failure,
            )
            return
        }
        val seconds = retryDelaySeconds(retryAttempt) ?: return
        retryAttempt += 1
        _state.value = _state.value.copy(
            phase = PlaybackPhase.ERROR,
            message = "Retrying in $seconds seconds…",
            retryAttempt = retryAttempt,
            retryInSeconds = seconds,
            canRetry = true,
            lastFailure = failure,
        )
        retryJob?.cancel()
        retryJob = scope.launch {
            try {
                delay(seconds * 1_000L)
                if (isCurrentSelection(channel, generation)) {
                    extractionJob = scope.launch { extractAndMaybeSchedule(channel, generation) }
                }
            } catch (_: CancellationException) {
                // Selecting another channel cancels the old recovery schedule.
            }
        }
    }

    private fun isCurrentSelection(channel: Channel, generation: Long): Boolean =
        currentChannel?.id == channel.id && selectionGeneration == generation

    private fun handleState(phase: PlaybackPhase) {
        val current = _state.value
        when (phase) {
            PlaybackPhase.BUFFERING -> _state.value = current.copy(
                phase = PlaybackPhase.BUFFERING,
                message = "Buffering live video…",
                canRetry = true,
            )

            PlaybackPhase.PLAYING -> {
                if (!hasRenderedFrames) {
                    hasRenderedFrames = true
                    retryAttempt = 0
                }
                _state.value = current.copy(
                    phase = PlaybackPhase.PLAYING,
                    message = null,
                    retryAttempt = 0,
                    retryInSeconds = null,
                    canRetry = true,
                    lastFailure = null,
                )
            }

            PlaybackPhase.PAUSED -> _state.value = current.copy(phase = PlaybackPhase.PAUSED)
            PlaybackPhase.ENDED -> _state.value = current.copy(
                phase = PlaybackPhase.ENDED,
                message = "The live broadcast has ended.",
                canRetry = true,
            )

            PlaybackPhase.ERROR -> handlePlayerError()
            PlaybackPhase.IDLE,
            PlaybackPhase.EXTRACTING,
            -> Unit
        }
    }

    private fun handlePlayerError() {
        val channel = currentChannel ?: return
        val generation = selectionGeneration
        val failure = ExtractionResult.Failure(
            reason = ExtractionResult.FailureReason.EXTRACTION_FAILED,
            userMessage = "Playback stopped. A fresh stream will be requested.",
        )
        playerManager.stop()
        scheduleRetry(channel, generation, failure)
    }

    companion object {
        fun retryDelaySeconds(attempt: Int): Long? = Constants.RETRY_DELAYS_SECONDS.getOrNull(attempt)
    }
}
