package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.osci.OscFn
import io.peekandpoke.klang.audio_be.voices.Voice.Phaser
import io.peekandpoke.klang.audio_be.voices.Voice.Tremolo

class SynthVoice(
    override val orbitId: Int,
    override val startFrame: Long,
    override val endFrame: Long,
    override val gateEndFrame: Long,
    override val gain: Double,
    override val pan: Double,
    override val postGain: Double,
    override val accelerate: Voice.Accelerate,
    override val vibrato: Voice.Vibrato,
    override val pitchEnvelope: Voice.PitchEnvelope?,
    override val filter: AudioFilter,
    override val envelope: Voice.Envelope,
    override val filterModulators: List<Voice.FilterModulator>,
    override val delay: Voice.Delay,
    override val reverb: Voice.Reverb,
    override val phaser: Phaser,
    override val tremolo: Tremolo,
    override val ducking: Voice.Ducking?,
    override val compressor: Voice.Compressor?,
    override val distort: Voice.Distort,
    override val crush: Voice.Crush,
    override val coarse: Voice.Coarse,
    val osc: OscFn,
    override val fm: Voice.Fm?,
    val freqHz: Double,
    val phaseInc: Double,
    var phase: Double = 0.0,
) : Voice {

    override fun render(ctx: Voice.RenderContext): Boolean {
        val blockEnd = ctx.blockStart + ctx.blockFrames
        // Lifecycle check
        if (ctx.blockStart >= endFrame) return false
        if (blockEnd <= startFrame) return true

        val vStart = maxOf(ctx.blockStart, startFrame)
        val vEnd = minOf(blockEnd, endFrame)
        val offset = (vStart - ctx.blockStart).toInt()
        val length = (vEnd - vStart).toInt()

        // Apply filter modulation (control rate - once per block)
        for (mod in filterModulators) {
            val envValue = calculateFilterEnvelope(mod.envelope, ctx)
            val newCutoff = mod.baseCutoff * (1.0 + mod.depth * envValue)
            mod.filter.setCutoff(newCutoff)
        }

        // 1. Calculate base pitch modulation (Vibrato, Pitch Env, Slide)
        // returns a reusable buffer with frequency multipliers (nominal 1.0)
        // If null, it means no pitch modulation is active.
        var modBuffer = fillPitchModulation(ctx, offset, length)

        // 2. Apply FM (Frequency Modulation)
        // We inject this into the modBuffer (creating it if necessary)
        if (fm != null && fm.depth != 0.0) {
            val buf = modBuffer ?: ctx.freqModBuffer
            // If modBuffer was null, we must initialize it to 1.0s before multiplying FM
            if (modBuffer == null) {
                for (i in 0 until length) buf[offset + i] = 1.0
                modBuffer = buf
            }

            // Calculate Modulator Parameters
            val modFreq = freqHz * fm.ratio
            val modInc = (io.peekandpoke.klang.audio_be.TWO_PI * modFreq) / ctx.sampleRate
            var modPhase = fm.modPhase

            // Calculate FM Envelope for this block
            val envLevel = calculateFilterEnvelope(fm.envelope, ctx)
            val effectiveDepth = fm.depth * envLevel

            for (i in 0 until length) {
                // Modulator Oscillator (Sine)
                val modSignal = kotlin.math.sin(modPhase) * effectiveDepth
                modPhase += modInc

                // Apply modulation: F_new = F_carrier + modSignal
                // Multiplier = (F_carrier + modSignal) / F_carrier = 1 + modSignal / F_carrier
                val fmMult = 1.0 + (modSignal / freqHz)

                // Apply to existing pitch modulation
                buf[offset + i] *= fmMult
            }
            // Save state
            fm.modPhase = modPhase
        }

        // Generate
        phase = osc.process(
            buffer = ctx.voiceBuffer,
            offset = offset,
            length = length,
            phase = phase,
            phaseInc = phaseInc,
            phaseMod = modBuffer
        )

        // Filter
        filter.process(ctx.voiceBuffer, offset, length)

        // Mix
        mixToOrbit(ctx, offset, length)

        return true
    }
}
