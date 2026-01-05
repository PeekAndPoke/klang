package io.peekandpoke.klang.tones.interval

import io.peekandpoke.klang.tones.pitch.NamedPitch
import io.peekandpoke.klang.tones.pitch.Pitch
import io.peekandpoke.klang.tones.pitch.PitchCoordinates
import io.peekandpoke.klang.tones.pitch.TonalPitch

typealias IntervalName = String

/**
 * Types of intervals.
 */
enum class IntervalType {
    Perfectable,
    Majorable;

    override fun toString(): String = when (this) {
        Perfectable -> "perfectable"
        Majorable -> "majorable"
    }
}

/**
 * Represents a musical interval.
 */
data class Interval(
    /** The step number: 0 = unison, 1 = second, ... 6 = seventh. */
    override val step: Int,
    /** Number of alterations: -2 = 'dd', -1 = 'd', 0 = 'P'/'M', 1 = 'A', ... */
    override val alt: Int,
    /** The number of octaves. */
    override val oct: Int = 0,
    /** Interval direction (1 ascending, -1 descending). */
    override val dir: Int = 1,
    /** Whether the interval is empty (represents "no interval"). */
    val empty: Boolean = false,
    /** The full name of the interval (e.g., "P5", "-M3"). */
    override val name: String = "",
    /** The interval number (e.g., 5 for "P5", -3 for "-M3"). */
    val num: Int = Int.MIN_VALUE,
    /** The interval quality (e.g., "P", "M", "m", "d", "A"). */
    val q: String = "",
    /** The interval type (Perfectable or Majorable). */
    val type: IntervalType? = null,
    /** The simplified interval number (within one octave). */
    val simple: Int = Int.MIN_VALUE,
    /** The size of the interval in semitones. */
    val semitones: Int = Int.MIN_VALUE,
    /** The interval chroma (0-11). */
    val chroma: Int = -1,
    /** The pitch coordinates. */
    val coord: PitchCoordinates? = null,
) : NamedPitch, Pitch {
    companion object {
        val NoInterval = Interval(
            step = -1,
            alt = -1,
            empty = true,
            name = "",
            num = Int.MIN_VALUE,
            q = "",
            type = null,
            simple = Int.MIN_VALUE,
            semitones = Int.MIN_VALUE,
            chroma = -1,
            coord = null
        )

        /**
         * Returns an [Interval] from a given source.
         */
        fun get(src: Any?): Interval {
            return when (src) {
                is String -> parse(src)
                is Interval -> src
                is Pitch -> get(pitchName(src))
                is NamedPitch -> get(src.name)
                else -> NoInterval
            }
        }

        /**
         * Tokenizes an interval string.
         * @private
         */
        fun tokenize(str: String?): List<String> {
            if (str == null) return listOf("", "")

            TONAL_REG.matchEntire(str)?.let { m ->
                return listOf(m.groupValues[1], m.groupValues[2])
            }
            SHORTHAND_REG.matchEntire(str)?.let { m ->
                return listOf(m.groupValues[2], m.groupValues[1])
            }
            return listOf("", "")
        }

        /**
         * Returns true if the object is a valid [Interval].
         */
        fun isInterval(src: Any?): Boolean {
            return src is Interval && !src.empty
        }

        /**
         * Returns the natural list of interval names.
         */
        fun names(): List<String> = "1P 2M 3M 4P 5P 6m 7m".split(" ")

        /**
         * Returns the simplified version of an interval.
         *
         * @param name The interval name.
         */
        fun simplify(name: String): String {
            val i = get(name)
            return if (i.empty) "" else "${i.simple}${i.q}"
        }

        /**
         * Returns the inversion of an interval.
         *
         * @param name The interval name.
         */
        fun invert(name: String): String {
            val i = get(name)
            if (i.empty) return ""
            val step = (7 - i.step) % 7
            val alt = if (i.type == IntervalType.Perfectable) -i.alt else -(i.alt + 1)
            return get(Pitch(step = step, alt = alt, oct = i.oct, dir = i.dir)).name
        }

        /**
         * Returns an interval name from a number of semitones.
         *
         * @param semitones The number of semitones.
         */
        fun fromSemitones(semitones: Int): String {
            val d = if (semitones < 0) -1 else 1
            val n = kotlin.math.abs(semitones)
            val c = n % 12
            val o = n / 12
            val resNum = d * (IN[c] + 7 * o)
            return "$resNum${IQ[c]}"
        }

        /**
         * Adds two intervals together and returns the name of the resulting interval.
         */
        fun add(a: String, b: String): String {
            val i1 = get(a)
            val i2 = get(b)
            val c1 = i1.coord
            val c2 = i2.coord
            if (c1 == null || c2 == null) return ""

            val f1 = when (c1) {
                is PitchCoordinates.Interval -> c1.fifths
                is PitchCoordinates.Note -> c1.fifths
                is PitchCoordinates.PitchClass -> c1.fifths
            }
            val o1 = when (c1) {
                is PitchCoordinates.Interval -> c1.octaves
                is PitchCoordinates.Note -> c1.octaves
                is PitchCoordinates.PitchClass -> 0
            }
            val f2 = when (c2) {
                is PitchCoordinates.Interval -> c2.fifths
                is PitchCoordinates.Note -> c2.fifths
                is PitchCoordinates.PitchClass -> c2.fifths
            }
            val o2 = when (c2) {
                is PitchCoordinates.Interval -> c2.octaves
                is PitchCoordinates.Note -> c2.octaves
                is PitchCoordinates.PitchClass -> 0
            }

            return fromCoord(PitchCoordinates.Note(f1 + f2, o1 + o2)).name
        }

        /**
         * Subtracts the second interval from the first one and returns the name of the resulting interval.
         */
        fun subtract(a: String, b: String): String {
            val i1 = get(a)
            val i2 = get(b)
            val c1 = i1.coord
            val c2 = i2.coord
            if (c1 == null || c2 == null) return ""

            val f1 = when (c1) {
                is PitchCoordinates.Interval -> c1.fifths
                is PitchCoordinates.Note -> c1.fifths
                is PitchCoordinates.PitchClass -> c1.fifths
            }
            val o1 = when (c1) {
                is PitchCoordinates.Interval -> c1.octaves
                is PitchCoordinates.Note -> c1.octaves
                is PitchCoordinates.PitchClass -> 0
            }
            val f2 = when (c2) {
                is PitchCoordinates.Interval -> c2.fifths
                is PitchCoordinates.Note -> c2.fifths
                is PitchCoordinates.PitchClass -> c2.fifths
            }
            val o2 = when (c2) {
                is PitchCoordinates.Interval -> c2.octaves
                is PitchCoordinates.Note -> c2.octaves
                is PitchCoordinates.PitchClass -> 0
            }

            return fromCoord(PitchCoordinates.Note(f1 - f2, o1 - o2)).name
        }

        /**
         * Transpose an interval by a number of perfect fifths.
         *
         * @param intervalName The interval name.
         * @param fifths The number of fifths.
         */
        fun transposeFifths(intervalName: String, fifths: Int): String {
            val i = get(intervalName)
            if (i.empty) return ""
            val c = i.coord ?: return ""

            val f = when (c) {
                is PitchCoordinates.Interval -> c.fifths
                is PitchCoordinates.Note -> c.fifths
                is PitchCoordinates.PitchClass -> c.fifths
            }
            val o = when (c) {
                is PitchCoordinates.Interval -> c.octaves
                is PitchCoordinates.Note -> c.octaves
                is PitchCoordinates.PitchClass -> 0
            }

            return fromCoord(PitchCoordinates.Note(f + fifths, o)).name
        }

        fun fromCoord(
            coord: PitchCoordinates,
            forceDescending: Boolean = false,
        ): Interval {
            return when (coord) {
                is PitchCoordinates.PitchClass -> {
                    val f = coord.fifths
                    val isDescending = f * 7 < 0
                    val ivl = if (forceDescending || isDescending) {
                        PitchCoordinates.Interval(-f, 0, -1)
                    } else {
                        PitchCoordinates.Interval(f, 0, 1)
                    }
                    get(TonalPitch.pitch(ivl))
                }

                is PitchCoordinates.Note -> {
                    val f = coord.fifths
                    val o = coord.octaves
                    val isDescending = f * 7 + o * 12 < 0
                    val ivl = if (forceDescending || isDescending) {
                        PitchCoordinates.Interval(-f, -o, -1)
                    } else {
                        PitchCoordinates.Interval(f, o, 1)
                    }
                    get(TonalPitch.pitch(ivl))
                }

                is PitchCoordinates.Interval -> {
                    get(TonalPitch.pitch(coord))
                }
            }
        }

        private fun parse(str: String): Interval {
            val tokens = tokenize(str)
            if (tokens[0] == "") {
                return NoInterval
            }
            val num = tokens[0].toInt()
            val q = tokens[1]
            val step = (kotlin.math.abs(num) - 1) % 7
            val t = TYPES[step]
            if (t == 'M' && q == "P") {
                return NoInterval
            }
            val type = if (t == 'M') IntervalType.Majorable else IntervalType.Perfectable

            val name = "" + num + q
            val dir = if (num < 0) -1 else 1
            val simple = if (num == 8 || num == -8) num else dir * (step + 1)
            val alt = qToAlt(type, q)
            val oct = (kotlin.math.abs(num) - 1) / 7
            val semitones = dir * (SIZES[step] + alt + 12 * oct)
            val chroma = (((dir * (SIZES[step] + alt)) % 12) + 12) % 12
            val coord = TonalPitch.coordinates(Pitch(step, alt, oct, dir))

            return Interval(
                empty = false,
                name = name,
                num = num,
                q = q,
                step = step,
                alt = alt,
                dir = dir,
                type = type,
                simple = simple,
                semitones = semitones,
                chroma = chroma,
                coord = coord,
                oct = oct
            )
        }

        private fun qToAlt(type: IntervalType, q: String): Int {
            return when {
                (q == "M" && type == IntervalType.Majorable) || (q == "P" && type == IntervalType.Perfectable) -> 0
                q == "m" && type == IntervalType.Majorable -> -1
                A_REGEX.matches(q) -> q.length
                D_REGEX.matches(q) -> -1 * (if (type == IntervalType.Perfectable) q.length else q.length + 1)
                else -> 0
            }
        }

        private fun pitchName(props: Pitch): String {
            val step = props.step
            val alt = props.alt
            val oct = props.oct ?: 0
            val dir = props.dir ?: 0

            if (dir == 0) {
                return ""
            }
            val calcNum = step + 1 + 7 * oct
            // this is an edge case: descending pitch class unison (see #243)
            val num = if (calcNum == 0) step + 1 else calcNum
            val d = if (dir < 0) "-" else ""
            val type = if (TYPES[step] == 'M') IntervalType.Majorable else IntervalType.Perfectable
            return d + num + altToQ(type, alt)
        }

        private fun altToQ(type: IntervalType, alt: Int): String {
            return when {
                alt == 0 -> if (type == IntervalType.Majorable) "M" else "P"
                alt == -1 && type == IntervalType.Majorable -> "m"
                alt > 0 -> "A".repeat(alt)
                else -> "d".repeat(if (type == IntervalType.Perfectable) kotlin.math.abs(alt) else kotlin.math.abs(alt) - 1)
            }
        }

        private val SIZES = intArrayOf(0, 2, 4, 5, 7, 9, 11)
        private const val TYPES = "PMMPPMM"
        private val A_REGEX = Regex("^A+$")
        private val D_REGEX = Regex("^d+$")
        private val IN = intArrayOf(1, 2, 2, 3, 3, 4, 5, 5, 6, 6, 7, 7)
        private val IQ = "P m M m M P d P m M m M".split(" ")

        // shorthand tonal notation (with quality after number)
        private const val INTERVAL_TONAL_REGEX = """^([-+]?\d+)(d{1,4}|m|M|P|A{1,4})$"""

        // standard shorthand notation (with quality before number)
        private const val INTERVAL_SHORTHAND_REGEX = """^(AA|A|P|M|m|d|dd)([-+]?\d+)$"""

        private val TONAL_REG = Regex(INTERVAL_TONAL_REGEX)
        private val SHORTHAND_REG = Regex(INTERVAL_SHORTHAND_REGEX)
    }
}
