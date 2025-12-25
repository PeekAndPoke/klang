package io.peekandpoke.klang.strudel

/**
 * Use to schedule the playback of a voice
 */
data class StrudelScheduledVoice(
    /** The event that triggered this voice */
    val evt: StrudelPatternEvent,
    /** Audio frame to start the voice */
    val startFrame: Long,
    /** Audio frame to stop the voice */
    val endFrame: Long,
    /** Frame when the note key is lifted */
    val gateEndFrame: Long,
)
