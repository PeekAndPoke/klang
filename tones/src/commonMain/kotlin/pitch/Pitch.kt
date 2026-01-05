package io.peekandpoke.klang.tones.pitch

/**
 * Represents a pitch that has a name.
 */
interface NamedPitch {
    val name: String
}

/**
 * Creates a new [Pitch] instance.
 */
fun Pitch(step: Int, alt: Int, oct: Int? = null, dir: Int? = null): Pitch =
    PitchImpl(step, alt, oct, dir)

/**
 * Represents a pitch with step, alteration, octave and direction.
 */
interface Pitch {
    /** The step number: 0 = C, 1 = D, ... 6 = B */
    val step: Int

    /** Number of alterations: -2 = 'bb', -1 = 'b', 0 = '', 1 = '#', ... */
    val alt: Int

    /** The octave (null for pitch classes) */
    val oct: Int?

    /** Interval direction (null for notes) */
    val dir: Int?
}

/**
 * Default implementation of [Pitch].
 */
data class PitchImpl(
    override val step: Int,
    override val alt: Int,
    override val oct: Int? = null,
    override val dir: Int? = null,
) : Pitch

/**
 * Represents the coordinates of a pitch.
 */
sealed class PitchCoordinates {
    /** Pitch class coordinates: [fifths] */
    data class PitchClass(val fifths: Int) : PitchCoordinates()

    /** Note coordinates: [fifths, octaves] */
    data class Note(val fifths: Int, val octaves: Int) : PitchCoordinates()

    /** Interval coordinates: [fifths, octaves, direction] */
    data class Interval(val fifths: Int, val octaves: Int, val direction: Int) : PitchCoordinates()

    /** Returns the coordinates as a list of integers. */
    fun toList(): List<Int> = when (this) {
        is PitchClass -> listOf(fifths)
        is Note -> listOf(fifths, octaves)
        is Interval -> listOf(fifths, octaves, direction)
    }
}

private val SIZES = intArrayOf(0, 2, 4, 5, 7, 9, 11)

/**
 * Returns the chroma of a pitch (0-11).
 */
fun chroma(pitch: Pitch): Int = (SIZES[pitch.step] + pitch.alt + 120) % 12

/**
 * Returns the height of a pitch.
 */
fun height(pitch: Pitch): Int {
    val dir = pitch.dir ?: 1
    val oct = pitch.oct ?: -100
    return dir * (SIZES[pitch.step] + pitch.alt + 12 * oct)
}

/**
 * Returns the midi number of a pitch, or null if it's not a note.
 */
fun midi(pitch: Pitch): Int? {
    val h = height(pitch)
    return if (pitch.oct != null && h >= -12 && h <= 115) h + 12 else null
}

// The number of fifths of [C, D, E, F, G, A, B]
private val FIFTHS = intArrayOf(0, 2, 4, -1, 1, 3, 5)

// The number of octaves it span each step
private val STEPS_TO_OCTS = FIFTHS.map { fifths ->
    kotlin.math.floor((fifths * 7).toDouble() / 12).toInt()
}.toIntArray()

/**
 * Get coordinates from pitch object
 */
fun coordinates(pitch: Pitch): PitchCoordinates {
    val step = pitch.step
    val alt = pitch.alt
    val oct = pitch.oct
    val dir = pitch.dir ?: 1

    val f = FIFTHS[step] + 7 * alt
    if (oct == null) {
        return PitchCoordinates.PitchClass(dir * f)
    }
    val o = oct - STEPS_TO_OCTS[step] - 4 * alt
    return if (pitch.dir != null) {
        PitchCoordinates.Interval(dir * f, dir * o, dir)
    } else {
        PitchCoordinates.Note(dir * f, dir * o)
    }
}

// We need to get the steps from fifths
// Fifths for CDEFGAB are [ 0, 2, 4, -1, 1, 3, 5 ]
// We add 1 to fifths to avoid negative numbers, so:
// for ["F", "C", "G", "D", "A", "E", "B"] we have:
private val FIFTHS_TO_STEPS = intArrayOf(3, 0, 4, 1, 5, 2, 6)

/**
 * Get pitch from coordinate objects
 */
fun pitch(coord: PitchCoordinates): Pitch {
    return when (coord) {
        is PitchCoordinates.PitchClass -> {
            val f = coord.fifths
            val step = FIFTHS_TO_STEPS[unaltered(f)]
            val alt = kotlin.math.floor((f + 1).toDouble() / 7).toInt()
            Pitch(step = step, alt = alt)
        }

        is PitchCoordinates.Note -> {
            val f = coord.fifths
            val o = coord.octaves
            val step = FIFTHS_TO_STEPS[unaltered(f)]
            val alt = kotlin.math.floor((f + 1).toDouble() / 7).toInt()
            val oct = o + 4 * alt + STEPS_TO_OCTS[step]
            Pitch(step = step, alt = alt, oct = oct)
        }

        is PitchCoordinates.Interval -> {
            val f = coord.fifths
            val o = coord.octaves
            val dir = coord.direction
            val step = FIFTHS_TO_STEPS[unaltered(f)] // This might be wrong for negative dir in original TS?
            // Original TS: const [f, o, dir] = coord; const step = FIFTHS_TO_STEPS[unaltered(f)];
            val alt = kotlin.math.floor((f + 1).toDouble() / 7).toInt()
            val oct = o + 4 * alt + STEPS_TO_OCTS[step]
            Pitch(step = step, alt = alt, oct = oct, dir = dir)
        }
    }
}

// Return the number of fifths as if it were unaltered
private fun unaltered(f: Int): Int {
    val i = (f + 1) % 7
    return if (i < 0) 7 + i else i
}

/**
 * Returns true if the object is a [Pitch].
 */
fun isPitch(src: Any?): Boolean {
    return src is Pitch
}

/**
 * Returns true if the object is a [NamedPitch].
 */
fun isNamedPitch(src: Any?): Boolean {
    return src is NamedPitch
}
