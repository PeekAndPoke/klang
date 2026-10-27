/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

/**
 * Reads a call's parentheses and arguments from source text, for the editor's tools (KLANG-00374): the analysis says
 * which call and which parameter; this finds where they are in the text as it stands now, after edits the analysis
 * has not seen. Strings (`"`, `'`, backticks, with escapes), line comments and block comments are skipped, and nested
 * parentheses, brackets and braces kept together.
 */
object CallText {

    /** One argument of a call: its name when written `name = value`, where it starts, where its value starts, its end. */
    data class Arg(
        val name: String?,
        /** The first offset of the argument's text, after any space. */
        val from: Int,
        /** The first offset of the value: after `name =` for a named argument, else [from]. */
        val valueFrom: Int,
        /** The offset just after the argument's text, before any space. */
        val to: Int,
    )

    /**
     * The opening parenthesis at [from] (after space and comments) and its matching closing one, as offsets; null when
     * there is no `(` there or it does not close.
     */
    fun parentheses(source: String, from: Int): IntRange? {
        val open = skipBlank(source, from)

        if (open >= source.length || source[open] != '(') {
            return null
        }

        var depth = 0
        var index = open

        while (index < source.length) {
            val skipped = skipQuoted(source, index)

            if (skipped != index) {
                index = skipped
                continue
            }

            when (source[index]) {
                '(', '[', '{' -> depth++

                ')', ']', '}' -> {
                    depth--

                    if (depth == 0) {
                        return if (source[index] == ')') open..index else null
                    }
                }
            }

            index++
        }

        return null
    }

    /** The arguments between [parentheses] (the offsets of `(` and `)`), split at the commas on their own level. */
    fun arguments(source: String, parentheses: IntRange): List<Arg> {
        val args = mutableListOf<Arg>()
        var start = parentheses.first + 1
        var depth = 0
        var index = start

        fun close(end: Int) {
            val from = skipBlank(source, start)
            var to = end

            while (to > from && source[to - 1].isWhitespace()) {
                to--
            }

            if (from < to) {
                args.add(argOf(source = source, from = from, to = to))
            }
        }

        while (index < parentheses.last) {
            val skipped = skipQuoted(source, index)

            if (skipped != index) {
                index = skipped
                continue
            }

            when (source[index]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--

                ',' -> if (depth == 0) {
                    close(index)
                    start = index + 1
                }
            }

            index++
        }

        close(parentheses.last)

        return args
    }

    /**
     * The literal a tool reads and writes in the argument of [parentheses] for a parameter, as offsets: the argument
     * written with the parameter's [name], else the one at [index] written without a name; its value when that is a
     * string or a number, or the literal its value starts with (`"c e"` in `"c e".fast(2)`). Null when there is no such
     * argument, or its value is not a literal or the head of a chain on one (`sine.range(1, 2)`, `f(4)`, `-3 * 2`).
     */
    fun literalOf(source: String, parentheses: IntRange, name: String, index: Int): IntRange? {
        val args = arguments(source, parentheses)
        val arg = args.firstOrNull { it.name == name } ?: args.getOrNull(index)?.takeIf { it.name == null } ?: return null
        val end = literalEnd(source, arg.valueFrom) ?: return null
        val next = skipBlank(source, end)

        // Only a chain may follow the literal in its argument
        if (next < arg.to && source[next] != '.') {
            return null
        }

        return arg.valueFrom until end
    }

    /**
     * The end (exclusive) of the string or number literal starting at [from]; null when none starts there. A number
     * as the lexer reads it: a sign (space may follow it), `0x`, `0o` or `0b` digits, or decimal digits with a fraction
     * and an exponent; never one a name or another digit runs on from (`2px`).
     */
    fun literalEnd(source: String, from: Int): Int? {
        if (from >= source.length) {
            return null
        }

        val first = source[from]

        if (first == '"' || first == '\'' || first == '`') {
            return skipQuoted(source, from).takeIf { it > from + 1 && source[it - 1] == first }
        }

        var index = from

        if (first == '-') {
            index++

            while (index < source.length && source[index].isWhitespace()) {
                index++
            }
        }

        val digitsFrom = index
        val prefixDigits = when (source.getOrNull(index + 1)?.lowercaseChar()?.takeIf { source.getOrNull(index) == '0' }) {
            'x' -> "0123456789abcdefABCDEF"
            'o' -> "01234567"
            'b' -> "01"
            else -> null
        }

        if (prefixDigits != null) {
            index += 2

            while (index < source.length && source[index] in prefixDigits) {
                index++
            }
        } else {
            index = decimalEnd(source, index)
        }

        val runsOn = source.getOrNull(index)?.let { it.isLetterOrDigit() || it == '_' } == true

        return index.takeIf { it > digitsFrom && !runsOn && !(prefixDigits != null && it == digitsFrom + 2) }
    }

