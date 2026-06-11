package io.peekandpoke.klang.audio_bridge


/**
 * Use to schedule the playback of a voice
 */
@WireFormat
data class ScheduledVoice(
    /** The ID of the playback (song) this voice belongs to */
    val playbackId: String,
    /** The event that triggered this voice */
    val data: VoiceData,
    /** Time in seconds relative to playback start */
    val startTime: Double,
    /** Time in seconds relative to playback start when the note key is lifted */
    val gateEndTime: Double,
    /** Frontend's playback start time in seconds (for epoch anchoring) */
    val playbackStartTime: Double,
)
