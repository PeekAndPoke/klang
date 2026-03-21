package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.signalgen.SignalContext
import io.peekandpoke.klang.audio_be.signalgen.SignalGen

/**
 * Synthesizer voice - generates audio from a [SignalGen] tree.
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
    /** SignalGen tree for waveform generation */
    private val signal: SignalGen,
    /** Per-voice rendering context for the SignalGen tree */
    private val signalCtx: SignalContext,
    /** Base frequency in Hz */
    private val freqHz: Double,
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
        signalCtx.offset = offset
        signalCtx.length = length
        signalCtx.voiceElapsedFrames = (ctx.blockStart - startFrame).toInt()
        signalCtx.phaseMod = pitchMod

        signal.generate(ctx.voiceBuffer, freqHz, signalCtx)
    }
}
