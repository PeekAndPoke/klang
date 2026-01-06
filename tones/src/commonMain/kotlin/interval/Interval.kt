package io.peekandpoke.klang.tones.interval

import io.peekandpoke.klang.tones.interval.Interval.Companion.INTERVAL_SHORTHAND_REGEX
import io.peekandpoke.klang.tones.interval.Interval.Companion.INTERVAL_TONAL_REGEX
import io.peekandpoke.klang.tones.pitch.NamedPitch
import io.peekandpoke.klang.tones.pitch.Pitch
import io.peekandpoke.klang.tones.pitch.PitchCoordinates

/** A string representing an interval name (e.g., "P5", "M3", "-2m"). */
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

        /** Cache for parsed intervals. */
        private val cache = mutableMapOf<String, Interval>()

        /**
         * Returns an [Interval] from a string name.
         */
        fun get(name: String): Interval = cache.getOrPut(name) { parse(name) }

        /**
         * Returns the [Interval] itself.
         */
        fun get(interval: Interval): Interval = interval

        /**
         * Converts a [Pitch] to an [Interval].
         */
        fun get(pitch: Pitch): Interval = get(pitchName(pitch))

        /**
         * Converts a [NamedPitch] to an [Interval].
         */
        fun get(named: NamedPitch): Interval = get(named.name)

        // Tokenizes an interval string into [number, quality]
        fun tokenize(str: String?): List<String> {
            if (str == null) return listOf("", "")

            TONAL_REG.matchEntire(str)?.groupValues?.let { (_, num, quality) ->
                return listOf(num, quality)
            }
            SHORTHAND_REG.matchEntire(str)?.groupValues?.let { (_, quality, num) ->
                return listOf(num, quality)
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
            // Step inversion: 0->0 (unison), 1->6 (second to seventh), etc.
            val step = (7 - i.step) % 7
            // Quality inversion: Perfect stays Perfect, Major becomes minor (-1 alt shift), etc.
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
        fun add(a: String, b: String): String = add(get(a), get(b))

        /**
         * Adds two intervals together and returns the name of the resulting interval.
         */
        fun add(i1: Interval, i2: Interval): String {
            val c1 = i1.coord
            val c2 = i2.coord
            if (c1 == null || c2 == null) return ""

            // We use the coordinate representation (fifths, octaves) to perform interval arithmetic
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
        fun subtract(a: String, b: String): String = subtract(get(a), get(b))

        /**
         * Subtracts the second interval from the first one and returns the name of the resulting interval.
         */
        fun subtract(i1: Interval, i2: Interval): String {
            val c1 = i1.coord
            val c2 = i2.coord
            if (c1 == null || c2 == null) return ""

            // We use the coordinate representation (fifths, octaves) to perform interval arithmetic
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

            // Extract fifths and octaves from current coordinates
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

            // Transpose by shifting fifths, then convert back to interval
            return fromCoord(PitchCoordinates.Note(f + fifths, o)).name
        }

        /**
         * Returns an [Interval] from [PitchCoordinates].
         *
         * @param coord The pitch coordinates.
         * @param forceDescending Whether to force the interval to be descending.
         */
        fun fromCoord(
            coord: PitchCoordinates,
            forceDescending: Boolean = false,
        ): Interval {
            return when (coord) {
                is PitchCoordinates.PitchClass -> {
                    val f = coord.fifths
                    // An interval is descending if it has a negative total semitone count
                    val isDescending = f * 7 < 0
                    val ivl = if (forceDescending || isDescending) {
                        // For descending intervals, we invert the coordinates and direction
                        PitchCoordinates.Interval(-f, 0, -1)
                    } else {
                        PitchCoordinates.Interval(f, 0, 1)
                    }
                    get(Pitch.pitch(ivl))
                }

                is PitchCoordinates.Note -> {
                    val f = coord.fifths
                    val o = coord.octaves
                    // Total semitones: fifths * 7 + octaves * 12
                    val isDescending = f * 7 + o * 12 < 0
                    val ivl = if (forceDescending || isDescending) {
                        PitchCoordinates.Interval(-f, -o, -1)
                    } else {
                        PitchCoordinates.Interval(f, o, 1)
                    }
                    get(Pitch.pitch(ivl))
                }

                is PitchCoordinates.Interval -> {
                    get(Pitch.pitch(coord))
                }
            }
        }

        // Parses an interval string and returns an Interval
        private fun parse(str: String): Interval {
            val tokens = tokenize(str)
            if (tokens[0] == "") {
                return NoInterval
            }
            val num = tokens[0].toInt()
            val q = tokens[1]
            // Step is 0-indexed: 0=unison, 1=second, ..., 6=seventh
            val step = (kotlin.math.abs(num) - 1) % 7
            val t = TYPES[step]
            if (t == 'M' && q == "P") {
                // Majorable intervals (2nd, 3rd, 6th, 7th) cannot be Perfect
                return NoInterval
            }
            val type = if (t == 'M') IntervalType.Majorable else IntervalType.Perfectable

            val name = "" + num + q
            val dir = if (num < 0) -1 else 1
            // Simple number is the interval number within one octave (e.g., 9th becomes 2nd)
            val simple = if (num == 8 || num == -8) num else dir * (step + 1)
            val alt = qToAlt(type, q)
            val oct = (kotlin.math.abs(num) - 1) / 7
            // Calculate semitones based on natural size of the step, alterations, and octaves
            val semitones = dir * (SIZES[step] + alt + 12 * oct)
            val chroma = (((dir * (SIZES[step] + alt)) % 12) + 12) % 12
            val coord = Pitch.coordinates(Pitch(step, alt, oct, dir))

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

        // Converts an interval quality string to the number of alterations
        private fun qToAlt(type: IntervalType, q: String): Int {
            return when {
                // Perfect or Major intervals have 0 alterations
                (q == "M" && type == IntervalType.Majorable) || (q == "P" && type == IntervalType.Perfectable) -> 0
                // minor intervals have -1 alteration
                q == "m" && type == IntervalType.Majorable -> -1
                // Augmented intervals 'A', 'AA', etc.
                A_REGEX.matches(q) -> q.length
                // diminished intervals 'd', 'dd', etc.
                D_REGEX.matches(q) -> -1 * (if (type == IntervalType.Perfectable) q.length else q.length + 1)
                else -> 0
            }
        }

        // Returns the name of an interval from its Pitch properties
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

        // Converts the number of alterations to an interval quality string
        private fun altToQ(type: IntervalType, alt: Int): String {
            return when {
                alt == 0 -> if (type == IntervalType.Majorable) "M" else "P"
                alt == -1 && type == IntervalType.Majorable -> "m"
                alt > 0 -> "A".repeat(alt)
                // for diminished, Majorable needs one more alteration than Perfectable to reach 'd' (minor is -1)
                else -> "d".repeat(if (type == IntervalType.Perfectable) kotlin.math.abs(alt) else kotlin.math.abs(alt) - 1)
            }
        }

        /** Natural sizes of intervals (number of semitones for major/perfect intervals). */
        private val SIZES = intArrayOf(0, 2, 4, 5, 7, 9, 11)

        /** The quality type of each step (Perfect or Major). */
        private const val TYPES = "PMMPPMM"

        /** Regex for augmented qualities (A, AA, ...). */
        private val A_REGEX = Regex("^A+$")

        /** Regex for diminished qualities (d, dd, ...). */
        private val D_REGEX = Regex("^d+$")

        /** Mapping from chroma to interval number (1-indexed). */
        private val IN = intArrayOf(1, 2, 2, 3, 3, 4, 5, 5, 6, 6, 7, 7)

        /** Mapping from chroma to interval quality. */
        private val IQ = "P m M m M P d P m M m M".split(" ")

        /** Regular expression for tonal interval notation (e.g., "5P", "-3M"). */
        private const val INTERVAL_TONAL_REGEX = """^([-+]?\d+)(d{1,4}|m|M|P|A{1,4})$"""

        /** Regular expression for shorthand interval notation (e.g., "P5", "M3"). */
        private const val INTERVAL_SHORTHAND_REGEX = """^(AA|A|P|M|m|d|dd)([-+]?\d+)$"""

        /** Compiled [INTERVAL_TONAL_REGEX]. */
        private val TONAL_REG = Regex(INTERVAL_TONAL_REGEX)

        /** Compiled [INTERVAL_SHORTHAND_REGEX]. */
        private val SHORTHAND_REG = Regex(INTERVAL_SHORTHAND_REGEX)
    }
}
