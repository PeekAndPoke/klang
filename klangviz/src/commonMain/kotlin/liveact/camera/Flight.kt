/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.camera

import io.peekandpoke.klang.viz.liveact.smoother

// The flight between the total and the mixing desk far back in the audience (the FOH): the camera travels from the
// total backward and upward over the audience's lights until the whole stage is small above them, stays there a
// while, and travels back. A round with a flight is on the total first, as every round is.

/** How a round with a flight goes. */
enum class FlightWay {
    /** It flies out, stays, and flies back in: no cut at all. */
    OutAndBack,

    /** It flies out and stays; back to the total by a cut. */
    OutThenCut,

    /** To the desk by a cut; it stays and flies back in. */
    CutThenIn,
}

/** When a round with a flight does what, in bars from the round's start. */
object Flight {
    /** The flight out begins. */
    const val OUT = 20.0

    /** The cut to the desk, where it does not fly out. */
    const val CUT = 28.0

    /** It has arrived at the desk. */
    const val THERE = 30.0

    /** The flight back in begins; or the cut back to the total, where it does not fly in. */
    const val IN = 38.0

    /** The round is over: it is on the total again. */
    const val END = 48.0

    /** The way of a round, by its [chance] (0 to 1): a third each. */
    fun way(chance: Double): FlightWay = when {
        chance < 1.0 / 3 -> FlightWay.OutAndBack
        chance < 2.0 / 3 -> FlightWay.OutThenCut
        else -> FlightWay.CutThenIn
    }

    /**
     * How far the camera is from the total (0) toward the desk (1) at bar [x] of a round that goes [way]. A flight
     * leaves and arrives very softly; a cut is a jump between 0 and 1.
     */
    fun share(way: FlightWay, x: Double): Double {
        val out = when (way) {
            FlightWay.CutThenIn -> if (x >= CUT) 1.0 else 0.0
            FlightWay.OutAndBack, FlightWay.OutThenCut -> smoother((x - OUT) / (THERE - OUT))
        }

        if (x < IN) {
            return out
        }

        return when (way) {
            FlightWay.OutThenCut -> 0.0
            FlightWay.OutAndBack, FlightWay.CutThenIn -> 1.0 - smoother((x - IN) / (END - IN))
        }
    }
}
