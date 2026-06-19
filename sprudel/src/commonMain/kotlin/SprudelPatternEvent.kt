package io.peekandpoke.klang.sprudel

import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.audio_bridge.SoundValue
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.common.SourceLocationChain
import io.peekandpoke.klang.common.math.CycleTimeSpan

/**
 * Voice Data used to create a Voice.
 */
data class SprudelPatternEvent(
    /** Visible portion after clipping */
    val part: CycleTimeSpan,
    /** Original complete even */
    val whole: CycleTimeSpan,
    /** The voice data */
    val data: SprudelVoiceData,
    /**
     * Source location chain for live code highlighting
     *
     * Tracks the transformation path from call site -> string literal -> atom.
     * Not included in serialization as it's only used for live-coding features.
     */
    override val sourceLocations: SourceLocationChain? = null,
) : KlangPatternEvent {

    override val startCycles: Double get() = whole.begin.toCycles()
    override val durationCycles: Double get() = whole.duration.toCycles()
    override val sound: SoundValue? get() = data.sound
    override fun toVoiceData(): VoiceData = data.toVoiceData()

    /**
     * Check if this event is an onset event (should be played).
     * With fixed-point [io.peekandpoke.klang.common.math.CycleTime] ticks, onset detection is an
     * exact integer comparison — no floating-point epsilon needed.
     */
    val isOnset: Boolean = whole.isValid && whole.begin.ticks == part.begin.ticks

    fun prependLocation(location: SourceLocation?) = when (location) {
        null -> this
        else -> copy(sourceLocations = sourceLocations?.prepend(location) ?: location.asChain())
    }

    fun prependLocations(locations: SourceLocationChain?) = when (locations) {
        null -> this
        else -> copy(sourceLocations = sourceLocations?.prepend(locations.locations) ?: locations)
    }

    fun appendLocation(location: SourceLocation?) = when (location) {
        null -> this
        else -> copy(sourceLocations = sourceLocations?.append(location) ?: location.asChain())
    }

    fun appendLocations(locations: SourceLocationChain?) = when (locations) {
        null -> this
        else -> copy(sourceLocations = sourceLocations?.append(locations.locations) ?: locations)
    }
}
