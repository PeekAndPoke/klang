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

        val modBuffer = fillPitchModulation(ctx, offset, length)

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
