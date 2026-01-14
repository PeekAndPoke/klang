package io.peekandpoke.klang.strudel.math

import kotlin.math.floor
import kotlin.random.Random

/**
 * 1D Berlin Noise implementation.
 */
class BerlinNoise(private val random: Random) {
    private val cache = mutableMapOf<Int, Double>()

    private fun getRandAt(t: Int): Double = cache.getOrPut(t) { random.nextDouble() }

    fun noise(t: Double): Double {
        val prevRidgeStartIndex = floor(t).toInt()
        val nextRidgeStartIndex = prevRidgeStartIndex + 1

        val prevRidgeBottomPoint = getRandAt(prevRidgeStartIndex)
        val height = getRandAt(nextRidgeStartIndex)
        val nextRidgeTopPoint = prevRidgeBottomPoint + height

        val currentPercent = t - prevRidgeStartIndex
        return (prevRidgeBottomPoint + currentPercent * (nextRidgeTopPoint - prevRidgeBottomPoint)) / 2.0
    }
}
