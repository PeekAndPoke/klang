package io.peekandpoke.klang.tones.time

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
         * Get time signature properties from a string (e.g., "4/4", "3+2/8").
         */
        fun get(literal: String): TimeSignature {
            val (_, up, low) = REGEX.matchEntire(literal)?.groupValues ?: return Invalid
            val parsed = parseParts(up, low) ?: return Invalid
            return buildTimeSignature(parsed.first, parsed.second)
        }

        /**
         * Get time signature properties from a pair (numerator, denominator).
         */
        fun get(literal: Pair<Int, Int>): TimeSignature {
            return buildTimeSignature(listOf(literal.first), literal.second)
        }

        /**
         * Get time signature properties from numerator and denominator.
         */
        fun get(upper: Int, lower: Int): TimeSignature {
            return buildTimeSignature(listOf(upper), lower)
        }

        /**
         * Get time signature properties from a list [numerator, denominator].
         * Supports additive numerators like [3, 2, 2] for the numerator list followed by denominator.
         */
        fun get(literal: List<Int>): TimeSignature {
            if (literal.size < 2) return Invalid
            val upper = literal.dropLast(1)
            val lower = literal.last()
            return buildTimeSignature(upper, lower)
        }

        // Splits numerator and denominator and converts them to integers
        private fun parseParts(up: String, low: String): Pair<List<Int>, Int>? {
            val upperList = up.split("+").mapNotNull { it.toIntOrNull() }
            val lower = low.toIntOrNull() ?: return null
            if (upperList.isEmpty()) return null
            return Pair(upperList, lower)
        }

        /**
         * A power of two has exactly one bit set in binary (e.g., 8 = 0b1000).
         * Subtracting 1 flips that bit and sets all lower bits (e.g., 7 = 0b0111).
         * AND-ing the two gives zero only for powers of two: 0b1000 & 0b0111 = 0b0000.
         *
         * Why not log2: `(log2(x.toDouble()) % 1.0) == 0.0` fails for certain integers
         * due to floating-point precision.
         *
         * Note: also exists as `Int.isPowerOfTwo()` in common/math. Duplicated here because
         * tones does not depend on the common module and adding that dependency for one function
         * would be overkill.
         */
        private fun isPowerOfTwo(x: Int): Boolean = x > 0 && (x and (x - 1)) == 0

        // Internal builder for TimeSignature objects
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
