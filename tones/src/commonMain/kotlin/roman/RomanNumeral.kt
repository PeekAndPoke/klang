package io.peekandpoke.klang.tones.roman

import io.peekandpoke.klang.tones.interval.interval
import io.peekandpoke.klang.tones.note.accToAlt
import io.peekandpoke.klang.tones.note.altToAcc
import io.peekandpoke.klang.tones.pitch.NamedPitch
import io.peekandpoke.klang.tones.pitch.Pitch

/**
 * Represents a roman numeral.
 *
 * @property step The step number: 0 = I, 1 = II, ... 6 = VII
 * @property alt Number of alterations: -1 = 'b', 0 = '', 1 = '#'
 * @property oct The octave (always 0 for roman numerals)
 * @property dir The direction (always 1 for roman numerals)
 * @property name The full name of the roman numeral (e.g., "#VIIb5")
 * @property empty Whether the roman numeral is empty
 * @property roman The roman numeral part (e.g., "VII")
 * @property interval The interval name (e.g., "7A")
 * @property acc The accidental string (e.g., "#")
 * @property chordType The chord type (e.g., "b5")
 * @property major Whether the roman numeral is uppercase
 */
data class RomanNumeral(
    override val step: Int,
    override val alt: Int,
    override val oct: Int? = 0,
    override val dir: Int? = 1,
    override val name: String = "",
    val empty: Boolean = false,
    val roman: String = "",
    val interval: String = "",
    val acc: String = "",
    val chordType: String = "",
    val major: Boolean = true,
) : Pitch, NamedPitch {
    companion object {
        /**
         * Represents an empty or invalid roman numeral.
         */
        val NoRomanNumeral = RomanNumeral(
            step = -1,
            alt = -1,
            empty = true,
            name = "",
            chordType = ""
        )
    }
}

private val ROMANS = "I II III IV V VI VII"
private val NAMES = ROMANS.split(" ")
private val NAMES_MINOR = ROMANS.lowercase().split(" ")

private val REGEX = Regex("""^([#]{1,}|b{1,}|x{1,}|)(IV|I{1,3}|VI{0,2}|iv|i{1,3}|vi{0,2})([^IViv]*)$""")

/**
 * Tokenizes a roman numeral string into [fullMatch, accidentals, romanNumeral, chordType].
 * @private
 */
fun tokenizeRomanNumeral(str: String): List<String> {
    val m = REGEX.matchEntire(str)
    return if (m != null) {
        val groups = m.groupValues
        listOf(
            groups[0],
            groups[1],
            groups[2],
            groups[3]
        )
    } else {
        listOf("", "", "", "")
    }
}

/**
 * Get roman numeral names.
 *
 * @param major Whether to return major (uppercase) or minor (lowercase) names.
 */
fun romanNumeralNames(major: Boolean = true): List<String> {
    return if (major) NAMES.toList() else NAMES_MINOR.toList()
}

/**
 * Returns a [RomanNumeral] from a given source (string, number, or Pitch).
 */
fun romanNumeral(src: Any?): RomanNumeral {
    return when (src) {
        is String -> parse(src)
        is Int -> {
            val name = NAMES.getOrNull(src) ?: ""
            if (name.isNotEmpty()) parse(name) else RomanNumeral.NoRomanNumeral
        }

        is Pitch -> parse(altToAcc(src.alt) + (NAMES.getOrNull(src.step) ?: ""))
        is NamedPitch -> romanNumeral(src.name)
        is RomanNumeral -> src
        else -> RomanNumeral.NoRomanNumeral
    }
}

private fun parse(src: String): RomanNumeral {
    val tokens = tokenizeRomanNumeral(src)
    val name = tokens[0]
    val acc = tokens[1]
    val roman = tokens[2]
    val chordType = tokens[3]

    if (roman.isEmpty()) {
        return RomanNumeral.NoRomanNumeral
    }

    val upperRoman = roman.uppercase()
    val step = NAMES.indexOf(upperRoman)
    val alt = accToAlt(acc)
    val dir = 1

    return RomanNumeral(
        empty = false,
        name = name,
        roman = roman,
        interval = interval(io.peekandpoke.klang.tones.pitch.Pitch(step, alt, 0, dir)).name,
        acc = acc,
        chordType = chordType,
        alt = alt,
        step = step,
        major = roman == upperRoman,
        oct = 0,
        dir = dir
    )
}
