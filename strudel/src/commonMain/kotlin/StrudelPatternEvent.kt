package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.math.Rational
import kotlinx.serialization.Serializable

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
)
