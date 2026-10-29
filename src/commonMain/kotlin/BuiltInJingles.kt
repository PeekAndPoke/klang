/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.peekandpoke.klang.builtinsongs.jingles.schmetterlingJingle

/**
 * A short piece the app plays by itself, as "Make Music Now" leaves the start page: [cycles] cycles of [code] at [rpm],
 * then it stops.
 */
data class Jingle(
    val id: String,
    val title: String,
    val rpm: Double,
    val cycles: Int,
    val code: String,
)

/**
 * The jingles, each in its own file under `builtinsongs/jingles/`. The start page plays one of [all] at random (the
 * maintainer, 2026-10-29: "prepare for a set of jingles in the StartPage so that later we can randomly play one of
 * them").
 */
object BuiltInJingles {

    const val PREFIX = "builtin-jingle"

    /** The marimba and the melody of "Der Schmetterling". */
    val schmetterling: Jingle = schmetterlingJingle

    /** Every jingle. */
    val all: List<Jingle> = listOf(schmetterling)
}
