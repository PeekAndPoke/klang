package io.peekandpoke

import kotlin.math.floor
import kotlin.math.pow

object StrudelNotes {
    val noteIndex = mapOf(
        "c" to 0, "d" to 2, "e" to 4, "f" to 5, "g" to 7, "a" to 9, "b" to 11
    )

    val accIndex = mapOf(
        '#' to 1, 'b' to -1, 's' to 1, 'f' to -1
    )

    // Exact Regex from JS 'tokenizeNote':
    // Group 1: PC ([a-gA-G])
    // Group 2: Acc ([#bsf]*)
    // Group 3: Oct (-?[0-9]*) -> Includes optional minus sign for C-1
    private val NOTE_REGEX = Regex("""^([a-gA-G])([#bsf]*)(-?[0-9]*)$""")

    private val SCALES = mapOf(
        "major" to listOf(0, 2, 4, 5, 7, 9, 11),
        "minor" to listOf(0, 2, 3, 5, 7, 8, 10),
        "dorian" to listOf(0, 2, 3, 5, 7, 9, 10),
        "phrygian" to listOf(0, 1, 3, 5, 7, 8, 10),
        "lydian" to listOf(0, 2, 4, 6, 7, 9, 10),
        "mixolydian" to listOf(0, 2, 4, 5, 7, 9, 10),
        "locrian" to listOf(0, 1, 3, 5, 6, 8, 10)
    )

    fun midiToFreq(m: Double): Double = 440.0 * 2.0.pow((m - 69.0) / 12.0)

    fun noteNameToMidi(noteRaw: String): Double {
        if (noteRaw.isEmpty()) return 69.0
        val note = noteRaw.trim()

        // 1. Raw MIDI number (e.g. "60")
        note.toDoubleOrNull()?.let { return it }

        // 2. Parse scientific pitch notation
        val match = NOTE_REGEX.matchEntire(note) ?: return 69.0 // Fallback to A4

        val (pc, acc, oct) = match.destructured

        val chroma = noteIndex[pc.lowercase()] ?: 9
        val offset = acc.sumOf { char -> accIndex[char] ?: 0 }

        // JS Logic: if oct is empty string, default to 3. If "-1", parse as -1.
        val octave = if (oct.isNotEmpty()) oct.toInt() else 3

        return ((octave + 1) * 12 + chroma + offset).toDouble()
    }

    fun resolveFreq(note: String, scale: String?): Double {
        val midi = noteNameToMidi(note)

        // If no scale context, simply return the absolute frequency
        if (scale.isNullOrBlank()) {
            return midiToFreq(midi)
        }

        // Heuristic: Strudel represents Scale Degrees 0, 1, 2 as MIDI notes C-1, C#-1, D-1 (0, 1, 2).
        // If the MIDI note is very low (< 30), we treat it as a Scale Degree.
        // If it's higher (e.g. C2=36, C4=60), we assume it's an absolute note overriding the scale.
        if (midi > 30.0) {
            return midiToFreq(midi)
        }

        // It's a scale degree!
        val degree = midi.toInt()

        // Parse Scale Context: "C4 minor" -> Root: "C4", Type: "minor"
        val parts = scale.split(" ", ":").filter { it.isNotBlank() }
        val rootNote = parts.getOrNull(0) ?: "C3"
        val scaleType = parts.getOrNull(1) ?: "major"

        val rootMidi = noteNameToMidi(rootNote)
        val intervals = SCALES[scaleType] ?: SCALES["major"]!!
        val len = intervals.size

        // Calculate Degree relative to Root
        // Use floor/modulo to handle negative degrees correctly
        val octaves = floor(degree.toDouble() / len).toInt()
        val index = ((degree % len) + len) % len
        val semitoneOffset = intervals[index]

        val finalMidi = rootMidi + (octaves * 12) + semitoneOffset
        return midiToFreq(finalMidi)
    }
}
