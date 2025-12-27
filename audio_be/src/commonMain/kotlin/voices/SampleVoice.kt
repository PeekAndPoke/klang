package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm

class SampleVoice(
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
    val sample: MonoSamplePcm,
    val rate: Double,
    var playhead: Double = 0.0,
) : Voice {
    override fun render(ctx: Voice.RenderContext): Boolean {
        val blockEnd = ctx.blockStart + ctx.blockFrames
        if (ctx.blockStart >= endFrame) return false
        if (blockEnd <= startFrame) return true

        val vStart = maxOf(ctx.blockStart, startFrame)
        val vEnd = minOf(blockEnd, endFrame)
        val offset = (vStart - ctx.blockStart).toInt()
        val length = (vEnd - vStart).toInt()

        val modBuffer = fillVibrato(ctx, offset, length)
        val pcm = sample.pcm
        val pcmMax = pcm.size - 1
        val out = ctx.voiceBuffer

        // Resample / Interpolate
        var ph = playhead
        for (i in 0 until length) {
            val idxOut = offset + i
            val base = ph.toInt()

            if (base >= pcmMax) {
                out[idxOut] = 0.0
            } else {
                val frac = ph - base.toDouble()
                val a = pcm[base]
                val b = pcm[base + 1]
                out[idxOut] = a + (b - a) * frac
            }

            ph += if (modBuffer != null) rate * modBuffer[idxOut] else rate
        }
        playhead = ph

        // Filter
        filter.process(ctx.voiceBuffer, offset, length)

        // Mix
        mixToOrbit(ctx, offset, length)

        return true
    }
}
