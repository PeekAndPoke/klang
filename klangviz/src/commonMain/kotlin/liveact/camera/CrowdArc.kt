/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.camera

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The arc behind the crowd (KLANG-00489; the maintainer, 2026-11-26: "another angle that arcs around the back of the
 * crowd pointing at the stage. Different starting and end positions each time"): the camera goes slowly along the
 * audience's half ring, high over their heads, looking at the stage. It is cut to and cut away from, never flown
 * ("Cut to, no flight").
 *
 * Where (the maintainer, the same day): "a bit lower and closer than the FOH angle and a bit more of the wall behind
 * the stage visible (in height)". The mixing desk ([STADIUM]) stands 46 m back and 11 m up and looks down on the
 * ground before the stage; the arc runs [FAR] m from the stage's middle at about [HIGH] m and looks at the stage's
 * middle a little above its floor ([AT]), so the picture tilts up and shows more of the wall behind.
 */
object CrowdArc {
    /**
     * How far from the stage's middle the arc runs, in metres: the crowd stands from 11.5 m to 62 m (`CrowdPlaces`).
     * It was 38 m (the maintainer, 2026-11-26: "and closer a bit").
     */
    const val FAR = 33.0

    /** How high the camera goes, in metres, and how far a round's start or end may lie above or below that. */
    const val HIGH = 8.0
    const val HIGH_SWAY = 0.8

    /** Where it looks: the stage's middle, this high. */
    val AT: Triple<Double, Double, Double> = Triple(0.0, 1.4, 0.0)

    /** Its lens, a little wider than the mixing desk's (27). */
    const val FOV = 28.0

    /** How far round the half ring it may go to either side, in radians from straight before the stage (60°). */
    const val WIDEST = PI / 3

    /**
     * How far it goes along the ring in a round, at least and at most, in radians (40° to 80°), and the bars of the
     * round in which it is shown, cut to at the first and cut away at the second, at the round's end (28 bars, some
     * 46 s). Twice as far in twice the time as at first (20° to 40° in 14 bars; the maintainer, 2026-11-26: "the arc
     * move could be twice as long or even longer"), so as fast as it was. It begins two bars after the round's early
     * looks have ended.
     */
    const val LEAST = 2 * PI / 9

    /** How far it goes along the ring in a round at most, in radians (80°): see [LEAST]. */
    const val MOST = 4 * PI / 9

    /** The bar of a round at which it is cut to: see [LEAST]. */
    const val FROM = 20

    /** The bar of a round at which it is cut away from, the round's end: see [LEAST]. */
    const val TO = 48

    /**
     * Where the arc of a round goes: from [start] to [end] (radians from straight before the stage, + to the stage's
     * right as the audience sees it, that is toward +x), at the heights [startHigh] and [endHigh].
     */
    data class Way(val start: Double, val end: Double, val startHigh: Double, val endHigh: Double)

    /**
     * The way of a round from three numbers from 0 to 1 its seed gives: [side] which way it goes (below a half from
     * the left to the right, else back) and where along the ring it begins, [length] how far it goes, [high] how high
     * it begins and ends. It begins where the whole way fits on the ring ([WIDEST]). NaN-guard: a number of no number
     * is 0.5.
     */
    fun way(side: Double, length: Double, high: Double): Way {
        val s = share(side)
        val l = share(length)
        val h = share(high)
        val sweep = LEAST + (MOST - LEAST) * l
        // which way, and how far in from the ring's end it begins, as a share of the room the way leaves
        val going = if (s < 0.5) 1 else -1
        val inward = if (s < 0.5) s * 2 else (s - 0.5) * 2
        val start = going * (-WIDEST + (2 * WIDEST - sweep) * inward)
        val end = start + going * sweep

        return Way(
            start = start,
            end = end,
            startHigh = HIGH + HIGH_SWAY * (2 * h - 1),
            endHigh = HIGH - HIGH_SWAY * (2 * h - 1),
        )
    }

    /** The camera [u] of the way along [way] (0 to 1, clamped; no number: its start), in the world. */
    fun at(way: Way, u: Double): Placement {
        // NaN-guard: no number is the start
        val x = if (u != u) 0.0 else u.coerceIn(0.0, 1.0)
        // a little slower at both ends than in the middle, never still: it is cut to and from while moving
        val e = x - 0.15 * sin(2 * PI * x) / (2 * PI)
        val angle = way.start + (way.end - way.start) * e
        val high = way.startHigh + (way.endHigh - way.startHigh) * e

        return Placement(pos = Triple(FAR * sin(angle), high, FAR * cos(angle)), at = AT, fov = FOV)
    }

    /** [x] as a share, 0 to 1. NaN-guard: what is no number is the middle. */
    private fun share(x: Double): Double = if (x != x) 0.5 else x.coerceIn(0.0, 1.0)
}
