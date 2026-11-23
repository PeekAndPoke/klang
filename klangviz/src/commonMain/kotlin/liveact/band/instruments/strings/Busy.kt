/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.strings

import io.peekandpoke.klang.viz.liveact.Timed
import io.peekandpoke.klang.viz.liveact.lastIndexAt
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min

// How busy a player is, for the body: the body goes with how much is being played over a few seconds, not with each
// stroke. Before, the body's drive rose at once with every note and fell again within a quarter of a second, so on a
// slow song the whole body jerked with each down-stroke of the strumming hand (the maintainer, 2026-11-23: "when the
// right hand does a down-stroke the whole body does a sudden movement ... the arm movement needs to be more
// decoupled from the body movement").

/** A note counts for the body from this many seconds before it sounds to as many after it. */
const val BUSY_REACH = 1.8

/** How much one note at full strength makes the body busy, at its own moment. */
const val BUSY_EACH = 0.6

/**
 * How busy the player of these notes (sorted by time) is at [t], 0 to 1: every note within [BUSY_REACH] seconds,
 * before or after (the body knows what comes), counts by its [strength] (0 to 1), most at its own moment and less
 * the further away, without a step anywhere. A lone note every few seconds stirs the body a little and slowly; a run
 * of notes keeps it going.
 */
fun <T : Timed> List<T>.busyAt(t: Double, strength: (T) -> Double): Double {
    // NaN-guard: at no time nobody is busy
    if (t != t) {
        return 0.0
    }

    var busy = 0.0
    var i = lastIndexAt(t + BUSY_REACH)

    while (i >= 0 && this[i].t > t - BUSY_REACH) {
        val away = abs(this[i].t - t) / BUSY_REACH

        busy += BUSY_EACH * strength(this[i]) * (0.5 + 0.5 * cos(PI * min(1.0, away)))
        i--
    }

    return min(1.0, busy)
}
