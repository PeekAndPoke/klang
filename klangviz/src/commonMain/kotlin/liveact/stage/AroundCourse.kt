/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.hash01
import io.peekandpoke.klang.viz.liveact.lights.GhostVisits
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/**
 * Which surrounding stands at a moment of the show, and how far it is there (0 to 1: it fades out before a turn and
 * in after it). [surrounding] is none where the look has none ([AroundLook.Off]).
 */
data class AroundMoment(val surrounding: Surrounding?, val presence: Double)

/**
 * When the surroundings turn (KLANG-00478; the maintainer, 2026-11-21: "every time it is really dark we flip the
 * surrounding to another one"), as a pure function of the show's time and seed: a still at a given time always shows
 * the same one.
 *
 * The moments that are really dark are the ghosts' visits ([GhostVisits]): the stage and the band are all but dark for
 * half a minute, all the light in the haze behind the fence, and the surroundings nearly gone. In the middle of each
 * visit ([FLIP_AFTER] seconds after it begins) the surrounding fades out over [FADE] seconds, turns to the next one, and
 * fades in over as long: unseen, as the ghosts are fully there then. The pitch black before the walk-in (`?opening=ember`)
 * needs no turn: the show begins in the first one, the forest, which comes up with the house. The drift's deepest
 * night is not dark enough (the band stands in the lamps' light), and a world held by hand has no visits: the forest
 * stays.
 *
 * The order goes by the show's seed: the forest first (the maintainer's own idea, the first and the default), then the
 * others in an order the seed picks, round and round, so all of them come by turns and none twice in a row.
 */
object AroundCourse {
    /** How long after a ghosts' visit begins the surrounding turns, in seconds: in its middle. */
    const val FLIP_AFTER = GhostVisits.HOLD / 2

    /** How long the surrounding takes to fade out before a turn, and in after it, in seconds. */
    const val FADE = 4.0

    /** The surroundings in the order the show of [seed] takes them: the forest, then the others as the seed has it. */
    fun order(seed: Int): List<Surrounding> {
        val rest = Surrounding.entries.filter { it != Surrounding.Forest }

        return listOf(Surrounding.Forest) + rest.sortedBy { hash01(seed * 0.618 + it.ordinal * 13.7 + 3.1) }
    }

    /** When the surrounding turns in round [round] of the ghosts' visits ([GhostVisits.ROUND]); null where none visit. */
    fun flipOf(round: Int, seed: Int): Double? = GhostVisits.beginOf(round = round, seed = seed)?.let { it + FLIP_AFTER }

    /**
     * The surrounding at the show's time [t] (seconds) of the show of [seed], with the look at [look]: none where it is
     * off; the one held where one is ([AroundLook.held]); by turns ([AroundLook.Turns]) where the world [drifts] (only
     * then the ghosts visit), else the first. A [t] of no number, or an endless one, is the first, fully there.
     */
    fun at(t: Double, seed: Int, look: AroundLook, drifts: Boolean): AroundMoment {
        val order = order(seed)

        when (look) {
            AroundLook.Off -> return AroundMoment(surrounding = null, presence = 0.0)
            AroundLook.Forest, AroundLook.Ruins, AroundLook.Stones -> return AroundMoment(surrounding = look.held, presence = 1.0)
            AroundLook.Turns -> Unit
        }

        // NaN-guard: no time is the first, as the show begins
        if (!drifts || t != t || !t.isFinite()) {
            return AroundMoment(surrounding = order.first(), presence = 1.0)
        }

        // the turns up to now, and how far off the nearest one is, before or after; a turn lies inside its own round,
        // so the rounds up to the next one cover both
        var turns = 0
        var nearest = Double.POSITIVE_INFINITY
        val last = floor(t / GhostVisits.ROUND).toInt() + 1

        for (round in 0..last) {
            val flip = flipOf(round = round, seed = seed) ?: continue

            if (flip <= t) {
                turns++
            }

            nearest = min(nearest, abs(flip - t))
        }

        val x = min(1.0, nearest / FADE)

        return AroundMoment(surrounding = order[turns % order.size], presence = x * x * (3 - 2 * x))
    }
}
