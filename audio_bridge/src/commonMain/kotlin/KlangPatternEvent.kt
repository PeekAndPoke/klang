package io.peekandpoke.klang.audio_bridge

import io.peekandpoke.klang.common.SourceLocationChain

/**
 * Common interface for pattern events that can be scheduled by the playback controller.
 *
 * Each pattern implementation (Strudel, sequencer, MIDI, etc.) provides its own event type
 * implementing this interface. The scheduling engine only needs these properties to convert
 * events into [ScheduledVoice] instances for the audio backend.
 */
interface KlangPatternEvent {
    /** Event start time in cycles (from pattern start) */
    val startCycles: Double

    /** Event duration in cycles */
    val durationCycles: Double

    /** Source locations for code highlighting */
    val sourceLocations: SourceLocationChain?

    /** Convert to engine-level voice data */
    fun toVoiceData(): VoiceData
}
