/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

/**
 * Where a local is declared in the analysed source: its name and the whole declaration. The analysis walk records it
 * on the [TypeScope.LocalBinding]; [AnalyzedAst.definitionAt] reads it.
 *
 * - A `let` / `const` / `export`: [start] is its keyword, [end] the end of its last token, closing brackets included.
 * - An arrow parameter: the parameter's name, from [start] to [end].
 *
 * The AST keeps less than this (a declaration's location is its keyword, an arrow's is its `=>`, a parameter is a bare
 * name, no node keeps a closing bracket), so the walk reads the rest back from the source, by the lexer's rules.
 *
 * @param namePos Offset of the declared name.
 * @param start   Offset where the declaration starts.
 * @param end     Offset where it ends (exclusive).
 */
data class DeclarationSite(val namePos: Int, val start: Int, val end: Int) {
    companion object {
        /**
         * The site of the `let` / `const` / `export` whose keyword starts at [keywordStart] in the source [codes] and
         * declares [name], or null when the name does not follow the keyword (blanks and comments between them are
         * read past). [lastEnd] is the end of the last token the walk met in the statement; the statement runs on from
         * there until every bracket opened since its keyword is closed again (`foo(\n  a,\n)` ends after its `)`).
         */
        internal fun ofStatement(codes: IntArray, keywordStart: Int, name: String, lastEnd: Int): DeclarationSite? {
            var keywordEnd = keywordStart

            while (keywordEnd < codes.size && codes[keywordEnd].isWordChar()) {
                keywordEnd++
            }

            val namePos = skipBlanks(codes = codes, from = keywordEnd, to = codes.size)

            if (!codes.hasWordAt(pos = namePos, word = name)) {
                return null
            }

            val end = statementEnd(codes = codes, start = keywordStart, lastEnd = maxOf(lastEnd, namePos + name.length))

            return DeclarationSite(namePos = namePos, start = keywordStart, end = end)
        }

        /**
         * The sites of an arrow's [params] in parameter order, read back from the text in front of its `=>`, which
         * starts at [arrowStart] in the source [codes]: `x =>` or `(a, b) =>`. Null when that text is not the parameter
         * list the parser read: a comment in it, say. Then the parameters have no site (no jump rather than a wrong one).
         */
        internal fun ofArrowParams(codes: IntArray, arrowStart: Int, params: List<String>): List<DeclarationSite>? {
            if (params.isEmpty()) {
                return emptyList()
            }

            if (arrowStart > codes.size) {
                return null
            }

            var i = skipBlanksBack(codes = codes, end = arrowStart)
            val starts = mutableListOf<Int>()

            if (i > 0 && codes[i - 1] == ')'.code) {
                i--

                while (true) {
                    i = skipBlanksBack(codes = codes, end = i)

                    if (i == 0) {
                        return null
                    }

                    val c = codes[i - 1]

                    when {
                        c == '('.code -> break
                        c == ','.code -> i--
                        c.isWordChar() -> {
                            i = wordStartBack(codes = codes, end = i)
                            starts.add(0, i)
                        }

                        else -> return null
                    }
                }
            } else if (i > 0 && codes[i - 1].isWordChar()) {
                starts.add(wordStartBack(codes = codes, end = i))
            }

            if (starts.size != params.size) {
                return null
            }

            return params.mapIndexed { index, param ->
                val pos = starts[index]

                if (!codes.hasWordAt(pos = pos, word = param)) {
                    return null
                }

                DeclarationSite(namePos = pos, start = pos, end = pos + param.length)
            }
        }
    }
}

/**
 * The first offset from [lastEnd] on where every bracket opened since [start] is closed again. Strings and comments are
 * skipped whole, as the lexer reads them.
 */
private fun statementEnd(codes: IntArray, start: Int, lastEnd: Int): Int {
    var depth = 0
    var i = start

    while (i < codes.size && (i < lastEnd || depth > 0)) {
        val skipped = skipNonCode(codes = codes, from = i)

        if (skipped != null) {
            i = skipped.end
            continue
        }

        when (codes[i]) {
            '('.code, '['.code, '{'.code -> depth++
            ')'.code, ']'.code, '}'.code -> depth--
        }

        i++
    }

    return maxOf(i, lastEnd)
}

/** Whether the identifier [word], and no longer one, stands at [pos]. */
private fun IntArray.hasWordAt(pos: Int, word: String): Boolean {
    val end = pos + word.length

    if (word.isEmpty() || end > size) {
        return false
    }

    for (k in word.indices) {
        if (this[pos + k] != word[k].code) {
            return false
        }
    }

    return end == size || !this[end].isWordChar()
}

/** The offset after the last code before [end] that is no blank (comments are not read backwards). */
private fun skipBlanksBack(codes: IntArray, end: Int): Int {
    var i = end

    while (i > 0 && codes[i - 1].isBlankChar()) {
        i--
    }

    return i
}

/** The start of the word that ends at [end]. */
private fun wordStartBack(codes: IntArray, end: Int): Int {
    var i = end

    while (i > 0 && codes[i - 1].isWordChar()) {
        i--
    }

    return i
}
