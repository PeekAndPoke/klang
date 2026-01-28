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
    override val filterModulators: List<Voice.FilterModulator>,
    override val delay: Voice.Delay,
    override val reverb: Voice.Reverb,
    override val distort: Voice.Distort,
    override val crush: Voice.Crush,
    override val coarse: Voice.Coarse,
    val samplePlayback: SamplePlayback,
    val sample: MonoSamplePcm,
    val rate: Double,
    var playhead: Double,
) : Voice {

    class SamplePlayback(
        val cut: Int?,
        val explicitLooping: Boolean,
        val explicitLoopStart: Double,
        val explicitLoopEnd: Double,
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
    // If explicit loop is requested (via .loop() or .loopAt()), use those points.
    // Otherwise fallback to sample metadata loop points (sustained samples).
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

    override fun render(ctx: Voice.RenderContext): Boolean {
        val blockEnd = ctx.blockStart + ctx.blockFrames
        if (ctx.blockStart >= endFrame) return false
        if (blockEnd <= startFrame) return true

        val vStart = max(ctx.blockStart, startFrame)
        val vEnd = min(blockEnd, endFrame)
        val offset = (vStart - ctx.blockStart).toInt()
        val length = (vEnd - vStart).toInt()

        // Apply filter modulation (control rate - once per block)
        for (mod in filterModulators) {
            val envValue = calculateFilterEnvelope(mod.envelope, ctx)
            val newCutoff = mod.baseCutoff * (1.0 + mod.depth * envValue)
            mod.filter.setCutoff(newCutoff)
        }

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

            // Check strict end (for .end() or sample end)
            if (ph >= samplePlayback.stopFrame) {
                out[idxOut] = 0.0
                // We let the loop continue to fill the rest of the buffer with silence
                // playhead keeps advancing (potentially past stopFrame) but output is silenced
            } else {
                // Read Sample
                val base = ph.toInt()

                if (base >= pcmMax) {
                    // End of sample reached (and not looping, or loop points are broken)
                    out[idxOut] = 0.0 // Clear output
                } else if (base >= 0) {
                    val frac = ph - base.toDouble()
                    val a = pcm[base]
                    val b = pcm[base + 1]
                    val sampleValue = a + (b - a) * frac

                    out[idxOut] = sampleValue
                }
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
