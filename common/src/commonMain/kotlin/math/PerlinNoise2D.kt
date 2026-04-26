package io.peekandpoke.klang.common.math

import kotlin.math.floor
import kotlin.random.Random

/**
 * 2D Perlin Noise — gradient noise on a unit-square lattice with quintic
 * smoothing. Output range is approximately `[-1, 1]`. Deterministic per seed.
 *
 * Sibling of [PerlinNoise] (1D). Useful for organic 2D variation: textures,
 * height fields, modulation.
 */
class PerlinNoise2D(random: Random = Random(0)) {
    private val p = IntArray(512)

    init {
        val permutation = IntArray(256) { it }
        // Shuffle using the provided random to make the noise unique to the seed
        for (i in 255 downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = permutation[i]
            permutation[i] = permutation[j]
            permutation[j] = tmp
        }

        for (i in 0..255) {
            p[i] = permutation[i]
            p[256 + i] = permutation[i]
        }
    }

    private fun fade(t: Double): Double = t * t * t * (t * (t * 6.0 - 15.0) + 10.0)
    private fun lerp(t: Double, a: Double, b: Double): Double = a + t * (b - a)

    /** 8 compass-direction gradients — the dot of the corner gradient with the offset to (x, y). */
    private fun grad(hash: Int, x: Double, y: Double): Double = when (hash and 7) {
        0 -> x + y
        1 -> -x + y
        2 -> x - y
        3 -> -x - y
        4 -> x
        5 -> -x
        6 -> y
        else -> -y
    }

    /**
     * Generates a 2D Perlin noise value at ([x], [y]).
     * Result is in approximately `[-1, 1]`.
     */
    fun noise(x: Double, y: Double): Double {
        val xi0 = floor(x).toInt()
        val yi0 = floor(y).toInt()
        val xi = xi0 and 255
        val yi = yi0 and 255
        val xf = x - xi0
        val yf = y - yi0
        val u = fade(xf)
        val v = fade(yf)

        val aa = p[p[xi] + yi]
        val ab = p[p[xi] + yi + 1]
        val ba = p[p[xi + 1] + yi]
        val bb = p[p[xi + 1] + yi + 1]

        val x1 = lerp(u, grad(aa, xf, yf), grad(ba, xf - 1.0, yf))
        val x2 = lerp(u, grad(ab, xf, yf - 1.0), grad(bb, xf - 1.0, yf - 1.0))
        return lerp(v, x1, x2)
    }
}
