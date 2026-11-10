/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import java.io.File

// Where the probe runners write their tables, and the hand-written JSON beside each table (KLANG-00420). The JSON is
// for a page that shows the results later; no JSON library is pulled in for it.

/**
 * The file a probe runner writes its text table to: `$KLANG_PROBE_OUT` when set (a file path), else [fileName] in
 * `<build dir>/probes/`, the build dir found from where the probe classes were compiled to
 * (`<build dir>/classes/kotlin/jvm/test/`), so the agents' `.build-claude/` and the maintainer's `build/` both work.
 */
fun probeOutputFile(fileName: String): File {
    System.getenv("KLANG_PROBE_OUT")?.let { return File(it) }

    val classes = File(ProbeStage::class.java.protectionDomain.codeSource.location.toURI())
    val buildDir = classes.parentFile?.parentFile?.parentFile?.parentFile ?: File(System.getProperty("java.io.tmpdir"))

    return File(buildDir, "probes/$fileName")
}

/** The JSON file next to a text table: `stage-probe.txt` becomes `stage-probe.json`; a name without `.txt` gets `.json` appended. */
fun jsonTwin(text: File): File {
    val name = text.name
    val base = if (name.endsWith(".txt")) name.dropLast(4) else name

    return File(text.parentFile, "$base.json")
}

/** Writes [text] to [target] (creating its folder) and says where it went. */
fun writeProbeFile(target: File, text: String, what: String) {
    target.parentFile?.mkdirs()
    target.writeText(text)
    println("$what written to ${target.absolutePath}")
}

/** [value] as a JSON string literal: quote, backslash and every control character escaped. */
fun jsonString(value: String): String = buildString {
    append('"')

    for (ch in value) {
        when {
            ch == '"' -> append("\\\"")
            ch == '\\' -> append("\\\\")
            ch == '\n' -> append("\\n")
            ch == '\r' -> append("\\r")
            ch == '\t' -> append("\\t")
            ch.code < 0x20 -> append("\\u").append(ch.code.toString(16).padStart(4, '0'))
            else -> append(ch)
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
