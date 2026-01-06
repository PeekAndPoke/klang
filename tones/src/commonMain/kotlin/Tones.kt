package io.peekandpoke.klang.tones

import io.peekandpoke.klang.tones.midi.Midi
import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.scale.Scale

/**
 * Main entry point for tone/music theory operations.
 * Provides utility functions for converting between notes, MIDI numbers, and frequencies.
 */
object Tones {
    /**
     * Converts a MIDI number to frequency in Hz.
     */
    fun midiToFreq(m: Double): Double = Midi.midiToFreq(m)

    /**
     * Converts a note name to frequency in Hz.
     */
    fun noteToFreq(note: String): Double = midiToFreq(noteNameToMidi(note))

    /**
     * Converts a note name to MIDI number.
     * Accepts raw MIDI numbers, note names with octave (e.g., "C4"), or note names without octave (defaults to octave 3).
     */
    fun noteNameToMidi(noteRaw: String): Double {
        val s = noteRaw.trim()
        if (s.isEmpty()) return 69.0

        // 1. Raw MIDI number
        s.toDoubleOrNull()?.let { return it }

        // 2. Resolve note with default octave 3
        val hasOctave = s.any { it.isDigit() }
        val n = Note.get(if (hasOctave) s else s + "3")

        return n.midi?.toDouble() ?: n.height.toDouble().takeIf { !n.empty } ?: 69.0
    }

    /**
     * Resolves a note to frequency using optional scale context.
     * If scale is provided and note is a scale degree (MIDI < 30), maps it within the scale.
     */
    fun resolveFreq(note: String, scale: String?): Double {
        val midi = noteNameToMidi(note)

        // Heuristic: absolute notes (> 30) bypass scale logic
        if (scale.isNullOrBlank() || midi > 30.0) return midiToFreq(midi)

        // Resolve scale and map degree using 0-based steps
        val s = Scale.get(scale.replace(":", " "))
        val rootMidi = noteNameToMidi(s.tonic ?: "C3")

        return midiToFreq(Midi.pcSetSteps(s.chroma, rootMidi)(midi.toInt()))
    }
}
