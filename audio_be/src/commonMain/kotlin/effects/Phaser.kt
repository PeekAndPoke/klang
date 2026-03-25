package io.peekandpoke.klang.audio_be.effects

import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.flushDenormal
import kotlin.math.PI
import kotlin.math.sin

class Phaser(sampleRate: Int) {
    private val inverseSampleRate = 1.0 / sampleRate
    private val twoPi = 2.0 * PI

    // Parameters
    var rate: Double = 0.0       // Hz
    var depth: Double = 0.0      // 0.0 to 1.0
    var centerFreq: Double = 1000.0 // Hz
    var sweepRange: Double = 1000.0 // Hz (depth of frequency sweep)
    var feedback: Double = 0.0   // 0.0 to 0.95 (clamped to prevent self-oscillation)
        set(value) {
            field = value.coerceIn(0.0, 0.95)
        }

    // State
    private var lfoPhase = 0.0
    private val stages = 6

    // Stereo state for all-pass filters: [channel][stage] — precision state stays Double
    private val z1 = Array(2) { DoubleArray(stages) }
    private var lastOutputLeft = 0.0
    private var lastOutputRight = 0.0

    fun process(buffer: StereoBuffer, frames: Int) {
        if (depth <= 0.001 && feedback <= 0.001) return

        val left = buffer.left
        val right = buffer.right

        // LFO increment per frame
        val lfoInc = rate * twoPi * inverseSampleRate

        for (i in 0 until frames) {
            // 1. LFO Calculation (Sine wave 0..1)
            lfoPhase += lfoInc
            if (lfoPhase > twoPi) lfoPhase -= twoPi

            // Map LFO (-1..1) to frequency range based on sweep
            val lfoVal = (sin(lfoPhase) + 1.0) * 0.5 // 0..1

            // Calculate all-pass coefficient (alpha)
            var modFreq = centerFreq + (lfoVal - 0.5) * sweepRange
            modFreq = modFreq.coerceIn(100.0, 18000.0) // Safety clamp

            val tan = kotlin.math.tan(PI * modFreq * inverseSampleRate)
            val alpha = (tan - 1.0) / (tan + 1.0)

            // 2. Process Stereo Channels
            // Left
            var inL = left[i].toDouble() + lastOutputLeft * feedback
            for (s in 0 until stages) {
                val x = inL
                val output = alpha * x + z1[0][s]
                z1[0][s] = flushDenormal(x - alpha * output)
                inL = output
            }
            lastOutputLeft = flushDenormal(inL)
            left[i] = (left[i] + inL * depth).toFloat()

            // Right
            var inR = right[i].toDouble() + lastOutputRight * feedback
            for (s in 0 until stages) {
                val x = inR
                val output = alpha * x + z1[1][s]
                z1[1][s] = flushDenormal(x - alpha * output)
                inR = output
            }
            lastOutputRight = flushDenormal(inR)
            right[i] = (right[i] + inR * depth).toFloat()
        }
    }
}
