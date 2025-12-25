package io.peekandpoke.klang.dsp.voice

import io.peekandpoke.klang.dsp.OscFn
import io.peekandpoke.klang.dsp.filters.AudioFilter

class SynthVoice(
    override val orbitId: Int,
    override val startFrame: Long,
    override val endFrame: Long,
    override val gateEndFrame: Long,
    override val gain: Double,
    override val pan: Double,
    override val filter: AudioFilter,
    override val envelope: Voice.Envelope,
    override val delay: Voice.Delay,
    override val reverb: Voice.Reverb,
    override val vibrator: Voice.Vibrator,
    override val effects: Voice.Effects,
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

        val modBuffer = fillVibrato(ctx, offset, length)

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
