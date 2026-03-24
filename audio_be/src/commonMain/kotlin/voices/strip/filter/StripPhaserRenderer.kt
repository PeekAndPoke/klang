package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.flushDenormal
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tan

/**
 * Per-voice phaser effect — 4-stage allpass cascade with sine LFO modulation.
 * Includes denormal flushing on all IIR state.
 */
class StripPhaserRenderer(
    private val rate: Double,
    private val depth: Double,
    private val center: Double,
    private val sweep: Double,
    sampleRate: Int,
) : BlockRenderer {

    private val stages = 4
    private var lfoPhase: Double = 0.0
    private val filterState = DoubleArray(stages)
    private var lastOutput: Double = 0.0
    private val feedback: Double = 0.5
    private val inverseSampleRate = 1.0 / sampleRate
    private val lfoIncrement = rate * TWO_PI * inverseSampleRate

    override fun render(ctx: BlockContext) {
        if (depth <= 0.0) return

        val buf = ctx.audioBuffer
        for (i in 0 until ctx.length) {
            val idx = ctx.offset + i

            lfoPhase += lfoIncrement
            if (lfoPhase > TWO_PI) lfoPhase -= TWO_PI

            val lfoValue = (sin(lfoPhase) + 1.0) * 0.5

            var modFreq = center + (lfoValue - 0.5) * sweep
            modFreq = modFreq.coerceIn(100.0, 18000.0)

            val tanValue = tan(PI * modFreq * inverseSampleRate)
            val alpha = (tanValue - 1.0) / (tanValue + 1.0)

            var signal = buf[idx].toDouble() + lastOutput * feedback

            for (s in 0 until stages) {
                val x = signal
                val output = alpha * x + filterState[s]
                filterState[s] = flushDenormal(x - alpha * output)
                signal = output
            }

            lastOutput = flushDenormal(signal)

            buf[idx] = (buf[idx] + signal * depth).toFloat()
        }
    }
}
