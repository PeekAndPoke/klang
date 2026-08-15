/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.ksp

import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSValueParameter
import java.io.File

/**
 * Extracts the source text of a Kotlin parameter's default value, for
 * KlangScript documentation and — when the text is literal-shaped — for
 * runtime default thunks.
 *
 * KSP1 does not expose the default expression of a function parameter as an
 * AST node — only `hasDefault: Boolean`. We scan the raw source file
 * around the parameter's reported line, find the parameter name, the `=`,
 * and the matching top-level `,` or `)`.
 *
 * The extractor is intentionally **fail-soft**: any unexpected token, missing
 * source location, unbalanced bracket, or runaway scan returns `null` and the
 * caller emits `defaultDoc = null`.
 *
 * ⚠️ NOT docs-only: extractions that look like plain literals (numbers,
 * strings, booleans — see `SafeDefaultLiteral.isSafe`) are pasted into the
 * generated registration as RUNTIME default thunks by `safeDefaultThunk`.
 * A plausible-but-wrong literal is therefore a behavioral bug, not a cosmetic
 * one — when in doubt, return null.
 */
object DefaultValueExtractor {

    /** Maximum number of source lines to read past the param's reported line. */
    private const val WINDOW_LINES = 50

    /**
     * Extract the default expression string for [param], or null if extraction
     * fails for any reason (no source available, no default, ambiguous parse,
     * etc.).
     *
     * The returned string is trimmed of leading/trailing whitespace.
     */
    fun extract(param: KSValueParameter): String? {
        if (!param.hasDefault) return null
        val location = param.location as? FileLocation ?: return null
        val name = param.name?.asString() ?: return null

        val lines = try {
            File(location.filePath).readLines()
        } catch (_: Throwable) {
            return null
        }

        val startLine = location.lineNumber - 1
        if (startLine < 0 || startLine >= lines.size) return null

        val window = lines
            .subList(startLine, minOf(lines.size, startLine + WINDOW_LINES))
            .joinToString("\n")

        return extractFromWindow(window, name)
    }

    /**
     * Pure scanning entry point — useful for tests. The [window] should start
     * at or near the parameter declaration; the scan finds [paramName] within
     * it as a standalone parameter declaration (identifier followed by `:`)
     * and returns whatever text follows the subsequent `=` up to the next
     * top-level `,` or `)`.
     *
     * A candidate that dead-ends (e.g. a same-named parameter inside a
     * function TYPE, whose `=`-scan runs out of the type's parens) is skipped
     * and the scan resumes after it — fail-soft null only when no candidate
     * is left. The retry is deliberately NOT bounded to the first declaration:
     * production callers are double-guarded (hasDefault + window starting at
     * the param's own line), and every structural bound breaks the "at or
     * near" window contract this function documents (see the pinned
     * later-declaration test).
     *
     * A `}`/`]` at value depth 0 is a dead-end rather than a value end (see
     * [findValueEnd]) — that rule retires the residual where a same-named,
     * typed lambda parameter in an EARLIER param's default
     * (`{ gain: Int -> acc = gain }`, or worse `{ gain: Double -> x = 0.5 }`
     * whose literal fragment would ship as a wrong RUNTIME default) could be
     * extracted: such candidates now dead-end and retry to the real parameter.
     *
     * Known residuals (accepted, pinned in tests where practical):
     *  - a `when (val gain: Int = 5)` subject binding in an earlier default is
     *    indistinguishable from a real parameter (its value legitimately ends
     *    at a `)`); a `val`-lookbehind would regress constructor val-params,
     *    so this stays. Requires an explicitly TYPED binding — the common
     *    untyped `when (val gain = …)` is already rejected by the `:` rule.
     *  - a default containing a `,` inside explicit generic call args
     *    (`mapOf<String, Int>()`) truncates at that comma — never
     *    literal-shaped, so docs-only; rejecting unbalanced `<` would also
     *    kill legitimate comparison defaults (`x < y`).
     */
    fun extractFromWindow(window: String, paramName: String): String? {
        // An empty name would match zero-length identifiers without ever
        // advancing `from` — guard instead of spinning.
        if (paramName.isEmpty()) {
            return null
        }
        var from = 0
        while (true) {
            val nameStart = findIdentifier(window, paramName, from) ?: return null
            val afterName = nameStart + paramName.length
            val eq = findEqualsAtTopLevel(window, afterName)
            if (eq != null) {
                val valueStart = eq + 1
                val valueEnd = findValueEnd(window, valueStart)
                if (valueEnd != null) {
                    val text = window.substring(valueStart, valueEnd).trim()
                    if (text.isNotEmpty()) {
                        return text
                    }
                }
            }
            from = afterName
        }
    }

    // ------------------------------------------------------------------------
    //  Internal — bracket/string/comment-aware scanners
    // ------------------------------------------------------------------------

    /**
     * Match [name] as a standalone Kotlin identifier (word boundaries) that is a
     * PARAMETER DECLARATION — i.e. followed by its `:` type annotation.
     */
    private fun findIdentifier(s: String, name: String, from: Int): Int? {
        var i = from
        while (i <= s.length - name.length) {
            val skipped = skipNoise(s, i) ?: return null
            if (skipped != i) {
                i = skipped
                continue
            }
            if (s.regionMatches(i, name, 0, name.length)) {
                val before = if (i == 0) ' ' else s[i - 1]
                val after = if (i + name.length >= s.length) ' ' else s[i + name.length]
                if (!before.isLetterOrDigit() && before != '_' &&
                    !after.isLetterOrDigit() && after != '_' &&
                    isParamDeclaration(s, i + name.length)
                ) {
                    return i
                }
            }
            i++
        }
        return null
    }

