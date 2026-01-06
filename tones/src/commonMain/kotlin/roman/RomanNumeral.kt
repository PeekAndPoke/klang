package io.peekandpoke.klang.tones.roman

import io.peekandpoke.klang.tones.interval.Interval
import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.pitch.NamedPitch
import io.peekandpoke.klang.tones.pitch.Pitch

/**
 * Represents a roman numeral.
 */
data class RomanNumeral(
    /** The step number: 0 = I, 1 = II, ... 6 = VII */
    override val step: Int,
    /** Number of alterations: -1 = 'b', 0 = '', 1 = '#' */
    override val alt: Int,
    /** The octave (always 0 for roman numerals) */
    override val oct: Int? = 0,
    /** The direction (always 1 for roman numerals) */
    override val dir: Int? = 1,
    /** The full name of the roman numeral (e.g., "#VIIb5") */
    override val name: String = "",
    /** Whether the roman numeral is empty */
    val empty: Boolean = false,
    /** The roman numeral part (e.g., "VII") */
    val roman: String = "",
    /** The interval name (e.g., "7A") */
    val interval: String = "",
    /** The accidental string (e.g., "#") */
    val acc: String = "",
    /** The chord type (e.g., "b5") */
    val chordType: String = "",
    /** Whether the roman numeral is uppercase */
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

        /** Standard uppercase roman numerals. */
        private const val ROMANS = "I II III IV V VI VII"

        /** List of uppercase roman numerals. */
        private val NAMES = ROMANS.split(" ")

        /** List of lowercase roman numerals. */
        private val NAMES_MINOR = ROMANS.lowercase().split(" ")

        /**
         * Regular expression for parsing roman numeral strings.
         * Groups: 1: accidentals, 2: roman numeral, 3: chord type/rest.
         */
        private val REGEX = Regex("""^([#]{1,}|b{1,}|x{1,}|)(IV|I{1,3}|VI{0,2}|iv|i{1,3}|vi{0,2})([^IViv]*)$""")

        /**
         * Tokenizes a roman numeral string into [fullMatch, accidentals, romanNumeral, chordType].
         * @private
         */
        fun tokenize(str: String): List<String> {
            val (fullMatch, acc, roman, chordType) = REGEX.matchEntire(str)?.groupValues
                ?: return listOf("", "", "", "")
            return listOf(fullMatch, acc, roman, chordType)
        }

        /**
         * Get roman numeral names.
         *
         * @param major Whether to return major (uppercase) or minor (lowercase) names.
         */
        fun names(major: Boolean = true): List<String> {
            return if (major) NAMES.toList() else NAMES_MINOR.toList()
        }

        /**
         * Returns a [RomanNumeral] from a string.
         */
        fun get(name: String): RomanNumeral = parse(name)

        /**
         * Returns a [RomanNumeral] from a 0-based step index.
         */
        fun get(index: Int): RomanNumeral {
            val name = NAMES.getOrNull(index) ?: ""
            return if (name.isNotEmpty()) parse(name) else NoRomanNumeral
        }

        /**
         * Converts an [Interval] to a [RomanNumeral].
         */
        fun get(interval: Interval): RomanNumeral = get(interval as Pitch)

        /**
         * Converts a [Pitch] to a [RomanNumeral].
         */
        fun get(pitch: Pitch): RomanNumeral {
            return parse(Note.altToAcc(pitch.alt) + (NAMES.getOrNull(pitch.step) ?: ""))
        }

        /**
         * Converts a [NamedPitch] to a [RomanNumeral].
         */
        fun get(named: NamedPitch): RomanNumeral = get(named.name)

        /**
         * Returns the [RomanNumeral] itself.
         */
        fun get(roman: RomanNumeral): RomanNumeral = roman

        /**
         * Internal parser for roman numeral strings.
         */
        private fun parse(src: String): RomanNumeral {
            val tokens = tokenize(src)
            val name = tokens[0]
            val acc = tokens[1]
            val roman = tokens[2]
            val chordType = tokens[3]

            if (roman.isEmpty()) {
                return NoRomanNumeral
            }

            val upperRoman = roman.uppercase()
            val step = NAMES.indexOf(upperRoman)
            val alt = Note.accToAlt(acc)
            val dir = 1

            return RomanNumeral(
                empty = false,
                name = name,
                roman = roman,
                // The interval name relative to the tonic
                interval = Interval.get(Pitch(step, alt, 0, dir)).name,
                acc = acc,
                chordType = chordType,
                alt = alt,
                step = step,
                // If it's uppercase, it's major
                major = roman == upperRoman,
                oct = 0,
                dir = dir
            )
        }
    }
}
