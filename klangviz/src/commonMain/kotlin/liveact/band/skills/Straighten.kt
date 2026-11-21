/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.skills

import io.peekandpoke.klang.viz.liveact.smooth
import kotlin.math.floor

/**
 * Straightening up: a member who stands bent over an instrument straightens up now and then. The back comes up, the
 * chest opens, the head lifts and looks out over the audience; a while later the member sinks back over the
 * instrument (the maintainer, 2026-11-17: "Currently they are always hunched over"). Only a string player's pose
 * shows it so far.
 *
 * When: the time is cut into stretches of [every] seconds, and in a stretch the member straightens once, if its
 * chance is under [chance] (0 never, 1 in every stretch). Slowly up over [RISE] seconds, held between [SHORTEST] and
 * [LONGEST] seconds, more slowly down over [SINK]. One time ends before the next begins: [every] is at least [LEAST].
 * A chance in slots, by a formula of its own that is older than `ChanceInSlots` (see `Trigger`).
 */
data class Straighten(val every: Double, val chance: Double) : Skill {

    init {
        require(every >= LEAST) { "A stretch of $every s is too short to straighten up in: at least $LEAST s" }
    }

    /**
     * How straight the member stands at [t] (seconds), from 0 (as always) to 1 (fully straight). [roll] gives the
     * chance that belongs to a stretch (its number, from 0 on) and a purpose (0, 1, 2), from 0 to 1, always the same
     * one: so the same moment has the same picture. Before the song (t under 0) the member stands as always.
     */
    fun at(t: Double, roll: (stretch: Int, purpose: Int) -> Double): Double {
        if (t < 0) {
            return 0.0
        }

        val stretch = floor(t / every).toInt()

        if (roll(stretch, 0) >= chance) {
            return 0.0
        }

        val hold = SHORTEST + (LONGEST - SHORTEST) * roll(stretch, 2)
        val whole = RISE + hold + SINK
        val start = stretch * every + roll(stretch, 1) * (every - whole)
        val x = t - start

        return smooth(x / RISE) * smooth((whole - x) / SINK)
    }

    companion object {
        const val RISE = 1.8
        const val SINK = 2.6
        const val SHORTEST = 4.0
        const val LONGEST = 9.0

        /** The shortest stretch: a whole time of standing straight fits into it. */
        const val LEAST = RISE + LONGEST + SINK
    }
}
