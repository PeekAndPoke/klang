package io.peekandpoke.klang.tones.time

import kotlin.math.log2

/**
 * Represents a musical time signature.
 */
sealed class TimeSignature {
    abstract val empty: Boolean
    abstract val name: String
    abstract val upper: Int
    abstract val lower: Int
    abstract val type: String?
    abstract val additive: List<Int>

    data class Valid(
        override val name: String,
        override val upper: Int,
        override val lower: Int,
        override val type: String,
        override val additive: List<Int> = emptyList(),
    ) : TimeSignature() {
        override val empty: Boolean = false
    }

    object Invalid : TimeSignature() {
        override val empty: Boolean = true
        override val name: String = ""
        override val upper: Int = 0
        override val lower: Int = 0
        override val type: String? = null
        override val additive: List<Int> = emptyList()
    }
}

private val NAMES = listOf("4/4", "3/4", "2/4", "2/2", "12/8", "9/8", "6/8", "3/8")

/**
 * Returns a list of common time signature names.
 */
fun timeSignatureNames(): List<String> = NAMES.toList()

private val REGEX = Regex("""^(\d*\d(?:\+\d)*)/(\d+)$""")

/**
 * Get time signature properties.
 *
 * @param literal A string (e.g., "4/4", "3+2/8") or a pair of integers.
 */
fun getTimeSignature(literal: Any?): TimeSignature {
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
    } ?: return TimeSignature.Invalid

    return buildTimeSignature(parsed.first, parsed.second)
}

private fun parseParts(up: String, low: String): Pair<List<Int>, Int>? {
    val upperList = up.split("+").mapNotNull { it.toIntOrNull() }
    val lower = low.toIntOrNull() ?: return null
    if (upperList.isEmpty()) return null
    return Pair(upperList, lower)
}

private fun isPowerOfTwo(x: Int): Boolean {
    if (x <= 0) return false
    return (log2(x.toDouble()) % 1.0) == 0.0
}

private fun buildTimeSignature(up: List<Int>, down: Int): TimeSignature {
    val totalUpper = up.sum()
    if (totalUpper == 0 || down == 0) return TimeSignature.Invalid

    val name = if (up.size > 1) "${up.joinToString("+")}/$down" else "$totalUpper/$down"
    val additive = if (up.size > 1) up else emptyList()

    val type = when {
        down == 4 || down == 2 -> "simple"
        down == 8 && totalUpper % 3 == 0 -> "compound"
        isPowerOfTwo(down) -> "irregular"
        else -> "irrational"
    }

    return TimeSignature.Valid(
        name = name,
        upper = totalUpper,
        lower = down,
        type = type,
        additive = additive
    )
}
