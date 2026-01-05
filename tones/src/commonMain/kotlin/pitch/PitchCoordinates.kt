package io.peekandpoke.klang.tones.pitch

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
