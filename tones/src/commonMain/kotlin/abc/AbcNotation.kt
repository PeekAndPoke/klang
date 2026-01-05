package io.peekandpoke.klang.tones.abc

import io.peekandpoke.klang.tones.distance.Distance
import io.peekandpoke.klang.tones.note.Note

object AbcNotation {
    /** Regular expression to parse ABC notation notes. Groups: 1: accidentals, 2: letter, 3: octave marks. */
    private val REGEX = Regex("""^(_{1,}|=|\^{1,}|)([abcdefgABCDEFG])([,']*)$""")

    /**
     * Tokenizes an ABC notation string into [accidentals, letter, octaveMarks].
     */
    fun tokenize(str: String): List<String> {
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
    fun toScientificNotation(str: String): String {
        val tokens = tokenize(str)
        val acc = tokens[0]
        val letter = tokens[1]
        val oct = tokens[2]

        if (letter.isEmpty()) return ""

        // Calculate octave from commas (down) and apostrophes (up)
        var o = 4
        for (char in oct) {
            if (char == ',') o-- else if (char == '\'') o++
        }

        // Convert ABC accidentals to scientific notation accidentals
        val a = when {
            acc.startsWith('_') -> acc.replace('_', 'b')
            acc.startsWith('^') -> acc.replace('^', '#')
            else -> ""
        }

        // Lowercase letters are one octave higher (octave 5)
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
    fun fromScientificNotation(str: String): String {
        val n = Note.get(str)
        if (n.empty || n.oct == null) return ""

        val letter = n.letter
        val acc = n.acc
        val oct = n.oct

        // Convert scientific notation accidentals to ABC accidentals
        val a = when {
            acc.startsWith('b') -> acc.replace('b', '_')
            acc.startsWith('#') -> acc.replace('#', '^')
            else -> ""
        }

        // Notes from octave 5 onwards are lowercase in ABC
        val l = if (oct > 4) letter.lowercase() else letter

        // Calculate octave marks (commas or apostrophes)
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
    fun transpose(note: String, interval: String): String {
        return fromScientificNotation(Distance.transpose(toScientificNotation(note), interval))
    }

    /**
     * Find the distance between two ABC notes.
     */
    fun distance(from: String, to: String): String {
        return Distance.distance(toScientificNotation(from), toScientificNotation(to))
    }
}
