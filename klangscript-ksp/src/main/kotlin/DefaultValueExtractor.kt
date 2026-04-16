package io.peekandpoke.klang.script.ksp

import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSValueParameter
import java.io.File

/**
 * Extracts the source text of a Kotlin parameter's default value, for display
 * in KlangScript documentation only.
 *
 * KSP1 does not expose the default expression of a function parameter as an
 * AST node — only `hasDefault: Boolean`. We scan the raw source file
 * around the parameter's reported line, find the parameter name, the `=`,
 * and the matching top-level `,` or `)`.
 *
 * The extractor is intentionally **fail-soft**: any unexpected token, missing
 * source location, unbalanced bracket, or runaway scan returns `null` and the
 * caller emits `defaultDoc = null`. Bridge generation never uses this value —
 * it's purely for human-readable documentation.
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
     * it as a standalone identifier and returns whatever text follows the
     * subsequent `=` up to the next top-level `,` or `)`.
     */
    fun extractFromWindow(window: String, paramName: String): String? {
        val nameStart = findIdentifier(window, paramName, 0) ?: return null
        val afterName = nameStart + paramName.length
        val eq = findEqualsAtTopLevel(window, afterName) ?: return null
        val valueStart = eq + 1
        val valueEnd = findValueEnd(window, valueStart) ?: return null
        return window.substring(valueStart, valueEnd).trim().ifEmpty { null }
    }

    // ------------------------------------------------------------------------
    //  Internal — bracket/string/comment-aware scanners
    // ------------------------------------------------------------------------

    /** Match [name] as a standalone Kotlin identifier (word boundaries). */
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
                    !after.isLetterOrDigit() && after != '_'
                ) {
                    return i
                }
            }
            i++
        }
        return null
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
                ')', ']', '}' -> {
                    if (depth == 0) return i  // closing of the enclosing param list
                    depth--
                }

                ',' -> if (depth == 0) return i
            }
            i++
        }
        return null
    }
}
