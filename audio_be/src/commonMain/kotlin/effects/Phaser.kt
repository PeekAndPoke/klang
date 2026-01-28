package io.peekandpoke.klang.audio_be.effects

import io.peekandpoke.klang.audio_be.StereoBuffer
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
    var feedback: Double = 0.0   // 0.0 to <1.0

    // State
    private var lfoPhase = 0.0
    private val stages = 6

    // Stereo state for all-pass filters: [channel][stage]
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
            // Modulation: center +/- sweep/2 or center + sweep * lfo?
            // Simple mapping: center + (lfo - 0.5) * sweep
            var modFreq = centerFreq + (lfoVal - 0.5) * sweepRange
            modFreq = modFreq.coerceIn(100.0, 18000.0) // Safety clamp

            // Alpha approximation for all-pass filter
            // alpha = (tan(PI*fc/fs) - 1) / (tan(PI*fc/fs) + 1)
            // Faster approx: (1 - freq * 2PI/fs) is rough for low freq
            // Better: simple 1-pole mapping
            // w = 2*PI*freq/fs; alpha = (w - 2)/(w + 2) is HighPass?
            // Standard digital allpass coefficient:
            val tan = kotlin.math.tan(PI * modFreq * inverseSampleRate)
            val alpha = (tan - 1.0) / (tan + 1.0)

            // 2. Process Stereo Channels
            // Left
            var inL = left[i] + lastOutputLeft * feedback
            for (s in 0 until stages) {
                // y[n] = alpha * (x[n] - y[n-1]) + x[n-1]
                // or standard: y[n] = -alpha * x[n] + x[n-1] + alpha * y[n-1] (Difference eq)

                // Using: y[n] = alpha * x[n] + x[n-1] - alpha * y[n-1]
                // Let's use the Transfer Function H(z) = (a + z^-1) / (1 + a z^-1)
                // y[n] = a * x[n] + x[n-1] - a * y[n-1]

                val x = inL
                val y = alpha * (x - z1[0][s]) + z1[0][s] // Wait, simpler form

                // Correct All-pass implementation:
                // y[n] = alpha * (input + prev_y) - prev_input
                // This requires storing more state.

                // Let's use standard form: y[n] = alpha * x[n] + x[n-1] - alpha * y[n-1]
                // z1 stores x[n-1] - alpha * y[n-1] ? No.

                // Direct Form II or Transposed:
                // y[n] = alpha * x[n] + z1; z1 = x[n] - alpha * y[n]
                val output = alpha * x + z1[0][s]
                z1[0][s] = x - alpha * output

                inL = output
            }
            lastOutputLeft = inL
            // Wet/Dry Mix (Phaser effect comes from mixing delayed phase-shifted signal with original)
            // Strudel usually mixes 50/50 when active
            left[i] += inL * depth // Add phaser signal

            // Right
            var inR = right[i] + lastOutputRight * feedback
            for (s in 0 until stages) {
                val x = inR
                val output = alpha * x + z1[1][s]
                z1[1][s] = x - alpha * output
                inR = output
            }
            lastOutputRight = inR
            right[i] += inR * depth
        }
    }
}
