package io.peekandpoke.klang.audio_be.filters.effects

import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.filters.AudioFilter
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tan

/**
 * Phaser effect - creates a sweeping, swooshing sound.
 * Uses a cascade of all-pass filters modulated by an LFO.
 *
 * @param rate LFO frequency in Hz (speed of the sweep).
 * @param depth Effect intensity (0.0 = no effect, 1.0 = full effect).
 * @param center Center frequency of the sweep in Hz.
 * @param sweep Frequency range of the sweep in Hz.
 * @param sampleRate Audio sample rate.
 */
class PhaserFilter(
    private val rate: Double,
    private val depth: Double,
    private val center: Double,
    private val sweep: Double,
    private val sampleRate: Int,
) : AudioFilter {

    // Number of all-pass filter stages (4 is a good balance)
    private val stages = 4

    // State: LFO phase and all-pass filter delay line
    private var lfoPhase: Double = 0.0
    private val filterState = DoubleArray(stages)

    // Feedback for a more pronounced effect
    private var lastOutput: Double = 0.0
    private val feedback: Double = 0.5

    // Pre-calculate constants
    private val inverseSampleRate = 1.0 / sampleRate
    private val lfoIncrement = rate * TWO_PI * inverseSampleRate

    override fun process(buffer: DoubleArray, offset: Int, length: Int) {
        // Early return if no phaser effect
        if (depth <= 0.0) return

        for (i in 0 until length) {
            val idx = offset + i

            // 1. Update LFO
            lfoPhase += lfoIncrement
            if (lfoPhase > TWO_PI) lfoPhase -= TWO_PI

            // Map LFO sine wave to 0..1 range
            val lfoValue = (sin(lfoPhase) + 1.0) * 0.5

            // 2. Calculate modulated frequency
            var modFreq = center + (lfoValue - 0.5) * sweep
            modFreq = modFreq.coerceIn(100.0, 18000.0) // Safety clamp

            // 3. Calculate all-pass filter coefficient (alpha)
            val tanValue = tan(PI * modFreq * inverseSampleRate)
            val alpha = (tanValue - 1.0) / (tanValue + 1.0)

            // 4. Process through all-pass filter cascade with feedback
            var signal = buffer[idx] + lastOutput * feedback

            for (s in 0 until stages) {
                // All-pass filter: y[n] = alpha * x[n] + z[n-1]
                //                  z[n] = x[n] - alpha * y[n]
                val output = alpha * signal + filterState[s]
                filterState[s] = signal - alpha * output
                signal = output
            }

            lastOutput = signal

            // 5. Mix wet and dry signals
            // Phaser effect comes from mixing the phase-shifted signal with original
            buffer[idx] += signal * depth
        }
    }
}
