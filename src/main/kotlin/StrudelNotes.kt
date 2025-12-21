package io.peekandpoke

import kotlin.math.pow

object StrudelNotes {
    val noteIndex = mapOf(
        'c' to 0,
        'd' to 2,
        'e' to 4,
        'f' to 5,
        'g' to 7,
        'a' to 9,
        'b' to 11,
    )

    fun midiToFreq(m: Double): Double = 440.0 * 2.0.pow((m - 69.0) / 12.0)

    fun noteNameToMidi(note: String): Double {
        if (note.isEmpty()) return 69.0
        val str = note.trim().lowercase()
        var i = 0
        val letter = str[i]
        val base = noteIndex[letter] ?: 9
        i++
        var acc = 0
        while (i < str.length && (str[i] == 'b' || str[i] == '#')) {
            acc += if (str[i] == '#') 1 else -1
            i++
        }
        val oct = if (i < str.length) str.substring(i).toIntOrNull() ?: 4 else 4
        val midi = (oct + 1) * 12 + base + acc

        return midi.toDouble()
    }
}
