/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.lights

import io.peekandpoke.klang.viz.liveact.hash01
import io.peekandpoke.klang.viz.liveact.stage.GhostLight
import kotlin.math.floor

/**
 * The ghosts' visits to a show whose world drifts (KLANG-00478; the maintainer, 2026-11-21, of the ghosts' scenery:
 * "A scenery like this also never appear during the performance ... would be nice if they would appear in the three
 * different colors, but not too often"): now and then the stage turns to the ghosts for a while and back to where
 * the drift is, each visit in the next of the three colours.
 *
 * The show's time is cut in rounds of [ROUND] seconds. Each round has one chance ([CHANCE]) of a visit of [HOLD]
 * seconds, which begins somewhere in the round's first part, so two visits never touch; none begins before [FIRST]
 * seconds of the song. Which rounds have one, where it begins and which colour comes first go by the show's seed
 * alone: a visit is the same whenever the show is asked.
 */
object GhostVisits {
    /** How long a round is, in seconds: at most one visit in each. */
    const val ROUND = 110.0

    /** The chance of a visit in a round. */
    const val CHANCE = 0.65

    /** How long a visit stays, in seconds, the turn to the ghosts included ([TURN_SECONDS]); the turn back comes after. */
    const val HOLD = 26.0

    /** How long the stage takes to turn to the ghosts and back, in seconds: slowly, as a mood creeps in. */
    const val TURN_SECONDS = 9.0

    /** No visit begins before this many seconds of the song. */
    const val FIRST = 60.0

    /** The latest a visit begins in its round, in seconds: so it is over, its turn back too, when the round ends. */
    private const val LATEST = ROUND - HOLD - TURN_SECONDS - 5.0

    /** The three colours, in the order the visits take them (the first by the seed). */
    private val COLOURS = listOf(GhostLight.Cold, GhostLight.Red, GhostLight.Green)

    /** Whether the ghosts visit at the show's time [t] (seconds) of the show of [seed]. No time of no number. */
    fun visiting(t: Double, seed: Int): Boolean {
        val round = roundOf(t) ?: return false
        val from = beginOf(round = round, seed = seed) ?: return false

        return t >= from && t < from + HOLD
    }

    /**
     * The ghosts' colour at [t]: the colour of the visit that runs, or of the last one that began, so it stays while
     * the stage turns back from it; before the first visit, the colour the first one will have.
     */
    fun light(t: Double, seed: Int): GhostLight {
        val round = roundOf(t) ?: 0
        // the visits begun up to now: this round's, where it has begun, and all of the rounds before
        var begun = 0

        for (r in 0..round) {
            val from = beginOf(round = r, seed = seed) ?: continue

            if (r < round || t >= from) {
                begun++
            }
        }

        val first = floor(hash01(seed * 0.618 + 77.7) * COLOURS.size).toInt().coerceIn(0, COLOURS.size - 1)

        return COLOURS[(first + maxOf(0, begun - 1)) % COLOURS.size]
    }

    /** The round [t] lies in, from 0; null before the song and for no number. */
    private fun roundOf(t: Double): Int? {
        // NaN-guard: no time is no round
        if (t != t || t < 0.0 || !t.isFinite()) {
            return null
        }

        return floor(t / ROUND).toInt()
    }

    /** When the visit of [round] begins, in seconds; null where the round has none. */
    private fun beginOf(round: Int, seed: Int): Double? {
        if (hash01(round * 127.1 + seed * 0.618 + 5.3) >= CHANCE) {
            return null
        }

        val from = round * ROUND + hash01(round * 311.7 + seed * 0.618 + 9.1) * LATEST

        return from.takeIf { it >= FIRST }
    }
}
