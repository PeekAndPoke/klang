/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

import io.peekandpoke.klang.script.types.KlangSymbol

/**
 * A local's declaration, as [AnalyzedAst.definitionAt] finds it for a word that reads the local: where Ctrl/Cmd-click
 * jumps to, and what the hover shows. Every offset is in the text the query was asked with (the editor's).
 *
 * @param namePos Offset of the declared name: where go-to-definition puts the cursor.
 * @param start   Offset where the declaration starts: the `let` / `const` / `export` keyword, or a parameter's name.
 * @param end     Offset where it ends (exclusive): after the statement's last token, closing brackets included, or
 *   after the parameter's name.
 * @param code    The declaration's code as the hover shows it ([codeOf]).
 * @param symbol  The local as the hover shows it: the symbol [AnalyzedAst.symbolAt] gives for it.
 */
data class LocalDefinition(
    val namePos: Int,
    val start: Int,
    val end: Int,
    val code: String,
    val symbol: KlangSymbol,
) {
    companion object {
        /** The most lines of a declaration the hover shows; a longer one ends in a `...` line. */
        const val MAX_CODE_LINES = 12

        /**
         * The code of the declaration from [start] to [end] in [text]: the whole lines that hold it (for a parameter,
         * the line that holds its name), their common indentation removed, at most [MAX_CODE_LINES] of them and then a
         * `...` line.
         */
        internal fun codeOf(text: String, start: Int, end: Int): String {
            val from = if (start > 0) text.lastIndexOf("\n", startIndex = start - 1) + 1 else 0
            val lastNewline = text.indexOf("\n", startIndex = maxOf(start, end - 1))
            val to = if (lastNewline < 0) text.length else lastNewline
            val lines = text.substring(from, to).lines()
            val shown = lines.take(MAX_CODE_LINES)
            val indent = shown.filter { it.isNotBlank() }.minOfOrNull { it.length - it.trimStart().length } ?: 0
            val code = shown.joinToString("\n") { it.drop(indent) }

            return if (lines.size > MAX_CODE_LINES) "$code\n..." else code
        }
    }
}