    /**
     * True when the identifier ending right before [from] is a parameter
     * declaration: the next meaningful char is a single `:` (the type
     * annotation). Rejects the enclosing FUNCTION name (followed by `(`),
     * named arguments (`name = value`) and method references (`name::…`).
     *
     * Without this check, `fun gain(gain: Double = 1.0): Gain = Gain(gain = gain)`
     * matches the function name first; the `=` scan then skips the whole param
     * list (depth 1) and locks onto the expression-body `=`, swallowing
     * everything up to the enclosing object's `}` — the MasterFx.gain bug.
     */
    private fun isParamDeclaration(s: String, from: Int): Boolean {
        var i = from
        while (i < s.length) {
            val skipped = skipNoise(s, i) ?: return false
            if (skipped != i) {
                i = skipped
                continue
            }
            if (s[i].isWhitespace()) {
                i++
                continue
            }
            return s[i] == ':' && (i + 1 >= s.length || s[i + 1] != ':')
        }
        return false
    }

    /**
     * If [s] at index [i] starts a string, char literal, or comment, return
     * the position immediately after it. If [i] is not noise, return [i]
     * unchanged. Returns null on an unterminated string / comment.
     */
    private fun skipNoise(s: String, i: Int): Int? {
        if (i >= s.length) return i
        val c = s[i]
        return when {
            // Triple-quoted raw string """…"""
            c == '"' && i + 2 < s.length && s[i + 1] == '"' && s[i + 2] == '"' -> {
                val end = s.indexOf("\"\"\"", i + 3)
                if (end == -1) null else end + 3
            }
            // Regular string literal
            c == '"' -> {
                var j = i + 1
                while (j < s.length) {
                    when (s[j]) {
                        '\\' -> j += 2
                        '"' -> return j + 1
                        else -> j++
                    }
                }
                null
            }
            // Char literal
            c == '\'' -> {
                var j = i + 1
                while (j < s.length) {
                    when (s[j]) {
                        '\\' -> j += 2
                        '\'' -> return j + 1
                        else -> j++
                    }
                }
                null
            }
            // Line comment
            c == '/' && i + 1 < s.length && s[i + 1] == '/' -> {
                val end = s.indexOf('\n', i)
                if (end == -1) s.length else end + 1
            }
            // Block comment (covers KDoc /** … */ too)
            c == '/' && i + 1 < s.length && s[i + 1] == '*' -> {
                val end = s.indexOf("*/", i + 2)
                if (end == -1) null else end + 2
            }

            else -> i
        }
    }

    /**
     * Find a single `=` at paren/bracket/brace depth 0 starting from [from].
     * Skips over `==`, `=>`, `<=`, `>=`, `!=` so they aren't misread as the
     * default-value marker.
     */
    private fun findEqualsAtTopLevel(s: String, from: Int): Int? {
        var i = from
        var depth = 0
        while (i < s.length) {
            val skipped = skipNoise(s, i) ?: return null
            if (skipped != i) {
                i = skipped
                continue
            }
            when (val c = s[i]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> {
                    depth--
                    if (depth < 0) return null  // ran past the param list before finding `=`
                }

                '=' -> {
                    if (depth == 0) {
                        val prev = if (i == 0) ' ' else s[i - 1]
                        val next = if (i + 1 >= s.length) ' ' else s[i + 1]
                        // `>` before `=` stays compound even though a glued generic
                        // (`List<Int>= x`) is thereby missed (fail-soft null):
                        // distinguishing that from a real `>=` comparison on
                        // false-candidate scans through lambda bodies proved
                        // unsafe — a misread ships a WRONG RUNTIME DEFAULT via
                        // safeDefaultThunk. A docs miss beats wrong behavior.
                        val isCompound = prev == '=' || prev == '<' || prev == '>' || prev == '!' ||
                                next == '=' || next == '>'
                        if (!isCompound) return i
                    }
                }

                else -> { /* advance */
                    @Suppress("UNUSED_EXPRESSION") c
                }
            }
            i++
        }
        return null
    }

    /**
     * Find the end of the default expression: a `,` or `)` at depth 0.
     * Returns the index of that delimiter (exclusive end of the value).
     *
     * A `}` or `]` at depth 0 is a DEAD-END, not an end: a genuine parameter
     * default can only terminate at `,` or `)`, so a closing brace/bracket
     * proves the candidate sits inside a brace construct (e.g. a lambda body
     * containing `x = literal`) — return null so the retry loop advances
     * instead of shipping the fragment as a wrong runtime default.
     */
    private fun findValueEnd(s: String, from: Int): Int? {
        var i = from
        var depth = 0
        while (i < s.length) {
            val skipped = skipNoise(s, i) ?: return null
            if (skipped != i) {
                i = skipped
                continue
            }
            when (s[i]) {
                '(', '[', '{' -> depth++
                ')' -> {
                    // For a genuine candidate this is the param list closing; a
                    // paren false candidate (e.g. a `when (val …)` subject
                    // binding) is indistinguishable here — see the known
                    // residuals in [extractFromWindow].
                    if (depth == 0) return i
                    depth--
                }

                ']', '}' -> {
                    if (depth == 0) return null  // inside a brace/bracket construct
                    depth--
                }

                ',' -> if (depth == 0) return i
            }
            i++
        }
        return null
    }
}
