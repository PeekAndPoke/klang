package io.peekandpoke.klang.audio_fe

import io.peekandpoke.klang.audio_bridge.ScheduledVoice

/**
 * Source of events that can be queried by time (in cycles).
 */
interface KlangEventSource {
    /**
     * Query events within a specific time range [from] (inclusive) to [to] (exclusive).
     * The implementation must return events sorted by start time.
     */
    fun query(from: Double, to: Double): List<ScheduledVoice>
}
