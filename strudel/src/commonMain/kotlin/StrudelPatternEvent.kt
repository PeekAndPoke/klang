package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.script.ast.SourceLocation
import io.peekandpoke.klang.script.ast.SourceLocationChain
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Voice Data used to create a Voice.
 */
@Serializable
data class StrudelPatternEvent(
    /** Visible portion after clipping */
    val part: TimeSpan,
    /** Original complete event (null for continuous patterns) */
    val whole: TimeSpan,
    /** The voice data */
    val data: StrudelVoiceData,
    /**
     * Source location chain for live code highlighting
     *
     * Tracks the transformation path from call site -> string literal -> atom.
     * Not included in serialization as it's only used for live-coding features.
     */
    @Transient
    val sourceLocations: SourceLocationChain? = null,
) {
    /** Check if this event has an onset (should be played) */
    fun hasOnset(): Boolean = whole.begin == part.begin

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

/**
 * Event fired when a voice is scheduled for playback
 *
 * Used for live code highlighting - provides timing and source location information.
 * Not serialized - only used for frontend callbacks.
 */
data class ScheduledVoiceEvent(
    /** Absolute start time (seconds from KlangTime epoch) */
    val startTime: Double,
    /** Absolute end time (seconds from KlangTime epoch) */
    val endTime: Double,
    /** The voice data being played */
    val data: StrudelVoiceData,
    /** Source location chain for highlighting */
    val sourceLocations: SourceLocationChain?,
) {
    /** Start time in milliseconds */
    val startTimeMs get() = (startTime * 1000).toLong()

    /** End time in milliseconds */
    val endTimeMs get() = (endTime * 1000).toLong()
}
