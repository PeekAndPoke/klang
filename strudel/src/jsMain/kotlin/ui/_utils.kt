package io.peekandpoke.klang.strudel.ui

import io.peekandpoke.klang.tones.note.Note
import kotlin.math.pow
import kotlin.math.roundToLong

// ── Staff position helpers ────────────────────────────────────────────────────

/**
 * Returns the staff position for this note (C4 = 0, D4 = 1, … B4 = 6, C5 = 7, …).
 * Returns null if the note is empty.
 */
fun Note.staffPosition(): Int? {
    if (empty) return null
    return step + 7 * ((oct ?: 4) - 4)
}

/**
 * Converts a staff position back to a note name string (lowercase letter + octave).
 * C4 = 0, D4 = 1, E4 = 2, F4 = 3, G4 = 4, A4 = 5, B4 = 6, C5 = 7, …
 */
fun staffPositionToNote(pos: Int): String {
    val octave = 4 + pos.floorDiv(7)
    val step = ((pos % 7) + 7) % 7
    val letter = Note.stepToLetter(step).lowercase()
    return "$letter$octave"
}

/**
 * Converts a scale degree (0-based) to a staff position given the scale's note list.
 *
 * The scale notes list contains pitch-class names with octave (e.g. ["C3","D3","E3","F3","G3","A3","B3"]).
 * Degrees outside [0, size) wrap around with octave adjustment.
 */
fun scaleDegreeToStaffPosition(degree: Int, scaleNotes: List<String>): Int {
    if (scaleNotes.isEmpty()) return 0
    val size = scaleNotes.size
    val idx = ((degree % size) + size) % size
    val octaveShift = degree.floorDiv(size)
    val note = Note.get(scaleNotes[idx])
    return (note.staffPosition() ?: 0) + 7 * octaveShift
}

/**
 * Finds the scale degree whose staff position is closest to [targetPos].
 * Returns the degree (may be outside [0, size) for out-of-range positions).
 */
fun nearestScaleDegree(targetPos: Int, scaleNotes: List<String>): Int {
    if (scaleNotes.isEmpty()) return 0
    val size = scaleNotes.size

    // Search within a reasonable range of octaves
    var bestDegree = 0
    var bestDist = Int.MAX_VALUE
    for (octShift in -2..2) {
        for (i in 0 until size) {
            val deg = octShift * size + i
            val pos = scaleDegreeToStaffPosition(deg, scaleNotes)
            val dist = kotlin.math.abs(pos - targetPos)
            if (dist < bestDist) {
                bestDist = dist
                bestDegree = deg
            }
        }
    }
    return bestDegree
}

// ── String / numeric helpers ──────────────────────────────────────────────────

/**
 * Wraps a mini-notation string in the appropriate quote style for committing back to source.
 * Multi-line strings use backtick quotes; single-line strings use double quotes.
 */
internal fun String.quoteForCommit(): String =
    if (contains('\n')) "`$this`" else "\"$this\""

internal fun Double.toFixed(decimals: Int): String {
    val s = roundTo(decimals).toString()
    val dotIdx = s.indexOf('.')
    return if (dotIdx < 0) s else s.trimEnd('0').trimEnd('.')
}

internal fun Double.roundTo(decimals: Int): Double {
    val factor = 10.0.pow(decimals)
    return (this * factor).roundToLong().toDouble() / factor
}

internal fun Double.decimalPlaces(): Int {
    val s = toString()
    val dot = s.indexOf('.')
    return if (dot < 0) 0 else s.substring(dot + 1).trimEnd('0').length
}
