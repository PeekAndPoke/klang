/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.ui

import io.peekandpoke.klang.sprudel.utils.toFixedTrimmed

// How the editor tools read an argument's source text and write a number back into it.
// One home for what every tool used to carry as a private copy.

/** An argument's source text as a number: trimmed, one surrounding pair of quotes dropped; [fallback] if it is no number. */
internal fun parseNum(text: String?, fallback: Double): Double =
    parseNumOrNull(text) ?: fallback

/** An argument's source text as a number, trimmed and one surrounding pair of quotes dropped; `null` if it is no number. */
internal fun parseNumOrNull(text: String?): Double? =
    parseStr(text)?.toDoubleOrNull()

/** An argument's source text, trimmed and one surrounding pair of quotes dropped. */
internal fun parseStr(text: String?): String? =
    text?.trim()?.removePrefix("\"")?.removeSuffix("\"")

/** A number as an editor tool writes it into an argument: at most 3 decimals, no trailing zeros (`0.25`, `1000`). */
internal fun Double.formatArg(): String =
    toFixedTrimmed(3)
