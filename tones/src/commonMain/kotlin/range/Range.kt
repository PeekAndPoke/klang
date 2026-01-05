package io.peekandpoke.klang.tones.range

import io.peekandpoke.klang.tones.collection.TonalArray
import io.peekandpoke.klang.tones.midi.Midi

object TonalRange {
    /**
     * Create a numeric range. You supply a list of notes or numbers and it will
     * be connected to create complex ranges.
     *
     * @param notes The list of notes (strings) or MIDI numbers (Int/Double).
     * @return A list of MIDI numbers.
     */
    fun numeric(notes: List<Any>): List<Int> {
        val midi: List<Int> = TonalArray.compact(
            notes.map { note ->
                when (note) {
                    is Number -> note.toInt()
                    else -> Midi.toMidi(note)
                }
            }
        )

        if (notes.isEmpty() || midi.size != notes.size) {
            return emptyList()
        }

        if (midi.size == 1) return listOf(midi[0])

        val result = mutableListOf(midi[0])
        for (i in 1 until midi.size) {
            val last = result.last()
            val next = midi[i]
            result.addAll(TonalArray.range(last, next).drop(1))
        }

        return result
    }

    /**
     * Create a range of chromatic notes.
     *
     * @param notes The list of notes or MIDI note numbers to create a range from.
     * @param sharps Whether to use sharps or flats.
     * @param pitchClass Whether to return pitch classes only.
     * @return A list of note names.
     */
    fun chromatic(
        notes: List<Any>,
        sharps: Boolean = false,
        pitchClass: Boolean = false,
    ): List<String> {
        return numeric(notes).map { midi ->
            Midi.midiToNoteName(midi.toDouble(), sharps = sharps, pitchClass = pitchClass)
        }
    }
}
