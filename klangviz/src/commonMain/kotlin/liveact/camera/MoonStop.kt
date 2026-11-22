/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.camera

import io.peekandpoke.klang.viz.liveact.smoother

// The stop of the flight round the band (the maintainer, 2026-11-22: "we do not have the moon-shot often enough ...
// so the 360 around the band could also do a pit-stop focussing on the moon"): half way round, behind the band, the
// camera comes to rest, its look rises from the band to the moon over the audience, stays there, sinks back to the
// band, and the flight goes on.

/** When the flight round the band does what, as shares of its time (0 to 1). */
object MoonStop {
    /** The camera rests behind the band from here to there. */
    const val FROM = 0.42
    const val TO = 0.58

    /**
     * How long the look takes to rise to the moon before the camera rests, and to sink back after it: long, so it
     * turns upward while the camera still flies and nobody sees where it began (at 0.10 it was "too sudden": the
     * maintainer, 2026-11-22).
     */
    const val TURN = 0.26

    /** How far round the band the camera is (0 to 1) at [u] of the flight's time: it eases into its rest and out of it. */
    fun travel(u: Double): Double = when {
        // NaN-guard: what is no number is the start
        u != u -> 0.0
        u < FROM -> 0.5 * smoother(u / FROM)
        u > TO -> 0.5 + 0.5 * smoother((u - TO) / (1 - TO))
        else -> 0.5
    }

    /** How much the look is on the moon (0: on the band, 1: on the moon) at [u] of the flight's time. */
    fun look(u: Double): Double = when {
        // NaN-guard: what is no number looks at the band
        u != u -> 0.0
        u < FROM -> smoother((u - (FROM - TURN)) / TURN)
        u > TO -> 1 - smoother((u - TO) / TURN)
        else -> 1.0
    }
}