    /** Past the digits, the fraction and the exponent from [from], as the lexer reads a decimal. */
    private fun decimalEnd(source: String, from: Int): Int {
        var index = from

        while (index < source.length && source[index].isDigit()) {
            index++
        }

        if (index == from) {
            return from
        }

        if (source.getOrNull(index) == '.' && (source.getOrNull(index + 1)?.isDigit() == true || isExponentAt(source, index + 1))) {
            index++

            while (index < source.length && source[index].isDigit()) {
                index++
            }
        }

        if (isExponentAt(source, index)) {
            index++

            if (source[index] == '+' || source[index] == '-') {
                index++
            }

            while (index < source.length && source[index].isDigit()) {
                index++
            }
        }

        return index
    }

    /** Does a complete exponent start at [index]: `e` or `E`, an optional sign, at least one digit? */
    private fun isExponentAt(source: String, index: Int): Boolean {
        if (source.getOrNull(index)?.lowercaseChar() != 'e') {
            return false
        }

        val afterSign = if (source.getOrNull(index + 1) == '+' || source.getOrNull(index + 1) == '-') index + 2 else index + 1

        return source.getOrNull(afterSign)?.isDigit() == true
    }

    /**
     * The offset of the last character of code before [pos]: space and comments are passed over, a string counts as
     * code; -1 when there is none.
     */
    fun lastCodeBefore(source: String, pos: Int): Int {
        var last = -1
        var index = 0

        while (index < pos && index < source.length) {
            val comment = skipComment(source, index)

            if (comment != index) {
                index = comment
                continue
            }

            val skipped = skipQuoted(source, index)

            when {
                skipped != index -> {
                    last = minOf(skipped, pos) - 1
                    index = skipped
                }

                source[index].isWhitespace() -> index++

                else -> {
                    last = index
                    index++
                }
            }
        }

        return last
    }

    private fun argOf(source: String, from: Int, to: Int): Arg {
        // `name = value`: an identifier, then a single `=` (not `==`, not `=>`)
        var index = from

        while (index < to && (source[index].isLetterOrDigit() || source[index] == '_')) {
            index++
        }

        val nameEnd = index
        val equals = skipBlank(source, index)
        val isNamed = nameEnd > from && !source[from].isDigit() && equals < to && source[equals] == '=' &&
            source.getOrNull(equals + 1) != '=' && source.getOrNull(equals + 1) != '>'

        return if (isNamed) {
            Arg(name = source.substring(from, nameEnd), from = from, valueFrom = skipBlank(source, equals + 1), to = to)
        } else {
            Arg(name = null, from = from, valueFrom = from, to = to)
        }
    }

    /** From [from] past space, line comments and block comments: where the next code starts. */
    fun skipBlank(source: String, from: Int): Int {
        var index = from

        while (index < source.length) {
            val skipped = skipComment(source, index)

            when {
                skipped != index -> index = skipped
                source[index].isWhitespace() -> index++
                else -> return index
            }
        }

        return index
    }

    /** Past the string or comment starting at [index]; [index] itself when none starts there. */
    private fun skipQuoted(source: String, index: Int): Int {
        val comment = skipComment(source, index)

        if (comment != index) {
            return comment
        }

        val quote = source[index]

        if (quote != '"' && quote != '\'' && quote != '`') {
            return index
        }

        var at = index + 1

        while (at < source.length) {
            when (source[at]) {
                '\\' -> at++
                quote -> return at + 1
            }

            at++
        }

        return source.length
    }

    /** Past the line or block comment starting at [index]; [index] itself when none starts there. */
    private fun skipComment(source: String, index: Int): Int {
        if (source[index] != '/' || index + 1 >= source.length) {
            return index
        }

        return when (source[index + 1]) {
            '/' -> source.indexOf('\n', index).let { if (it < 0) source.length else it }
            '*' -> source.indexOf("*/", index + 2).let { if (it < 0) source.length else it + 2 }
            else -> index
        }
    }
}
