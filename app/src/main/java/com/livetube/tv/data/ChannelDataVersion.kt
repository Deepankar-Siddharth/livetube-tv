package com.livetube.tv.data

/**
 * What should happen to a freshly downloaded document compared with the active one.
 *
 * `data_version` is the only authority: a higher version is a candidate that still has to pass
 * validation, an equal version means "already current", and a lower version never replaces a
 * newer local document.
 */
enum class ChannelDataVersionDecision {
    /** Remote data_version is higher: validate, then activate. */
    UPDATE,

    /** Same data_version: keep the active document. */
    CURRENT,

    /** Remote data_version is lower: never downgrade automatically. */
    DOWNGRADE,

    /** The remote document uses a schema this build cannot interpret. */
    INCOMPATIBLE,
}

object ChannelDataVersion {

    fun decide(
        remote: ChannelDocument,
        local: ChannelDocument?,
    ): ChannelDataVersionDecision {
        if (remote.schemaVersion > JsonUtils.SCHEMA_VERSION) return ChannelDataVersionDecision.INCOMPATIBLE
        if (local == null) return ChannelDataVersionDecision.UPDATE
        return when {
            remote.dataVersion > local.dataVersion -> ChannelDataVersionDecision.UPDATE
            remote.dataVersion == local.dataVersion -> ChannelDataVersionDecision.CURRENT
            else -> ChannelDataVersionDecision.DOWNGRADE
        }
    }

    /**
     * True when the document is newer than [other] by the authoritative rule.
     * `updated_at` and file timestamps are deliberately not part of the decision.
     */
    fun isNewer(remote: ChannelDocument, other: ChannelDocument?): Boolean =
        decide(remote, other) == ChannelDataVersionDecision.UPDATE
}
