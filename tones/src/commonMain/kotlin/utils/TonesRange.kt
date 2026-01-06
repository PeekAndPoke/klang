package io.peekandpoke.klang.tones.utils

import io.peekandpoke.klang.tones.midi.Midi
import kotlin.jvm.JvmName

/**
 * Functions for creating ranges of notes and MIDI numbers.
 */
object TonesRange {
    /**
     * Create a numeric range from a list of note names.
     */
    fun numeric(notes: List<String>): List<Int> {
        val midi: List<Int> = TonesArray.compact(
            notes.map { note -> Midi.toMidi(note) }
        )
        return buildNumeric(midi, notes.size)
    }

    /**
     * Create a numeric range from a list of MIDI numbers.
     */
    @JvmName("numericFromInt")
    fun numeric(midi: List<Int>): List<Int> {
        return buildNumeric(midi, midi.size)
    }

    /**
     * Create a range of chromatic notes from note names.
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
     * Create a range of chromatic notes from MIDI numbers.
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
