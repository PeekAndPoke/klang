/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

/**
 * An argument slot that is about to begin at a cursor: the cursor stands right after a `(` or after a `,` of the
 * innermost open `(`, with nothing but blanks, comments and the word being typed in its slot. Read from the editor's
 * text alone, so it holds while the text does not parse; [AnalyzedAst.namedArgumentsAt] pairs it with the analysis,
 * which knows the callee.
 *
 * The lexical rules are the lexer's: `//` and `/* */` comments, `"` and `'` strings and backtick strings, each to its
 * closing quote with backslash escapes honoured (a string may span lines). The lexer reads a backtick string whole,
 * so a cursor inside a template literal's `${...}` counts as inside the string. A cursor inside any of them is in no
 * slot.
 *
 * @param openParen     Offset of the `(` that opens the argument list, in the scanned text.
 * @param namedArgs     The names of the call's other arguments written `name = value`, before and after the cursor.
 * @param hasPositional Whether another argument of the call is positional.
 */
internal class ArgumentSlotAt(
    val openParen: Int,
    val namedArgs: Set<String>,
    val hasPositional: Boolean,
) {
    companion object {
        /**
         * The slot that begins at [pos] in [text], or null when [pos] is no slot start: outside any `(`, inside `[`
         * or `{`, inside a string or comment, after a value has begun in the slot, or with anything but blanks
         * after the word at [pos] in the slot. The word at [pos] is what is being typed: a name's prefix, or a value
         * of one word (`800`, `x`), which a name then goes in front of.
         */
        fun scan(text: String, pos: Int): ArgumentSlotAt? {
            if (pos < 0 || pos > text.length) {
                return null
            }

            // Char codes, as the lexer reads them: a Char is boxed on Kotlin/JS
            val codes = IntArray(text.length) { text[it].code }
            val frame = innermostOpenFrame(codes = codes, pos = pos) ?: return null

            if (frame.opener != '('.code) {
                return null
            }

            // The slots before the cursor's, then the cursor's own up to the cursor, which must be blank
            val starts = listOf(frame.offset + 1) + frame.commas.map { it + 1 }

            if (!isBlank(codes = codes, from = starts.last(), to = pos)) {
                return null
            }

            val before = starts.zipWithNext { from, nextStart -> classifySlot(text, codes, SlotSpan(from = from, to = nextStart - 1)) }

            // The word being typed, then the rest of the cursor's slot, which must be blank too
            var wordEnd = pos

            while (wordEnd < codes.size && codes[wordEnd].isWordChar()) {
                wordEnd++
            }

            val after = slotsUntilClose(codes = codes, from = wordEnd)
            val rest = after.first()

            if (!isBlank(codes = codes, from = rest.from, to = rest.to)) {
                return null
            }

            val others = before + after.drop(1).map { classifySlot(text, codes, it) }

            return ArgumentSlotAt(
                openParen = frame.offset,
                namedArgs = others.mapNotNull { (it as? SlotKind.Named)?.name }.toSet(),
                hasPositional = others.any { it == SlotKind.Positional },
            )
        }
    }
}

// ── Private helpers ────────────────────────────────────────────────────────
// Char codes are written `'('.code`: Kotlin folds them into Int constants at compile time, no Char at run time.

/** A stretch `[from, to)` of the text. */
private class SlotSpan(val from: Int, val to: Int)

/** An open bracket (its char code) at [offset], and the commas met directly inside it, not in a nested bracket. */
private class OpenBracket(val opener: Int, val offset: Int) {
    val commas = mutableListOf<Int>()
}

/** What an argument slot holds. */
private sealed interface SlotKind {
    /** Only blanks and comments (the empty slot after a trailing comma). */
    data object Blank : SlotKind

    /** `name = value`: an `=` that is no `==`, `===` or `=>`. */
    data class Named(val name: String) : SlotKind

    /** Anything else: a value. */
    data object Positional : SlotKind
}

/** The innermost bracket still open at [pos], or null at the top level and inside a string or comment. */
private fun innermostOpenFrame(codes: IntArray, pos: Int): OpenBracket? {
    val stack = mutableListOf<OpenBracket>()
    var i = 0

    while (i < pos) {
        val skipped = skipNonCode(codes = codes, from = i)

        if (skipped != null) {
            // A cursor inside the string or comment, or at the end of one that is still open there, is in no slot
            if (skipped.end > pos || (skipped.end == pos && skipped.openAtEnd)) {
                return null
            }

            i = skipped.end
            continue
        }

        when (val c = codes[i]) {
            '('.code, '['.code, '{'.code -> stack.add(OpenBracket(opener = c, offset = i))
            ')'.code, ']'.code, '}'.code -> stack.removeLastOrNull()
            ','.code -> stack.lastOrNull()?.commas?.add(i)
        }

        i++
    }

    return stack.lastOrNull()
}

