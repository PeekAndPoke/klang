package io.peekandpoke.klang.audio_bridge

import kotlinx.serialization.Serializable

/**
 * Use to schedule the playback of a voice
 */
@Serializable
data class ScheduledVoice(
    /** The event that triggered this voice */
    val data: VoiceData,
    /** Audio frame to start the voice */
    val startFrame: Long,
    /** Audio frame to stop the voice */
    val endFrame: Long,
    /** Frame when the note key is lifted */
    val gateEndFrame: Long,
)
