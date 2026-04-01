package io.peekandpoke.klang.sprudel.ui

import io.peekandpoke.klang.sprudel.lang.parser.MnNode
import kotlin.math.pow

// Staff position helpers moved to tones module: tones/note/NoteStaffPosition.kt
// Import them via: io.peekandpoke.klang.tones.note.staffPosition, staffPositionToNote, etc.

// ── Selection ─────────────────────────────────────────────────────────────────

/** The currently selected item in a note staff — identified by node [MnNode.id]. */
sealed interface MnSelection {
    val nodeId: Int

    data class Atom(val node: MnNode.Atom) : MnSelection {
        override val nodeId get() = node.id
    }

    data class Rest(val node: MnNode.Rest) : MnSelection {
        override val nodeId get() = node.id
    }
}

val MnSelection?.atom: MnNode.Atom? get() = (this as? MnSelection.Atom)?.node
val MnSelection?.rest: MnNode.Rest? get() = (this as? MnSelection.Rest)?.node

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
    // Use round() instead of roundToLong(): Long is boxed in Kotlin/JS (emulated via wrapper object),
    // causing unnecessary heap allocation. round() returns Double directly — no boxing.
    return kotlin.math.round(this * factor) / factor
}

internal fun Double.decimalPlaces(): Int {
    val s = toString()
    val dot = s.indexOf('.')
    return if (dot < 0) 0 else s.substring(dot + 1).trimEnd('0').length
}
