package io.peekandpoke.klang.tones.time

import kotlin.math.floor

typealias RhythmPattern = List<Int>

/**
 * Create a rhythm pattern from a number or concatenation of numbers in binary form.
 *
 * @param numbers one or more numbers
 * @return a list of 0s and 1s representing the rhythm pattern
 */
fun rhythmBinary(vararg numbers: Int): RhythmPattern {
    val pattern = mutableListOf<Int>()
    for (number in numbers) {
        number.toString(2).forEach { digit ->
            pattern.add(digit.toString().toInt())
        }
    }
    return pattern
}

/**
 * Create a rhythmic pattern using an hexadecimal number.
 *
 * @param hexNumber string with the hexadecimal number
 * @return a list of 0s and 1s representing the rhythm pattern
 */
fun rhythmHex(hexNumber: String): RhythmPattern {
    val pattern = mutableListOf<Int>()
    for (char in hexNumber) {
        val digit = char.toString().toIntOrNull(16) ?: 0
        val binary = digit.toString(2).padStart(4, '0')
        binary.forEach { b ->
            pattern.add(if (b == '1') 1 else 0)
        }
    }
    return pattern
}

/**
 * Create a rhythm pattern from the onsets sizes.
 *
 * @param numbers the onsets sizes
 * @return a list of 0s and 1s representing the rhythm pattern
 */
fun rhythmOnsets(vararg numbers: Int): RhythmPattern {
    val pattern = mutableListOf<Int>()
    for (number in numbers) {
        pattern.add(1)
        repeat(number) {
            pattern.add(0)
        }
    }
    return pattern
}

/**
 * Create a random rhythm pattern with a specified length.
 *
 * @param length length of the pattern
 * @param probability Threshold where random number is considered a beat (defaults to 0.5)
 * @param rnd A random function (returns 0.0 to 1.0)
 * @return a list of 0s and 1s representing the rhythm pattern
 */
fun rhythmRandom(
    length: Int,
    probability: Double = 0.5,
    rnd: () -> Double = { kotlin.random.Random.nextDouble() },
): RhythmPattern {
    val pattern = mutableListOf<Int>()
    repeat(length) {
        pattern.add(if (rnd() >= probability) 1 else 0)
    }
    return pattern
}

/**
 * Create a rhythm pattern based on the given probability thresholds.
 *
 * @param probabilities An array with the probability of each step to be a beat
 * @param rnd A random function
 * @return a list of 0s and 1s representing the rhythm pattern
 */
fun rhythmProbability(
    probabilities: List<Double>,
    rnd: () -> Double = { kotlin.random.Random.nextDouble() },
): RhythmPattern {
    return probabilities.map { p -> if (rnd() <= p) 1 else 0 }
}

/**
 * Rotate a pattern right.
 *
 * @param pattern the pattern to rotate
 * @param rotations the number of steps to rotate
 * @return the rotated pattern
 */
fun rhythmRotate(pattern: RhythmPattern, rotations: Int): RhythmPattern {
    if (pattern.isEmpty()) return pattern
    val len = pattern.size
    val rotated = MutableList(len) { 0 }
    for (i in 0 until len) {
        val pos = (((i - rotations) % len) + len) % len
        rotated[i] = pattern[pos]
    }
    return rotated
}

/**
 * Generates an euclidean rhythm pattern.
 *
 * @param steps The length of the pattern
 * @param beats The number of beats
 * @return a list of 0s and 1s representing the rhythmic pattern
 */
fun rhythmEuclid(steps: Int, beats: Int): RhythmPattern {
    val pattern = MutableList(steps) { 0 }
    var d = -1

    for (i in 0 until steps) {
        val v = floor(i.toDouble() * (beats.toDouble() / steps.toDouble())).toInt()
        pattern[i] = if (v != d) 1 else 0
        d = v
    }
    return pattern
}
