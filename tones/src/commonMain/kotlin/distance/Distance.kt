package io.peekandpoke.klang.tones.distance

import io.peekandpoke.klang.tones.interval.Interval
import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.pitch.PitchCoordinates
import kotlin.math.floor

/**
 * Musical distance functions.
 */
object Distance {
    /**
     * Transpose a note by an interval.
     */
    fun transpose(note: String, interval: String): String {
        val n = Note.get(note)
        val i = Interval.get(interval)

        return transposeInternal(n, i)
    }

    fun transpose(note: Note, interval: String): String =
        transposeInternal(note, Interval.get(interval))

    fun transpose(note: String, interval: Interval): String =
        transposeInternal(Note.get(note), interval)

    fun transpose(note: Note, interval: Interval): String =
        transposeInternal(note, interval)

    private fun transposeInternal(n: Note, i: Interval): String {
        if (n.empty || i.empty) {
            return ""
        }

        if (n.empty || i.empty) {
            return ""
        }

        val noteCoord = n.coord ?: return ""
        val intervalCoord = i.coord ?: return ""

        // Transposition is done by adding the fifths and octaves of the note and the interval coordinates.
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

        return Note.fromCoord(tr).name
    }

    /**
     * Transpose a note by a number of perfect fifths.
     *
     * @param noteName The note name.
     * @param fifths The number of fifths.
     * @return The transposed note name.
     */
    fun transposeFifths(noteName: String, fifths: Int): String {
        val n = Note.get(noteName)
        if (n.empty) return ""
        val noteCoord = n.coord ?: return ""

        val tr: PitchCoordinates = when (noteCoord) {
            is PitchCoordinates.PitchClass -> {
                PitchCoordinates.PitchClass(noteCoord.fifths + fifths)
            }

            is PitchCoordinates.Note -> {
                PitchCoordinates.Note(noteCoord.fifths + fifths, noteCoord.octaves)
            }

            else -> return ""
        }
        return Note.fromCoord(tr).name
    }

    /**
     * Transpose a note by a number of octaves.
     *
     * @param noteName The note name.
     * @param octaves The number of octaves.
     * @return The transposed note name.
     */
    fun transposeOctaves(noteName: String, octaves: Int): String {
        val n = Note.get(noteName)
        if (n.empty || n.oct == null) return n.name
        return n.pc + (n.oct + octaves)
    }

    /**
     * Find the interval distance between two notes.
     */
    fun distance(from: String, to: String): String =
        distanceInternal(Note.get(from), Note.get(to))

    fun distance(from: Note, to: String): String =
        distanceInternal(from, Note.get(to))

    fun distance(from: String, to: Note): String =
        distanceInternal(Note.get(from), to)

    fun distance(from: Note, to: Note): String =
        distanceInternal(from, to)

    private fun distanceInternal(from: Note, to: Note): String {
        if (from.empty || to.empty) {
            return ""
        }

        val fcoord = from.coord ?: return ""
        val tcoord = to.coord ?: return ""

        // Calculate the difference in fifths between the two notes.
        val fifths = when (tcoord) {
            is PitchCoordinates.PitchClass -> tcoord.fifths
            is PitchCoordinates.Note -> tcoord.fifths
            is PitchCoordinates.Interval -> tcoord.fifths
        } - when (fcoord) {
            is PitchCoordinates.PitchClass -> fcoord.fifths
            is PitchCoordinates.Note -> fcoord.fifths
            is PitchCoordinates.Interval -> fcoord.fifths
        }

        // Calculate the difference in octaves.
        // If both are notes, use the octave coordinates directly.
        // Otherwise, calculate octaves based on the fifths difference.
        val octs = if (fcoord is PitchCoordinates.Note && tcoord is PitchCoordinates.Note) {
            tcoord.octaves - fcoord.octaves
        } else {
            -floor((fifths * 7).toDouble() / 12).toInt()
        }

        // Handle edge case for unison descending intervals (e.g. C4 to C4 descending)
        val forceDescending = to.height == from.height &&
                to.midi != null &&
                from.oct == to.oct &&
                from.step > to.step

        return Interval.fromCoord(PitchCoordinates.Note(fifths, octs), forceDescending).name
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
            if (tonic == null) {
                ""
            } else {
                // Handle negative degrees and octave wrapping
                val index = if (normalized < 0) (len - (-normalized % len)) % len else normalized % len
                val octaves = floor(normalized.toDouble() / len).toInt()

                // Calculate the interval name for the octave shift
                val intervalName = if (octaves >= 0) "P${octaves * 7 + 1}" else "P${octaves * 7 - 1}"

                // Transpose the tonic by the octave shift, then by the interval at the index
                val rootTransposed = transpose(tonic, intervalName)
                transpose(rootTransposed, intervals[index])
            }
        }
    }
}
