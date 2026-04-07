package io.peekandpoke.klang.common.math

import kotlin.math.floor
import kotlin.random.Random

/**
 * 1D Berlin Noise implementation.
 *
 * Smooth random noise via linear interpolation between deterministic hash values.
 * Uses Int-only hashing (no Long) — safe for Kotlin/JS where Long is boxed.
 */
class BerlinNoise(random: Random) {
    private val seed = random.nextInt()

    /** Deterministic hash for an integer index — zero allocations, Int-only. */
    private fun getValAt(index: Int): Double {
        // MurmurHash3-style 32-bit finalizer: fast, good distribution, no Long
        var h = seed xor (index * 0x9E3779B1.toInt())
        h = (h xor (h ushr 16)) * 0x85EBCA6B.toInt()
        h = (h xor (h ushr 13)) * 0xC2B2AE35.toInt()
        h = h xor (h ushr 16)
        return (h.toDouble() / Int.MAX_VALUE.toDouble() + 1.0) * 0.5 // map to ~[0, 1]
    }

    fun noise(t: Double): Double {
        val currentIndex = floor(t).toInt()
        val nextIndex = currentIndex + 1

        val v0 = getValAt(currentIndex)
        val v1 = getValAt(nextIndex)

        val currentPercent = t - currentIndex

        return v0 + currentPercent * (v1 - v0)
    }
}
