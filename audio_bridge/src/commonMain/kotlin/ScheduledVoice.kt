package io.peekandpoke.klang.audio_bridge

import kotlinx.serialization.Serializable

/**
 * Use to schedule the playback of a voice
 */
@Serializable
data class ScheduledVoice(
    /** The event that triggered this voice */
    val data: VoiceData,
    /** Time in seconds to start the voice */
    val startTime: Double,
    /** Time in seconds when the note key is lifted (Start + Duration) */
    val gateEndTime: Double,
)
