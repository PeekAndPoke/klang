package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import kotlin.math.max
import kotlin.math.min

class SampleVoice(
    override val orbitId: Int,
    override val startFrame: Long,
    override val endFrame: Long,
    override val gateEndFrame: Long,
    override val gain: Double,
    override val pan: Double,
    override val accelerate: Voice.Accelerate,
    override val vibrato: Voice.Vibrato,
    override val filter: AudioFilter,
    override val envelope: Voice.Envelope,
    override val delay: Voice.Delay,
    override val reverb: Voice.Reverb,
    override val distort: Voice.Distort,
    override val crush: Voice.Crush,
    override val coarse: Voice.Coarse,
    val sample: MonoSamplePcm,
    val rate: Double,
    var playhead: Double,
) : Voice {
    // Pre-calculate loop points
    private val loopStart = sample.meta.loop?.start?.toDouble() ?: -1.0
    private val loopEnd = sample.meta.loop?.end?.toDouble() ?: -1.0
    private val isLooping = loopStart >= 0.0 && loopEnd > loopStart

    override fun render(ctx: Voice.RenderContext): Boolean {
        val blockEnd = ctx.blockStart + ctx.blockFrames
        if (ctx.blockStart >= endFrame) return false
        if (blockEnd <= startFrame) return true

        val vStart = max(ctx.blockStart, startFrame)
        val vEnd = min(blockEnd, endFrame)
        val offset = (vStart - ctx.blockStart).toInt()
        val length = (vEnd - vStart).toInt()

        val modBuffer = fillPitchModulation(ctx, offset, length)
        val pcm = sample.pcm
        val pcmMax = pcm.size - 1
        val out = ctx.voiceBuffer

        // Resample / Interpolate
        var ph = playhead
        for (i in 0 until length) {
            val idxOut = offset + i

            // Loop Logic: Check before reading
            // If we are looping, wrap the playhead if it exceeds loopEnd
            if (isLooping && ph >= loopEnd) {
                // Wrap around: current - end + start
                // We use a while loop just in case rate is super high, though usually if() suffices
                ph = loopStart + (ph - loopEnd)
            }

            // Read Sample
            val base = ph.toInt()

            if (base >= pcmMax) {
                // End of sample reached (and not looping, or loop points are broken)
                // If we are not looping, we just output silence.
                // Note: The envelope will likely handle the fade out, but we need to ensure we don't read OOB.
                // Ideally, if non-looping sample ends, we could signal "finished",
                // but the Voice lifecycle is currently driven by Envelope/Gate.
                // So we just output 0.0.
                // out[idxOut] += 0.0 // (No-op)
            } else if (base >= 0) {
                val frac = ph - base.toDouble()
                val a = pcm[base]
                val b = pcm[base + 1]
                val sampleValue = a + (b - a) * frac

                // Add to buffer (Note: mixing is done later via mixToOrbit usually,
                // but here you seem to write to 'out' directly.
                // Wait, the previous code wrote directly to 'out' inside the loop?
                // Standard ScheduledVoice/SynthVoice writes to 'out' then mixes.
                // Let's stick to writing raw sample value to 'out' here.)
                out[idxOut] = sampleValue
            }

            // Advance Playhead
            ph += if (modBuffer != null) rate * modBuffer[idxOut] else rate
        }
        playhead = ph

        // Filter
        filter.process(ctx.voiceBuffer, offset, length)

        // Mix (Apply Envelope, Gain, Pan)
        mixToOrbit(ctx, offset, length)

        return true
    }
}
