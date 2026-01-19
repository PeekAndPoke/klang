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
