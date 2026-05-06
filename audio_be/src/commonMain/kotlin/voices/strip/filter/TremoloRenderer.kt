package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import kotlin.math.sin

/**
 * Tremolo effect — rhythmic amplitude modulation via sine LFO.
 */
class TremoloRenderer(
    rate: Double,
    private val depth: Double,
    sampleRate: Int,
) : BlockRenderer {

    private var phase: Double = 0.0
    private val phaseIncrement: Double = (rate * TWO_PI) / sampleRate

    override fun render(ctx: BlockContext) {
        if (depth <= 0.0) return

        val buf = ctx.audioBuffer
        for (i in 0 until ctx.length) {
            val idx = ctx.offset + i

            phase += phaseIncrement
            if (phase > TWO_PI) phase -= TWO_PI

            val lfoNorm = (sin(phase) + 1.0) * 0.5
            val gain = 1.0 - (depth * (1.0 - lfoNorm))

            buf[idx] = (buf[idx] * gain)
        }
    }
}
