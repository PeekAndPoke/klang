/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

// The hand-written JSON beside each probe table (KLANG-00420), common code so the JVM runners and the Kotlin/JS cost
// table write the same form. The JSON is for a page that shows the results later; no JSON library is pulled in for it.

/**
 * [value] as a JSON string literal: quote, backslash and every control character escaped. Walks the UTF-16 code units
 * as Ints (no `Char` on Kotlin/JS, `/code-style` section 5).
 */
fun jsonString(value: String): String = buildString {
    append('"')

    for (i in value.indices) {
        val code = value[i].code

        when {
            code == 0x22 -> append("\\\"")
            code == 0x5C -> append("\\\\")
            code == 0x0A -> append("\\n")
            code == 0x0D -> append("\\r")
            code == 0x09 -> append("\\t")
            code < 0x20 -> append("\\u").append(code.toString(16).padStart(4, '0'))
            else -> appendRange(value, startIndex = i, endIndex = i + 1)
        }
    }

    append('"')
}

/**
 * [value] as a JSON number. JSON has no NaN or infinity, so those are written as the STRINGS "NaN", "Infinity" and
 * "-Infinity" (a reader that wants the number converts them back).
 */
fun jsonNumber(value: Double): String = when {
    value != value -> "\"NaN\"" // NaN-guard
    value == Double.POSITIVE_INFINITY -> "\"Infinity\""
    value == Double.NEGATIVE_INFINITY -> "\"-Infinity\""
    else -> value.toString()
}

/** One JSON value: null, a string, a number, a boolean, a map (an object, in its own order) or a list. */
fun jsonValue(value: Any?): String = when (value) {
    null -> "null"
    is String -> jsonString(value)
    is Double -> jsonNumber(value)
    is Int -> value.toString()
    is Boolean -> value.toString()
    is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}", separator = ", ") { (k, v) -> "${jsonString(k.toString())}: ${jsonValue(v)}" }
    is List<*> -> value.joinToString(prefix = "[", postfix = "]", separator = ", ") { jsonValue(it) }
    else -> error("no JSON form for ${value::class.simpleName}")
}

/** A list of row objects as a JSON array, one row per line. */
fun jsonRows(rows: List<Map<String, Any?>>): String = buildString {
    append("[")

    rows.forEachIndexed { index, row ->
        append(if (index == 0) "\n  " else ",\n  ")
        append(jsonValue(row))
    }

    append(if (rows.isEmpty()) "]\n" else "\n]\n")
}
