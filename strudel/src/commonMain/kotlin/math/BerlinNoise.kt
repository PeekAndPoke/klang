package io.peekandpoke.klang.strudel.math

import kotlin.math.floor
import kotlin.random.Random

/**
 * 1D Berlin Noise implementation.
 */
class BerlinNoise(private val random: Random) {
    // Use a stable seed for hashing to ensure different noise instances
    // produce different results even for the same indices.
    private val seed = random.nextLong()

    /** Generates a deterministic random Double for a specific integer index */
    private fun getValAt(index: Int): Double {
        // Combine the instance seed with the index to get a stable unique seed
        val stableSeed = (seed * 2862933555777941757L) + (index.toLong() * 3037000493L)
        return Random(stableSeed).nextDouble()
    }

    fun noise(t: Double): Double {
        val currentIndex = floor(t).toInt()
        val nextIndex = currentIndex + 1

        // Always fetch the values for the boundaries of the current segment
        val v0 = getValAt(currentIndex)
        val v1 = getValAt(nextIndex)

        val currentPercent = t - currentIndex

        // Linearly interpolate between the two stable points
        return v0 + currentPercent * (v1 - v0)
    }
}
