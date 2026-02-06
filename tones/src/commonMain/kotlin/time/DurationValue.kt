package io.peekandpoke.klang.tones.time

import kotlin.math.pow

/**
 * Represents a musical duration value.
 */
data class DurationValue(
    /** Whether the duration is empty. */
    val empty: Boolean,
    /** The decimal value of the duration (e.g., 0.25 for a quarter note). */
    val value: Double,
    /** The name of the duration (e.g., "quarter", "q.."). */
    val name: String,
    /** The fractional representation of the duration (numerator, denominator). */
    val fraction: Pair<Int, Int>,
    /** The shorthand notation of the duration. */
    val shorthand: String,
    /** The dots for the duration. */
    val dots: String,
    /** A list of names for the duration. */
    val names: List<String>,
) {
    companion object {
        val NoDuration = DurationValue(
            empty = true,
            name = "",
            value = 0.0,
            fraction = Pair(0, 0),
            shorthand = "",
            dots = "",
            names = emptyList()
        )

        /** Cache for parsed duration values. */
        private val cache = mutableMapOf<String, DurationValue>()

        /**
         * The data for the duration values.
         *
         * Triple: (denominator relative to whole note, shorthand, names)
         * E.g., a "whole" note has denominator 1.0, a "quarter" note has denominator 4.0.
         */
        private val DATA: List<Triple<Double, String, List<String>>> = listOf(
            Triple(0.125, "dl", listOf("large", "duplex longa", "maxima", "octuple", "octuple whole")),
            Triple(0.25, "l", listOf("long", "longa")),
            Triple(0.5, "d", listOf("double whole", "double", "breve")),
            Triple(1.0, "w", listOf("whole", "semibreve")),
            Triple(2.0, "h", listOf("half", "minim")),
            Triple(4.0, "q", listOf("quarter", "crotchet")),
            Triple(8.0, "e", listOf("eighth", "quaver")),
            Triple(16.0, "s", listOf("sixteenth", "semiquaver")),
            Triple(32.0, "t", listOf("thirty-second", "demisemiquaver")),
            Triple(64.0, "sf", listOf("sixty-fourth", "hemidemisemiquaver")),
            Triple(128.0, "h", listOf("hundred twenty-eighth")),
            Triple(256.0, "th", listOf("two hundred fifty-sixth"))
        )

        /**
         * Pre-calculated base duration values (without dots).
         */
        private val VALUES: List<DurationValue> = DATA.map { (denominator, shorthand, names) ->
            DurationValue(
                empty = false,
                dots = "",
                name = "",
                value = 1.0 / denominator,
                fraction = if (denominator < 1.0) {
                    Pair((1.0 / denominator).toInt(), 1)
                } else {
                    Pair(
                    1,
                    denominator.toInt()
                )
                },
                shorthand = shorthand,
                names = names
            )
        }

        /**
         * Returns a list of all duration names.
         */
        fun names(): List<String> = VALUES.flatMap { it.names }

        /**
         * Returns a list of all duration shorthands.
         */
        fun shorthands(): List<String> = VALUES.map { it.shorthand }

        /**
         * Regular expression to split base duration from dots.
         */
        private val REGEX = Regex("""^([^.]+)(\.*)$""")

        /**
         * Get duration value properties.
         *
         * @param name The duration name or shorthand (e.g., "quarter", "q", "q..").
         */
        fun get(name: String): DurationValue = cache.getOrPut(name) {
            val (_, simple, dots) = REGEX.matchEntire(name)?.groupValues ?: return@getOrPut NoDuration

            // Find base duration in the pre-calculated VALUES list
            val base = VALUES.find { it.shorthand == simple || simple in it.names }
                ?: return@getOrPut NoDuration

            // Calculate the fractional value including dots
            val fraction = calcDots(base.fraction, dots.length)
            val value = fraction.first.toDouble() / fraction.second.toDouble()

            base.copy(name = name, dots = dots, value = value, fraction = fraction)
        }

        // Calculates the fraction for a base duration with a given number of dots
        private fun calcDots(fraction: Pair<Int, Int>, dots: Int): Pair<Int, Int> {
            val p = 2.0.pow(dots).toInt()

            // Scale up numerator and denominator to have a common denominator for dot calculations
            var numerator = fraction.first * p
            val denominator = fraction.second * p
            val base = numerator

            // Add the dot values (1/2, 1/4, 1/8, etc. of the base value)
            for (i in 0 until dots) {
                numerator += base / 2.0.pow(i + 1).toInt()
            }

            // Simplify the resulting fraction
            var n = numerator
            var d = denominator
            while (n > 0 && d > 0 && n % 2 == 0 && d % 2 == 0) {
                n /= 2
                d /= 2
            }
            return Pair(n, d)
        }
    }
}
