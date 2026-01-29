package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_bridge.AdsrEnvelope

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

    class Vibrato(
        val rate: Double,
        val depth: Double,
        var phase: Double = 0.0,
    )

    // Pitch / Glisando
    class Accelerate(
        val amount: Double,
    )

    class PitchEnvelope(
        val attackFrames: Double,
        val decayFrames: Double,
        val releaseFrames: Double,
        val amount: Double, // In semitones
        val curve: Double,
        val anchor: Double, // 0.0 .. 1.0 (usually 0.0)
    )

    class Envelope(
        val attackFrames: Double,
        val decayFrames: Double,
        val sustainLevel: Double,
        val releaseFrames: Double,
        var level: Double = 0.0,
    ) {
        companion object {
            fun of(adsr: AdsrEnvelope.Resolved, sampleRate: Int) = Envelope(
                attackFrames = adsr.attack * sampleRate,
                decayFrames = adsr.decay * sampleRate,
                sustainLevel = adsr.sustain,
                releaseFrames = adsr.release * sampleRate
            )
        }
    }

    class FilterModulator(
        val filter: AudioFilter.Tunable,
        val envelope: Envelope,
        val depth: Double,
        val baseCutoff: Double,
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
        /** reverb fade time (feedback) */
        val roomFade: Double? = null,
        /** lowpass filter cutoff */
        val roomLp: Double? = null,
        /** dim frequency (currently unused in simple model) */
        val roomDim: Double? = null,
        /** impulse response name */
        val iResponse: String? = null,
    )

    class Phaser(
        val rate: Double,
        val depth: Double,
        val center: Double,
        val sweep: Double,
    )

    class Tremolo(
        val rate: Double,
        val depth: Double,
        val skew: Double,
        val phase: Double,
        val shape: String?,
        // State
        var currentPhase: Double = 0.0,
    )

    class Ducking(
        val orbitId: Int,
        val attackSeconds: Double,
        val depth: Double,
    )

    class Compressor(
        val thresholdDb: Double,
        val ratio: Double,
        val kneeDb: Double,
        val attackSeconds: Double,
        val releaseSeconds: Double,
    )

    class Distort(
        val amount: Double,
    )

    class Crush(
        val amount: Double,
    )

    class Coarse(
        val amount: Double,
        var lastCoarseValue: Double = 0.0,
        var coarseCounter: Double = 0.0,
    )

    // Timing
    val startFrame: Long
    val endFrame: Long
    val gateEndFrame: Long

    // Routing
    val orbitId: Int

    // Dynamics
    val gain: Double
    val pan: Double
    val postGain: Double

    // Pitch
    val accelerate: Accelerate
    val vibrato: Vibrato
    val pitchEnvelope: PitchEnvelope?

    // Audio processing
    val filter: AudioFilter
    val envelope: Envelope
    val filterModulators: List<FilterModulator>
    val delay: Delay
    val reverb: Reverb
    val phaser: Phaser
    val tremolo: Tremolo
    val ducking: Ducking?
    val compressor: Compressor?

    // global effects
    val distort: Distort
    val crush: Crush
    val coarse: Coarse

    /**
     * Renders the voice into the context's buffers.
     * Returns true if the voice is still active, false if it finished.
     */
    fun render(ctx: RenderContext): Boolean
}
