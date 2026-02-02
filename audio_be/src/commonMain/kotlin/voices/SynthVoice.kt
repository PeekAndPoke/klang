package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.osci.OscFn

/**
 * Synthesizer voice - generates audio from oscillators.
 * Extends AbstractVoice and only implements the signal generation logic.
 */
class SynthVoice(
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

    // SynthVoice-Specific Parameters
    /** Oscillator function for waveform generation */
    private val osc: OscFn,
    /** Base frequency in Hz */
    private val freqHz: Double,
    /** Phase increment per sample */
    private val phaseInc: Double,
    /** Current oscillator phase */
    private var phase: Double = 0.0,
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

    override fun getBaseFrequency(): Double = freqHz

    override fun generateSignal(
        ctx: Voice.RenderContext,
        offset: Int,
        length: Int,
        pitchMod: DoubleArray?,
    ) {
        // Generate oscillator output with pitch modulation
        phase = osc.process(
            buffer = ctx.voiceBuffer,
            offset = offset,
            length = length,
            phase = phase,
            phaseInc = phaseInc,
            phaseMod = pitchMod
        )
    }
}
