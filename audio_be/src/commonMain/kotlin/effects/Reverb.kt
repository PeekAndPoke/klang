package io.peekandpoke.klang.audio_be.effects

import io.peekandpoke.klang.audio_be.StereoBuffer

// TODO: align with Strudel
//  - currently the effect of "room" and "size" are waaaay to strong compared to strudel
class Reverb(
    val sampleRate: Int,
) {
    // Tuning (Schroeder / Freeverb standard values scaled)
    private val combTuning = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
    private val allPassTuning = intArrayOf(556, 441, 341, 225)

    private val combsL = combTuning.map { Comb(it) }
    private val combsR = combTuning.map { Comb(it + 23) } // Decorrelate Right channel

    private val allPassL = allPassTuning.map { AllPass(it) }
    private val allPassR = allPassTuning.map { AllPass(it + 23) }

    // Parameters
    var roomSize: Double = 0.5 // 0.0 .. 1.0 (Feedback)
    var damp: Double = 0.5     // 0.0 .. 1.0 (High frequency damping)
    var width: Double = 1.0    // 0.0 .. 1.0 (Stereo width) -- Not strictly exposed but useful

    // Extended Strudel Parameters
    var roomFade: Double? = null
    var roomLp: Double? = null
    var roomDim: Double? = null
    var iResponse: String? = null

    fun process(input: StereoBuffer, output: StereoBuffer, length: Int) {
        val inL = input.left
        val inR = input.right
        val outL = output.left
        val outR = output.right

        // Calculate feedback and damping based on parameters
        // Typical Freeverb ranges: Feedback 0.7..0.98, Damping 0..0.4

        // Use roomFade for feedback if available, otherwise fallback to roomSize
        val effectiveSize = roomFade ?: roomSize
        val feedback = (effectiveSize * 0.28f) + 0.7f

        // Use roomLp to control damping if available
        // roomLp (Hz) -> damp (0..1)
        // High cutoff = low damping (bright)
        // Low cutoff = high damping (dull)
        val effectiveDamp = if (roomLp != null) {
            val nyquist = sampleRate / 2.0
            // Normalize freq to 0..1 and invert for damping
            val normalized = (roomLp!! / nyquist).coerceIn(0.0, 1.0)
            1.0 - normalized
        } else {
            damp
        }

        val damping = effectiveDamp * 0.4f

        for (i in 0 until length) {
            val inpL = inL[i]
            val inpR = inR[i]

            // Sum input (mono-summing for reverb input is common, or maintain stereo)
            // Let's maintain stereo input to combs
            var sumL = 0.0
            var sumR = 0.0

            // Process Parallel Comb Filters
            for (comb in combsL) sumL += comb.process(inpL, feedback, damping)
            for (comb in combsR) sumR += comb.process(inpR, feedback, damping)

            // Process Series All-Pass Filters
            for (ap in allPassL) sumL = ap.process(sumL)
            for (ap in allPassR) sumR = ap.process(sumR)

            // Mix to output
            outL[i] += sumL
            outR[i] += sumR
        }
    }

    private class Comb(val size: Int) {
        val buffer = DoubleArray(size)
        var pos = 0
        var store = 0.0

        fun process(input: Double, feedback: Double, damp: Double): Double {
            val output = buffer[pos]
            store = (output * (1.0 - damp)) + (store * damp)
            buffer[pos] = input + (store * feedback)
            if (++pos >= size) pos = 0
            return output
        }
    }

    private class AllPass(val size: Int) {
        val buffer = DoubleArray(size)
        var pos = 0

        // Standard Freeverb Allpass feedback is 0.5
        private val feedback = 0.5

        fun process(input: Double): Double {
            val buffered = buffer[pos]
            val output = -input + buffered
            buffer[pos] = input + (buffered * feedback)
            if (++pos >= size) pos = 0
            return output
        }
    }
}
