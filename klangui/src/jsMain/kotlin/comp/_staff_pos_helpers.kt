package io.peekandpoke.klang.ui.comp

import io.peekandpoke.klang.tones.note.Note

internal val diatonicOffset = mapOf('c' to 0, 'd' to 1, 'e' to 2, 'f' to 3, 'g' to 4, 'a' to 5, 'b' to 6)

/**
 * Staff position of [noteName] with C4 = 0.
 * Treble lines: 2(E4) 4(G4) 6(B4) 8(D5) 10(F5).
 * Bass lines: −10(G2) −8(B2) −6(D3) −4(F3) −2(A3).
 */
internal fun staffPos(noteName: String): Int? {
    val note = Note.get(noteName)
    if (note.empty) return null
    val oct = note.oct ?: return null
    val letter = note.pc.firstOrNull()?.lowercaseChar() ?: return null
    return (oct - 4) * 7 + (diatonicOffset[letter] ?: return null)
}