/**
 * The slots from [from] (inside an argument list, at bracket depth 0) up to the `)` that closes the list, or to the
 * end of the text when nothing closes it. Never empty: the first span is the rest of the slot [from] stands in.
 */
private fun slotsUntilClose(codes: IntArray, from: Int): List<SlotSpan> {
    val slots = mutableListOf<SlotSpan>()
    var slotStart = from
    var depth = 0
    var i = from

    while (i < codes.size) {
        val skipped = skipNonCode(codes = codes, from = i)

        if (skipped != null) {
            i = skipped.end
            continue
        }

        when (codes[i]) {
            '('.code, '['.code, '{'.code -> depth++
            ')'.code, ']'.code, '}'.code -> {
                if (depth == 0) {
                    slots.add(SlotSpan(from = slotStart, to = i))

                    return slots
                }

                depth--
            }

            ','.code -> if (depth == 0) {
                slots.add(SlotSpan(from = slotStart, to = i))
                slotStart = i + 1
            }
        }

        i++
    }

    slots.add(SlotSpan(from = slotStart, to = codes.size))

    return slots
}

/** Whether `[from, to)` holds nothing but blanks and comments. */
private fun isBlank(codes: IntArray, from: Int, to: Int): Boolean = skipBlanks(codes = codes, from = from, to = to) >= to

/** Index of the first code in `[from, to)` that is no blank and in no comment, or [to]. */
private fun skipBlanks(codes: IntArray, from: Int, to: Int): Int {
    var i = from

    while (i < to) {
        if (codes[i].isBlankChar()) {
            i++
            continue
        }

        val skipped = skipNonCode(codes = codes, from = i)

        if (skipped == null || skipped.isString) {
            return i
        }

        i = skipped.end
    }

    return to
}

/** What the [slot] holds: blank, `name = value`, or a positional value. */
private fun classifySlot(text: String, codes: IntArray, slot: SlotSpan): SlotKind {
    val start = skipBlanks(codes = codes, from = slot.from, to = slot.to)

    if (start >= slot.to) {
        return SlotKind.Blank
    }

    if (!codes[start].isWordChar() || codes[start] in '0'.code..'9'.code) {
        return SlotKind.Positional
    }

    var nameEnd = start

    while (nameEnd < slot.to && codes[nameEnd].isWordChar()) {
        nameEnd++
    }

    val eq = skipBlanks(codes = codes, from = nameEnd, to = slot.to)
    // `=` alone: `==`, `===` and `=>` belong to a value
    val isNamed = eq < slot.to && codes[eq] == '='.code &&
            (eq + 1 >= slot.to || (codes[eq + 1] != '='.code && codes[eq + 1] != '>'.code))

    return if (isNamed) SlotKind.Named(text.substring(start, nameEnd)) else SlotKind.Positional
}

/**
 * A string or comment found at an index: [end] is the index after it. [openAtEnd]: a cursor at [end] is still inside
 * it (a line comment runs on to its newline, an unterminated string or comment to the end of the text).
 */
private class NonCode(val end: Int, val openAtEnd: Boolean, val isString: Boolean)

/** The string or comment that starts at [from], or null when none does. */
private fun skipNonCode(codes: IntArray, from: Int): NonCode? {
    val c = codes[from]
    val next = if (from + 1 < codes.size) codes[from + 1] else -1

    return when {
        c == '/'.code && next == '/'.code -> {
            var i = from + 2

            while (i < codes.size && codes[i] != '\n'.code) {
                i++
            }

            NonCode(end = i, openAtEnd = true, isString = false)
        }

        c == '/'.code && next == '*'.code -> {
            var i = from + 2

            while (i + 1 < codes.size && !(codes[i] == '*'.code && codes[i + 1] == '/'.code)) {
                i++
            }

            if (i + 1 < codes.size) {
                NonCode(end = i + 2, openAtEnd = false, isString = false)
            } else {
                NonCode(end = codes.size, openAtEnd = true, isString = false)
            }
        }

        c == '"'.code || c == '\''.code || c == '`'.code -> {
            var i = from + 1

            while (i < codes.size && codes[i] != c) {
                // A backslash escapes the next character, the closing quote included
                i += if (codes[i] == '\\'.code) 2 else 1
            }

            if (i < codes.size) {
                NonCode(end = i + 1, openAtEnd = false, isString = true)
            } else {
                NonCode(end = codes.size, openAtEnd = true, isString = true)
            }
        }

        else -> null
    }
}

/** A blank as the lexer skips it: space, tab, carriage return, newline. */
private fun Int.isBlankChar(): Boolean = this == ' '.code || this == '\t'.code || this == '\r'.code || this == '\n'.code

/** A code of an identifier or a number, as the lexer reads them: ASCII letters, digits and `_`. */
private fun Int.isWordChar(): Boolean =
    this in 'a'.code..'z'.code || this in 'A'.code..'Z'.code || this in '0'.code..'9'.code || this == '_'.code
