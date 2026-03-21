package io.peekandpoke.klang.sprudel

import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.common.SourceLocationChain
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.abs

/**
 * Voice Data used to create a Voice.
 */
@Serializable
data class SprudelPatternEvent(
    /** Visible portion after clipping */
    val part: TimeSpan,
    /** Original complete even */
    val whole: TimeSpan,
    /** The voice data */
    val data: SprudelVoiceData,
    /**
     * Source location chain for live code highlighting
     *
     * Tracks the transformation path from call site -> string literal -> atom.
     * Not included in serialization as it's only used for live-coding features.
     */
    @Transient
    override val sourceLocations: SourceLocationChain? = null,
) : KlangPatternEvent {

    override val startCycles: Double get() = whole.begin.toDouble()
    override val durationCycles: Double get() = whole.duration.toDouble()
    override fun toVoiceData(): VoiceData = data.toVoiceData()

    /** Check if this event is an onset event (should be played) */
    val isOnset: Boolean = whole.isValid && abs(whole.begin.toDouble() - part.begin.toDouble()) < ONSET_EPSILON

    companion object {
        /** Epsilon for onset detection to handle floating-point precision issues */
        private const val ONSET_EPSILON = 1e-6
    }

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
