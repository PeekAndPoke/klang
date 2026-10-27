/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

// A whole-call tool's argument list (KLANG-00374: the editor's Sprudel tool rewrites a call with them): read into one
// text per parameter, and written back. Text helpers beside [CallText].

private val namedArgRegex = Regex("^(\\w+)\\s*=(?![=>])\\s*([\\s\\S]*)$")

/**
 * Aligns raw argument texts to declared parameter positions. All-named calls align by name;
 * otherwise the texts are taken positionally (KlangScript forbids mixing the two forms).
 * Unknown names and overflow positions are dropped.
 */
fun alignArgsToParams(paramNames: List<String>, argTexts: List<String>): List<String?> {
    val out = MutableList<String?>(paramNames.size) { null }
    val named = argTexts.mapNotNull { t -> namedArgRegex.matchEntire(t) }
    if (named.size == argTexts.size && argTexts.isNotEmpty()) {
        for (m in named) {
            val idx = paramNames.indexOf(m.groupValues[1])
            if (idx >= 0) out[idx] = m.groupValues[2].trim()
        }
    } else {
        argTexts.forEachIndexed { i, t ->
            if (i < out.size && t.isNotEmpty()) out[i] = t
        }
    }
    return out
}

/**
 * Serializes per-param texts back into an argument list: a contiguous prefix of provided
 * values becomes positional args; anything with gaps becomes ALL-named args (KlangScript
 * forbids mixing positional and named).
 */
fun serializeCallArgs(paramNames: List<String>, texts: List<String?>): String {
    @Suppress("NAME_SHADOWING")
    val texts = texts.map { t -> t?.takeIf { it.isNotBlank() } }
    val lastIdx = texts.indexOfLast { it != null }
    if (lastIdx < 0) return ""
    val prefix = texts.take(lastIdx + 1)
    return if (prefix.all { it != null }) {
        prefix.joinToString(", ")
    } else {
        texts.mapIndexedNotNull { i, t -> t?.let { "${paramNames[i]} = $it" } }
            .joinToString(", ")
    }
}
