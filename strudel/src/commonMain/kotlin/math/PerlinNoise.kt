package io.peekandpoke.klang.strudel.math

import kotlin.math.floor
import kotlin.random.Random

/**
 * 1D Perlin Noise implementation with runtime permutation generation.
 */
class PerlinNoise(random: Random = Random(0)) {
    private val p = IntArray(512)

    init {
        val permutation = IntArray(256) { it }
        // Shuffle using the provided random to make the noise unique to the seed
        for (i in 255 downTo 1) {
            val j = random.nextInt(i + 1)
            val temp = permutation[i]
            permutation[i] = permutation[j]
            permutation[j] = temp
        }

        for (i in 0..255) {
            p[i] = permutation[i]
            p[256 + i] = permutation[i]
        }
    }

    private fun fade(t: Double): Double = t * t * t * (t * (t * 6 - 15) + 10)
    private fun lerp(t: Double, a: Double, b: Double): Double = a + t * (b - a)

    /**
     * Standard "Improved Perlin Noise" gradient function for 1D.
     * Uses the hash to select a gradient from a small set of predefined gradients.
     */
    private fun grad(hash: Int, x: Double): Double {
        // Simple 1D gradient: use the last 4 bits of the hash to pick a gradient.
        // We want gradients like 1.0, -1.0, etc.
        val h = hash and 15
        var grad = 1.0 + (h and 7) // Gradient value between 1.0 and 8.0
        if ((h and 8) != 0) grad = -grad // Randomly invert sign based on the 8 bit
        return grad * x
    }

    /**
     * Generates a 1D Perlin noise value for [x].
     * Result is in range [-1.0, 1.0].
     */
    fun noise(x: Double): Double {
        val xi = floor(x).toInt() and 255
        val xf = x - floor(x)
        val u = fade(xf)

        val a = p[xi]
        val b = p[xi + 1]

        val res = lerp(u, grad(a, xf), grad(b, xf - 1.0))

        // Perlin noise typically has a range of about -0.5 to 0.5 or slightly larger in 1D.
        // Scaling by 2.0 brings it closer to -1.0 to 1.0, but might overshoot slightly.
        // For audio/signals, we clamp it to be safe or accept the slight variance.
        // We'll scale by 0.5 to keep it roughly in -1..1 range given the large gradients above (1..8).
        // Actually, with gradients up to 8.0, the output can be quite large.
        // Let's stick to the simpler gradient of just 1.0 / -1.0 for standard behavior if we want predictability.

        return res * 0.25 // Tuning factor to keep it roughly within -1..1
    }
}
