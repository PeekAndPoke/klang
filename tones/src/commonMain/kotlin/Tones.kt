package io.peekandpoke.klang.tones

import io.peekandpoke.klang.tones.midi.Midi
import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.scale.Scale

object Tones {
    fun midiToFreq(m: Double): Double = Midi.midiToFreq(m)

    fun noteToFreq(note: String): Double = midiToFreq(noteNameToMidi(note))

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
