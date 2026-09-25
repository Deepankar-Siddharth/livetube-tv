package com.livetube.tv.extractor

/** Metadata and a short-lived media URL produced by a fresh extraction. */
sealed interface ExtractionResult {
    data class Success(
        val mediaUrl: String,
        val mimeType: String,
        val title: String,
        val channelName: String,
        val resolutionHeight: Int?,
        val isLive: Boolean,
    ) : ExtractionResult

    enum class FailureReason {
        INVALID_CHANNEL,
        NO_LIVE_BROADCAST,
        EXTRACTION_FAILED,
        NETWORK_FAILED,
    }

    data class Failure(
        val reason: FailureReason,
        val userMessage: String,
    ) : ExtractionResult
}
