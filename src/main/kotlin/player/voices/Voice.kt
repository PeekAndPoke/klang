package io.peekandpoke.player.voices

import io.peekandpoke.dsp.AudioFilter
import io.peekandpoke.player.orbits.Orbits

sealed interface Voice {
    class RenderContext(
        val orbits: Orbits,
        /** Sample rate in Hz */
        val sampleRate: Int,
        /** Number of frames per block */
        val blockFrames: Int,
        /** Shared scratchpad for audio */
        val voiceBuffer: DoubleArray,
        /** Shared scratchpad for frequency modulation */
        val freqModBuffer: DoubleArray,
    ) {
        // Mutable fields updated per block
        var blockStart: Long = 0
    }

    class Envelope(
        val attackFrames: Double,
        val decayFrames: Double,
        val sustainLevel: Double,
        val releaseFrames: Double,
        var level: Double = 0.0,
    )

    class Delay(
        val amount: Double, // mix amount 0 .. 1
        val time: Double,
        val feedback: Double,
    )

    class Reverb(
        /** mix amount 0 .. 1 */
        val room: Double,
        /** room size 0 .. 1 */
        val roomSize: Double,
    )

    class Vibrator(
        val rate: Double,
        val depth: Double,
        var phase: Double = 0.0,
    )

    // Timing
    val startFrame: Long
    val endFrame: Long
    val gateEndFrame: Long

    // Routing
    val orbitId: Int
    val gain: Double
    val pan: Double

    // Audio processing
    val filter: AudioFilter
    val envelope: Envelope
    val delay: Delay
    val reverb: Reverb
    val vibrator: Vibrator

    /**
     * Renders the voice into the context's buffers.
     * Returns true if the voice is still active, false if it finished.
     */
    fun render(ctx: RenderContext): Boolean
}
