package io.peekandpoke.klang.tones

import io.peekandpoke.klang.tones.midi.Midi
import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.scale.ScaleTypeDictionary
import kotlin.math.floor

object Tones {
    fun midiToFreq(m: Double): Double = Midi.midiToFreq(m)

    fun noteToFreq(note: String): Double = midiToFreq(noteNameToMidi(note))

    fun noteNameToMidi(noteRaw: String): Double {
        if (noteRaw.isBlank()) return 69.0
        val s = noteRaw.trim()

        // 1. Raw MIDI number (e.g. "60")
        s.toDoubleOrNull()?.let { return it }

        // 2. Standardize accidentals (support 's'/'f' legacy)
        // We preserve the first character to avoid changing an 'f' note to a 'b' note.
        val standard = if (s.length > 1) {
            s[0] + s.substring(1).replace('s', '#').replace('f', 'b')
        } else {
            s
        }

        // 3. Handle default octave
        // Tones.kt expects "C" to mean "C3" (MIDI 48).
        val hasDigit = standard.any { it.isDigit() }
        val finalNote = if (!hasDigit && standard.isNotEmpty() && standard[0].lowercaseChar() in 'a'..'g') {
            standard + "3"
        } else {
            standard
        }

        val n = Note.get(finalNote)
        return if (!n.empty && n.midi != null) {
            n.midi!!.toDouble()
        } else if (!n.empty && n.height != -1) {
            n.height.toDouble()
        } else {
            69.0 // Fallback to A4
        }
    }

    fun resolveFreq(note: String, scale: String?): Double {
        val midi = noteNameToMidi(note)

        // If no scale context, or absolute note (> 30), return absolute frequency
        if (scale.isNullOrBlank() || midi > 30.0) {
            return midiToFreq(midi)
        }

        val degree = midi.toInt()

        // Normalize scale string: handle legacy s/f only when they follow a note letter
        val normalizedScale = scale.replace(":", " ")
            .replace(Regex("(?<=[a-gA-G])s"), "#")
            .replace(Regex("(?<=[a-gA-G])f"), "b")

        // Parse Scale Context: "C4 minor" -> Root: "C4", Type: "minor"
        val parts = normalizedScale.split(" ").filter { it.isNotBlank() }
        val rootNote = parts.getOrNull(0) ?: "C3"
        val scaleType = parts.getOrNull(1) ?: "major"

        val rootMidi = noteNameToMidi(rootNote)

        // Resolve scale type and calculate degree
        val st = ScaleTypeDictionary.get(scaleType)
        val chroma = if (!st.empty) st.chroma else "101011010101" // Default to Major
        val set = Midi.pcSet(chroma)
        val len = set.size

        if (len == 0) return midiToFreq(rootMidi + degree)

        // Use original degree calculation logic for backward compatibility
        val octaves = floor(degree.toDouble() / len).toInt()
        val index = ((degree % len) + len) % len
        val semitoneOffset = set[index]

        val finalMidi = rootMidi + (octaves * 12) + semitoneOffset
        return midiToFreq(finalMidi)
    }
}
