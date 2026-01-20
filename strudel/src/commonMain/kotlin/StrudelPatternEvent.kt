package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.script.ast.SourceLocationChain
import io.peekandpoke.klang.strudel.math.Rational
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Voice Data used to create a Voice.
 */
@Serializable
data class StrudelPatternEvent(
    /** The begin of the note (in cycles) */
    val begin: Rational,
    /** The end of the note (in cycles) */
    val end: Rational,
    /** The duration of the note (in cycles) */
    val dur: Rational,
    /** The voice data */
    val data: VoiceData,
    /**
     * Source location chain for live code highlighting
     *
     * Tracks the transformation path from call site -> string literal -> atom.
     * Not included in serialization as it's only used for live-coding features.
     */
    @Transient
    val sourceLocations: SourceLocationChain? = null,
)

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
    val data: VoiceData,
    /** Source location chain for highlighting */
    val sourceLocations: SourceLocationChain?,
) {
    /** Start time in milliseconds */
    val startTimeMs get() = (startTime * 1000).toLong()

    /** End time in milliseconds */
    val endTimeMs get() = (endTime * 1000).toLong()
}
