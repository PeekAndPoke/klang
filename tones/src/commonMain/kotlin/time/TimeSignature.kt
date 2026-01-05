package io.peekandpoke.klang.tones.time

import kotlin.math.log2

/**
 * Represents a musical time signature.
 */
sealed class TimeSignature {
    /** Whether the time signature is empty/invalid. */
    abstract val empty: Boolean

    /** The string representation of the time signature (e.g., "4/4", "3+2/8"). */
    abstract val name: String

    /** The numerator of the time signature. */
    abstract val upper: Int

    /** The denominator of the time signature. */
    abstract val lower: Int

    /** The type of the time signature (e.g., "simple", "compound", "irregular", "irrational"). */
    abstract val type: String?

    /** The additive parts of the numerator if it's an additive time signature (e.g., [3, 2] for "3+2/8"). */
    abstract val additive: List<Int>

    /**
     * Represents a valid musical time signature.
     */
    data class Valid(
        /** The string representation of the time signature. */
        override val name: String,
        /** The numerator of the time signature. */
        override val upper: Int,
        /** The denominator of the time signature. */
        override val lower: Int,
        /** The type of the time signature. */
        override val type: String,
        /** The additive parts of the numerator. */
        override val additive: List<Int> = emptyList(),
    ) : TimeSignature() {
        /** Whether the time signature is empty/invalid. */
        override val empty: Boolean = false
    }

    /**
     * Represents an invalid or empty time signature.
     */
    object Invalid : TimeSignature() {
        /** Whether the time signature is empty/invalid. */
        override val empty: Boolean = true

        /** The string representation of the time signature. */
        override val name: String = ""

        /** The numerator of the time signature. */
        override val upper: Int = 0

        /** The denominator of the time signature. */
        override val lower: Int = 0

        /** The type of the time signature. */
        override val type: String? = null

        /** The additive parts of the numerator. */
        override val additive: List<Int> = emptyList()
    }

    companion object {
        /** Common time signature names. */
        private val NAMES = listOf("4/4", "3/4", "2/4", "2/2", "12/8", "9/8", "6/8", "3/8")

        /** Regular expression for time signatures (supports additive numerator like "3+2/8"). */
        private val REGEX = Regex("""^(\d*\d(?:\+\d)*)/(\d+)$""")

        /**
         * Returns a list of common time signature names.
         */
        fun names(): List<String> = NAMES.toList()

        /**
         * Get time signature properties.
         *
         * @param literal A string (e.g., "4/4", "3+2/8") or a pair of integers.
         */
        fun get(literal: Any?): TimeSignature {
            val parsed = when (literal) {
                is String -> {
                    val match = REGEX.matchEntire(literal)
                    if (match != null) {
                        val up = match.groupValues[1]
                        val low = match.groupValues[2]
                        parseParts(up, low)
                    } else {
                        null
                    }
                }

                is Pair<*, *> -> {
                    parseParts(literal.first.toString(), literal.second.toString())
                }

                is List<*> -> {
                    if (literal.size >= 2) {
                        parseParts(literal[0].toString(), literal[1].toString())
                    } else null
                }

                else -> null
            } ?: return Invalid

            return buildTimeSignature(parsed.first, parsed.second)
        }

        /**
         * Splits numerator and denominator and converts them to integers.
         */
        private fun parseParts(up: String, low: String): Pair<List<Int>, Int>? {
            val upperList = up.split("+").mapNotNull { it.toIntOrNull() }
            val lower = low.toIntOrNull() ?: return null
            if (upperList.isEmpty()) return null
            return Pair(upperList, lower)
        }

        /**
         * Checks if a number is a power of two.
         */
        private fun isPowerOfTwo(x: Int): Boolean {
            if (x <= 0) return false
            return (log2(x.toDouble()) % 1.0) == 0.0
        }

        /**
         * Internal builder for [TimeSignature] objects.
         */
        private fun buildTimeSignature(up: List<Int>, down: Int): TimeSignature {
            val totalUpper = up.sum()
            if (totalUpper == 0 || down == 0) return Invalid

            val name = if (up.size > 1) "${up.joinToString("+")}/$down" else "$totalUpper/$down"
            val additive = if (up.size > 1) up else emptyList()

            // Determine time signature type
            val type = when {
                down == 4 || down == 2 -> "simple"
                down == 8 && totalUpper % 3 == 0 -> "compound"
                isPowerOfTwo(down) -> "irregular"
                else -> "irrational"
            }

            return Valid(
                name = name,
                upper = totalUpper,
                lower = down,
                type = type,
                additive = additive
            )
        }
    }
}
