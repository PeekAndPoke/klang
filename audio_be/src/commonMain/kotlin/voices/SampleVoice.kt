package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm

/**
 * Sample voice - plays back pre-recorded audio samples.
 * Extends AbstractVoice and only implements the sample playback logic.
 */
class SampleVoice(
    // Lifecycle & Routing
    startFrame: Long,
    endFrame: Long,
    gateEndFrame: Long,
    orbitId: Int,

    // Synthesis & Pitch
    fm: Voice.Fm?,
    accelerate: Voice.Accelerate,
    vibrato: Voice.Vibrato,
    pitchEnvelope: Voice.PitchEnvelope?,

    // Dynamics
    gain: Double,
    pan: Double,
    postGain: Double,
    envelope: Voice.Envelope,
    compressor: Voice.Compressor?,
    ducking: Voice.Ducking?,

    // Filters & Modulation
    filter: AudioFilter,
    filterModulators: List<Voice.FilterModulator>,

    // Time-Based Effects
    delay: Voice.Delay,
    reverb: Voice.Reverb,

    // Raw Effect Data
    phaser: Voice.Phaser,
    tremolo: Voice.Tremolo,
    distort: Voice.Distort,
    crush: Voice.Crush,
    coarse: Voice.Coarse,

    // SampleVoice-Specific Parameters
    /** Sample playback configuration (looping, cut groups, etc.) */
    internal val samplePlayback: SamplePlayback,
    /** Sample PCM data to play */
    private val sample: MonoSamplePcm,
    /** Playback rate multiplier (includes pitch shifting) */
    private val rate: Double,
    /** Current playhead position in samples */
    private var playhead: Double,
) : AbstractVoice(
    startFrame = startFrame,
    endFrame = endFrame,
    gateEndFrame = gateEndFrame,
    orbitId = orbitId,
    fm = fm,
    accelerate = accelerate,
    vibrato = vibrato,
    pitchEnvelope = pitchEnvelope,
    gain = gain,
    pan = pan,
    postGain = postGain,
    envelope = envelope,
    compressor = compressor,
    ducking = ducking,
    filter = filter,
    filterModulators = filterModulators,
    delay = delay,
    reverb = reverb,
    phaser = phaser,
    tremolo = tremolo,
    distort = distort,
    crush = crush,
    coarse = coarse
) {

    /**
     * Sample playback configuration.
     * Controls looping behavior, cut groups, and playback boundaries.
     */
    class SamplePlayback(
        /** Cut group ID (voices in same group cut each other) */
        val cut: Int?,
        /** Whether explicit looping is enabled */
        val explicitLooping: Boolean,
        /** Loop start point in samples */
        val explicitLoopStart: Double,
        /** Loop end point in samples */
        val explicitLoopEnd: Double,
        /** Stop frame (for .end() slicing) */
        val stopFrame: Double,
    ) {
        companion object {
            val default = SamplePlayback(
                cut = null,
                explicitLooping = false,
                explicitLoopStart = -1.0,
                explicitLoopEnd = -1.0,
                stopFrame = Double.MAX_VALUE
            )
        }
    }

    // Pre-calculate loop points
    private val loopStart = if (samplePlayback.explicitLooping) {
        samplePlayback.explicitLoopStart
    } else {
        sample.meta.loop?.start?.toDouble() ?: -1.0
    }

    private val loopEnd = if (samplePlayback.explicitLooping) {
        samplePlayback.explicitLoopEnd
    } else {
        sample.meta.loop?.end?.toDouble() ?: -1.0
    }

    private val isLooping = loopStart >= 0.0 && loopEnd > loopStart

    // Base frequency for FM (use sample's base pitch)
    private val basePitchHz = sample.meta.let {
        // If sample has a base pitch, use it, otherwise default to 440 Hz
        440.0 // This would ideally come from sample metadata
    }

    override fun getBaseFrequency(): Double = basePitchHz

    override fun generateSignal(
        ctx: Voice.RenderContext,
        offset: Int,
        length: Int,
        pitchMod: DoubleArray?,
    ) {
        val pcm = sample.pcm
        val pcmMax = pcm.size - 1
        val out = ctx.voiceBuffer

        // Generate sample output with pitch modulation
        var ph = playhead
        for (i in 0 until length) {
            val idxOut = offset + i

            // Loop Logic
            if (isLooping && ph >= loopEnd) {
                ph = loopStart + (ph - loopEnd)
            }

            // Check strict end
            if (ph >= samplePlayback.stopFrame) {
                out[idxOut] = 0.0
            } else {
                // Read Sample with linear interpolation
                val base = ph.toInt()

                if (base >= pcmMax) {
                    out[idxOut] = 0.0
                } else if (base >= 0) {
                    val frac = ph - base.toDouble()
                    val a = pcm[base]
                    val b = pcm[base + 1]
                    val sampleValue = a + (b - a) * frac

                    out[idxOut] = sampleValue
                }
            }

            // Advance Playhead with pitch modulation
            ph += if (pitchMod != null) rate * pitchMod[idxOut] else rate
        }

        playhead = ph
    }
}
