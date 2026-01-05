package io.peekandpoke.klang.tones.abc

import io.peekandpoke.klang.tones.note.note
import io.peekandpoke.klang.tones.distance.distance as distanceNote
import io.peekandpoke.klang.tones.distance.transpose as transposeNote

private val REGEX = Regex("""^(_{1,}|=|\^{1,}|)([abcdefgABCDEFG])([,']*)$""")

/**
 * Tokenizes an ABC notation string into [accidentals, letter, octaveMarks].
 */
fun tokenizeAbc(str: String): List<String> {
    val m = REGEX.matchEntire(str)
    return if (m != null) {
        val groups = m.groupValues
        listOf(groups[1], groups[2], groups[3])
    } else {
        listOf("", "", "")
    }
}

/**
 * Convert a (string) note in ABC notation into a (string) note in scientific notation.
 *
 * @example
 * abcToScientificNotation("c") // => "C5"
 */
fun abcToScientificNotation(str: String): String {
    val tokens = tokenizeAbc(str)
    val acc = tokens[0]
    val letter = tokens[1]
    val oct = tokens[2]

    if (letter.isEmpty()) return ""

    var o = 4
    for (char in oct) {
        if (char == ',') o-- else if (char == '\'') o++
    }

    val a = when {
        acc.startsWith('_') -> acc.replace('_', 'b')
        acc.startsWith('^') -> acc.replace('^', '#')
        else -> ""
    }

    return if (letter[0].code > 96) {
        letter.uppercase() + a + (o + 1)
    } else {
        letter + a + o
    }
}

/**
 * Convert a (string) note in scientific notation into a (string) note in ABC notation.
 *
 * @example
 * scientificToAbcNotation("C#4") // => "^C"
 */
fun scientificToAbcNotation(str: String): String {
    val n = note(str)
    if (n.empty || n.oct == null) return ""

    val letter = n.letter
    val acc = n.acc
    val oct = n.oct!!

    val a = when {
        acc.startsWith('b') -> acc.replace('b', '_')
        acc.startsWith('#') -> acc.replace('#', '^')
        else -> ""
    }

    val l = if (oct > 4) letter.lowercase() else letter
    val o = when {
        oct == 5 -> ""
        oct > 4 -> "'".repeat(oct - 5)
        else -> ",".repeat(4 - oct)
    }

    return a + l + o
}

/**
 * Transpose an ABC note by an interval.
 */
fun transposeAbc(note: String, interval: String): String {
    return scientificToAbcNotation(transposeNote(abcToScientificNotation(note), interval))
}

/**
 * Find the distance between two ABC notes.
 */
fun distanceAbc(from: String, to: String): String {
    return distanceNote(abcToScientificNotation(from), abcToScientificNotation(to))
}
