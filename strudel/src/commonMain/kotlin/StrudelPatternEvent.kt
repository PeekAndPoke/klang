package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlinx.serialization.Serializable

/**
 * Voice Data used to create a Voice.
 */
@Serializable
data class StrudelPatternEvent(
    /** The begin of the note (in cycles) */
    val begin: Double,
    /** The end of the note (in cycles) */
    val end: Double,
    /** The duration of the note (in cycles) */
    val dur: Double,
    /** The voice data */
    val data: VoiceData,
)
