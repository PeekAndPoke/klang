package io.peekandpoke.klang.tones.utils

import io.peekandpoke.klang.tones.midi.Midi
import kotlin.jvm.JvmName

/**
 * Functions for creating continuous ranges of notes and MIDI numbers.
 *
 * Given a sparse list of endpoints (note names or MIDI numbers), these helpers
 * fill in every semitone between successive pairs to produce a complete chromatic
 * range. For example, `["C4", "E4"]` becomes `[60, 61, 62, 63, 64]`.
 */
object TonesRange {
    /**
     * Creates a continuous MIDI number range by filling in every semitone between the given notes.
     *
     * @param notes Note names (e.g. `["C4", "E4"]`). Invalid names cause an empty result.
     * @return A list of MIDI numbers covering every semitone between successive notes.
     */
    fun numeric(notes: List<String>): List<Int> {
        val midi: List<Int> = TonesArray.compact(
            notes.map { note -> Midi.toMidi(note) }
        )
        return buildNumeric(midi, notes.size)
    }

    /**
     * Creates a continuous MIDI number range by filling in every semitone between the given values.
     *
     * @param midi MIDI numbers to interpolate between.
     * @return A list of MIDI numbers covering every semitone between successive inputs.
     */
    @JvmName("numericFromInt")
    fun numeric(midi: List<Int>): List<Int> {
        return buildNumeric(midi, midi.size)
    }

    /**
     * Creates a chromatic note-name range between the given notes.
     *
     * @param notes Note names defining the range endpoints (e.g. `["C4", "E4"]`).
     * @param sharps If `true`, use sharps (`F#`) instead of flats (`Gb`) for accidentals.
     * @param pitchClass If `true`, omit octave numbers from the output names.
     * @return Note names for every semitone between successive inputs.
     */
    fun chromatic(
        notes: List<String>,
        sharps: Boolean = false,
        pitchClass: Boolean = false,
    ): List<String> {
        return numeric(notes).map { midi ->
            Midi.midiToNoteName(midi.toDouble(), sharps = sharps, pitchClass = pitchClass)
        }
    }

    /**
     * Creates a chromatic note-name range between the given MIDI numbers.
     *
     * @param midi MIDI numbers defining the range endpoints.
     * @param sharps If `true`, use sharps (`F#`) instead of flats (`Gb`) for accidentals.
     * @param pitchClass If `true`, omit octave numbers from the output names.
     * @return Note names for every semitone between successive inputs.
     */
    @JvmName("chromaticFromInt")
    fun chromatic(
        midi: List<Int>,
        sharps: Boolean = false,
        pitchClass: Boolean = false,
    ): List<String> {
        return numeric(midi).map { m ->
            Midi.midiToNoteName(m.toDouble(), sharps = sharps, pitchClass = pitchClass)
        }
    }

    // Builds a numeric range by filling in all MIDI numbers between the given notes
    private fun buildNumeric(midi: List<Int>, originalSize: Int): List<Int> {
        if (midi.isEmpty() || midi.size != originalSize) {
            return emptyList()
        }

        if (midi.size == 1) return listOf(midi[0])

        val result = mutableListOf(midi[0])
        for (i in 1 until midi.size) {
            val last = result.last()
            val next = midi[i]
            result.addAll(TonesArray.range(last, next).drop(1))
        }

        return result
    }
}
