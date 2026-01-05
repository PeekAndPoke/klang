package io.peekandpoke.klang.tones.distance

import io.peekandpoke.klang.tones.interval.coordToInterval
import io.peekandpoke.klang.tones.interval.interval
import io.peekandpoke.klang.tones.note.coordToNote
import io.peekandpoke.klang.tones.note.note
import io.peekandpoke.klang.tones.pitch.PitchCoordinates
import kotlin.math.floor

/**
 * Transpose a note by an interval.
 *
 * @param noteName The note or note name to transpose.
 * @param intervalName The interval or interval name to use for transposition.
 * @return The transposed note name or empty string if inputs are invalid.
 */
fun transpose(noteName: Any?, intervalName: Any?): String {
    val n = note(noteName)
    val i = interval(intervalName)

    if (n.empty || i.empty) {
        return ""
    }

    val noteCoord = n.coord ?: return ""
    val intervalCoord = i.coord ?: return ""

    val tr: PitchCoordinates = when (noteCoord) {
        is PitchCoordinates.PitchClass -> {
            val iFifths = when (intervalCoord) {
                is PitchCoordinates.PitchClass -> intervalCoord.fifths
                is PitchCoordinates.Note -> intervalCoord.fifths
                is PitchCoordinates.Interval -> intervalCoord.fifths
            }
            PitchCoordinates.PitchClass(noteCoord.fifths + iFifths)
        }

        is PitchCoordinates.Note -> {
            val iFifths = when (intervalCoord) {
                is PitchCoordinates.PitchClass -> intervalCoord.fifths
                is PitchCoordinates.Note -> intervalCoord.fifths
                is PitchCoordinates.Interval -> intervalCoord.fifths
            }
            val iOctaves = when (intervalCoord) {
                is PitchCoordinates.PitchClass -> 0
                is PitchCoordinates.Note -> intervalCoord.octaves
                is PitchCoordinates.Interval -> intervalCoord.octaves
            }
            PitchCoordinates.Note(noteCoord.fifths + iFifths, noteCoord.octaves + iOctaves)
        }

        is PitchCoordinates.Interval -> return ""
    }

    return coordToNote(tr).name
}

/**
 * Find the interval distance between two notes or pitch classes.
 *
 * @param fromNote The note or note name to calculate distance from.
 * @param toNote The note or note name to calculate distance to.
 * @return The interval name or empty string if inputs are invalid.
 */
fun distance(fromNote: Any?, toNote: Any?): String {
    val from = note(fromNote)
    val to = note(toNote)
    if (from.empty || to.empty) {
        return ""
    }

    val fcoord = from.coord ?: return ""
    val tcoord = to.coord ?: return ""

    val fifths = when (tcoord) {
        is PitchCoordinates.PitchClass -> tcoord.fifths
        is PitchCoordinates.Note -> tcoord.fifths
        is PitchCoordinates.Interval -> tcoord.fifths
    } - when (fcoord) {
        is PitchCoordinates.PitchClass -> fcoord.fifths
        is PitchCoordinates.Note -> fcoord.fifths
        is PitchCoordinates.Interval -> fcoord.fifths
    }

    val octs = if (fcoord is PitchCoordinates.Note && tcoord is PitchCoordinates.Note) {
        tcoord.octaves - fcoord.octaves
    } else {
        -floor((fifths * 7).toDouble() / 12).toInt()
    }

    val forceDescending = to.height == from.height &&
            to.midi != null &&
            from.oct == to.oct &&
            from.step > to.step

    return coordToInterval(PitchCoordinates.Note(fifths, octs), forceDescending).name
}

/**
 * Creates a function that transposes a list of intervals by a tonic.
 *
 * @param intervals A list of interval names.
 * @param tonic The tonic note name.
 * @return A function that takes a normalized degree (Int) and returns a note name.
 */
fun tonicIntervalsTransposer(
    intervals: List<String>,
    tonic: String?,
): (Int) -> String {
    val len = intervals.size
    return { normalized ->
        if (tonic == null) ""
        else {
            val index = if (normalized < 0) (len - (-normalized % len)) % len else normalized % len
            val octaves = floor(normalized.toDouble() / len).toInt()
            val intervalName = if (octaves >= 0) "P${octaves * 7 + 1}" else "P${octaves * 7 - 1}"

            val rootTransposed = transpose(tonic, intervalName)
            transpose(rootTransposed, intervals[index])
        }
    }
}
